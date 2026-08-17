package com.example.roboticgit.data

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Turns a folder picked through the Storage Access Framework back into a
 * filesystem path.
 *
 * JGit works on [java.io.File], not on document URIs, so a picked folder is
 * only useful here once it has a path. A tree URI carries a document id of the
 * form `volume:relative/path` -- `primary:Documents/git` for built-in storage,
 * `1707-3A0E:Projects` for a removable card. The volume is mounted at
 * `/storage/<volume>`, except for `primary`, whose mount point the framework
 * reports separately.
 *
 * Kept apart from the Android call so the mapping itself can be unit-tested:
 * the previous version of this lived inline in the settings screen, assumed
 * every pick was on primary storage, and rewrote an SD card path into a
 * primary-storage one that does not exist.
 */
object DocumentTreePaths {

    /**
     * The path [documentId] names, or null if it is not a form we can map.
     *
     * [primaryRoot] is `Environment.getExternalStorageDirectory()`, passed in
     * rather than read here because that call needs a device.
     */
    fun forDocumentId(documentId: String, primaryRoot: String): String? {
        val separator = documentId.indexOf(':')
        if (separator < 0) return null

        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1).trim('/')

        val root = when {
            volume.isEmpty() -> return null
            volume.equals("primary", ignoreCase = true) -> primaryRoot.trimEnd('/')
            // Anything else is a mounted volume named by its id: SD cards, USB
            // storage. "downloads" and other synthetic providers have no path
            // at all, but they also cannot hold a working tree.
            volume.equals("downloads", ignoreCase = true) -> return null
            else -> "/storage/$volume"
        }

        return if (relative.isEmpty()) root else "$root/$relative"
    }

    /** [forDocumentId] for the URI an `OpenDocumentTree` pick hands back. */
    fun forTreeUri(uri: Uri, primaryRoot: String): String? =
        runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?.let { forDocumentId(it, primaryRoot) }
}
