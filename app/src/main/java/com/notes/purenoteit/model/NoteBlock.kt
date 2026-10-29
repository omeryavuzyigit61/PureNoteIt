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
    DIVIDER,
    AUDIO,
    IMAGE
}

data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    var type: BlockType = BlockType.PARAGRAPH,
    var content: String = "",
    var isChecked: Boolean = false,
    var calloutIcon: String = "💡",
    var mediaPath: String? = null
)

object BlockParser {
    private val gson = Gson()
    private const val PREFIX = "NOTION_BLOCKS_V1:"

    fun blocksToJson(blocks: List<NoteBlock>): String {
        return PREFIX + gson.toJson(blocks)
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

    fun toPlainTextSummary(blocks: List<NoteBlock>, maxLines: Int = 4): String {
        return blocks
            .filter { it.type != BlockType.DIVIDER && it.content.isNotBlank() }
            .take(maxLines)
            .joinToString("\n") { block ->
                when (block.type) {
                    BlockType.TODO -> if (block.isChecked) "☑ ${block.content}" else "☐ ${block.content}"
                    BlockType.BULLET_LIST -> "• ${block.content}"
                    BlockType.CALLOUT -> "${block.calloutIcon} ${block.content}"
                    else -> block.content
                }
            }
    }
}
