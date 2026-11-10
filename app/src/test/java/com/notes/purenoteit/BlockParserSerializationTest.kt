package com.notes.purenoteit

import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.util.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockParserSerializationTest {

    @Test
    fun `kotlinx serialization round-trip alt sayfa syncUuid ve goreceli medya yollarini korumalidir`() {
        val original = listOf(
            NoteBlock(id = "b1", type = BlockType.HEADING_1, content = "Proje Planı"),
            NoteBlock(id = "b2", type = BlockType.TODO, content = "Faz 1", isChecked = true),
            NoteBlock(
                id = "b3",
                type = BlockType.IMAGE,
                content = "",
                mediaPath = "media/images/${"a".repeat(64)}.jpg"
            ),
            NoteBlock(
                id = "b4",
                type = BlockType.SUB_PAGE,
                content = "Alt Sayfa 1",
                subPageNoteId = 15,
                subPageSyncUuid = "sub-uuid-15"
            ),
            NoteBlock(
                id = "b5",
                type = BlockType.TABLE,
                tableData = listOf(listOf("Ad", "Durum"), listOf("Sync", "Hazır"))
            )
        )

        val serialized = BlockParser.serialize(original)
        assertTrue(serialized.startsWith("NOTION_BLOCKS_V1:"))
        assertTrue(serialized.contains("\"subPageNoteId\":15"))
        assertTrue(serialized.contains("\"subPageSyncUuid\":\"sub-uuid-15\""))

        val parsed = BlockParser.parse(serialized)
        assertEquals(5, parsed.size)
        assertEquals(BlockType.HEADING_1, parsed[0].type)
        assertEquals("Proje Planı", parsed[0].content)
        assertEquals(true, parsed[1].isChecked)
        assertEquals("media/images/${"a".repeat(64)}.jpg", parsed[2].mediaPath)
        assertEquals(15, parsed[3].subPageNoteId)
        assertEquals("sub-uuid-15", parsed[3].subPageSyncUuid)
        assertEquals("Hazır", parsed[4].tableData[1][1])
    }

    @Test
    fun `eski Gson formatindaki eksik ve null alanli NOTION_BLOCKS_V1 JSON kayipsiz okunmalidir`() {
        // Eski sürümler subPageSyncUuid alanını içermez ve codeLanguage/toggleBody bazen null olabilir.
        val legacyGsonRaw = "NOTION_BLOCKS_V1:" +
            """[
                {"id":"old-1","type":"HEADING_1","content":"📌 Eski Başlık","isChecked":false,"calloutIcon":"💡","mediaPath":null,"isExpanded":true,"tableData":null,"toggleBody":null,"codeLanguage":null,"subPageNoteId":null},
                {"id":"old-2","type":"SUB_PAGE","content":"Detay","subPageNoteId":42}
            ]"""

        val parsed = BlockParser.parse(legacyGsonRaw)
        assertEquals(2, parsed.size)
        assertEquals("old-1", parsed[0].id)
        assertEquals("Eski Başlık", parsed[0].content)
        assertEquals("kotlin", parsed[0].codeLanguage)
        assertEquals("", parsed[0].toggleBody)
        assertTrue(parsed[0].tableData.isEmpty())

        assertEquals(BlockType.SUB_PAGE, parsed[1].type)
        assertEquals(42, parsed[1].subPageNoteId)
        assertEquals(null, parsed[1].subPageSyncUuid)
    }

    @Test
    fun `subPage richBlocksJson metadata hem yeni DTO hem eski regex kontrolleriyle uyumlu olmalidir`() {
        val rawMeta = BlockParser.buildSubPageMetadataJson(parentNoteId = 99, parentSyncUuid = "parent-uuid-99")
        assertTrue(rawMeta.contains("\"isSubPage\":true"))
        assertTrue(Regex(""""parentNoteId"\s*:\s*99\b""").containsMatchIn(rawMeta))

        val parsedMeta = BlockParser.parseRichMetadata(rawMeta)
        assertNotNull(parsedMeta)
        assertEquals(true, parsedMeta?.isSubPage)
        assertEquals(99, parsedMeta?.parentNoteId)
        assertEquals("parent-uuid-99", parsedMeta?.parentSyncUuid)
    }

    @Test
    fun `Converters String listesini kotlinx serialization ile donusturmeli ve hatali veride guvenli donmelidir`() {
        val converters = Converters()
        val sample = listOf("media/images/abc.jpg", "media/audio/def.m4a")
        val json = converters.fromListString(sample)
        val decoded = converters.fromStringList(json)

        assertEquals(sample, decoded)
        assertEquals(emptyList<String>(), converters.fromStringList(null))
        assertEquals(emptyList<String>(), converters.fromStringList(""))
        assertEquals(emptyList<String>(), converters.fromStringList("{corrupt"))
    }

    @Test
    fun `eski markdown duz metin formatlari geriye donuk bloklara cevrilmelidir`() {
        val legacyMd = "# Merhaba\n[x] Görev Tamam\n> Önemli Not\n---"
        val blocks = BlockParser.parse(legacyMd)
        assertEquals(4, blocks.size)
        assertEquals(BlockType.HEADING_1, blocks[0].type)
        assertEquals("Merhaba", blocks[0].content)
        assertEquals(BlockType.TODO, blocks[1].type)
        assertEquals(true, blocks[1].isChecked)
        assertEquals(BlockType.CALLOUT, blocks[2].type)
        assertEquals(BlockType.DIVIDER, blocks[3].type)
    }
}
