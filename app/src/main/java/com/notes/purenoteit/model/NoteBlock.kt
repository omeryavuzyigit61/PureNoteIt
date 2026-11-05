package com.notes.purenoteit.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

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

data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    var type: BlockType = BlockType.PARAGRAPH,
    var content: String = "",
    var isChecked: Boolean = false,
    var calloutIcon: String = "💡",
    var mediaPath: String? = null,
    var isExpanded: Boolean = true,
    var tableData: List<List<String>> = emptyList(),
    var toggleBody: String = "",
    var codeLanguage: String = "kotlin",
    var subPageNoteId: Int? = null
)

data class SearchSnippetMatch(
    val snippet: String,
    val matchStart: Int,
    val matchEnd: Int
)

object BlockParser {
    private val gson = Gson()
    private const val PREFIX = "NOTION_BLOCKS_V1:"

    fun blocksToJson(blocks: List<NoteBlock>): String {
        return PREFIX + gson.toJson(blocks)
    }

    fun serialize(blocks: List<NoteBlock>): String {
        return blocksToJson(blocks)
    }

    fun parse(raw: String?): List<NoteBlock> {
        if (raw.isNullOrBlank()) {
            return listOf(NoteBlock(type = BlockType.PARAGRAPH, content = ""))
        }

        // Case 1: JSON blocks format
        if (raw.startsWith(PREFIX)) {
            val json = raw.removePrefix(PREFIX)
            return try {
                val type = object : TypeToken<List<NoteBlock>>() {}.type
                val list: List<NoteBlock> = gson.fromJson(json, type)
                list.forEach { b ->
                    if (b.codeLanguage.isNullOrBlank()) {
                        b.codeLanguage = "kotlin"
                    }
                }
                if (list.isEmpty()) listOf(NoteBlock()) else list
            } catch (e: Exception) {
                listOf(NoteBlock(type = BlockType.PARAGRAPH, content = raw))
            }
        }

        // Case 2: Legacy markdown / plain text -> Parse into blocks seamlessly
        val blocks = mutableListOf<NoteBlock>()
        val lines = raw.lines()

        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    blocks.add(NoteBlock(type = BlockType.HEADING_1, content = line.removePrefix("# ")))
                }
                line.startsWith("## ") -> {
                    blocks.add(NoteBlock(type = BlockType.HEADING_2, content = line.removePrefix("## ")))
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
