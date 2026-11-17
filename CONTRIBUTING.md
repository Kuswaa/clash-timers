# Contributing

This is a solo project, so I'm not accepting pull requests. Bug reports and ideas in Issues are very welcome.

- **Bugs:** use the [bug report template](https://github.com/Kuswaa/clash-timers/issues/new/choose) and include the crash report from the app if there is one.
- **New game items:** if the widget shows `New building`, `New squad` or similar, the app's **Unknown items** section lists the item's number. Please put it in an issue together with the item's name in the game.
- **Ideas:** open an issue and describe what you'd like the widget to do.

If you build the app yourself, a few rules keep it working:

- **Never commit a real village export.** It contains your player tag. Test data goes in `app/src/test/resources/fixtures`, with the tag replaced by a fake one like `#TEST000`.
- **Run the tests:** `./gradlew :app:testDebugUnitTest`.
- **Keep R8 off.** Code shrinking caused a start-up crash on Android 10 that the build checks didn't catch.
- **Widget layouts** can only use classes that RemoteViews allows. Anything else, even a plain `View`, stops the whole widget from loading.
- **No network access.** The app has no internet permission, and that won't change.
