# Setup and recovery

## Prepare

Keep a private record of your original Home component, enabled packages and any settings you change. Keep the stock launcher enabled until you have verified the replacement. Review the dock, hidden apps and Live TV package in `launcher/app/src/main/res/values/config.xml`; tuner package names vary by TV.

Install Android platform-tools and authorize your computer using the TV’s debugging settings. Set `TV_HOST` to your own TV address. Examples use documentation-only addresses.

## Install the launcher

```sh
export TV_HOST=192.0.2.10
./launcher/build-and-install.sh
```

The helper builds the launcher, records the previous Home privately in `backup/install-*/original-home.json`, installs the APK, starts it and verifies Home selection. It attempts to restore the previous Home if handover fails. It does not install another test app or grant optional services. Preserve the original recovery record across updates.

The package is `com.gathin.tvhome`, shared with upstream variants. An update must use the same signing certificate as the installed app. A signature conflict requires planning a migration; do not routinely uninstall an existing launcher and lose its data.

## Optional access

- **Continue Watching:** where supported, grant `android.permission.READ_TV_LISTINGS` to `com.gathin.tvhome` using ADB. Apps must publish their own rows; this does not expose private player databases.
- **Spotify:** select Settings → Spotify control access and enable Cinema Home in the system notification access screen. No Spotify API key is needed.
- **AirPlay:** the shortcut opens `io.github.jqssun.airplay` if independently installed. It does not bundle a receiver.
- **Remote shortcuts:** the optional accessibility service filters configured remote keys and cannot retrieve window contents. Bindings are device-local; public defaults do not intercept a particular remote’s YouTube key.

Settings contains wallpaper, dock selection, Spotify access and TV settings. Earlier experimental controls retained in legacy source are not part of the normal Home interface.

## Private weather configuration

Public location resources are empty. To configure weather for a local debug build, create an ignored `launcher/app/src/debug/res/values/weather-local.xml` resource file defining `weather_location_name`, `weather_latitude` and `weather_longitude`. Use your own valid values. Home shows weather when all three are configured; no location picker or additional Home toggle is needed. Keep this file private and do not distribute the personalized APK. Release builds do not include the debug overlay.

## Recovery

With ADB authorized, use the original package and component from your private record:

```sh
adb -s "$TV_HOST:5555" shell pm enable --user 0 ORIGINAL_STOCK_PACKAGE
adb -s "$TV_HOST:5555" shell cmd package set-home-activity --user 0 ORIGINAL_HOME_COMPONENT
adb -s "$TV_HOST:5555" shell input keyevent KEYCODE_HOME
```

The optional computer cleanup helpers are separate from launcher installation. No household removal list is shipped. Use only a package list you have reviewed for your own firmware, and retain its recovery records.

Turn debugging off after setup when you no longer need it. Installation and injected remote commands can interrupt viewing; perform them while the TV is available.
