# Robotic Git

Android 用の Git クライアント。「なんでAndroid向けのGitクライアントないねん！！！」から始まった個人プロジェクトです。

端末上の実リポジトリを [JGit](https://www.eclipse.org/jgit/) で直接操作します。クローンして、差分を見て、ステージして、コミットして、pushするまでを端末だけで完結させることが目的です。

> **beta です。** 作者が日常的に使える程度には動きますが、まだ広く使われてはいません。
> 大事なリポジトリで使う前に、まずは壊れて困らないもので試してください。

## できること

**リポジトリ**

- クローン（URL貼り付け、またはアカウントのリポジトリ一覧から選択）
- 端末上にすでにあるリポジトリを開く（フォルダ選択、クローン先ディレクトリの外でも可）
- 複数選択してまとめて fetch / pull / 削除 / 一覧から外す

**変更を扱う**

- ファイル単位の status・差分表示（コミット済みの差分も見られる）
- ファイル単位のステージ／アンステージ／ロールバック
- コミット（ステージされたものだけが入る）
- push / pull / fetch

**ブランチとマージ**

- ブランチの作成・切り替え・削除、リモート追跡ブランチの一覧
- マージ、コンフリクトの一覧・内容表示・解決・マージ中断

**その他**

- 内蔵エディタ（サイズが大きいファイルは読み取り専用、さらに大きいものは開かない）
- リモート（origin等）の追加・変更・削除
- Material 3 / ダイナミックカラー / ライト・ダーク / フォント選択
- 大画面では2ペイン表示（折りたたみ端末・タブレット）

## 対応しているホスティング

| | トークンでの clone/push | リポジトリ一覧の取得 |
|---|---|---|
| GitHub | ✅ | ✅ |
| GitLab | ✅ | ✅ |
| Gitea / Forgejo | ✅ | ✅ |
| その他（Custom） | ✅ | — （URLを貼ってクローン） |

認証は Personal Access Token です。**トークンは発行元のホストにしか送りません**（リモートのURLのホストを見てアカウントを選ぶ）。保存先は Android Keystore で暗号化された領域です。

セルフホストのforgeは家庭内LANで平文HTTPで動いていることが多いので、**平文HTTPでの通信を許可しています**（自分でインストールしたCA証明書も信頼します）。その代わり、`http://` のリモートに送るトークンは同じLAN上から読める状態で流れます。これはforgeをhttpで公開していること自体の性質ですが、このアプリの設定がそれを許している以上、承知の上で使ってください。

## インストール

[Releases](https://github.com/MoomA-0750/robotic-git/releases) から APK をダウンロードして、提供元不明のアプリのインストールを許可した上で開いてください。Android 8.0 (API 26) 以上。

初回起動時に **「すべてのファイルへのアクセス」** を求めます。リポジトリを端末の共有ストレージ（`/storage/emulated/0/RoboticGit` 等）に置いて他のアプリからも触れるようにするためで、ここを許可しないとクローンできません。

## ビルド

```bash
./gradlew assembleRelease
```

リリース署名鍵はリポジトリの外に置きます。

```
~/.android-keystores/robotic-git/keystore.properties
  storeFile= / storePassword= / keyAlias= / keyPassword=
```

このファイルが無い環境でもビルドは通ります（その場合はdebug鍵で署名され、配布には使えません）。

テスト:

```bash
./gradlew testDebugUnitTest                        # 単体
./gradlew connectedAndroidTest                     # 計装（debug）
./gradlew connectedAndroidTest -PtestBuildType=release   # 計装（R8適用後）
```

計装テストは実際のリポジトリを端末上に作り、`file://` のリモートに対して本当に push / pull します。

## ライセンス

[Apache License 2.0](LICENSE)。自由に使って構いませんが、**無保証**です。

念のため明示しておくと、このアプリはあなたのリポジトリとアクセストークンを扱います。データの喪失・トークンの漏洩・その他あらゆる損害について、作者は責任を負いません。自分のリポジトリでどう動くかは、自分で確かめた上で使ってください。

### 使っているもの

| | ライセンス |
|---|---|
| [Eclipse JGit](https://www.eclipse.org/jgit/) | EDL 1.0 (BSD 3-Clause) |
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 |
| [OkHttp](https://square.github.io/okhttp/) / [Retrofit](https://square.github.io/retrofit/) | Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache-2.0 |
| [Coil](https://coil-kt.github.io/coil/) | Apache-2.0 |

## わかっている制約

- 巨大なファイルはエディタで開けません（512KB超は読み取り専用、8MB超は開きません）
- SSH鍵での認証は未対応（HTTPS + トークンのみ）
- rebase、cherry-pick、stash、submodule の操作は未実装
- 大量のリポジトリや巨大な履歴での動作は検証していません
