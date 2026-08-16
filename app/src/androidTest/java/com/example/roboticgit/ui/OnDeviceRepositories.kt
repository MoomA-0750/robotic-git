package com.example.roboticgit.ui

import android.content.Context
import com.example.roboticgit.data.AuthManager
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.URIish
import java.io.File

/**
 * Builds real git repositories on the device for the UI tests to drive.
 *
 * Everything lives under the app's private files directory, so these tests need
 * no storage permission, and the "remote" is a bare repository reached over
 * JGit's local transport -- meaning push and pull behave for real without a
 * network or a token.
 */
object OnDeviceRepositories {

    const val BRANCH = "main"
    const val REPO_NAME = "uitest"

    private var cloneDirBeforeTests: String? = null
    private var trackedPathsBeforeTests: Set<String>? = null

    /**
     * Wipes any previous run and points the app's default clone directory at a
     * fresh workspace.
     *
     * [AuthManager] is a process-wide singleton backed by persistent storage, so
     * the clone directory has to be re-pointed rather than re-constructed -- and
     * the original has to be remembered. A test that repoints it and walks away
     * leaves the installed app looking for repositories in a directory the test
     * has since deleted. Pair every call with [restoreDefaultCloneDir].
     */
    fun freshWorkspace(context: Context): File {
        val auth = AuthManager.get(context)
        if (cloneDirBeforeTests == null) {
            cloneDirBeforeTests = auth.getDefaultCloneDir()
        }
        val workspace = File(context.filesDir, "ui-test-workspace")
        workspace.deleteRecursively()
        workspace.mkdirs()
        auth.setDefaultCloneDir(workspace.absolutePath)
        return workspace
    }

    /**
     * Empties the list of repositories tracked outside the clone directory, so a
     * test can see the screen a new user sees.
     *
     * Those paths are the user's, not the test's, so they are put back by
     * [restoreAppState] -- which runs from `@After` and therefore also runs when
     * an assertion fails. Only a crash of the whole process could strand them,
     * and they are directory paths, re-addable from the Path tab.
     */
    fun clearTrackedRepositories(context: Context) {
        val auth = AuthManager.get(context)
        if (trackedPathsBeforeTests == null) {
            trackedPathsBeforeTests = auth.getTrackedRepoPaths()
        }
        auth.getTrackedRepoPaths().forEach { auth.removeTrackedRepoPath(it) }
    }

    /** Undoes every change these helpers made to the app's persisted settings. */
    fun restoreAppState(context: Context) {
        val auth = AuthManager.get(context)
        cloneDirBeforeTests?.let { auth.setDefaultCloneDir(it) }
        trackedPathsBeforeTests?.let { saved ->
            auth.getTrackedRepoPaths().forEach { auth.removeTrackedRepoPath(it) }
            saved.forEach { auth.addTrackedRepoPath(it) }
        }
    }

    /** A repository with one commit, wired to a bare remote it is level with. */
    fun repoWithRemote(workspace: File): Repos {
        val remoteDir = File(workspace, "remote.git")
        val remote = Git.init().setDirectory(remoteDir).setBare(true)
            .setInitialBranch(BRANCH).call()

        val localDir = File(workspace, REPO_NAME)
        val local = Git.init().setDirectory(localDir).setInitialBranch(BRANCH).call()
        configureIdentity(local)
        writeFile(localDir, "README.md", "initial\n")
        local.add().addFilepattern("README.md").call()
        local.commit().setMessage("initial commit").call()

        local.remoteAdd().setName("origin").setUri(URIish(remoteDir.toURI().toString())).call()
        local.push().setRemote("origin")
            .setRefSpecs(RefSpec("refs/heads/$BRANCH:refs/heads/$BRANCH")).call()
        local.repository.config.apply {
            setString("branch", BRANCH, "remote", "origin")
            setString("branch", BRANCH, "merge", "refs/heads/$BRANCH")
            save()
        }

        return Repos(localDir, local, remoteDir, remote)
    }

    /**
     * Advances the remote behind the local repository's back, so the next push
     * from it is a non-fast-forward and gets rejected.
     */
    fun makeRemoteDiverge(repos: Repos, workspace: File) {
        val otherDir = File(workspace, "other-clone")
        val other = Git.cloneRepository()
            .setURI(repos.remoteDir.toURI().toString())
            .setDirectory(otherDir)
            .call()
        try {
            configureIdentity(other)
            writeFile(otherDir, "from-elsewhere.txt", "published by someone else\n")
            other.add().addFilepattern("from-elsewhere.txt").call()
            other.commit().setMessage("someone else's work").call()
            other.push().setRemote("origin").call()
        } finally {
            other.close()
        }

        // Now commit locally too, so the two histories have genuinely diverged.
        writeFile(repos.localDir, "local-only.txt", "my work\n")
        repos.local.add().addFilepattern("local-only.txt").call()
        repos.local.commit().setMessage("my work").call()
    }

    fun writeFile(dir: File, relativePath: String, content: String) {
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun configureIdentity(git: Git) {
        git.repository.config.apply {
            setString("user", null, "name", "UI Test")
            setString("user", null, "email", "uitest@example.com")
            save()
        }
    }

    /** The paths recorded in the repository's current HEAD commit. */
    fun pathsInHeadCommit(git: Git): Set<String> {
        val repository = git.repository
        org.eclipse.jgit.revwalk.RevWalk(repository).use { walk ->
            val head = walk.parseCommit(repository.resolve("HEAD"))
            repository.newObjectReader().use { reader ->
                val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser()
                    .apply { reset(reader, head.tree) }
                val oldTree = if (head.parentCount > 0) {
                    org.eclipse.jgit.treewalk.CanonicalTreeParser()
                        .apply { reset(reader, walk.parseCommit(head.getParent(0)).tree) }
                } else {
                    org.eclipse.jgit.treewalk.EmptyTreeIterator()
                }
                org.eclipse.jgit.diff.DiffFormatter(
                    org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE
                ).use { formatter ->
                    formatter.setRepository(repository)
                    return formatter.scan(oldTree, newTree).map { it.newPath }.toSet()
                }
            }
        }
    }

    fun headId(git: Git): String = git.repository.resolve("HEAD").name

    class Repos(
        val localDir: File,
        val local: Git,
        val remoteDir: File,
        val remote: Git
    ) {
        fun close() {
            local.close()
            remote.close()
        }
    }
}
