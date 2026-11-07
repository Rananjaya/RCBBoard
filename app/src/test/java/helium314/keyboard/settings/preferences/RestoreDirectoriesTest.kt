// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Backup restore must never lose the user's current files: on success the restored files replace
 * them, on failure the original files (including the device protected ones: custom layouts,
 * background images and fonts) are put back unchanged.
 */
class RestoreDirectoriesTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var protectedDir: File

    @BeforeTest fun setUp() {
        root = Files.createTempDirectory("restore-test").toFile()
        filesDir = File(root, "files")
        protectedDir = File(root, "device-protected/files")
        write(filesDir, "UserHistoryDictionary.en/UserHistoryDictionary.en.body", "my learned words")
        write(filesDir, "blacklists/en.txt", "blocked")
        write(protectedDir, "layouts/custom.main.my_layout.json", "my layout")
        write(protectedDir, "custom_font", "my font")
    }

    @AfterTest fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(dir: File, name: String, content: String) =
        File(dir, name).apply { parentFile!!.mkdirs(); writeText(content) }

    /** All files below [dir] as relative path -> content. */
    private fun contents(dir: File): Map<String, String> =
        dir.walk().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readText() }

    @Test fun successfulRestoreReplacesFiles() {
        val restore = RestoreDirectories(listOf(filesDir, protectedDir))
        assertTrue(restore.moveAside())
        assertFalse(filesDir.exists())
        assertFalse(protectedDir.exists()) // the bug: this directory was never set aside
        write(filesDir, "blacklists/de.txt", "restored")
        write(protectedDir, "layouts/custom.main.restored.json", "restored layout")

        restore.commit()

        assertEquals(mapOf("blacklists/de.txt" to "restored"), contents(filesDir))
        assertEquals(mapOf("layouts/custom.main.restored.json" to "restored layout"), contents(protectedDir))
        assertFalse(File(filesDir.path + "2").exists())
        assertFalse(File(protectedDir.path + "2").exists())
    }

    @Test fun failedRestoreRollsBackAndKeepsDeviceProtectedFiles() {
        val filesBefore = contents(filesDir)
        val protectedBefore = contents(protectedDir)
        val restore = RestoreDirectories(listOf(filesDir, protectedDir))
        assertTrue(restore.moveAside())
        // partially restored files, then the restore fails
        write(filesDir, "blacklists/en.txt", "half restored")
        write(protectedDir, "custom_font", "half restored font")
        write(protectedDir, "layouts/custom.main.partial.json", "partial")

        restore.rollback()

        assertEquals(filesBefore, contents(filesDir))
        assertEquals(protectedBefore, contents(protectedDir))
        assertFalse(File(filesDir.path + "2").exists())
        assertFalse(File(protectedDir.path + "2").exists())
    }

    @Test fun sameDirectoryTwiceIsRestoredOnlyOnce() {
        // on Android < 7 the device protected files directory is the normal files directory
        val before = contents(filesDir)
        val restore = RestoreDirectories(listOf(filesDir, File(filesDir.path)))
        assertTrue(restore.moveAside())
        write(filesDir, "blacklists/en.txt", "half restored")

        restore.rollback()

        assertEquals(before, contents(filesDir)) // previously the rollback deleted it a second time
    }

    @Test fun leftoverCopyAbortsBeforeChangingAnything() {
        val filesBefore = contents(filesDir)
        val protectedBefore = contents(protectedDir)
        write(File(protectedDir.path + "2"), "custom_font", "left over from an interrupted restore")

        assertFalse(RestoreDirectories(listOf(filesDir, protectedDir)).moveAside())

        assertEquals(filesBefore, contents(filesDir)) // already moved directory was put back
        assertEquals(protectedBefore, contents(protectedDir))
        assertFalse(File(filesDir.path + "2").exists())
        assertEquals("left over from an interrupted restore", File(protectedDir.path + "2", "custom_font").readText())
    }

    @Test fun directoryCreatedByAFailedRestoreIsRemoved() {
        protectedDir.deleteRecursively() // no device protected files yet
        val restore = RestoreDirectories(listOf(filesDir, protectedDir))
        assertTrue(restore.moveAside())
        write(protectedDir, "custom_font", "half restored font")

        restore.rollback()

        assertFalse(protectedDir.exists())
        assertEquals("my learned words", File(filesDir, "UserHistoryDictionary.en/UserHistoryDictionary.en.body").readText())
    }
}
