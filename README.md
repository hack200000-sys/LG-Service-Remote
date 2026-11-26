# LG Service Remote (Android)

Target: LG 43UK660H hotel TV (webOS, Pro:Centric) and other LG webOS TVs.

## Build the APK (GitHub, ~5 min, free)
1. Create a new GitHub repo and upload this folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab and wait for "Build APK" to finish (or start it with "Run workflow").
3. Open the run and download the **LGServiceRemote-apk** artifact, then unzip it to get `app-debug.apk`.
4. Copy it to the phone and allow "Install unknown apps".

Local build (needs Android Studio / SDK): `gradle :app:assembleDebug`

## Enabling casting on the 43UK660H
Casting is controlled in the **Installer Menu**, not the factory menu:
1. Tap **Open INSTALLER MENU** (IR phone pointed at the TV). The app holds Settings ~7 s, then sends 1105 + OK.
   If that fails, try 0413, 9876 or 0000, or do it by hand with the room remote.
2. Go to **Network / IP Environment → Media Share**.
3. Enable **Screen Share** (Miracast for Android and Windows) and **DIAL** (YouTube/Netflix "cast" button).
   Turn on **Smart Share / Media Renderer (DMR)** too, if you want DLNA.
4. Exit, then power-cycle the TV.
Google Cast / AirPlay on UK660H depends on the firmware and region; update the firmware first if the option is missing.
Note: Pro:Centric or a TV-clone/USB config can push settings back. If the setting reverts, change it in the
Pro:Centric server config or the clone file, then re-clone the TVs.

## Factory menus (only if needed)
- **IR In-Start / EZ-Adjust**: hidden service-remote keys. Password is usually 0413 (sometimes 0000).
- **NET In-Start / EZ-Adjust**: launch `com.webos.app.factorywin` over SSAP (port 3000 or 3001).
  Hotel mode often blocks network control; IR is the reliable path on hospitality TVs.
Photograph every screen before changing anything. Wrong values (model/panel options, tool option)
can disable inputs or brick the set.

## Requirements
- IR functions: a phone with an IR blaster (many Xiaomi/Redmi/POCO models, some Huawei/Honor). Most Samsung/Pixel phones don't have one.
- Network functions: phone and TV on the same LAN/VLAN. Accept the pairing prompt on the TV.

## Run from a PC over ADB (scripts/)
On the phone: enable Developer options and **USB debugging**, connect it by USB, and accept the "Allow USB debugging" prompt.
Install Android platform-tools on the PC, so `adb` is in PATH or next to the script. Put `app-debug.apk` next to the script for auto-install.
Open the app once by hand and accept the warning dialog.

- Windows: double-click `scripts\lg_adb.bat` (menu-driven).
- Linux/macOS: `./scripts/lg_adb.sh installer 1105`, `./scripts/lg_adb.sh key OK`, `./scripts/lg_adb.sh log`

Raw commands:
```
adb shell am start -n com.dxbmr.lgservice/.MainActivity --es cmd installer --es code 1105
adb shell am start -n com.dxbmr.lgservice/.MainActivity --es cmd key --es key DOWN
adb shell am start -n com.dxbmr.lgservice/.MainActivity --es cmd net_instart --es ip 192.168.1.50
adb logcat -s LGSR:I
```
Note: the TV itself (webOS) does not run ADB. ADB drives the phone, and the phone sends IR or network commands to the TV.

## Run on the phone itself (Termux, no PC)
1. Install the app and open it once (accept the warning).
2. Install **Termux** from F-Droid or GitHub (not the outdated Play Store build).
3. Copy `scripts/lg_phone.sh` to the phone's Downloads, then in Termux:
   ```
   termux-setup-storage
   cp ~/storage/downloads/lg_phone.sh ~/ && chmod +x ~/lg_phone.sh
   ~/lg_phone.sh
   ```
4. Pick from the menu, or run directly: `~/lg_phone.sh installer 1105`, `~/lg_phone.sh key OK`,
   `~/lg_phone.sh net-instart 192.168.1.50`.

If the app doesn't come to the front on Android 10+, allow Termux **Display over other apps**.
