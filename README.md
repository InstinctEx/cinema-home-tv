# Cinema Home TV

A quiet Android TV launcher: a photograph, clock, optional weather and a glass app dock. Press Down for Continue Watching and the rest of your apps.

Built for remote control and kept small enough to suit a TV with 1 GB of RAM. The interface uses native Android views, restrained motion and locally prepared glass textures.

## What is included

- Five app shortcuts with monochrome marks and a moving focus highlight.
- Continue Watching from supported apps’ published Android TV rows, with a background preview for the selected title.
- A Spotify media-session pill with playback controls when notification access is granted.
- An AirPlay receiver shortcut and a small Settings panel for wallpaper, dock apps, Spotify access and Android settings.
- Optional privately configured weather and remote shortcuts.

Player apps and an AirPlay receiver are installed separately. The launcher does not provide video sources, streaming accounts or addons. Continue Watching depends on what each app publishes.

## Build on your computer

Requires Android SDK 34, a compatible JDK (17 or 21), and Python 3 for the optional installation/recovery helpers.

```sh
cd launcher
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Set `ANDROID_HOME` to your SDK, or create a local `launcher/local.properties` containing `sdk.dir=YOUR_SDK_PATH`. The APK is generated at `launcher/app/build/outputs/apk/debug/app-debug.apk`. Building does not contact your TV.

```sh
python3 -m unittest discover -s tests
```

See [setup and recovery](SETUP.md) before installing. Android 8.0/API 26 or newer is required; vendor firmware and permissions vary. The launcher was checked on a low-RAM Android 11 TV. This does not guarantee compatibility with every television.

## Memory and privacy

Low-RAM devices use one artwork worker and a 4 MiB decoded-image cache. Artwork downloads, decoding and disk storage are bounded. Home releases image references when hidden, stops active observers on pause, and cancels stale backdrop work. These cache limits do not represent the app’s total memory use.

Public weather resources are blank. This repository contains no household configuration, TV captures, device backups or account credentials. Optional weather and online artwork make network requests; see [privacy](docs/PRIVACY.md).

## Project notes

- [Architecture](docs/ARCHITECTURE.md)
- [Review and validation](docs/REVIEW.md)
- [Asset credits](docs/ASSETS.md)
- [Contributing](CONTRIBUTING.md)

This is a standalone source snapshot based on [tv-vibehack](https://github.com/benfirad/tv-vibehack) and [cleanyourtv](https://github.com/Gathin23/cleanyourtv), with visual inspiration from [CleanFrame](https://github.com/nthnclrk/CleanFrame). Original attribution is retained. Code is covered by the [MIT license](LICENSE); fonts, photographs and brand marks retain their respective terms described in the asset credits.
