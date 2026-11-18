# Dragon Mounts: Legacy Companion

懐いたドラゴンを指輪に紐づけ、召喚やミニオン化を行う Minecraft Forge 向けアドオンです。バージョン 0.1.0 では指輪の発行と Curios への装備ができます。召喚とミニオン化は今後実装します。

- [要件定義書](docs/要件定義書.md)
- [技術調査・実装方針](docs/技術調査.md)

## 開発環境

Minecraft 1.20.1、Forge 47.4.10、JDK 17 を使用します。開発時は Gradle Wrapper が Dragon Mounts: Legacy 1.2.3-beta と Curios API 5.14.1+1.20.1 を取得します。

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

専用サーバーの開発起動は `.\gradlew.bat runServer` を使用します。開発クライアントは `run/`、専用サーバーは `run-server/` にデータを作成します。

## 指輪の動作確認

1. ビルドした `build/libs/dragon-mounts-legacy-companion-0.1.0.jar` を使用します。以前の開発用 JAR がある場合は、新しい JAR に差し替えます。
2. まだ懐いていないドラゴンを DML の通常手順で懐かせます。成功時に指輪が1個手に入ります。インベントリが満杯の場合は足元付近に落ちます。
3. Curios の Ring スロットに装備します。別のプレイヤーへの指輪の受け渡しと、Ring スロットが複数ある環境で2個目の装備を試すと、装備が拒否されます。

すでに懐いていたドラゴンへの遡及発行は行いません。指輪の表示名は発行時の個体名または種類名に基づきます。
