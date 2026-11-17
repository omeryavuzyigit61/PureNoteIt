package com.notes.purenoteit

import com.notes.purenoteit.domain.usecase.StaticSyntaxChecker
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.sync.PureNotePlatform
import com.notes.purenoteit.sync.currentPlatformDisplayName
import com.notes.purenoteit.sync.currentPlatformTarget
import com.notes.purenoteit.sync.parseIso8601ToMillis
import com.notes.purenoteit.sync.sha256Hex
import com.notes.purenoteit.sync.sharedRandomUuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SharedIosTest {
    @Test
    fun platformAndPrimitivesWorkOnIos() {
        assertEquals(PureNotePlatform.IOS, currentPlatformTarget())
        assertEquals("iOS", currentPlatformDisplayName())

        val idA = sharedRandomUuid()
        val idB = sharedRandomUuid()
        assertNotEquals(idA, idB)
        assertTrue(idA.length >= 32)

        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            sha256Hex("hello".encodeToByteArray())
        )
        assertTrue(parseIso8601ToMillis("2026-11-17T12:00:00.000Z") > 0L)
        assertTrue(parseIso8601ToMillis("2026-11-17T12:00:00Z") > 0L)
    }

    @Test
    fun blockParserAndSyntaxCheckerWorkOnIos() {
        val block = SharedIosEditorBridge.createBlock(BlockType.TODO, "Test task #work", isChecked = true)
        val serialized = SharedIosEditorBridge.serializeBlocks(listOf(block))
        val parsed = BlockParser.parse(serialized)
        assertEquals(1, parsed.size)
        assertEquals(BlockType.TODO, parsed.first().type)
        assertEquals("Test task #work", parsed.first().content)
        assertTrue(parsed.first().isChecked)

        assertEquals(listOf("work"), SharedIosEditorBridge.extractHashtags("Test #work [[Plan]]"))
        assertEquals(listOf("Plan"), SharedIosEditorBridge.extractWikiLinks("Test #work [[Plan]]"))
        assertTrue(StaticSyntaxChecker.analyze("fun main() { println(\"hi\") }", "kotlin").isValid)
        assertFalse(StaticSyntaxChecker.analyze("fun main() {", "kotlin").isValid)
    }
}
