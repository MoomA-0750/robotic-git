package com.example.roboticgit.data

/**
 * How a file's size decides what the editor may do with it.
 *
 * The editor holds the whole file in one `BasicTextField`, which means every
 * keystroke re-lays-out the entire text. That is fine for source files and
 * unusable well before it is impossible: a few hundred kilobytes still opens,
 * but typing in it stops feeling like typing.
 *
 * Rather than let the user find that edge by walking into it, the size decides
 * up front. Past [MAX_EDITABLE_BYTES] the file opens read-only, which is what
 * someone wants from a generated file or a lock file anyway. Past
 * [MAX_READABLE_BYTES] it does not open at all -- reading it would mean holding
 * the bytes, the decoded string and the layout at once, and the failure there
 * is not slowness but the process being killed.
 */
object EditorLimits {

    /** Above this a file opens read-only. Roughly ten thousand lines of code. */
    const val MAX_EDITABLE_BYTES: Long = 512L * 1024

    /** Above this a file is not opened at all. */
    const val MAX_READABLE_BYTES: Long = 8L * 1024 * 1024

    fun accessFor(sizeBytes: Long): FileAccess = when {
        sizeBytes > MAX_READABLE_BYTES -> FileAccess.TOO_LARGE
        sizeBytes > MAX_EDITABLE_BYTES -> FileAccess.READ_ONLY
        else -> FileAccess.EDITABLE
    }

    /** For the message shown when a file is refused or made read-only. */
    fun describeSize(sizeBytes: Long): String = when {
        sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
        else -> "$sizeBytes B"
    }
}

enum class FileAccess { EDITABLE, READ_ONLY, TOO_LARGE }

/**
 * A file opened in the editor, together with what may be done to it.
 *
 * [text] is empty when [access] is [FileAccess.TOO_LARGE]; nothing was read.
 */
data class FileContents(
    val text: String,
    val sizeBytes: Long,
    val access: FileAccess
) {
    val isEditable: Boolean get() = access == FileAccess.EDITABLE
}
