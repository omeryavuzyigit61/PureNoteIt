package com.notes.purenoteit

import com.notes.purenoteit.model.*
import kotlin.test.*

class InkModelTest {
    private fun stroke(id: String, vararg xy: Pair<Float, Float>) =
        InkStroke(id, points = xy.map { InkPoint(it.first, it.second, 0.7f, 1L) })

    @Test
    fun codecRoundTrip() {
        val doc = InkDocument(strokes = listOf(stroke("a", 1f to 2f, 3f to 4f)))
        assertEquals(doc, InkCodec.decode(InkCodec.encode(doc)))
    }

    @Test
    fun corruptOrBlankDecodesToNull() {
        assertNull(InkCodec.decode(null))
        assertNull(InkCodec.decode(""))
        assertNull(InkCodec.decode("{not json"))
    }

    @Test
    fun eraseRemovesOnlyHitStrokes() {
        val doc = InkDocument(strokes = listOf(stroke("a", 0f to 0f), stroke("b", 100f to 100f)))
        val out = doc.eraseAt(1f, 1f, 5f)
        assertEquals(listOf("b"), out.strokes.map { it.id })
        assertSame(out, out.eraseAt(500f, 500f, 5f))
    }

    @Test
    fun legacyDrawingBlockWithoutInkStillParses() {
        val raw = "NOTION_BLOCKS_V1:[{\"id\":\"x\",\"type\":\"DRAWING\",\"mediaPath\":\"drawings/a.png\"}]"
        val b = BlockParser.parse(raw).single()
        assertEquals("", b.inkJson)
        assertEquals("drawings/a.png", b.mediaPath)
    }

    @Test
    fun inkSurvivesBlockSerialization() {
        val ink = InkCodec.encode(InkDocument(strokes = listOf(stroke("a", 1f to 1f))))
        val blocks = listOf(NoteBlock(type = BlockType.DRAWING, inkJson = ink))
        val back = BlockParser.parse(BlockParser.serialize(blocks)).single()
        assertEquals(ink, back.inkJson)
    }

    @Test
    fun widthScalesWithPressure() {
        val s = InkStroke("w", baseWidth = 4f)
        assertTrue(InkCodec.widthAt(s, InkPoint(0f, 0f, 1f)) > InkCodec.widthAt(s, InkPoint(0f, 0f, 0.1f)))
        val h = s.copy(tool = InkTool.HIGHLIGHTER)
        assertEquals(InkCodec.widthAt(h, InkPoint(0f, 0f, 1f)), InkCodec.widthAt(h, InkPoint(0f, 0f, 0.1f)))
    }
}
