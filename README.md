# MX3 Launcher

A minimal Google TV launcher that shows a flat grid of
apps and nothing else.

## Features

- **Flat app grid**, 5/6/7 columns (your choice), showing every
  installed launchable app.
- **Top bar**: live clock, a Wi-Fi status icon that jumps straight to
  system network settings, a system settings shortcut, and this app's own
  settings shortcut.
- **Theme**: system / light / dark, with a choice of background gradients.
- **Show/hide and reorder apps** from a dedicated settings screen —
  reordering is move-up/move-down buttons rather than drag-and-drop,
  since drag gestures don't have a sane D-pad equivalent.
- **Left/right wraps around** the edge of a row, so you don't have to
  cross the whole grid one column at a time to get from the first app in
  a row to the last (or back).
- **Press Menu on a focused app icon** to jump straight to that app's own
  system "App info" page (permissions, storage, uninstall) — the usual
  long-press destination on touch launchers, reached here without a
  touch-only gesture.
- **Backup and restore** your settings (theme, gradient, columns, app
  order/visibility) to the Downloads folder, with multiple timestamped
  backups to pick from when restoring, plus a one-tap reset to defaults.
- **Recovers automatically after standby.** If the TV's own built-in
  launcher takes over the screen after waking from standby (which can
  happen if Android frees up the launcher's memory while the screen was
  off), this brings MX3 Launcher back to the front on its own.

## Installing

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and hit Run.

After installing, press Home on the device — Android will prompt you to
choose a launcher (or go to `Settings → Apps → Default apps → Home app`
to switch manually later). Pick MX3 Launcher.

## Settings

Everything customizable lives in the launcher's own Settings screen
(reachable from the gear icon in the top bar): theme, background
gradient, number of columns, and which apps show up and in what order.
Backup, restore, and reset-to-defaults are in the same screen, under
"Backup & restore."

## License

GPL-3.0-or-later. See `LICENSE`.
