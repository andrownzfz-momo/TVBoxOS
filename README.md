# TVBoxOS

TVBox / 影视仓 系列 Android 直播点播客户端源码 + GitHub Actions 自动构建。

## 构建产物

- 每次 push 到 `main` 或手动触发 [build 工作流](.github/workflows/build.yml)，产出**已签名**的 `TVBoxOS_<时间>.apk`（Actions Artifact）。
- 手动触发并勾选「同时发布 GitHub Release」时，同时发到 Releases 页。
- [sync-upstream 工作流](.github/workflows/sync-upstream.yml) 每 6 小时同步上游 `q215613905/TVBoxOS` 的新提交。

签名密钥以 Actions Secrets 存放：`TVBOX_KEYSTORE_B64` / `TVBOX_KEYSTORE_PASSWORD` / `TVBOX_KEY_ALIAS` / `TVBOX_KEY_PASSWORD`。

## 源码构成与吸收来源

| 来源 | 许可 | 吸收内容 |
|---|---|---|
| [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) | AGPL-3.0 | **基座**（j4Uq/TVBoxOSC 每日构建的上游），整体源码 |
| [j4Uq/TVBoxOSC](https://github.com/j4Uq/TVBoxOSC) | — | 云构建流水线配方：Python 环境与依赖、`local.properties` 归零、gradle 签名注入方式、APK 重命名与发布、artifact 上传 |
| [takagen99/Box](https://github.com/takagen99/Box) | AGPL-3.0 | `BrotliInterceptor` + `internal/BrotliSource`（HTTP `Content-Encoding: br` 透明解压，`org.brotli:dec` 解码），已接入 `catvod/net/OkHttp` 主客户端 |
| [FongMi/TV](https://github.com/FongMi/TV) | GPL-3.0 | 架构对照参考（现代模块化实现、自带 signingConfigs 的 Gradle 签名做法） |
| [CatVodTVOfficial/TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC) | AGPL-3.0 | 源祖（archived） |

本仓库自有改动：

1. **默认 EPG** 指向自建零服务器 EPG 站：`https://epg.773211.xyz/epg.xml`（XMLTV，GitHub Actions 聚合 51zmt / fanmingming 定时更新）。
2. `versionName` 追加构建时间 `1.0.0.<yyyyMMdd-HHmm>`。
3. `app/build.gradle` 内置 `RELEASE_*` 属性签名配置（有则签，无则产出未签名包）。
4. `pyramid/build.gradle` 对写死的 `D:/Programs/Python/...` 路径加存在性守卫，Windows 本机与 Linux CI 均可配置。

## 本地构建

```powershell
# 需要 JDK 11~17（Gradle 7.5 不支持 21）、Android SDK
.\gradlew.bat assembleJavaRelease `
  -PRELEASE_STORE_FILE=E:\lsposed\TVBoxOS\.signing\TVBoxOS.jks `
  -PRELEASE_STORE_PASSWORD=xxx `
  -PRELEASE_KEY_ALIAS=tvboxos `
  -PRELEASE_KEY_PASSWORD=xxx
```

产物：`app\build\outputs\apk\java\release\TVBox_release-java.apk`。
6 个 flavor：`java`/`java32`/`java64`/`python`/`python32`/`python64`（本仓库 CI 只出 `java`）。

## EPG 接入说明

App 内默认 EPG 地址为 `https://epg.773211.xyz/epg.xml`。在「设置 → EPG 地址」可改：
- XMLTV 地址（`.xml` 结尾）按 XMLTV 解析；
- DIYP 模板地址需含 `{name}` 与 `{date}` 占位符。

## 致谢

Credits: [CatVodTVOfficial/TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC)、[q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS)、[takagen99/Box](https://github.com/takagen99/Box)、[j4Uq/TVBoxOSC](https://github.com/j4Uq/TVBoxOSC)、[FongMi/TV](https://github.com/FongMi/TV)。
