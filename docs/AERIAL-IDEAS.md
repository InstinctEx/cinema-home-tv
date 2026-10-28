# Aerial source review for Cinema Home

This is the comparison made before the 4.0 implementation. See [the current feature and validation guide](RELEASE-4.md) for what is now built.

Reviewed on 27 October 2026 at commit [`83567858`](https://github.com/pengchujin/aerial-tv/tree/83567858cad09a2e3b57d16296c88e249ac9cef3).
This replaces the earlier README-led comparison. I cloned the repository and inspected the
implementation files linked below; I did not build or run Aerial on a TV.

## Keep our design

Keep the quiet photograph, Outfit font, five-app glass dock and Down-to-browse layout.
Our dock explicitly calls `appCard(..., true)`, selecting `BrandMarkDrawable.drawMonochrome()`:
cream `#EAEAE2` silhouettes with real transparent cutouts. The browser preview now follows those
paths. Its first version incorrectly used the colored variants; the Android implementation was
not changed. Other apps retain their native colored artwork, as the Android source does.

## Wallpaper, screensaver and motion inventory

These deserve separate treatment: animated wallpaper is a first-class Aerial feature, not just
another name for its screensaver. All can retain our iconography, type and dock layout.

| Capability | Verified behavior | How it could fit Cinema Home |
|---|---|---|
| Aerial video wallpaper | `LiveWallpaper.kt`: silent footage starts after 10 seconds of inactivity on the top row. A key stops it; the paused frame becomes the wallpaper, including sampled glass. Playback resumes from that point. | Add a motion wallpaper source behind our existing Home. Keep the still coast as default and fallback. |
| Always-moving wallpaper | `AerialSettings.wallpaperAlwaysMoves`: aerial or personal video keeps playing while navigating the top row; lower app grids and panels still pause it. | Offer Still / Move when idle / Always move, without changing the dock. |
| Personal video wallpaper | `VideoWallpaper.kt`: uploaded local videos loop silently, with a still frame when paused and decoder cleanup when Home is covered. | Let the owner choose their own ambient loop; no reliance on a remote footage service. |
| Wallpaper-to-screensaver continuity | `Screensaver.kt` / `LiveFilm`: the same aerial player and moment can continue as Home fades away. It avoids restarting the scene or switching video formats mid-transition. | Fade our dock and utilities out while the background continues. This is a separate feature from merely playing a video. |
| Animated photo screensaver | `PhotoSaver`: Ken Burns zoom/pan, alternating drift direction, 18-second slide intervals and 2-second crossfades. Reduced motion disables the pan/zoom. | Use our existing landscape scenes, then owner-uploaded photos. This can be demonstrated locally with no aerial-video asset. |
| Slow still-photo transition into screensaver | `drawIn`: a 60-second push in and back out, up to 8% zoom with horizontal drift. | A gentle ambient mode that preserves our photographic style. |
| Aerial screensaver catalogue | `Aerials.kt` plus `assets/aerials.json`: 114 films with location names and timed caption changes. Codec/display probing selects HD, 4K SDR or Dolby Vision; failure paths fall back. | Optional scenic screensaver with our clock styling. Native quality support needs testing; do not assume 4K/DV from the panel resolution. |
| Offline video screensaver | `VideoSaver.kt`: personal uploaded videos play silently without a network dependency. | Reuse personal wallpaper clips as screensaver media. |
| Screensaver controls | `ScreensaverSkips` and settings: left/right selects scenes; other keys wake; configurable start after 2/5/10/20 minutes or never; optional Back-to-start; optional time on aerial/photo modes. | Keep these controls in our small Settings panel; preview interaction locally. |
| Moving clock / burn-in mitigation | Clock mode relocates the clock; aerial/photo overlays also shift subtly over time. | Retain our existing drifting clock and extend its presentation to photo/video modes. |
| System screensaver choice | `ScreensaverMode.System`: leave screensaver behavior to firmware and display its timer where available. | Preserve our existing Android DreamService integration rather than forcing an in-Home replacement. |
| Daily/personal-photo wallpaper rotation | `Wallpapers.kt`: Bing daily images with eight retained days, personal photos, daily personal-photo rotation and built-ins. | Our current scenes plus optional local rotation; external daily photos remain optional. |
| App-reactive ambient background | Wallpaper kind `Auto`: background light follows focused app artwork. | Optional soft color response while keeping our custom cream app marks. Different from our existing Continue Watching movie backdrop. |
| Live HDMI wallpaper | `SourceWallpaper.kt`: an actual silent TV-input picture, weak-signal retries and cleanup when covered. | A console or set-top box behind the dock, when the F&U input service permits it. |
| Wallpaper-aware glass | `HomeChrome.kt` / `LiveWallpaper.kt`: blurred wallpaper samples and periodic video-frame samples keep the plate related to the current scene. | Retain our glass design, but update its sampled background for motion sources. |
| Tile motion and feedback | `AppTile.kt`: focus spring zoom/lift, press compression, sheen/parallax, edge nudge, move-mode jiggle and staggered appearance. | Selectively add press feedback/edge nudges. Keep our shared gliding highlight rather than replacing it with their focus treatment. |
| Navigation audio | `Sounds.kt`: TV sounds, Aerial sounds or off; distinct focus/select/back/edge feedback, with no tick for programmatic focus restoration. | Optional subtle sounds with an off switch, retaining our own sound assets. |
| Layout/presentation controls | `WallpaperSheet.kt`: single/double row, second-row peek, glass plate on/off, focused/all app names, clock left/right and small/large, weather visibility, focus contrast and Auto/Full/Lite effects. | Preserve our design as the default; expose only choices the user wants. These are available ideas, not requirements to add every toggle. |

Source references: [live wallpaper](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/LiveWallpaper.kt),
[personal video wallpaper](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/VideoWallpaper.kt),
[screensavers](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/Screensaver.kt),
[personalization panel](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/wallpaper/WallpaperSheet.kt),
[tile motion](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/AppTile.kt).

For an away-from-home browser prototype, photo pan/zoom, crossfades, dock fade-out, local-video
wallpaper selection, clocks and scene controls are all previewable. A browser implementation does
not establish Android decoding performance, HDMI support or firmware screensaver behavior.

## Organization, controls and system features

| Feature verified in source | What Aerial actually implements | Recommendation for our build |
|---|---|---|
| [Phone editor](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/wallpaper/PhoneServer.kt) and `assets/web/index.html` | QR URL with a persisted random token; routes for rearranging, folders, hiding, renaming, wallpaper/photo/video uploads, custom icons, inputs and weather city selection. Edits update live app state. | High value. Put a QR entry inside our Settings and let a phone choose wallpaper, dock order and weather location. Start with those three tasks; validate uploads and bound image/storage sizes for our TV. This is a new Android service, separate from the localhost simulation. |
| [Remote organization](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/Overlays.kt) and [layout persistence](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/apps/LayoutStore.kt) | Hold OK / Menu opens move, folder, hide, app info, boot target and uninstall actions. Layout keys survive temporarily missing apps; folders can be renamed, reordered and ungrouped. Hidden apps can be recovered. | First implementation: contextual Move / Pin / Hide using our existing glass panel. Our current dock checklist can reorder only through deselect/reselect; Other apps has no organization. Add folders only to the lower area when needed. Preserve package keys through app updates. |
| [Named HDMI tiles](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/apps/SourceLooks.kt) | Device choices include Apple TV, PlayStation, Switch, Xbox, computer, set-top box and speaker. Device type and optional custom name are stored by input ID. | First implementation: a direct HDMI dock shortcut using one of our cream glyphs and a short name. Keep our tile design; do not import Aerial's colored device panels. We already enumerate inputs in `showInputs()`. Switching still needs F&U verification. |
| [Start on an app or input](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/MainActivity.kt) | One selected app or input can open on boot/wake. Boot count avoids repeat launches; wake routes are deduplicated for 15 seconds, and late inputs may be awaited for up to 30 seconds. | Useful optional setting: start with Nuvio or a console. Default to Cinema Home. Port the lifecycle behavior carefully so returning from a movie does not launch it again; test both cold boot and standby. |
| [Fuller control center](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/control/ControlCenter.kt) and `system/SystemState.kt` | Inputs, network, volume/mute, brightness, personalization, launcher setup and updates. Brightness has separate vendor paths. | Add network status/settings, Inputs, mute and volume to our current Settings panel. Direct brightness remains conditional on our firmware; do not assume Aerial's Xiaomi/TCL handling applies to F&U. Keep our quiet two-button header. |
| [Automatic Lite effects](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/system/DeviceClass.kt) and `settings/AerialSettings.kt` | Auto classifies low-RAM devices, memory below 1800 MiB, pre-Android-7 systems or at most two cores as low end. Lite uses solid panels and removes expensive embellishments. | Worth adapting selectively. We already shrink artwork caches/workers on Android's low-RAM flag. Add total-memory awareness and an explicit effects fallback while preserving our custom icons and gliding focus. Do not replace our already precomputed glass without measurements. |
| [Performance diagnostics](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/system/FrameStats.kt), `Diagnostics.kt`, `DiagLog.kt` | A bounded frame-duration ring records median/p90/p99/slowest timings after startup; diagnostics include device class. | High value for the 1 GB TV: extend our existing local `HomeDiagnostics` with RAM and frame metrics to compare glass and animation changes. Keep reporting local and free of personal media details. |
| [Wallpaper sources](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/wallpaper/Wallpapers.kt) | Built-ins, uploaded photos, daily Bing images, daily personal-photo rotation, app-following backgrounds, videos and inputs. Shaded/blurred wallpaper results are saved for cold starts. | Next: local photo upload and optional daily rotation using our existing loader. Keep our existing three scene choices and movie backdrops. Bing adds an external dependency; make it opt-in. Baked wallpaper persistence is worth profiling before adding because our image cache already exists. |
| [Hidden-app PIN](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/PinPad.kt) and `settings/AerialSettings.kt` | Numeric or directional PIN; temporary unlock expires after ten minutes or HOME. The hidden-app sheet requires unlock, and the phone page omits hidden apps while locked. | Optional, if hiding apps matters for the household. A directional PIN suits remotes without numbers. This is a launcher convenience lock, not Android-wide app blocking. We currently have neither hidden apps nor a PIN. |
| [Curated icon library and overrides](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/apps/IconLibrary.kt) and `CustomIcons.kt` | Downloaded tile library for installed apps, owner-uploaded overrides, per-app return to original art; custom uploads take precedence. Library checks are throttled. | Borrow the precedence rule, not their art: our bundled cream glyphs remain authoritative. Later allow an explicit per-app override for apps we have not themed. No automatic remote icon replacement is needed. |
| [In-app updates](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/update/Updates.kt) | Periodic version check; user-triggered APK download and Android installer. It checks package/version and compares signer sets when available; unreadable signer data is deferred to Android's installer. | Useful once we have stable signed releases and our own release endpoint. Do not reuse their download URLs. Our current private build/install workflow is enough while design is changing. |

## Interesting, but hardware experiments first

- **Live HDMI behind the dock:** [`SourceWallpaper.kt`](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/ui/home/SourceWallpaper.kt) uses a nonfocusable `TvView`, delayed tuning and weak-signal retries. It is silent, falls back when unavailable and releases the input when Home is covered. Its hardware layer cannot be sampled for glass. This could look excellent behind our themed dock, but the implementation notes cite Xiaomi verification; our F&U support is unknown.
- **Aerial/video screensavers:** [`Aerials.kt`](https://github.com/pengchujin/aerial-tv/blob/83567858cad09a2e3b57d16296c88e249ac9cef3/app/src/main/java/app/orchard/tv/wallpaper/Aerials.kt) probes codecs/display support, chooses HD/4K/Dolby Vision and supports lower-quality fallback. `AerialStream.kt` caches film openings; `VideoWallpaper.kt` pauses, captures a still and releases the decoder. Try a separate 1080p screensaver first, retaining our clock as fallback. Video behind Home adds lifecycle and decoder pressure; Apple footage has separate ownership terms.
- **4K UI / refresh-rate overrides:** their `UhdCanvas.kt` and activity contain capability gates and fallback machinery. Keep our 720p-targeted native UI until hardware measurements justify extra rendering work.
- **Vendor launcher revival:** accessibility/notification/sync recovery exists for specific firmware problems. Our launcher already has its own accessibility setup. Import a workaround only for a reproduced failure on our device.

## Suggested implementation order

1. Prototype motion wallpapers and the seamless screensaver transition in the browser, keeping our custom icons and dock. Start with photo motion and an owner-supplied video loop.
2. Add native wallpaper playback with lifecycle cleanup, still-frame fallback and on-TV validation when home.
3. Remote app organization and named HDMI shortcuts, followed by network/volume/input controls.
4. A paired phone editor for wallpaper, dock and weather.
5. Optional live HDMI, folders, hidden-app PIN, boot targets and signed-release updates.

The original review did not implement features. The subsequent 4.0 implementation and its validation
limits are documented in [RELEASE-4.md](RELEASE-4.md). The localhost page remains a simulation of Android. Its SVGs reproduce our monochrome path geometry, but browser rendering still differs
from Android Canvas and cannot establish TV performance or input compatibility.
