package com.example.roboticgit.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a file's size lets the editor do with it.
 *
 * The editor keeps the whole file in one text field, so editing cost grows with
 * the file. Deciding by size up front is what keeps the user from discovering
 * the limit by typing into a file that has stopped responding.
 */
class EditorLimitsTest {

    @Test
    fun `an ordinary source file is editable`() {
        assertEquals(FileAccess.EDITABLE, EditorLimits.accessFor(40 * 1024))
    }

    @Test
    fun `an empty file is editable`() {
        assertEquals(FileAccess.EDITABLE, EditorLimits.accessFor(0))
    }

    /** The limit itself is still allowed; only past it is not. */
    @Test
    fun `a file exactly at the editable limit is still editable`() {
        assertEquals(FileAccess.EDITABLE, EditorLimits.accessFor(EditorLimits.MAX_EDITABLE_BYTES))
    }

    @Test
    fun `a file past the editable limit opens read-only`() {
        assertEquals(
            FileAccess.READ_ONLY,
            EditorLimits.accessFor(EditorLimits.MAX_EDITABLE_BYTES + 1)
        )
    }

    @Test
    fun `a file exactly at the readable limit still opens`() {
        assertEquals(FileAccess.READ_ONLY, EditorLimits.accessFor(EditorLimits.MAX_READABLE_BYTES))
    }

    @Test
    fun `a file past the readable limit is not opened at all`() {
        assertEquals(
            FileAccess.TOO_LARGE,
            EditorLimits.accessFor(EditorLimits.MAX_READABLE_BYTES + 1)
        )
    }

    /**
     * The size is shown to explain a refusal, so it has to read as a size a
     * person recognises rather than a byte count they have to divide.
     */
    @Test
    fun `sizes are described in the unit that suits them`() {
        assertEquals("512 B", EditorLimits.describeSize(512))
        assertEquals("2 KB", EditorLimits.describeSize(2 * 1024))
        assertEquals("1.5 MB", EditorLimits.describeSize((1.5 * 1024 * 1024).toLong()))
    }
}
