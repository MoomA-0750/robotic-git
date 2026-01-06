# Robotic Git - AI Implementation TODO

このファイルは、Robotic Git Android アプリに不足している機能の実装To-Doリストです。
AIアシスタントが順次実装していくための詳細なタスクリストとして作成されています。

---

## 🔴 最優先 - ブランチ管理機能

### Task 1: ブランチ一覧表示と現在のブランチ表示
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加（新しい「Branches」タブまたはヘッダー表示）
- `RepoDetailViewModel.kt` - 状態管理追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `getCurrentBranch(repoPath: String): String?` - 現在のブランチ名取得
  - `listBranches(repoPath: String): List<BranchInfo>` - ローカルブランチ一覧
  - `listRemoteBranches(repoPath: String): List<BranchInfo>` - リモートブランチ一覧
- [ ] `data/model/BranchInfo.kt` を新規作成:
  ```kotlin
  data class BranchInfo(
      val name: String,
      val fullName: String,
      val isRemote: Boolean,
      val isCurrent: Boolean,
      val lastCommitHash: String?,
      val lastCommitMessage: String?,
      val lastCommitTime: Long?
  )
  ```
- [ ] `RepoDetailViewModel.kt` に状態を追加:
  - `currentBranch: StateFlow<String?>`
  - `branches: StateFlow<List<BranchInfo>>`
  - `loadBranches()` メソッド
- [ ] `RepoDetailScreen.kt` に新しいタブ「Branches」を追加、またはヘッダーに現在のブランチを表示
- [ ] ブランチ一覧UI（LazyColumn + Card）を実装
- [ ] リモート/ローカルブランチの切り替えタブを実装

**JGit 実装例:**
```kotlin
fun getCurrentBranch(repoPath: String): String? {
    val repo = Git.open(File(repoPath)).repository
    return repo.branch
}

fun listBranches(repoPath: String): List<BranchInfo> {
    val git = Git.open(File(repoPath))
    val currentBranch = git.repository.branch
    return git.branchList().call().map { ref ->
        val name = ref.name.removePrefix("refs/heads/")
        BranchInfo(
            name = name,
            fullName = ref.name,
            isRemote = false,
            isCurrent = name == currentBranch,
            // ... commit info
        )
    }
}
```

---

### Task 2: ブランチ作成機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - ダイアログUI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `createBranch(repoPath: String, branchName: String, startPoint: String? = null): Result<String>`
- [ ] バリデーション実装:
  - ブランチ名の妥当性チェック（空白、特殊文字など）
  - 既存ブランチ名との重複チェック
- [ ] `RepoDetailScreen.kt` にブランチ作成ダイアログを追加:
  - ブランチ名入力フィールド
  - 作成元コミット/ブランチ選択（オプション）
  - 作成後にチェックアウトするかのチェックボックス
- [ ] エラーハンドリング（名前重複、無効な名前など）

