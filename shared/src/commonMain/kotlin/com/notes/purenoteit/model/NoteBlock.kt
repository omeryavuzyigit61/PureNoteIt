package com.notes.purenoteit.model

import com.notes.purenoteit.sync.sharedRandomUuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class BlockType {
    PARAGRAPH,
    HEADING_1,
    HEADING_2,
    TODO,
    BULLET_LIST,
    CALLOUT,
    TOGGLE,
    TABLE,
    DIVIDER,
    AUDIO,
    IMAGE,
    DRAWING,
    WEB_BOOKMARK,
    SUB_PAGE,
    CODE,
    MATH,
    TABLE_OF_CONTENTS,
    PAGE_RELATIONS
}

@Serializable
data class NoteBlock(
    val id: String = sharedRandomUuid(),
    var type: BlockType = BlockType.PARAGRAPH,
    var content: String = "",
    var isChecked: Boolean = false,
    var calloutIcon: String = "💡",
    var mediaPath: String? = null,
    var isExpanded: Boolean = true,
    var tableData: List<List<String>> = emptyList(),
    var toggleBody: String = "",
    var codeLanguage: String = "kotlin",
    var subPageNoteId: Int? = null,
    var subPageSyncUuid: String? = null,
    /** Vector ink payload (see [InkCodec]) for pen-first DRAWING blocks; empty for legacy PNG-only drawings. */
    var inkJson: String = ""
)

data class SearchSnippetMatch(
    val snippet: String,
    val matchStart: Int,
    val matchEnd: Int
)

@Serializable
data class NoteRichMetadata(
    val isSubPage: Boolean = false,
    val parentNoteId: Int? = null,
    val parentSyncUuid: String? = null
)

data class SubPageHierarchyRefs(
    val syncUuids: Set<String> = emptySet(),
    val legacyIdToTitle: Map<Int, String> = emptyMap(),
    val titleFallbacks: Set<String> = emptySet()
)

object BlockParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }
    private const val PREFIX = "NOTION_BLOCKS_V1:"

    private val LEGACY_HEADING_EMOJIS = listOf(
        "📌", "🎯", "✅", "💡", "🚀", "⚡", "🧘", "🧳", "🗺️", "✨", "🔥", "💎",
        "🥗", "💪", "🌿", "📊", "📋", "🧭", "📚", "📖", "💻", "📐", "🎓", "🏛️", "🏛"
    )

    fun sanitizeHeading(content: String): String {
        var clean = content.trim()
        for (prefix in LEGACY_HEADING_EMOJIS) {
            if (clean.startsWith(prefix)) {
                clean = clean.removePrefix(prefix).trimStart()
            }
        }
        return clean
    }

    fun blocksToJson(blocks: List<NoteBlock>): String {
        return PREFIX + json.encodeToString(blocks)
    }

    fun serialize(blocks: List<NoteBlock>): String {
        return blocksToJson(blocks)
    }

    fun buildSubPageMetadataJson(parentNoteId: Int?, parentSyncUuid: String? = null): String {
        return json.encodeToString(
            NoteRichMetadata(
                isSubPage = true,
                parentNoteId = parentNoteId,
                parentSyncUuid = parentSyncUuid?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun parseRichMetadata(rawJson: String?): NoteRichMetadata? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            json.decodeFromString<NoteRichMetadata>(rawJson)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether [rawJson] marks a note as a child subpage without false-positives on explicit null parent IDs.
     */
    fun isSubPageMetadata(rawJson: String?): Boolean {
        if (rawJson.isNullOrBlank()) return false
        val parsed = parseRichMetadata(rawJson)
        if (parsed != null) {
            return parsed.isSubPage || parsed.parentNoteId != null || !parsed.parentSyncUuid.isNullOrBlank()
        }
        return rawJson.contains("\"isSubPage\":true") ||
            Regex(""""parentNoteId"\s*:\s*\d+""").containsMatchIn(rawJson)
    }

    /**
     * Indexes all `SUB_PAGE` block pointers across decrypted descriptions so root note filtering
     * prefers cross-device `subPageSyncUuid` and guards legacy `subPageNoteId` against collisions.
     */
    fun collectSubPageHierarchyRefs(descriptions: Iterable<String?>): SubPageHierarchyRefs {
        val uuids = LinkedHashSet<String>()
        val legacyIds = LinkedHashMap<Int, String>()
        val titles = LinkedHashSet<String>()
        for (rawDesc in descriptions) {
            if (rawDesc.isNullOrBlank() || !rawDesc.startsWith(PREFIX)) continue
            for (blk in parse(rawDesc)) {
                if (blk.type != BlockType.SUB_PAGE) continue
                val subUuid = blk.subPageSyncUuid?.takeIf { it.isNotBlank() }
                if (subUuid != null) {
                    uuids += subUuid
                } else if (blk.subPageNoteId != null && blk.subPageNoteId!! > 0) {
                    legacyIds[blk.subPageNoteId!!] = blk.content.trim()
                } else if (blk.subPageNoteId == null && blk.content.isNotBlank()) {
                    titles += blk.content.trim().lowercase()
                }
            }
        }
        return SubPageHierarchyRefs(syncUuids = uuids, legacyIdToTitle = legacyIds, titleFallbacks = titles)
    }

    /**
     * Determines whether a note should be treated as a child subpage in root note lists.
     * Legacy integer IDs only match when metadata confirms subpage status or titles match.
     */
    fun isSubPageNote(
        noteId: Int,
        noteSyncUuid: String,
        noteTitle: String?,
        richBlocksJson: String?,
        refs: SubPageHierarchyRefs
    ): Boolean {
        val byMetadata = isSubPageMetadata(richBlocksJson)
        if (byMetadata) return true
        if (noteSyncUuid.isNotBlank() && noteSyncUuid in refs.syncUuids) return true
        val legacyTitle = if (noteId > 0) refs.legacyIdToTitle[noteId] else null
        if (legacyTitle != null) {
            val cleanNoteTitle = noteTitle.orEmpty().trim()
            if (legacyTitle.isBlank() || cleanNoteTitle.equals(legacyTitle, ignoreCase = true)) {
                return true
            }
        }
        val cleanLowerTitle = noteTitle.orEmpty().trim().lowercase()
        return cleanLowerTitle.isNotBlank() && cleanLowerTitle in refs.titleFallbacks && byMetadata
    }

    fun parse(raw: String?): List<NoteBlock> {
        if (raw.isNullOrBlank()) {
            return listOf(NoteBlock(type = BlockType.PARAGRAPH, content = ""))
        }

        // Case 1: JSON blocks format (supports legacy Gson JSON and current kotlinx.serialization payloads)
        if (raw.startsWith(PREFIX)) {
            val payload = raw.removePrefix(PREFIX)
            return try {
                val list: List<NoteBlock> = json.decodeFromString(payload)
                list.forEach { b ->
                    if (b.codeLanguage.isBlank()) {
                        b.codeLanguage = "kotlin"
                    }
                    if (b.type == BlockType.HEADING_1 || b.type == BlockType.HEADING_2) {
                        b.content = sanitizeHeading(b.content)
                    }
                }
                if (list.isEmpty()) listOf(NoteBlock()) else list
            } catch (_: Exception) {
                listOf(NoteBlock(type = BlockType.PARAGRAPH, content = raw))
            }
        }

        // Case 2: Legacy markdown / plain text -> Parse into blocks seamlessly
        val blocks = mutableListOf<NoteBlock>()
        val lines = raw.lines()

        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    blocks.add(NoteBlock(type = BlockType.HEADING_1, content = sanitizeHeading(line.removePrefix("# "))))
                }
                line.startsWith("## ") -> {
                    blocks.add(NoteBlock(type = BlockType.HEADING_2, content = sanitizeHeading(line.removePrefix("## "))))
                }
                line.startsWith("[ ] ") -> {
                    blocks.add(NoteBlock(type = BlockType.TODO, content = line.removePrefix("[ ] "), isChecked = false))
                }
                line.startsWith("[x] ") || line.startsWith("[X] ") -> {
                    blocks.add(NoteBlock(type = BlockType.TODO, content = line.substring(4), isChecked = true))
                }
                line.startsWith("• ") || line.startsWith("- ") -> {
                    blocks.add(NoteBlock(type = BlockType.BULLET_LIST, content = line.substring(2)))
                }
                line.startsWith("> ") -> {
                    blocks.add(NoteBlock(type = BlockType.CALLOUT, content = line.removePrefix("> ")))
                }
                line.startsWith("```") -> {
                    val lang = line.removePrefix("```").trim()
                    blocks.add(NoteBlock(type = BlockType.CODE, codeLanguage = if (lang.isNotBlank()) lang else "kotlin"))
                }
                line.startsWith("$$") -> {
                    blocks.add(NoteBlock(type = BlockType.MATH, content = line.removePrefix("$$").removeSuffix("$$").trim()))
                }
                line.trim() == "---" -> {
                    blocks.add(NoteBlock(type = BlockType.DIVIDER))
                }
                else -> {
                    blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = line))
                }
            }
        }

        return if (blocks.isEmpty()) listOf(NoteBlock()) else blocks
    }

    fun calculateTasks(blocks: List<NoteBlock>): Pair<Int, Int> {
        val todoBlocks = blocks.filter { it.type == BlockType.TODO }
        val total = todoBlocks.size
        val completed = todoBlocks.count { it.isChecked }
        return Pair(total, completed)
    }

    /**
     * Deep In-Content Search Snippet Extractor:
     * Searches all block types (paragraphs, headings, code, formulas, tables, checklists)
     * and extracts the context around the matched query with start/end indices for highlighting.
     */
    fun findSearchSnippet(rawDescription: String?, query: String): SearchSnippetMatch? {
        if (rawDescription.isNullOrBlank() || query.isBlank()) return null
        val blocks = parse(rawDescription)
        val q = query.trim().lowercase()

        for (block in blocks) {
            val textsToSearch = mutableListOf<String>()
            if (block.content.isNotBlank()) textsToSearch.add(block.content)
            if (block.toggleBody.isNotBlank()) textsToSearch.add(block.toggleBody)
            if (block.tableData.isNotEmpty()) {
                block.tableData.forEach { row ->
                    textsToSearch.add(row.joinToString(" | "))
                }
            }

            for (text in textsToSearch) {
                val idx = text.indexOf(q, ignoreCase = true)
                if (idx != -1) {
                    val start = maxOf(0, idx - 22)
                    val end = minOf(text.length, idx + q.length + 32)
                    val prefix = if (start > 0) "…" else ""
                    val suffix = if (end < text.length) "…" else ""
                    val cleanSnippet = prefix + text.substring(start, end).replace("\n", " ").trim() + suffix

                    val snippetMatchIdx = cleanSnippet.indexOf(q, ignoreCase = true)
                    return if (snippetMatchIdx != -1) {
                        SearchSnippetMatch(cleanSnippet, snippetMatchIdx, snippetMatchIdx + q.length)
                    } else {
                        SearchSnippetMatch(cleanSnippet, 0, 0)
                    }
                }
            }
        }
        return null
    }

    private fun isLikelyMetadataJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") && trimmed.endsWith("}")
    }

    private fun cleanHumanContent(block: NoteBlock): String =
        if (isLikelyMetadataJson(block.content)) "" else block.content.trim()

    fun toPlainTextSummary(
        blocks: List<NoteBlock>,
        maxLines: Int = 4,
        includeDecorations: Boolean = true
    ): String {
        return blocks
            .filter { block ->
                when (block.type) {
                    BlockType.DIVIDER, BlockType.PAGE_RELATIONS -> false
                    BlockType.TABLE -> block.tableData.isNotEmpty()
                    BlockType.IMAGE, BlockType.DRAWING, BlockType.AUDIO ->
                        !block.mediaPath.isNullOrBlank() || cleanHumanContent(block).isNotBlank()
                    else -> block.content.isNotBlank()
                }
            }
            .take(maxLines)
            .joinToString("\n") { block ->
                when (block.type) {
                    BlockType.TODO -> if (includeDecorations) {
                        if (block.isChecked) "☑ ${block.content}" else "☐ ${block.content}"
                    } else {
                        block.content
                    }
                    BlockType.BULLET_LIST -> "• ${block.content}"
                    BlockType.CALLOUT -> if (includeDecorations && block.calloutIcon.isNotBlank()) {
                        "${block.calloutIcon} ${block.content}"
                    } else {
                        block.content
                    }
                    BlockType.TOGGLE -> if (includeDecorations) "▶ ${block.content}" else block.content
                    BlockType.TABLE -> {
                        val rows = (block.tableData.size - 1).coerceAtLeast(0)
                        if (includeDecorations) "📊 Tablo ($rows satır)" else "Tablo ($rows satır)"
                    }
                    BlockType.WEB_BOOKMARK -> if (includeDecorations) "🌐 ${block.content}" else block.content
                    BlockType.SUB_PAGE -> if (includeDecorations) "📄 ${block.content}" else block.content
                    BlockType.CODE -> if (includeDecorations) {
                        "💻 Kod (${block.codeLanguage}): ${block.content.take(30)}"
                    } else {
                        "Kod (${block.codeLanguage}): ${block.content.take(30)}"
                    }
                    BlockType.MATH -> "∑ ${block.content}"
                    BlockType.TABLE_OF_CONTENTS -> if (includeDecorations) "📑 İçindekiler Tablosu" else "İçindekiler"
                    BlockType.IMAGE -> cleanHumanContent(block).ifBlank {
                        if (includeDecorations) "🖼️ Görsel" else "Görsel"
                    }
                    BlockType.DRAWING -> cleanHumanContent(block).ifBlank {
                        if (includeDecorations) "✏️ Çizim" else "Çizim"
                    }
                    BlockType.AUDIO -> cleanHumanContent(block).ifBlank {
                        if (includeDecorations) "🎙️ Ses Kaydı" else "Ses Kaydı"
                    }
                    else -> block.content
                }
            }
    }
}
