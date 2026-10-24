# LetianStore — 自建应用商店框架（开源版）

3.0.1修复候选已完成隔离安装状态验证，但实体安装被Play保护拦截，稳定发布已撤回，仍待验收；详见[发布记录](docs/RELEASE-3.0.1.md)。

一个 ~60KB 的纯 Java Android 应用商店：浏览自建应用目录、下载（断点续传 + SHA256 校验）、安装、更新、自更新。无任何第三方依赖，no-gradle 纯 build-tools 构建。

**框架开源，配置不开源**：仓库里没有任何真实服务器地址/签名，全部通过打包时注入。

```
fork 本仓库
  → cp config/store.properties.example config/store.properties（填你的）
  → ./build_store.sh
  → 得到完全属于你自己的商店 APK
```

## 配置注入原理

商店运行所需的全部部署信息集中在 `StoreConfig.java` 的 5 个占位符里：

| 占位符 | properties 键 | 作用 |
|---|---|---|
| `__STORE_INDEX__` | `index_url` | apps.json 清单地址（唯一必需） |
| `__STORE_WEB__` | `web_url` | 网页版入口 |
| `__STORE_CDN__` | `cdn_base` | 大文件加速域 |
| `__STORE_NAME__` | `store_name` | 显示名 |
| `__SELF_SLUG__` | `self_slug` | 自更新 slug |

`build_store.sh` 打包时对 **.build 工作副本** 做 sed 替换，源码目录永不改动——所以 `git status` 永远干净，真实值永远不会被 commit。

### 配置优先级（运行时）

1. 打包注入值（StoreConfig）
2. APK 内 assets/store.properties（构建时可选内置）
3. `/sdcard/Android/data/<pkg>/files/store.properties`（免重打包调试覆盖）
4. 同源推导：`index_url` 的目录部分作为图标/相对路径基准

### 大文件加速（可选）

把清单里的 `.apk` URL 换成 CDN 域名即可（比如接对象存储 + CDN）。清单本身的 json 很小，任何静态服务器都能扛；`cdn_base` 配好后，商店会把旧域名 `.apk` 下载映射到加速域（先 HEAD 探测，通过才切换）。

## 服务端

只需要一个静态 `apps.json`（结构见下）+ 若干 APK + 图标 PNG，任何静态托管都行：

```json
{
  "updated": "2026-10-24",
  "self": { "versionCode": 9, "versionName": "3.0.1", "url": "https://…/store.apk",
            "sha256": "…", "bytes": 65536, "notes": "…" },
  "apps": [
    {
      "slug": "demo", "name": "示例应用", "package": "com.example.demo",
      "versionCode": 3, "versionName": "1.2.0", "minSdk": 24,
      "url": "https://…/demo-1.2.0.apk", "sha256": "…", "bytes": 12345678,
      "icon": "https://…/demo.png", "category": "工具", "dev": "you",
      "summary": "一句话简介", "notes": "更新说明",
      "tags": ["标签"], "features": ["特性"],
      "force": false, "updated": "2026-10-24",
      "web": "https://…", "source": "https://github.com/…"
    }
  ]
}
```

字段除 `slug/name/package/url` 外全部可选；`sha256` 提供则下载后强校验；`force: true` 预留强更。

## 构建

要求：JDK 8+、Android SDK（build-tools 34+ 与 platforms；`ANDROID_HOME` 或 `/opt/android-sdk`）。

```sh
./build_store.sh config/store.properties out/letian-store.apk
```

产物约 60-80KB（比 115KB 的旧版还小：没有 WebView 壳、没有冗余资源）。

## 目录结构

```
app/src/main/
  AndroidManifest.xml
  java/site/zhangyue/store/
    StoreConfig.java      ← 唯一注入位（打包时替换占位符）
    RuntimeConfig.java    ← 配置优先级/同源推导/加速域映射
    Model.java            ← apps.json 解析
    Repo.java             ← 目录缓存/刷新/CDN 探测
    Apps.java             ← 已安装状态比对
    DlManager.java        ← 串行下载队列/断点续传/SHA256/进度通知
    ApkProvider.java      ← content:// 暴露 APK 给系统安装器
    Installer.java        ← 安装流程 + 权限引导
    InstallReceiver.java  ← 安装结果回执
    IconCache.java        ← 图标磁盘+内存缓存
    MainActivity.java     ← 首页（搜索/分组/更新角标）
    DetailActivity.java   ← 详情页
    DownloadActivity.java ← 下载管理
    SelfUpdate.java       ← 商店自更新检测
    TextDrawable.java     ← 离线占位图标
    Http.java
  res/ (strings/themes/icons)
build_store.sh          ← no-gradle 一键构建 + 注入
config/store.properties.example
```

## License

MIT。框架与「乐天」品牌无关的任何人可自由使用；`config/store.properties.example` 里的示例域名仅作格式演示。