**JGit 実装例:**
```kotlin
fun createBranch(repoPath: String, branchName: String, startPoint: String? = null): Result<String> {
    return try {
        val git = Git.open(File(repoPath))
        val ref = git.branchCreate()
            .setName(branchName)
            .apply { startPoint?.let { setStartPoint(it) } }
            .call()
        Result.success(ref.name)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

### Task 3: ブランチ切り替え（Checkout）機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `checkoutBranch(repoPath: String, branchName: String): Result<String>`
  - `checkStatus(repoPath: String): Boolean` - 未コミット変更の確認
- [ ] 未コミット変更がある場合の警告ダイアログ実装
- [ ] チェックアウト中のプログレス表示
- [ ] ブランチリストからのワンタップチェックアウト
- [ ] チェックアウト後の自動リフレッシュ（ファイル状態、コミット履歴）

**JGit 実装例:**
```kotlin
fun checkoutBranch(repoPath: String, branchName: String): Result<String> {
    return try {
        val git = Git.open(File(repoPath))
        git.checkout()
            .setName(branchName)
            .call()
        Result.success("Switched to branch '$branchName'")
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun hasUncommittedChanges(repoPath: String): Boolean {
    val git = Git.open(File(repoPath))
    val status = git.status().call()
    return !status.isClean
}
```

---

### Task 4: ブランチ削除機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `deleteBranch(repoPath: String, branchName: String, force: Boolean = false): Result<String>`
- [ ] 削除前の確認ダイアログ実装
- [ ] 現在のブランチは削除不可のチェック
- [ ] マージされていないブランチの警告
- [ ] Force削除オプション

**JGit 実装例:**
```kotlin
fun deleteBranch(repoPath: String, branchName: String, force: Boolean = false): Result<String> {
    return try {
        val git = Git.open(File(repoPath))
        git.branchDelete()
            .setBranchNames(branchName)
            .setForce(force)
            .call()
        Result.success("Deleted branch '$branchName'")
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

### Task 5: ブランチマージ機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加
- `RepoDetailViewModel.kt` - アクション追加
- `data/model/MergeResult.kt` - 新規作成

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `mergeBranch(repoPath: String, branchName: String): MergeResult`
- [ ] `data/model/MergeResult.kt` を新規作成:
  ```kotlin
  sealed class MergeResult {
      object Success : MergeResult()
      data class Conflict(val conflictFiles: List<String>) : MergeResult()
      data class Failure(val message: String) : MergeResult()
  }
  ```
- [ ] マージダイアログUI実装:
  - マージ元ブランチ選択
  - Fast-forward オプション
  - コミットメッセージ編集
- [ ] マージ成功時の通知
- [ ] コンフリクト発生時の画面遷移（Task 9 に繋がる）

**JGit 実装例:**
```kotlin
fun mergeBranch(repoPath: String, branchName: String): MergeResult {
    return try {
        val git = Git.open(File(repoPath))
        val result = git.merge()
            .include(git.repository.resolve(branchName))
            .call()

        when (result.mergeStatus) {
            MergeResult.MergeStatus.MERGED,
            MergeResult.MergeStatus.FAST_FORWARD -> MergeResult.Success
            MergeResult.MergeStatus.CONFLICTING -> {
                val conflicts = result.conflicts?.keys?.toList() ?: emptyList()
                MergeResult.Conflict(conflicts)
            }
            else -> MergeResult.Failure(result.mergeStatus.toString())
        }
    } catch (e: Exception) {
        MergeResult.Failure(e.message ?: "Unknown error")
    }
}
```

---

## 🔴 最優先 - リモート管理機能

### Task 6: リモート一覧表示・追加・削除
**ファイル:**
- `GitManager.kt` - メソッド追加
- `SettingsScreen.kt` または新規 `RemotesScreen.kt` - UI追加
- `SettingsViewModel.kt` または新規 `RemotesViewModel.kt` - 状態管理
- `data/model/RemoteInfo.kt` - 新規作成

**実装内容:**
- [ ] `data/model/RemoteInfo.kt` を新規作成:
  ```kotlin
  data class RemoteInfo(
      val name: String,
      val fetchUrl: String,
      val pushUrl: String
  )
  ```
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `listRemotes(repoPath: String): List<RemoteInfo>`
  - `addRemote(repoPath: String, name: String, url: String): Result<Unit>`
  - `removeRemote(repoPath: String, name: String): Result<Unit>`
  - `setRemoteUrl(repoPath: String, name: String, url: String): Result<Unit>`
- [ ] リモート管理画面のUI実装:
  - リモート一覧表示（LazyColumn）
  - リモート追加ダイアログ（名前、URL入力）
  - リモート削除確認ダイアログ
  - URL編集機能
- [ ] `RepoDetailScreen.kt` にリモート情報を表示（現在のブランチのトラッキング情報など）

**JGit 実装例:**
```kotlin
fun listRemotes(repoPath: String): List<RemoteInfo> {
    val git = Git.open(File(repoPath))
    return git.remoteList().call().map { remote ->
        RemoteInfo(
            name = remote.name,
            fetchUrl = remote.urIs.firstOrNull()?.toString() ?: "",
            pushUrl = remote.pushURIs.firstOrNull()?.toString()
                ?: remote.urIs.firstOrNull()?.toString() ?: ""
        )
    }
}

fun addRemote(repoPath: String, name: String, url: String): Result<Unit> {
    return try {
        val git = Git.open(File(repoPath))
        git.remoteAdd()
            .setName(name)
            .setUri(URIish(url))
            .call()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## 🔴 最優先 - コンフリクト解決機能

### Task 7: コンフリクト検出と表示
**ファイル:**
- `GitManager.kt` - メソッド追加
- `data/model/ConflictFile.kt` - 新規作成
- `RepoDetailViewModel.kt` - 状態管理追加

**実装内容:**
- [ ] `data/model/ConflictFile.kt` を新規作成:
  ```kotlin
  data class ConflictFile(
      val path: String,
      val oursContent: String,
      val theirsContent: String,
      val baseContent: String?,
      val currentContent: String
  )
  ```
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `getConflictingFiles(repoPath: String): List<String>`
  - `getConflictContent(repoPath: String, filePath: String): ConflictFile`
- [ ] `FileStatus.kt` に `CONFLICTING` 状態を追加
- [ ] Changes タブでコンフリクトファイルを特別表示（赤いアイコンなど）

**JGit 実装例:**
```kotlin
fun getConflictingFiles(repoPath: String): List<String> {
    val git = Git.open(File(repoPath))
    val status = git.status().call()
    return status.conflicting.toList()
}
```

---

### Task 8: コンフリクト解決UI
**ファイル:**
- 新規作成: `ui/screens/ConflictResolveScreen.kt`
- 新規作成: `ui/viewmodel/ConflictResolveViewModel.kt`
- `RoboticGitNavigation.kt` - ルート追加

**実装内容:**
- [ ] 3-way マージビューの実装:
  - Ours（現在のブランチ）表示
  - Theirs（マージ元ブランチ）表示
  - Base（共通の祖先）表示
  - Result（解決後）編集エリア
- [ ] コンフリクトマーカー（`<<<<<<<`, `=======`, `>>>>>>>`）のハイライト表示
- [ ] ワンタップで選択肢を選べるUI:
  - 「Use Ours」ボタン
  - 「Use Theirs」ボタン
  - 「Use Both」ボタン
  - 手動編集モード
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `resolveConflict(repoPath: String, filePath: String, resolvedContent: String): Result<Unit>`
  - `markAsResolved(repoPath: String, filePath: String): Result<Unit>`
- [ ] 解決完了後の自動ステージング

**実装例（コンフリクトマーカー解析）:**
```kotlin
data class ConflictSection(
    val oursLines: List<String>,
    val theirsLines: List<String>,
    val startLine: Int,
    val endLine: Int
)

fun parseConflictMarkers(content: String): List<ConflictSection> {
    // <<<<<< HEAD から ====== までが ours
    // ====== から >>>>>> までが theirs
    // を解析してリストで返す
}
```

---

### Task 9: マージ中断・完了機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `abortMerge(repoPath: String): Result<Unit>` - マージを中断
  - `isMerging(repoPath: String): Boolean` - マージ中かチェック
- [ ] マージ中の場合、画面上部にバナー表示:
  - 「Merging branch 'feature' into 'main'」
  - 「Abort Merge」ボタン
  - 「Complete Merge」ボタン（全てのコンフリクトが解決済みの場合のみ有効）
- [ ] Complete Merge → 自動でコミット作成

**JGit 実装例:**
```kotlin
fun isMerging(repoPath: String): Boolean {
    val git = Git.open(File(repoPath))
    return git.repository.repositoryState == RepositoryState.MERGING
}

fun abortMerge(repoPath: String): Result<Unit> {
    return try {
        val git = Git.open(File(repoPath))
        git.reset()
            .setMode(ResetCommand.ResetType.HARD)
            .setRef("HEAD")
            .call()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## 🟡 中優先度 - スタッシュ機能

### Task 10: スタッシュ作成・一覧・適用
**ファイル:**
- `GitManager.kt` - メソッド追加
- 新規作成: `ui/screens/StashScreen.kt` または `RepoDetailScreen.kt` に新タブ追加
- `RepoDetailViewModel.kt` - 状態管理追加
- `data/model/StashEntry.kt` - 新規作成

**実装内容:**
- [ ] `data/model/StashEntry.kt` を新規作成:
  ```kotlin
  data class StashEntry(
      val index: Int,
      val message: String,
      val branch: String?,
      val timestamp: Long
  )
  ```
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `stashChanges(repoPath: String, message: String? = null): Result<Unit>`
  - `listStashes(repoPath: String): List<StashEntry>`
  - `applyStash(repoPath: String, index: Int): Result<Unit>`
  - `popStash(repoPath: String, index: Int): Result<Unit>`
  - `dropStash(repoPath: String, index: Int): Result<Unit>`
- [ ] スタッシュ画面のUI実装:
  - スタッシュ一覧（LazyColumn）
  - 各スタッシュの詳細（メッセージ、ブランチ、時刻）
  - Apply / Pop / Drop ボタン
- [ ] Changes タブに「Stash Changes」ボタンを追加

**JGit 実装例:**
```kotlin
fun stashChanges(repoPath: String, message: String? = null): Result<Unit> {
    return try {
        val git = Git.open(File(repoPath))
        git.stashCreate()
            .apply { message?.let { setWorkingDirectoryMessage(it) } }
            .call()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun listStashes(repoPath: String): List<StashEntry> {
    val git = Git.open(File(repoPath))
    return git.stashList().call().mapIndexed { index, revCommit ->
        StashEntry(
            index = index,
            message = revCommit.fullMessage,
            branch = null, // JGit doesn't provide this easily
            timestamp = revCommit.commitTime.toLong() * 1000
        )
    }
}
```

---

## 🟡 中優先度 - タグ管理機能

### Task 11: タグ一覧・作成・削除
**ファイル:**
- `GitManager.kt` - メソッド追加
- 新規作成: `ui/screens/TagsScreen.kt` または Branches タブと統合
- `RepoDetailViewModel.kt` - 状態管理追加
- `data/model/TagInfo.kt` - 新規作成

**実装内容:**
- [ ] `data/model/TagInfo.kt` を新規作成:
  ```kotlin
  data class TagInfo(
      val name: String,
      val commitHash: String,
      val message: String?,
      val taggerName: String?,
      val timestamp: Long?
  )
  ```
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `listTags(repoPath: String): List<TagInfo>`
  - `createTag(repoPath: String, tagName: String, message: String?, commitHash: String?): Result<Unit>`
  - `deleteTag(repoPath: String, tagName: String): Result<Unit>`
  - `pushTag(repoPath: String, tagName: String, remoteName: String = "origin"): Result<Unit>`
- [ ] タグ管理画面のUI実装:
  - タグ一覧表示（LazyColumn）
  - 軽量タグ / 注釈付きタグの区別
  - タグ作成ダイアログ
  - タグ削除確認ダイアログ
  - タグのプッシュ機能

**JGit 実装例:**
```kotlin
fun createTag(repoPath: String, tagName: String, message: String?, commitHash: String?): Result<Unit> {
    return try {
        val git = Git.open(File(repoPath))
        git.tag()
            .setName(tagName)
            .apply {
                message?.let { setMessage(it) }
                commitHash?.let { setObjectId(git.repository.resolve(it)) }
            }
            .call()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

## 🟡 中優先度 - その他の機能

### Task 12: リベース機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- UI追加（ブランチ画面またはマージダイアログ内）

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `rebase(repoPath: String, upstream: String): Result<RebaseResult>`
  - `abortRebase(repoPath: String): Result<Unit>`
  - `continueRebase(repoPath: String): Result<Unit>`
- [ ] リベースダイアログUI実装
- [ ] リベース中のコンフリクト処理（Task 7-9 と連携）

---

### Task 13: チェリーピック機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - History タブに追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `cherryPick(repoPath: String, commitHash: String): Result<Unit>`
- [ ] コミット履歴の各項目に「Cherry-pick」メニューを追加
- [ ] コンフリクト発生時の処理

---

### Task 14: SSH鍵管理
**ファイル:**
- 新規作成: `data/SshKeyManager.kt`
- `SettingsScreen.kt` - UI追加

**実装内容:**
- [ ] SSH鍵生成機能
- [ ] 鍵の一覧表示
- [ ] 公開鍵のコピー機能
- [ ] JGit の SshSessionFactory 統合

---

## 🟢 低優先度 - プラットフォーム統合

### Task 15: Pull Request一覧・作成
**ファイル:**
- `GitHubApiService.kt` - エンドポイント追加
- 新規作成: `ui/screens/PullRequestsScreen.kt`
- 新規作成: `ui/viewmodel/PullRequestViewModel.kt`

**実装内容:**
- [ ] GitHub API で PR 一覧取得
- [ ] PR 詳細表示
- [ ] PR 作成UI
- [ ] レビューコメント表示

---

### Task 16: Issue管理
**ファイル:**
- `GitHubApiService.kt` - エンドポイント追加
- 新規作成: `ui/screens/IssuesScreen.kt`

**実装内容:**
- [ ] Issue 一覧表示
- [ ] Issue 詳細表示
- [ ] Issue 作成・編集

---

### Task 17: Blame機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - Files タブに追加

**実装内容:**
- [ ] ファイルの各行の最終変更者・コミットを表示
- [ ] JGit の BlameCommand 使用

---

### Task 18: サブモジュール管理
**ファイル:**
- `GitManager.kt` - メソッド追加

**実装内容:**
- [ ] サブモジュール一覧表示
- [ ] サブモジュール初期化
- [ ] サブモジュール更新

---

### Task 19: Git LFS対応
**ファイル:**
- `GitManager.kt` - LFS統合
- `build.gradle.kts` - LFS依存関係追加

**実装内容:**
- [ ] LFS ファイルの検出
- [ ] LFS ファイルのダウンロード
- [ ] LFS ファイルのプッシュ

---

### Task 20: コミット履歴グラフ表示
**ファイル:**
- `RepoDetailScreen.kt` - History タブに追加
- 新規作成: `ui/components/CommitGraph.kt`

**実装内容:**
- [ ] ブランチツリーのグラフィカル表示
- [ ] Canvas APIを使用したグラフ描画
- [ ] 分岐・合流の可視化

---

## 実装の優先順位

### フェーズ 1（最優先）
1. Task 1-5: ブランチ管理完全実装
2. Task 6: リモート管理
3. Task 7-9: コンフリクト解決

### フェーズ 2（中優先度）
4. Task 10: スタッシュ機能
5. Task 11: タグ管理
6. Task 12: リベース機能

### フェーズ 3（低優先度）
7. Task 13-20: その他の高度な機能

---

## 実装時の注意事項

### 一般的なガイドライン
- JGit の例外処理を適切に行う（RefNotFoundException, GitAPIException など）
- すべての Git 操作は `Dispatchers.IO` で実行する
- 長時間かかる操作（clone, fetch, push など）はプログレス表示を実装する
- エラーメッセージはユーザーフレンドリーな日本語または英語で表示する
- 破壊的な操作（ブランチ削除、force push など）は確認ダイアログを表示する

### JGit の基本パターン
```kotlin
suspend fun gitOperation(repoPath: String): Result<T> = withContext(Dispatchers.IO) {
    try {
        val git = Git.open(File(repoPath))
        // ... JGit 操作
        Result.success(result)
    } catch (e: Exception) {
        Log.e("GitManager", "Error in operation", e)
        Result.failure(e)
    }
}
```

### 認証
- 既存の `AuthManager` と連携してトークンを取得
- HTTPS 認証では `UsernamePasswordCredentialsProvider` を使用:
  ```kotlin
  val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")
  git.push().setCredentialsProvider(credentialsProvider).call()
  ```

### UI/UX
- Material Design 3 のコンポーネントを使用
- 既存のテーマ、色、タイポグラフィに準拠
- レスポンシブデザインを維持（Compact/Medium/Expanded レイアウト）
- 空の状態（Empty State）を適切に処理

---

このTo-Doリストは、Robotic Git を完全な機能を持つGitクライアントにするためのロードマップです。
各タスクは独立して実装可能ですが、一部のタスク間には依存関係があります（例：コンフリクト解決はマージ機能に依存）。
