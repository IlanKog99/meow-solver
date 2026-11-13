package com.ilan.meowsolver

import com.ilan.meowsolver.core.DebugLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DebugLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dir: File
    private val stamp = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} """)

    // DebugLog is a singleton: re-point it at a fresh dir for every test.
    @Before fun setUp() {
        dir = tmp.newFolder()
        DebugLog.init(dir)
    }

    private fun lines() = DebugLog.text().let { if (it.isEmpty()) emptyList() else it.lines() }

    @Test fun constants() {
        assertEquals(2000, DebugLog.MAX_LINES)
        assertEquals("debug.log", DebugLog.FILE_NAME)
    }

    @Test fun freshLogIsEmpty() = assertEquals("", DebugLog.text())

    @Test fun logLineIsTimestampedAndOrdered() {
        DebugLog.log("first")
        DebugLog.log("second")
        val l = lines()
        assertEquals(2, l.size)
        assertTrue(l[0], stamp.containsMatchIn(l[0]) && l[0].endsWith(" first"))
        assertTrue(l[1], stamp.containsMatchIn(l[1]) && l[1].endsWith(" second"))
    }

    @Test fun multilineMessageSplitsFirstTimestamped() {
        DebugLog.log("a\nb\nc")
        val l = lines()
        assertEquals(3, l.size)
        assertTrue(l[0], stamp.containsMatchIn(l[0]) && l[0].endsWith(" a"))
        assertTrue(l[1].endsWith("b"))
        assertTrue(l[2].endsWith("c"))
    }

    @Test fun persistsToFileAndSurvivesReinit() {
        DebugLog.log("persisted")
        assertTrue(File(dir, DebugLog.FILE_NAME).readText().contains("persisted"))
        val other = tmp.newFolder()
        DebugLog.init(other)
        assertFalse(DebugLog.text().contains("persisted"))
        DebugLog.init(dir)
        assertTrue(DebugLog.text().contains("persisted"))
    }

    @Test fun reinitSameDirDoesNotDuplicate() {
        DebugLog.log("once")
        DebugLog.init(dir)
        DebugLog.init(dir)
        assertEquals(1, lines().count { it.endsWith(" once") })
        assertEquals(1, lines().size)
    }

    @Test fun errorIncludesClassMessageAndStackTrace() {
        DebugLog.error("ctx", IllegalStateException("boom"))
        val t = DebugLog.text()
        assertTrue(t, t.contains("ctx"))
        assertTrue(t, t.contains("IllegalStateException"))
        assertTrue(t, t.contains("boom"))
        assertTrue(t, t.contains("DebugLogTest")) // stack frame from this test
        assertTrue(lines().size > 1)
    }

    @Test fun clearEmptiesMemoryAndDisk() {
        DebugLog.log("x")
        DebugLog.clear()
        assertEquals("", DebugLog.text())
        DebugLog.init(tmp.newFolder())
        DebugLog.init(dir)
        assertEquals("", DebugLog.text())
    }

    @Test fun capDropsOldestKeepsNewestInMemoryAndOnDisk() {
        val n = DebugLog.MAX_LINES + 50
        for (i in 0 until n) DebugLog.log("m$i.")
        val l = lines()
        assertTrue("size ${l.size}", l.size <= DebugLog.MAX_LINES)
        assertTrue(l.last().endsWith(" m${n - 1}."))
        assertFalse(DebugLog.text().contains(" m0."))
        DebugLog.init(tmp.newFolder())
        DebugLog.init(dir)
        assertTrue(lines().size <= DebugLog.MAX_LINES)
        assertTrue(lines().last().endsWith(" m${n - 1}."))
    }

    @Test fun capWithHugeMultilineMessageKeepsMostRecent() {
        DebugLog.log("old")
        DebugLog.log((0 until DebugLog.MAX_LINES + 10).joinToString("\n") { "x$it" } + "\nLAST")
        assertTrue(lines().size <= DebugLog.MAX_LINES)
        assertTrue(lines().last().endsWith("LAST"))
    }

    @Test fun neverThrowsOnUnwritableDir() {
        val notADir = tmp.newFile("plain-file")
        DebugLog.init(File(notADir, "sub")) // can't be created: parent is a file
        DebugLog.log("hi")
        DebugLog.error("err", RuntimeException("e"))
        DebugLog.text()
        DebugLog.clear()
        DebugLog.init(File(dir, "does/not/exist"))
        DebugLog.log("hi again")
        DebugLog.text()
    }

    @Test fun concurrentLogsLoseNothing() {
        val threads = (0 until 4).map { t ->
            Thread { for (j in 0 until 200) DebugLog.log("c-$t-$j-end") }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val l = lines()
        assertEquals(800, l.size)
        for (t in 0 until 4) for (j in 0 until 200)
            assertEquals("c-$t-$j-end", 1, l.count { it.endsWith(" c-$t-$j-end") })
    }
}
