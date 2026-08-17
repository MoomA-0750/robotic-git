package com.example.roboticgit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mapping from a picked folder to a path JGit can open.
 *
 * The version this replaced hardcoded `/storage/emulated/0/` for every volume,
 * so a repository on an SD card was handed a primary-storage path that does not
 * exist -- and the failure looked like the setting being ignored.
 */
class DocumentTreePathsTest {

    private val primary = "/storage/emulated/0"

    @Test
    fun aFolderOnPrimaryStorageKeepsItsRelativePath() {
        assertEquals(
            "/storage/emulated/0/Documents/git",
            DocumentTreePaths.forDocumentId("primary:Documents/git", primary)
        )
    }

    @Test
    fun theRootOfPrimaryStorageIsThePrimaryRootItself() {
        assertEquals(primary, DocumentTreePaths.forDocumentId("primary:", primary))
    }

    @Test
    fun aRemovableVolumeResolvesUnderItsOwnMountPoint() {
        assertEquals(
            "/storage/1707-3A0E/Projects/repo",
            DocumentTreePaths.forDocumentId("1707-3A0E:Projects/repo", primary)
        )
    }

    @Test
    fun aDeviceWhoseStorageIsMountedElsewhereStillResolves() {
        assertEquals(
            "/sdcard/Documents",
            DocumentTreePaths.forDocumentId("primary:Documents", "/sdcard/")
        )
    }

    @Test
    fun aDocumentIdWithoutAVolumeIsRejected() {
        assertNull(DocumentTreePaths.forDocumentId("Documents/git", primary))
        assertNull(DocumentTreePaths.forDocumentId(":Documents", primary))
    }

    @Test
    fun theDownloadsProviderIsRejectedBecauseItHasNoPath() {
        assertNull(DocumentTreePaths.forDocumentId("downloads:12345", primary))
    }
}
