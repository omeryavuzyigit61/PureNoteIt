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
    var subPageSyncUuid: String? = null
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

    fun buildSubPageMetadataJson(parentNoteId: Int, parentSyncUuid: String? = null): String {
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

    fun toPlainTextSummary(blocks: List<NoteBlock>, maxLines: Int = 4): String {
        return blocks
            .filter { it.type != BlockType.DIVIDER && (it.content.isNotBlank() || it.tableData.isNotEmpty()) }
            .take(maxLines)
            .joinToString("\n") { block ->
                when (block.type) {
                    BlockType.TODO -> if (block.isChecked) "☑ ${block.content}" else "☐ ${block.content}"
                    BlockType.BULLET_LIST -> "• ${block.content}"
                    BlockType.CALLOUT -> "${block.calloutIcon} ${block.content}"
                    BlockType.TOGGLE -> "▶ ${block.content}"
                    BlockType.TABLE -> "📊 Tablo (${if (block.tableData.isNotEmpty()) block.tableData.size - 1 else 0} satır)"
                    BlockType.WEB_BOOKMARK -> "🌐 ${block.content}"
                    BlockType.SUB_PAGE -> "📄 ${block.content}"
                    BlockType.CODE -> "💻 Kod (${block.codeLanguage}): ${block.content.take(30)}"
                    BlockType.MATH -> "∑ ${block.content}"
                    BlockType.TABLE_OF_CONTENTS -> "📑 İçindekiler Tablosu"
                    else -> block.content
                }
            }
    }
}
