# Robotic Git

## 画面は Material 3 Expressive で作る

- Android の画面は Material 3 Expressive に従う。無印の Material 3 とは別物で、無印の部品や書き方へ戻さない
- 置き換え表と決まりは `android-m3-expressive` スキルにある
- 画面を書いたら `scripts/check_m3e.sh <パス>` が「問題なし」になること（Edit/Write のたびにフックでも走る）
- どうしても無印を使う行は、同じ行の末尾に `// m3e-allow: 理由` を書く
