#!/usr/bin/env bash
# LG Service Remote - ADB control (Linux/macOS)
# Usage:
#   ./lg_adb.sh setup [app-debug.apk]      install app + check IR blaster
#   ./lg_adb.sh installer [code]           Installer Menu (default 1105)
#   ./lg_adb.sh instart | ezadjust | instop
#   ./lg_adb.sh key OK|BACK|UP|DOWN|LEFT|RIGHT|SETTINGS|HOME|EXIT|POWER|0-9
#   ./lg_adb.sh digits 1234
#   ./lg_adb.sh net-instart <tv-ip> [secure] | net-ezadjust <tv-ip> [secure]
#   ./lg_adb.sh log
set -euo pipefail
PKG=com.dxbmr.lgservice
ACT=$PKG/.MainActivity
command -v adb >/dev/null || { echo "adb not found (install Android platform-tools)"; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "No phone found: connect USB and enable USB debugging"; exit 1; }

run() { adb shell input keyevent KEYCODE_WAKEUP; adb shell am start -n "$ACT" "$@"; }
sec() { [[ "${1:-}" == "secure" ]] && echo "--ez secure true" || echo "--ez secure false"; }

case "${1:-}" in
  setup)
    apk="${2:-app-debug.apk}"
    adb shell pm list features | grep -qi consumerir && echo "IR blaster: YES" || echo "IR blaster: NOT FOUND"
    [[ -f "$apk" ]] && adb install -r "$apk" || echo "APK not found: $apk" ;;
  installer)    run --es cmd installer --es code "${2:-1105}" ;;
  instart)      run --es cmd instart ;;
  ezadjust)     run --es cmd ezadjust ;;
  instop)       run --es cmd instop ;;
  key)          run --es cmd key --es key "${2:?key name}" ;;
  digits)       run --es cmd digits --es code "${2:?digits}" ;;
  net-instart)  run --es cmd net_instart  --es ip "${2:?tv ip}" $(sec "${3:-}") ;;
  net-ezadjust) run --es cmd net_ezadjust --es ip "${2:?tv ip}" $(sec "${3:-}") ;;
  log)          adb logcat -d -s LGSR:I ;;
  *) sed -n '2,11p' "$0"; exit 1 ;;
esac
