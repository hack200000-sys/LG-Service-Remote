#!/data/data/com.termux/files/usr/bin/bash
# LG Service Remote - run ON THE PHONE (Termux), no PC needed.
# Sends commands to the installed LG Service Remote app, which then fires IR / network at the TV.
#
# Setup (once):
#   1. Install the app (app-debug.apk), open it once and accept the warning.
#   2. Install Termux from F-Droid or GitHub (the Play Store build is outdated).
#   3. Copy this file to the phone, then in Termux:
#        termux-setup-storage
#        cp ~/storage/downloads/lg_phone.sh ~/ && chmod +x ~/lg_phone.sh
#   4. Run:  ~/lg_phone.sh            (menu)
#      or:   ~/lg_phone.sh installer 1105 | key OK | digits 1234 | instart | ezadjust | instop
#            ~/lg_phone.sh net-instart 192.168.1.50 [secure] | net-ezadjust <ip> [secure]
#
# Android 10+: if the app doesn't come to the front, give Termux
# "Display over other apps" (Settings > Apps > Termux > Advanced).

PKG=com.dxbmr.lgservice
ACT=$PKG/.MainActivity

command -v am >/dev/null || { echo "'am' not found - run this inside Termux"; exit 1; }

run() {
  # --activity-single-top delivers repeat commands to the open app (onNewIntent)
  am start --activity-single-top -n "$ACT" "$@" >/dev/null 2>&1 \
    || { echo "Could not start $PKG - is the app installed?"; return 1; }
  echo "Sent: $*"
}
sec() { [ "${1:-}" = "secure" ] && echo "--ez secure true" || echo "--ez secure false"; }

do_cmd() {
  case "${1:-}" in
    installer)    run --es cmd installer --es code "${2:-1105}" ;;
    instart)      run --es cmd instart ;;
    ezadjust)     run --es cmd ezadjust ;;
    instop)       run --es cmd instop ;;
    key)          [ -n "${2:-}" ] && run --es cmd key --es key "$2" || echo "Usage: key OK|BACK|UP|DOWN|LEFT|RIGHT|SETTINGS|HOME|EXIT|POWER|0-9" ;;
    digits)       [ -n "${2:-}" ] && run --es cmd digits --es code "$2" || echo "Usage: digits 1234" ;;
    net-instart)  [ -n "${2:-}" ] && run --es cmd net_instart  --es ip "$2" $(sec "${3:-}") || echo "Usage: net-instart <tv-ip> [secure]" ;;
    net-ezadjust) [ -n "${2:-}" ] && run --es cmd net_ezadjust --es ip "$2" $(sec "${3:-}") || echo "Usage: net-ezadjust <tv-ip> [secure]" ;;
    *) sed -n '2,15p' "$0"; return 1 ;;
  esac
}

# Command-line mode
if [ $# -gt 0 ]; then do_cmd "$@"; exit $?; fi

# Interactive menu
pause() { sleep 0.4; }
while true; do
  cat <<'EOF'

=== LG Service Remote (point the phone at the TV) ===
 1) Installer Menu (hold Settings + code)
 2) IR In-Start        3) IR EZ-Adjust       4) IR In-Stop
 5) Arrow pad mode (w/a/s/d, o=OK, b=Back, e=Exit, m=Settings, h=Home, 0-9, q=quit)
 6) Type digits
 7) NET In-Start       8) NET EZ-Adjust
 9) Power
 0) Quit
EOF
  printf "Choice: "; read -r c
  case "$c" in
    1) printf "Code [1105]: "; read -r code; do_cmd installer "${code:-1105}"
       echo "Wait ~10 s. If no menu: try 0413, 9876 or 0000." ;;
    2) do_cmd instart ;;
    3) do_cmd ezadjust ;;
    4) do_cmd instop ;;
    5) echo "Pad mode - press a key (q to leave)"
       while IFS= read -rsn1 k; do
         case "$k" in
           w) do_cmd key UP ;;     s) do_cmd key DOWN ;;
           a) do_cmd key LEFT ;;   d) do_cmd key RIGHT ;;
           o) do_cmd key OK ;;     b) do_cmd key BACK ;;
           e) do_cmd key EXIT ;;   m) do_cmd key SETTINGS ;;
           h) do_cmd key HOME ;;   [0-9]) do_cmd key "$k" ;;
           q) break ;;
         esac
         pause
       done ;;
    6) printf "Digits: "; read -r dg; do_cmd digits "$dg" ;;
    7|8) printf "TV IP: "; read -r ip; printf "Secure port 3001? (y/N): "; read -r s
       [ "$s" = "y" ] && s=secure || s=""
       [ "$c" = 7 ] && do_cmd net-instart "$ip" $s || do_cmd net-ezadjust "$ip" $s
       echo "Accept the pairing prompt on the TV." ;;
    9) do_cmd key POWER ;;
    0|q) exit 0 ;;
    *) echo "?" ;;
  esac
done
