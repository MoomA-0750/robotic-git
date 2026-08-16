package com.example.roboticgit.data

import com.example.roboticgit.data.model.GitRepo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.RefSpec
import java.io.File

/**
 * Helpers for building throwaway git repositories on the local filesystem.
 *
 * [GitManager] has no Android dependencies, so everything it does can be
 * exercised from a plain JVM unit test. Remotes are modelled as bare
 * repositories reached over JGit's local transport, which keeps the tests
 * offline and fast.
 */
internal object TestGitFixtures {

    const val BRANCH = "main"

    /** Creates a repository with a deterministic identity and one initial commit. */
    fun initRepoWithCommit(dir: File): Git {
        dir.mkdirs()
        val git = Git.init()
            .setDirectory(dir)
            .setInitialBranch(BRANCH)
            .call()
        configureIdentity(git)
        writeFile(dir, "README.md", "initial\n")
        git.add().addFilepattern("README.md").call()
        git.commit().setMessage("initial commit").call()
        return git
    }

    /** Creates a bare repository to be used as a push/pull target. */
    fun initBareRemote(dir: File): Git {
        dir.mkdirs()
        return Git.init()
            .setDirectory(dir)
            .setBare(true)
            .setInitialBranch(BRANCH)
            .call()
    }

    /**
     * Commits are rejected outright when JGit cannot determine an identity, and
     * the ambient machine config must not leak into test results.
     */
    fun configureIdentity(git: Git) {
        val config = git.repository.config
        config.setString("user", null, "name", "Test User")
        config.setString("user", null, "email", "test@example.com")
        config.save()
    }

    fun writeFile(dir: File, relativePath: String, content: String) {
        val file = File(dir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun readFile(dir: File, relativePath: String): String = File(dir, relativePath).readText()

    /** Writes [relativePath], stages it and commits it in one step. */
    fun commitFile(
        git: Git,
        dir: File,
        relativePath: String,
        content: String,
        message: String
    ): RevCommit {
        writeFile(dir, relativePath, content)
        git.add().addFilepattern(relativePath).call()
        return git.commit().setMessage(message).call()
    }

    /**
     * Creates [name] from the current HEAD, commits [content] to [relativePath]
     * on it, then returns to [BRANCH].
     *
     * Uses JGit directly rather than GitManager's own branch commands, so that a
     * failure in a merge test points at the merge rather than at the setup.
     */
    fun branchWithCommit(
        git: Git,
        dir: File,
        name: String,
        relativePath: String,
        content: String
    ) {
        git.checkout().setCreateBranch(true).setName(name).call()
        commitFile(git, dir, relativePath, content, "$name: change $relativePath")
        git.checkout().setName(BRANCH).call()
    }

    /** Wires [remote] up as "origin" and performs the initial explicit push. */
    fun linkAndSeedRemote(git: Git, remoteDir: File) {
        git.remoteAdd()
            .setName("origin")
            .setUri(org.eclipse.jgit.transport.URIish(remoteDir.toURI().toString()))
            .call()
        git.push()
            .setRemote("origin")
            .setRefSpecs(RefSpec("refs/heads/$BRANCH:refs/heads/$BRANCH"))
            .call()
        // Establish the upstream tracking config a real clone would already have.
        val config = git.repository.config
        config.setString("branch", BRANCH, "remote", "origin")
        config.setString("branch", BRANCH, "merge", "refs/heads/$BRANCH")
        config.save()
    }

    fun cloneFrom(remoteDir: File, into: File): Git {
        val git = Git.cloneRepository()
            .setURI(remoteDir.toURI().toString())
            .setDirectory(into)
            .call()
        configureIdentity(git)
        return git
    }

    /** The paths recorded in [commit], i.e. what the commit actually changed. */
    fun pathsInCommit(repository: Repository, commit: RevCommit): Set<String> {
        org.eclipse.jgit.revwalk.RevWalk(repository).use { walk ->
            val formatter = org.eclipse.jgit.diff.DiffFormatter(
                org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE
            )
            formatter.setRepository(repository)
            formatter.use {
                val parent = if (commit.parentCount > 0) {
                    walk.parseCommit(commit.getParent(0).id).tree
                } else {
                    null
                }
                val diffs = if (parent == null) {
                    val empty = org.eclipse.jgit.treewalk.EmptyTreeIterator()
                    val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser()
                    repository.newObjectReader().use { reader -> newTree.reset(reader, commit.tree) }
                    it.scan(empty, newTree)
                } else {
                    it.scan(parent, commit.tree)
                }
                return diffs.map { entry ->
                    if (entry.changeType == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE) {
                        entry.oldPath
                    } else {
                        entry.newPath
                    }
                }.toSet()
            }
        }
    }

    fun headCommit(git: Git): RevCommit {
        val head = git.repository.resolve(Constants.HEAD)
        org.eclipse.jgit.revwalk.RevWalk(git.repository).use { walk ->
            return walk.parseCommit(head)
        }
    }

    fun headId(git: Git): String = git.repository.resolve(Constants.HEAD).name

    /** Builds the [GitRepo] value object the way the app's data layer does. */
    fun repoOf(dir: File): GitRepo = GitRepo(dir.name, dir.absolutePath, dir)
}
