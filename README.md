# Dragon Mounts: Legacy Companion

懐いたドラゴンを指輪に紐づけ、召喚やミニオン化を行う Minecraft Forge 向けアドオンです。現在は技術調査と開発基盤の段階です。

- [要件定義書](docs/要件定義書.md)
- [技術調査・実装方針](docs/技術調査.md)

## 開発環境

Minecraft 1.20.1、Forge 47.4.10、JDK 17 を使用します。開発時は Gradle Wrapper が Dragon Mounts: Legacy 1.2.3-beta と Curios API 5.14.1+1.20.1 を取得します。

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

専用サーバーの開発起動は `.\gradlew.bat runServer` を使用します。開発クライアントは `run/`、専用サーバーは `run-server/` にデータを作成します。
