# Clash Timers

[![Latest release](https://img.shields.io/github/v/release/Kuswaa/clash-timers)](https://github.com/Kuswaa/clash-timers/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
[![License: MIT](https://img.shields.io/github/license/Kuswaa/clash-timers)](LICENSE)

A home screen widget for Android that shows your Clash of Clans builder, laboratory and pet house timers at a glance. It works fully offline: no account, no server, no ads and no internet permission.

<p align="center">
  <img src="docs/screenshots/hero.png" alt="The Clash Timers widget showing builder, lab and pet upgrades with live countdowns" width="420">
</p>

> This is an unofficial fan project and is not endorsed by Supercell. See the [disclaimer](#disclaimer).

## Features

- **Every timer in one widget:** all your builders, the Goblin Builder and Goblin Researcher during events, the laboratory and the pet house, with progress bars and icons.
- **Live countdowns or finish times:** times tick every second on the home screen, without opening the app. In Settings you can show the finish time instead (`Thu 18:36`), or both (`1d 4h · Thu 18:36`).
- **Real finish times:** the in-game timer ignores helpers you haven't used yet. Clash Timers includes the coming Builder's Apprentice and Lab Assistant sessions, so you see when an upgrade will really finish.
- **Potions and snacks:** tap to add a Builder Potion, Research Potion, Pet Potion, Builder Bite or Study Soup, and every countdown updates straight away. Boosts stack the same way they do in the game. Tap `−` to undo. Potions running in the game are picked up from the export.
- **Helpers:** tap `+` on a row to put the Apprentice or Lab Assistant on that upgrade. The bottom line shows what each helper is doing, its cooldown, or that it's ready again.
- **Notifications:** get told the moment a builder, the lab or the pet house is free, or a helper is ready. Tapping the notification opens Clash of Clans.
- **Sync from the game:** add the **Clash Sync** tile to Quick Settings, then copy the data in Clash, pull down the notification shade and tap the tile.
- **Several villages:** each village is saved separately, so syncing a second account doesn't overwrite the first. The widget shows the village you synced last.
- **New game items:** things from a game update that the app doesn't know yet show as `New building`, `New squad` and so on. In the app's **Unknown items** section you can give them a name and an image.
- **Any size:** at 4×5 you see everything; resize it smaller and the rows shrink, down to one line per section.
- **Adjustable:** if Supercell rebalances something, you can change potion strength, helper savings, the helper interval and the builder count in Settings.

<p align="center">
  <img src="docs/screenshots/potion-active.png" alt="The widget with a Builder Potion running: the potion button shows the time left and the builder countdowns are shorter" width="420">
</p>

## Install

Clash Timers isn't on the Play Store, so you install the APK yourself.

1. On your phone, download `ClashTimers-<version>.apk` from the [latest release](https://github.com/Kuswaa/clash-timers/releases/latest).
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Optional: compare the file's SHA-256 checksum with the one in the release notes.

Requires Android 8.0 or newer.

## Setup

1. **Open the app once.** It asks for the name to show on the widget. If you skip this, it shows "Chief".
2. **Add the widget.** Long-press your home screen, choose **Widgets**, find **Clash Timers** and place it. The default size is 4×5.
3. **Sync your village.** In Clash of Clans, go to **Settings → Data Export → Copy**. Go back to your home screen and tap **Sync** on the widget.
4. **Allow notifications** if you want finish alerts. You can turn them on under **Settings → Notifications** in the app.
5. **Optional: add the Clash Sync tile.** Pull down the notification shade, edit the Quick Settings tiles and drag **Clash Sync** in. Then you can sync without leaving the game.

### Battery and auto-start

Many phones stop background apps to save battery, which can make notifications arrive late or stop after a reboot. To prevent that, open the app, go to **Settings → App settings (battery, auto-start)**, and then:

- Set battery use to **Unrestricted** (some phones call it **No restrictions**).
- Turn on **Auto-start** if your phone has it. Tecno, Infinix, Xiaomi, Oppo, Vivo and others often do.

## How sync works

The game doesn't offer a live connection, so the widget works from the **village data export** you copy in the game.

1. **Copy:** the export is a block of text the game puts on your clipboard. It lists your upgrades and how much time each had left when you copied it.
2. **Sync:** when you tap Sync (on the widget, the Clash Sync tile or in the app), the app reads the clipboard once, checks that it's a village export and saves the timers under that village's tag. It doesn't read the clipboard at any other time. Android 12 and newer may show a "pasted from clipboard" message, which is normal.
3. **Countdown:** the export records when it was copied, so timers stay correct even if you sync a while later. From then on the app works out the remaining time on the phone.

Sync again whenever you start a new upgrade or change something in the game. The widget can't see the game by itself.

## Privacy

**The app has no internet permission**, so it can't send anything anywhere. You can check this in the [manifest](app/src/main/AndroidManifest.xml).

- There's no account, analytics, ads or tracking.
- It only reads the clipboard when you tap Sync.
- Your village data, settings and the names and images you add are stored in the app's private storage on your phone.
- Crash reports stay on your phone too. You choose whether to copy one and share it.
- The app opts out of Android backups and device-to-device transfers, so its data isn't copied to Google or to a new phone.

| Permission | Why it's needed |
|---|---|
| Exact alarms (`USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`) | Update the widget at the exact moment a timer finishes |
| Notifications (`POST_NOTIFICATIONS`) | Tell you when a builder, the lab or the pet house is free, or a helper is ready |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Set the alarms again after a reboot |

## FAQ

**Why does the widget show a shorter time than the game?**
The game's timer doesn't count helper sessions that haven't happened yet. The widget does, so it shows the predicted finish. The next helper session is exact. Later ones are a best case, because a session only starts once you're in the game. Each sync corrects the prediction.

**Sync failed. What should I do?**
Copy the data again in the game (**Settings → Data Export → Copy**) and tap Sync straight away. Sync fails if the clipboard holds something else, or if the export is older than the one already saved.

**The widget's countdowns don't tick on my launcher.**
Some launchers don't draw live countdowns properly. Turn off **Live seconds countdown** in the app and the widget shows times like `1d 20h` instead.

**Notifications arrive late or not at all.**
Follow the [battery and auto-start](#battery-and-auto-start) steps, and check that notifications are on in the app's Settings.

**A row says `New building` or `New squad`.**
That's something new from a game update which the app doesn't have a name for yet. Open the app's **Unknown items** section: you can type its name and pick an image for it there, and the widget uses them straight away. It would also help if you [open an issue](https://github.com/Kuswaa/clash-timers/issues/new/choose) with the number shown there, so the next version knows it.

**I play two accounts.**
Each village is saved separately. Sync the account you want to see; the widget shows the one you synced last, and notifications keep coming for both.

**Does it support the Builder Base?**
No, only the home village.

**Can this get my account banned?**
The app doesn't connect to the game or change it, and it doesn't automate anything. It only reads the text that the game's own export button copies. It's still an unofficial app, and Supercell's rules are theirs to decide.

**The app crashed. How do I report it?**
Open the app, find **Last crash**, tap **Copy** and paste the report into a [bug report](https://github.com/Kuswaa/clash-timers/issues/new/choose).

## Building from source

You'll need:

- [Android Studio](https://developer.android.com/studio), or the Android SDK with platform 37 installed.
- JDK 25. Android Studio's bundled JDK works, and Gradle downloads one if it can't find it.

Android Studio finds the SDK by itself. If you only use the command line, set `ANDROID_HOME` to your SDK folder first.

```sh
git clone https://github.com/Kuswaa/clash-timers.git
cd clash-timers
./gradlew :app:testDebugUnitTest   # run the unit tests
./gradlew assembleDebug            # debug build you can install right away
```

### Release build

```sh
./gradlew assembleRelease
```

Android only installs signed APKs. Without a keystore this produces `app-release-unsigned.apk`, so create your own:

```sh
keytool -genkeypair -v -keystore my-release.jks -alias mykey -keyalg RSA -keysize 4096 -validity 18250
```

Then put its details in `~/.android-signing/clashtimers/keystore.properties`:

```properties
storeFile=/absolute/path/to/my-release.jks
storePassword=your-store-password
keyAlias=mykey
keyPassword=your-key-password
```

To keep this file somewhere else, pass `-PclashtimersSigning=/path/to/keystore.properties`. Never commit your keystore or this file.

The signed APK is written to `app/build/outputs/apk/release/`. Android won't install it over the official release because the signatures differ, so uninstall that version first.

### Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/.../core` | Export parser, timer engine and widget model (plain Kotlin, unit tested) |
| `app/src/main/java/.../widget` | The RemoteViews widget |
| `app/src/main/java/.../sync` | Reading the clipboard and saving the result |
| `app/src/test/resources/fixtures` | Anonymized village exports used by the tests |
| `tools/` | Node scripts that regenerate the names, upgrade times and icons from [clash-of-clans-data](https://github.com/chiefpansancolt/clash-of-clans-data) |

This is a solo project, so pull requests aren't accepted, but bug reports and ideas are very welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

The code is under the [MIT License](LICENSE). That doesn't cover the game art and data, or the font. See [NOTICE](NOTICE) for details.

## Disclaimer

This material is unofficial and is not endorsed by Supercell. For more information see Supercell's Fan Content Policy: [www.supercell.com/fan-content-policy](https://www.supercell.com/fan-content-policy).

Clash of Clans names and images are the property of Supercell.
