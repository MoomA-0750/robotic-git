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
- [x] `GitManager.kt` に以下のメソッドを追加:
  - `getCurrentBranch(repoPath: String): String?` - 現在のブランチ名取得
  - `listBranches(repoPath: String): List<BranchInfo>` - ローカルブランチ一覧
  - `listRemoteBranches(repoPath: String): List<BranchInfo>` - リモートブランチ一覧
- [x] `data/model/BranchInfo.kt` を新規作成
- [x] `RepoDetailViewModel.kt` に状態を追加
- [x] `RepoDetailScreen.kt` に新しいタブ「Branches」を追加、またはヘッダーに現在のブランチを表示
- [x] ブランチ一覧UI（LazyColumn + Card）を実装
- [x] リモート/ローカルブランチの切り替えタブを実装

---

### Task 2: ブランチ作成機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - ダイアログUI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [x] `GitManager.kt` に以下のメソッドを追加:
  - `createBranch(repoPath: String, branchName: String, startPoint: String? = null): Result<String>`
- [x] バリデーション実装:
  - ブランチ名の妥当性チェック
- [x] `RepoDetailScreen.kt` にブランチ作成ダイアログを追加
- [x] エラーハンドリング（名前重複、無効な名前など）

---

### Task 3: ブランチ切り替え（Checkout）機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [x] `GitManager.kt` に以下のメソッドを追加:
  - `checkoutBranch(repoPath: String, branchName: String): Result<String>`
  - `checkStatus(repoPath: String): Boolean` - 未コミット変更の確認
- [x] 未コミット変更がある場合の警告ダイアログ実装
- [x] ブランチリストからのワンタップチェックアウト
- [x] チェックアウト後の自動リフレッシュ（ファイル状態、コミット履歴）

---

### Task 4: ブランチ削除機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加
- `RepoDetailViewModel.kt` - アクション追加

**実装内容:**
- [x] `GitManager.kt` に以下のメソッドを追加:
  - `deleteBranch(repoPath: String, branchName: String, force: Boolean = false): Result<String>`
- [x] 削除前の確認ダイアログ実装
- [x] 現在のブランチは削除不可のチェック
- [x] Force削除オプション

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
- [ ] `data/model/MergeResult.kt` を新規作成
- [ ] マージダイアログUI実装:
  - マージ元ブランチ選択
  - Fast-forward オプション
  - コミットメッセージ編集
- [ ] マージ成功時の通知
- [ ] コンフリクト発生時の画面遷移（Task 9 に繋がる）

---

## 🔴 最優先 - リモート管理機能

### Task 6: リモート一覧表示・追加・削除
**ファイル:**
- `GitManager.kt` - メソッド追加
- `SettingsScreen.kt` または新規 `RemotesScreen.kt` - UI追加
- `SettingsViewModel.kt` または新規 `RemotesViewModel.kt` - 状態管理
- `data/model/RemoteInfo.kt` - 新規作成

**実装内容:**
- [ ] `data/model/RemoteInfo.kt` を新規作成
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `listRemotes(repoPath: String): List<RemoteInfo>`
  - `addRemote(repoPath: String, name: String, url: String): Result<Unit>`
  - `removeRemote(repoPath: String, name: String): Result<Unit>`
  - `setRemoteUrl(repoPath: String, name: String, url: String): Result<Unit>`
- [ ] リモート管理画面のUI実装

---

## 🔴 最優先 - コンフリクト解決機能

### Task 7: コンフリクト検出と表示
**ファイル:**
- `GitManager.kt` - メソッド追加
- `data/model/ConflictFile.kt` - 新規作成
- `RepoDetailViewModel.kt` - 状態管理追加

**実装内容:**
- [ ] `data/model/ConflictFile.kt` を新規作成
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `getConflictingFiles(repoPath: String): List<String>`
  - `getConflictContent(repoPath: String, filePath: String): ConflictFile`
- [ ] `FileStatus.kt` に `CONFLICTING` 状態を追加
- [ ] Changes タブでコンフリクトファイルを特別表示

---

### Task 8: コンフリクト解決UI
**ファイル:**
- 新規作成: `ui/screens/ConflictResolveScreen.kt`
- 新規作成: `ui/viewmodel/ConflictResolveViewModel.kt`
- `RoboticGitNavigation.kt` - ルート追加

**実装内容:**
- [ ] 3-way マージビューの実装
- [ ] コンフリクトマーカーのハイライト表示
- [ ] ワンタップで選択肢を選べるUI
- [ ] `GitManager.kt` に解決用メソッドを追加
- [ ] 解決完了後の自動ステージング

---

### Task 9: マージ中断・完了機能
**ファイル:**
- `GitManager.kt` - メソッド追加
- `RepoDetailScreen.kt` - UI追加

**実装内容:**
- [ ] `GitManager.kt` に以下のメソッドを追加:
  - `abortMerge(repoPath: String): Result<Unit>`
  - `isMerging(repoPath: String): Boolean`
- [ ] マージ中の場合、画面上部にバナー表示
- [ ] Complete Merge → 自動でコミット作成

---

## 🟡 中優先度 - スタッシュ機能
（以下略）
