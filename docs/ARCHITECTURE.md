# Launcher architecture

`MainActivity` initially presents a photograph, time, weather, AirPlay, Settings and a five-app dock,
with Spotify beneath the weather when a session is available.
Down builds the lower page on demand, then scrolls to Continue Watching, other installed apps. Lower-page artwork is not requested until that page is opened. Spotify sits below
the weather in the header. `GlideFocus` supplies one moving glass highlight per row/control group;
native property animation drives both the highlights and scrolling. Settings has four entries: wallpaper, dock apps, music control access and Android settings.

`ContinueWatching` reads supported apps' published Android TV entries, ranks them before retaining
at most twelve, and preserves app resume intents. Only NUVIO's continuation preview channel is imported.
`SpotifySessions` and `MusicAccessService` supply the optional music pill. The existing weather helper provides a small cached line below the clock when a location is configured.
It refreshes hourly while Home is visible and stops scheduling work when Home pauses. `ArtworkLoader` and `ArtworkDiskCache` bound artwork work and storage.
`FrostedPanelDrawable` prepares a smooth 256×144 ARGB glass texture with a small separable blur, caches it for the current wallpaper, and draws a restrained gradient edge. It does not blur the screen every frame.

Home does not load discovery catalogs or synopsis metadata, prefetch neighboring backdrops,
or render saved-title shelves. The earlier ClassicActivity/AppAdapter and their catalog helper remain
as legacy source; normal Home does not instantiate them. The existing Android ClockDream is retained
because it is configured as the TV's system screensaver; Home no longer has a second idle screen.

Pause stops UI ticks, TV-row observers and Spotify callbacks. Pause invalidates image work; stop clears
ImageView references. Resume reconstructs the two rows and reloads images from disk. Low-RAM devices
use one artwork worker and a 4 MiB decoded-image cache, plus a 48 MiB disk cache. Native/GPU views,
ImageViews and other allocations are additional memory.

Validation: 30 launcher JVM tests, Android debug build and lint. The separate Python helper tests
cover cleanup/recovery and installation, not the visual layout or physical remote feel.

Continue Watching requests a bounded 1280×720 backdrop after a 280 ms focus dwell. Generation checks reject late results; leaving the row fades back to the wallpaper. Quiet dock glass samples its original Home position, compensating for ancestor scrolling so a return from the lower page cannot leave a clamped grey texture.

Home returns with an opacity fade for the header and dock after wallpaper and layout readiness and window focus, with a bounded image-readiness fallback. The 500 ms view animation respects the system animation scale (300 ms at a scale of 0.6). The wallpaper brightens gently alongside it. Waiting for window focus prevents the fade from being spent behind the outgoing app. A 320×180 wallpaper preview stays behind the controls while the full image reloads. At RGB565 this is about 113 KiB; a different bitmap format can use more. The preview is discarded under background memory pressure. Full artwork and app-icon references are still released while Home is stopped. The activity window also uses a short enter fade and suppresses the blank starting preview. No full-screen scale or live blur is added.
