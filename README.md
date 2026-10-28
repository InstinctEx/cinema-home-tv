# Cinema Home

A quiet Android TV home screen: a photograph or motion scene, time, AirPlay, Settings and a custom-icon app dock.
Press Down for Continue Watching, other apps and the Spotify pill.
Built with native Java views for an **F&U Android 11 TV with about 1 GB of RAM**.
The quiet photograph and shared glass dock take inspiration from [CleanFrame](https://github.com/nthnclrk/CleanFrame).

A fork of [cleanyourtv](https://github.com/Gathin23/cleanyourtv), with the launcher foundation
from [benfirad/tv-vibehack](https://github.com/benfirad/tv-vibehack). Maintained by
[InstinctEx](https://github.com/InstinctEx).

## What it does

Version **4.0.8** adds QR phone editing, animated wallpapers and screensavers, app organization, HDMI
shortcuts and expanded controls while keeping the custom cream-icon theme. See the
[4.0 feature guide and validation limits](docs/RELEASE-4.md).

- **App dock:** five chosen apps and a glass highlight that glides between selections. The same motion is used in Continue Watching, other apps and the top controls.
- **Below Home:** press Down to scroll to Continue Watching and other installed apps. Continue Watching resumes through entries published by supported apps, including NUVIO.
- **Spotify pill beneath the weather:** track, artist, cover art, progress and supported playback controls.
- **AirPlay:** opens the separately installed receiver.
- **Grouped settings:** appearance, scenes, apps, connections, sound and system controls.

Home has no discovery shelves, saved-title library, poster menus or trailer autoplay.
Pausing on a Continue Watching title loads its movie background; leaving the row restores the wallpaper.
There are no catalog or synopsis requests. Card artwork comes from the apps' published entries. A small weather line can appear below the clock after choosing a city in the paired phone editor. Read [network and permissions](docs/PRIVACY.md).

The photograph is bundled, glass uses a tiny sampled texture, and Home releases image references
when another app covers it. Cache limits are not total process memory limits.

## Build without touching a TV

For a browser design preview without Android tooling or a TV connection:

```sh
python3 preview/server.py
```

Open [localhost:8765](http://localhost:8765). Use arrow keys, Enter and Escape, or click.
Settings includes the scene, personalization and screensaver controls. Open
[the phone editor](http://localhost:8765/phone/) to change the shared demo state, upload media
and arrange apps. Enable **Sample media** to show sample Continue Watching and Spotify content.
This browser simulation plays scenes but does not run Android or change settings on your TV.
The server binds to this computer only and serves an explicit list of preview assets.
See [Aerial feature comparison](docs/AERIAL-IDEAS.md) for compatible ideas to add later.

Preview checks: `python3 -m unittest discover -s preview -v` and `node --check preview/app.js`.

Requires a full **JDK 17 or 21**, Android SDK platform **34**, build-tools **34.0.0**, and Python 3
for the helper tests. Gradle and dependencies download on the first build.

```sh
git clone https://github.com/InstinctEx/cinema-home-tv.git
cd cinema-home-tv
export ANDROID_HOME="$HOME/Library/Android/sdk"  # use your SDK location
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > launcher/local.properties
./launcher/gradlew -p launcher testDebugUnitTest lintDebug assembleDebug --console=plain
python3 -m unittest discover -s tests -v
```

The APK is produced at `launcher/app/build/outputs/apk/debug/app-debug.apk`.
Builds do not install anything. No prebuilt APK or private signing key is distributed.

## Install and customize

Start with the [setup and restore guide](SETUP.md). **Installation changes the TV's Home app.**
Keep a recovery record and leave the stock launcher enabled while verifying the replacement.
Cleanup is optional and separate from launcher installation.

```sh
# Only when you intend to connect to and change your TV:
export TV_HOST=192.0.2.10  # replace with your TV's address
./launcher/build-and-install.sh
```

Cinema Home retains the upstream package ID, `com.gathin.tvhome`. It is a replacement build,
not a side-by-side installation with other forks using that ID. A different signing certificate
can prevent an in-place update; preserve settings and recovery information before switching builds.

The current defaults target the inspected F&U set, including its Live TV package. Weather has no bundled location and is off by default. Other Android TV devices need configuration and testing. See
[device notes](docs/RELEASE-4.md) and [launcher architecture](docs/ARCHITECTURE.md).

## Tested and unverified

| Area | Status |
|---|---|
| Real TV | F&U BARTU_FU / menlo, MediaTek MT5867, Android 11, firmware RTMA.250416.176, 1280×720 UI, about 1 GB RAM |
| Current source | See the 4.0 validation guide; debug build, Android lint and automated tests are run locally |
| Current device checks | Installed on 22 October; see local validation record for remote checks |
| AirPlay | Shortcut to a separate receiver; no receiver bundled, end-to-end mirroring unverified |
| Other TVs / reboot persistence | No broad compatibility or cold-boot guarantee |

A lightweight launcher cannot add video-decoder capabilities to the TV. Playback quality,
4K support and REMUX performance belong to the player, stream and device hardware.

## Repository map

| Path | Purpose |
|---|---|
| [launcher/](launcher/) | Android app, resources and JVM tests |
| [tools/](tools/) | ADB transport, installer, reversible cleanup and recovery helpers |
| [tests/](tests/) | Helper tests using simulated TV responses |
| [SETUP.md](SETUP.md) | Setup, optional grants, installation and undo |
| [docs/PRIVACY.md](docs/PRIVACY.md) | Network endpoints, permissions and local storage |
| [docs/ASSETS.md](docs/ASSETS.md) | Asset origins, licenses and screenshot notes |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Reporting issues and contributing to this fork |

## Credits and license

MIT; the original Gathin copyright and license are preserved in [LICENSE](LICENSE).
Thanks to [Gathin23](https://github.com/Gathin23/cleanyourtv) for the original launcher and cleanup
project, and [benfirad](https://github.com/benfirad/tv-vibehack) for the launcher this version builds on.
Outfit uses the SIL Open Font License. App names, logos, movie artwork and screenshots remain
subject to their respective owners' rights; see [asset credits](docs/ASSETS.md).

Cinema Home is an independent community fork and is not affiliated with NUVIO, Stremio,
Spotify, Netflix, SmartTube, the TV vendor, or the upstream maintainers.
