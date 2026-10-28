# Launcher architecture

`MainActivity` initially presents a photograph, time, weather, AirPlay, Settings and a five-app dock,
with Spotify beneath the weather when a session is available.
Down builds the lower page on demand, then scrolls to Continue Watching, other installed apps. Lower-page artwork is not requested until that page is opened. Spotify sits below
the weather in the header. `GlideFocus` supplies one moving glass highlight per row/control group;
native property animation drives both the highlights and scrolling. Settings exposes appearance, scenes/screensavers, QR editing, organization, inputs, network, sound/picture, privacy and maintenance. See [the 4.0 guide](RELEASE-4.md).

`ContinueWatching` reads supported apps' published Android TV entries, ranks them before retaining
at most twelve, and preserves app resume intents. Only NUVIO's continuation preview channel is imported.
`SpotifySessions` and `MusicAccessService` supply the optional music pill. The existing weather helper provides a small cached line below the clock when a location is configured.
It refreshes hourly while Home is visible and stops scheduling work when Home pauses. `ArtworkLoader` and `ArtworkDiskCache` bound artwork work and storage.
`FrostedPanelDrawable` prepares a smooth 256×144 ARGB glass texture with a small separable blur, caches it for the current wallpaper, and draws a restrained gradient edge. It does not blur the screen every frame.

Home does not load discovery catalogs or synopsis metadata, prefetch neighboring backdrops,
or render saved-title shelves. The earlier ClassicActivity/AppAdapter and their catalog helper remain
as legacy source; normal Home does not instantiate them. The existing Android ClockDream is retained
as the TV's registered system screensaver; Home also supports a configurable idle scene through `SceneView`.

Pause stops UI ticks, TV-row observers and Spotify callbacks. Stop invalidates image work and clears
ImageView references. Resume reconstructs the two rows and reloads images from disk. Low-RAM devices
use one artwork worker and a 4 MiB decoded-image cache, plus a 48 MiB disk cache. Native/GPU views,
ImageViews and other allocations are additional memory.

Validation: 30 launcher JVM tests, Android debug build and lint. The separate Python helper tests
cover cleanup/recovery and installation, not the visual layout or physical remote feel.

Continue Watching requests a bounded 1280×720 backdrop after a 280 ms focus dwell. Generation checks reject late results; leaving the row fades back to the wallpaper. Quiet dock glass samples its original Home position, compensating for ancestor scrolling so a return from the lower page cannot leave a clamped grey texture.

## Version 4 components

`LauncherLayout` owns stable app order, hidden apps, names and folder membership. `FeaturePolicy`
holds independently tested validation and ordering rules. `HomeSettings` validates portable visual
settings; device IDs, PINs, weather coordinates and release hosts are excluded from exports.

`SceneView` owns a single MediaPlayer/TextureView and bounded photo decoding worker. It reuses the
player when the wallpaper continues into the screensaver, captures a small still when pausing, and
releases decoder resources on pause. `MediaLibrary` stores bounded uploads in private app storage.
`PhoneServer` uses a fresh token and temporary socket server; worker threads handle uploads and
HTTP, while state changes are dispatched to the main thread. `assets/phone/` is the same editor
served by the native app and the loopback browser simulator.

`PinLock` stores salted derived PINs, `FrameStats` retains a bounded frame-time ring,
`DailyWallpaper` performs bounded HTTPS downloads, and `LauncherUpdates` accepts only newer APKs
matching this package and its current signing certificate. `BootReceiver` only starts Home when
Android already resolves Cinema Home as the selected HOME application.
