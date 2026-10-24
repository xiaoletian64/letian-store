#!/bin/sh
# build_store.sh — 乐天商店框架 no-gradle 一键构建（106 服务器验证可用）
#
# 用法:
#   ./build_store.sh [config/store.properties] [输出.apk]
#
# 开源要点：真实地址/账号不进仓库。打包时把 config/store.properties 里的
# 值注入到 .build 副本的 StoreConfig.java，源码目录永不改动。
set -e

PROPS="${1:-config/store.properties}"
OUT="${2:-}"
DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR"

[ -f "$PROPS" ] || { echo "✗ 缺少配置文件 $PROPS（参考 config/store.properties.example）"; exit 1; }

get() { grep "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r'; }

INDEX_URL=$(get index_url)
WEB_URL=$(get web_url)
CDN_BASE=$(get cdn_base)
STORE_NAME=$(get store_name)
SELF_SLUG=$(get self_slug)
VC=$(get versionCode)
VN=$(get versionName)
KEYSTORE=$(get keystore)
ALIAS=$(get alias)
KSPASS=$(get storepass)
KEYPASS=$(get keypass)
ASSETS_PROPS=$(get assets_props)

[ -n "$INDEX_URL" ] || { echo "✗ 配置缺 index_url"; exit 1; }
[ -n "$VC" ] && [ -n "$VN" ] || { echo "✗ 配置缺 versionCode/versionName"; exit 1; }
OUT="${OUT:-store-v$VN.apk}"

# ---- 工具链探测（Android SDK / build-tools）----
if [ -d /opt/android-sdk ]; then SDK=/opt/android-sdk
elif [ -d "$ANDROID_HOME" ]; then SDK="$ANDROID_HOME"
elif [ -d "$HOME/Android/Sdk" ]; then SDK="$HOME/Android/Sdk"
else echo "✗ 找不到 Android SDK（设 ANDROID_HOME）"; exit 1; fi
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
PLATFORM=$(ls "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)
[ -x "$BT/aapt2" ] && [ -n "$PLATFORM" ] || { echo "✗ build-tools/platforms 不完整: $BT / $PLATFORM"; exit 1; }
echo "· SDK: $SDK  BT: $(basename "$BT")"

# ---- 工作副本 ----
rm -rf .build && mkdir -p .build/classes
cp -r app/src/main/. .build/
# 占位符注入（只动副本）
sed -e "s|__STORE_INDEX__|$INDEX_URL|" \
    -e "s|__STORE_WEB__|$WEB_URL|" \
    -e "s|__STORE_CDN__|$CDN_BASE|" \
    -e "s|__STORE_NAME__|$STORE_NAME|" \
    -e "s|__SELF_SLUG__|$SELF_SLUG|" \
    .build/java/site/zhangyue/store/StoreConfig.java > .build/StoreConfig.java.new
mv .build/StoreConfig.java.new .build/java/site/zhangyue/store/StoreConfig.java
echo "· 配置已注入: index=$INDEX_URL"

# ---- assets（可选运行时覆盖配置）----
if [ -n "$ASSETS_PROPS" ] && [ -f "$ASSETS_PROPS" ]; then
    mkdir -p .build/assets && cp "$ASSETS_PROPS" .build/assets/store.properties
    echo "· assets store.properties 已内置"
fi

# ---- aapt2 编译资源 + 链接 ----
"$BT/aapt2" compile --dir .build/res -o .build/res.zip
[ -d .build/assets ] || mkdir -p .build/assets
"$BT/aapt2" link -o .build/base.apk \
    -I "$PLATFORM" \
    --manifest .build/AndroidManifest.xml \
    --version-code "$VC" --version-name "$VN" \
    --min-sdk-version 24 --target-sdk-version 35 \
    -A .build/assets \
    --auto-add-overlay \
    .build/res.zip
echo "· 资源链接完成"

# ---- javac（严格：有任何错误直接终止）----
find .build/java -name "*.java" > .build/srcs.txt
JOUT=$(javac -encoding UTF-8 -source 8 -target 8 -nowarn \
    -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" \
    -d .build/classes @.build/srcs.txt 2>&1)
echo "$JOUT" | grep -q "error:" && { echo "$JOUT" | tail -40; echo "✗ javac 失败"; exit 1; }
[ -d .build/classes/site ] || { echo "✗ javac 无输出（失败）"; echo "$JOUT" | tail -20; exit 1; }
echo "· javac 完成"

# ---- d8 dex ----
find .build/classes -name "*.class" > .build/classes.txt
"$BT/d8" --release --lib "$PLATFORM" --min-api 24 \
    --output .build @.build/classes.txt
[ -f .build/classes.dex ] || { echo "✗ d8 失败"; exit 1; }
(cd .build && zip -q -j base.apk classes.dex)
echo "· dex 完成"

# ---- 对齐 + 签名 ----
"$BT/zipalign" -f 4 .build/base.apk .build/aligned.apk
if [ -n "$KEYSTORE" ] && [ -f "$KEYSTORE" ]; then
    "$BT/apksigner" sign \
        --ks "$KEYSTORE" --ks-key-alias "$ALIAS" \
        --ks-pass "pass:$KSPASS" --key-pass "pass:$KEYPASS" \
        --out "$OUT" .build/aligned.apk
    "$BT/apksigner" verify --print-certs "$OUT" | head -3
else
    echo "⚠ 无 keystore，输出未签名包 $OUT-unsigned"
    cp .build/aligned.apk "$OUT-unsigned"
fi

SHA=$(sha256sum "$OUT" | cut -c1-16)
SIZE=$(du -h "$OUT" | cut -f1)
echo "✓ 构建完成: $OUT  ($SIZE, sha256 $SHA…)"
