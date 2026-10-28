# Setup and restore

Cinema Home is a source-built launcher replacement for Android TV 8+ (API 26). The tested
configuration is described in [release validation notes](docs/RELEASE-4.md). Build instructions are in the
[README](README.md#build-without-touching-a-tv).

## Before installation

1. Record the model, firmware, installed packages, disabled packages, current Home component and
   any settings you intend to change. Keep those files private and outside Git.
2. Install Android platform-tools on your computer. Enable debugging on the TV only for setup,
   authorize your computer and use a trusted local network.
3. Check the current Home and keep its component/package in your recovery notes. Keep the stock
   launcher enabled until Cinema Home starts, accepts D-pad input and handles Home correctly.
4. Edit `launcher/app/src/main/res/values/config.xml` for your dock and Live TV package. The shipped
   Live TV default is MediaTek-specific; do not assume it matches a different TV.
5. Review [privacy and permissions](docs/PRIVACY.md), including the online features and optional weather configuration.

## Install

```sh
export TV_HOST=192.0.2.10  # example only; replace with your TV's address
./launcher/build-and-install.sh
```

The script builds locally and calls `tools/install_launcher.py`. The installer connects with
ADB, records the previous Home in a private `backup/install-*/original-home.json`, installs the
APK, confirms the activity starts, selects it as Home and verifies resolution. It attempts to
restore the prior Home if handover fails. It does not apply a cleanup list or grant optional access.
Vendor behavior can still differ; check navigation and a return from another app before proceeding.

`DISABLE_STOCK_LAUNCHER=1` is an explicit fallback for a TV that refuses handover while stock Home
is enabled. It is not the default. Use it only after checking the recorded Home, a working recovery
path and the device-specific requirements. Never disable input, pairing, keyboard, network or core Android services.

The package ID is shared with upstream forks. If Android reports a signature conflict, stop and
plan a migration; do not delete an existing launcher's data as a routine install step.

## Optional access

- **Continue Watching:** grant TV listings access if Android permits it:
  `adb -s "$TV_HOST:5555" shell pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS`.
  Some vendor permission models differ. The feature depends on apps publishing entries; this grant
  cannot reveal private viewing history that an app does not publish.
- **Spotify:** open Control Center → Spotify Now Playing → Music control access, then enable
  Cinema Home in Android's notification access settings. No Spotify Web API credentials are used.
- **Remote shortcuts and sleep timer:** enable Cinema Home's accessibility service only if you
  want these functions. It does not retrieve window contents. Some TVs do not expose the required
  standby action; the timer is off by default.
- **AirPlay:** install and configure a compatible receiver independently if desired. The launcher
  only opens `io.github.jqssun.airplay`; it does not install, bundle or auto-start a receiver.

## Customize

Control Center edits dock apps, the poster app, wallpapers, dimming, glass, sound and optional
online rows. A document picker is needed to select a custom wallpaper or export/import settings.
Backup & restore Home saves a local restore point and validates portable JSON imports. App accounts,
viewing history, timers and remote key assignments are excluded; reselect custom wallpaper files after import.

NUVIO can ask for a profile; choose your own. NUVIO and Stremio handle sources, playback and accounts.
The launcher is not a streaming service and does not install addons or configure content providers.

Weather is off and unconfigured by default. To use it, privately fill `weather_location_name`,
`weather_latitude` and `weather_longitude` in `config.xml`, rebuild and enable Weather in Control Center.
All three values must be valid before any forecast request is made. Keep personal coordinates out
of commits. The provider resolves the timezone from those configured coordinates; no GPS is used.
There is no on-TV location picker.

## Optional cleanup

You do not need to debloat the TV to use this launcher. Read each package and comment in the
matching example first. The F&U list records one household's choices and has only a short test
history; it is not a general safe list for all F&U TVs. Voice, casting, streaming apps and firmware
updaters are personal choices, not required removals.

```sh
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.0.2.10
./tools/disable.sh YOUR_REVIEWED_LIST.txt
# Undo the recorded cleanup:
./tools/restore.sh YOUR_REVIEWED_LIST.txt
```

The helpers use reversible `pm disable-user`, keep recovery records and guard Home, TV inputs and
setup-wizard Wi-Fi ownership. Do not use override flags to bypass an unexplained refusal. Never
run a list for a different board just because its brand matches.

## Restore stock Home

Use the recorded original component and package from your private recovery record, not a guessed
package from another TV. With ADB still authorized:

```sh
adb -s "$TV_HOST:5555" shell pm enable --user 0 ORIGINAL_STOCK_PACKAGE
adb -s "$TV_HOST:5555" shell cmd package set-home-activity --user 0 ORIGINAL_HOME_COMPONENT
adb -s "$TV_HOST:5555" shell input keyevent KEYCODE_HOME
```

Replace the uppercase placeholders with your recorded values. Cleanup restore and Home selection
are separate: verify both. This procedure leaves Cinema Home installed. Preserve your original
recovery record when updating the APK; an update-time record may already name Cinema Home.

After setup, turn debugging off, revoke authorizations and verify the network ADB port is closed.
Do not interrupt another person's viewing to run installation, restore or injected-key checks.
