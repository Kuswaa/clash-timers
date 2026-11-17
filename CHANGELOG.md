# Changelog

All notable changes to this project are listed here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

1.0.2 is the first version published on GitHub. Earlier versions were shared privately.

## [1.1.0] - 2026-11-17

### Added

- **Goblin Researcher:** during the event the lab can run two researches at once. The widget now shows both, with a `GOBLIN` row for the Goblin's research that disappears when it's done (#2, #4).
- **Study Soup:** a new button on the LAB line, next to the research potion. One Soup speeds up the lab, the Goblin Researcher and the pet house (4× for an hour), and it stacks and undoes like the Builder Bite.
- **Finish times:** a setting to show when an upgrade finishes (`Thu 18:36`) instead of, or next to, the time left (`1d 4h · Thu 18:36`).
- **Quick Settings tile "Clash Sync":** copy the data in the game, pull down the notification shade and tap the tile to sync without leaving Clash.
- **Unknown items:** a section in the app that lists items from a game update that the app doesn't know yet. You can give them a name and pick an image for them; both stay on your phone.
- **Town Hall 19:** names and icons for the Squad Barracks, Spiky Tower, Firecracker Tower and Barch Squad.
- **Several villages:** each village tag gets its own saved data. Syncing a second account no longer overwrites the first one's timers, helper links or potions. The widget shows the village synced last.
- Notification when the Builder's Apprentice or the Lab Assistant is ready again.

### Changed

- The helper line now says what each helper is doing: working (`Apprentice: King · next 20:49`), on cooldown (`Apprentice: 14h`) or ready (`Apprentice ready ✓`). It counts forward by itself between syncs. "Idle" was misleading.
- Potion and snack rules are fixed instead of a setting: the Builder Potion and Builder Bite also speed up the Goblin Builder, the Research Potion the Goblin Researcher, and helpers are never multiplied by potions. The "Goblin potion" switch is gone.
- Unknown items show as `New building`, `New squad`, `New spell` and so on with a matching icon, instead of `Unknown (1000123)` and initials.
- Smaller widgets: rows shrink when the widget is resized below its normal size, and a very small widget shows one line per section with the next finish (#1).
- Potion, Bite and Soup buttons have bigger, fixed tap areas.
- App data is no longer included in Android backups or device-to-device transfers. It stays on your phone.

### Fixed

- Research and pet potions running in the game were ignored. The app now reads them from the export, like the builder potion.
- Fixed a crash, "Broadcast already finished", on some phones (seen on vivo, Android 12) when the widget took too long to refresh (#7).
- Widget taps, especially on the Bite button, were sometimes ignored.
- A helper export without `helper_cooldown` (the Prospector) no longer breaks the helper timing.

## [1.0.2] - 2026-11-03

### Fixed

- Fixed a crash at start-up on Android 10 that affected 1.0 and 1.0.1. It came from code optimization in the release build.

### Changed

- Release builds no longer use code shrinking or optimization, so the app runs reliably across phones and Android versions. The APK is larger as a result, about 9 MB instead of about 2 MB.

## 1.0.1 - 2026-11-03

### Added

- Crash reporting that stays on the phone: a crash screen, and a **Last crash** section in the app with Copy, Show and Clear.
- A notification that opens the crash screen when the app crashes in the background.

## 1.0 - 2026-11-03

First release.

### Added

- Home screen widget with builder, laboratory and pet house timers, live countdowns and progress bars.
- Sync from the game's village data export through the clipboard.
- Finish times that include Builder's Apprentice and Lab Assistant sessions, potions and Builder Bites.
- Support for the event Goblin Builder.
- Notifications when a builder, the lab or the pet house is free.
- Settings for boost strength, helper savings, helper interval and builder count.

[1.1.0]: https://github.com/Kuswaa/clash-timers/releases/tag/v1.1.0
[1.0.2]: https://github.com/Kuswaa/clash-timers/releases/tag/v1.0.2
