#!/bin/sh
# release-checklist.sh — 乐天商店发版检查单：每次发版前跑一遍，适配项不再漏
# 用法: sh release-checklist.sh <apk路径> <期望versionCode>
set -e
APK="${1:?用法: release-checklist.sh <apk> <vc>}"
VC="${2:?缺期望versionCode}"
BT=/opt/android-sdk/build-tools/36.0.0
AAPT="$BT/aapt"
FAIL=0
say() { printf '%s %s\n' "$1" "$2"; }
chk() { if [ "$1" = "$2" ]; then say "✓" "$3"; else say "✗" "$3（期望 $2，实际 $1）"; FAIL=1; fi; }

say "== 基础 =="
BADGING=$($AAPT dump badging "$APK" | head -1)
chk "$(echo "$BADGING" | grep -oE "versionCode='[0-9]+'" | cut -d"'" -f2)" "$VC" "versionCode 单调递增（查历史：3.0.0=8 3.0.1=9 3.0.2=10 3.0.3=11 3.0.4=13）"
chk "$($BT/apksigner verify --print-certs "$APK" 2>/dev/null | grep -c 'SHA-256 digest')" "1" "签名可验（companion 61d8245f…）"

say "== 适配 =="
DUMP=$($AAPT dump xmltree "$APK" --file AndroidManifest.xml 2>/dev/null)
echo "$DUMP" | grep -q "RECEIVE_BOOT_COMPLETED" && say "✓" "BOOT 权限（开机自动更新）" || { say "✗" "缺 RECEIVE_BOOT_COMPLETED"; FAIL=1; }
echo "$DUMP" | grep -q "UpdateService" && say "✓" "UpdateService 注册" || { say "✗" "缺 UpdateService"; FAIL=1; }
echo "$DUMP" | grep -q "ApkProvider\|\.apk\"" && say "✓" "ApkProvider（装包必需，v3 已知坑）" || { say "✗" "缺 ApkProvider"; FAIL=1; }
unzip -l "$APK" | grep -q "mipmap-anydpi-v26/ic_launcher.xml" && say "✓" "自适应图标（各厂商桌面）" || { say "✗" "缺 anydpi-v26 自适应图标"; FAIL=1; }
unzip -l "$APK" | grep -q "res/values-v29" && say "✓" "DayNight 主题（深色跟随系统）" || { say "✗" "缺 values-v29 DayNight"; FAIL=1; }
echo "$DUMP" | grep -q "uiMode" && { say "✗" "configChanges 含 uiMode（深色切换不重建，必须去掉）"; FAIL=1; } || say "✓" "configChanges 无 uiMode"
"$BT/dexdump" "$APK" 2>/dev/null | grep -q "Ui;" && say "✓" "Ui 调色板类在 dex（深浅色共用）" || { say "✗" "Ui 类缺失"; FAIL=1; }

say "== 功能 =="
"$BT/dexdump" "$APK" 2>/dev/null | grep -q "sweepInstalled" && say "✓" "装完自动删包逻辑" || { say "✗" "缺 sweepInstalled"; FAIL=1; }
"$BT/dexdump" "$APK" 2>/dev/null | grep -q "urlFallback" && say "✓" "备用源回落字段" || { say "✗" "缺 urlFallback"; FAIL=1; }

say ""
[ "$FAIL" = "0" ] && echo "ALL_PASS" || { echo "HAS_FAIL"; exit 1; }
