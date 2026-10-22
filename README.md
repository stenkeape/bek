# Dual Apps Manager (app source)

Small root UI for the Dual Apps module: create the space, toggle apps, open clones.

## Build the APK (pick one)

**Android Studio:** open this folder, Build > Build APK(s).
Output: `app/build/outputs/apk/debug/app-debug.apk`

**GitHub (no setup):** push this folder to a new GitHub repo, open the Actions tab,
run "Build APK", download the `DualAppsManager` artifact.

**Command line** (Android SDK + Gradle 8.14): `gradle assembleDebug`

## Add it to the module

Rename the APK to `DualAppsManager.apk` and put it in the module's `apk/` folder
BEFORE zipping/flashing the module. Or, on an already installed module, copy it to
`/data/adb/modules/dualapps_clone/apk/DualAppsManager.apk` and run `su -c dualapp app`.
