# Cinema Home 4.0

The approved 3.11-simple design (`CinemaHomeTV` commit `1efe4c0`) remains the default: Outfit, custom cream vector icons, a shared glass
dock and a photographic backdrop. The additions live behind Settings and long-press actions. The original `GlideFocus` source and
prepared-frame arrival methods are preserved: 220 ms shared focus motion, a 350 ms settle delay,
and an 800 ms control/wallpaper fade. Full effects and the existing system screensaver remain
the defaults, so low-RAM classification does not silently replace the approved glass appearance.
The preview includes a **Replay entrance** control for checking the animation.

## Included

- Wallpaper sources: bundled scenes, time of day, timed rotation, personal photos, daily personal
  photo, daily Bing photograph, focused-app colors, aerial films and local video loops.
- Motion: off, after 10 seconds idle, or always on the top row. A single native player yields the
  decoder when an app covers Home. Input returns to a frozen frame; wallpaper-to-screensaver playback
  can continue on the same player.
- Screensavers: clock, animated photos, aerial films, offline videos or the system screensaver;
  2/5/10/20-minute timer or off, optional clock overlay, left/right scene selection and Back-to-start.
- Aerial catalogue: 114 entries, timed English location captions, 1080p/4K/Dolby Vision selection
  gated by decoder/display support, with video failure falling back to photographs.
- QR phone editor: temporary 192-bit pairing URL, 15-minute expiry, same-origin write checks, no
  account. A visible foreground notification keeps it available across apps until expiry or Disconnect. Reopen Settings → Connections → Connect your phone for a new code.
- Phone edits: dock reorder, app names, folders, visibility, uploaded icon overrides, wallpaper and
  video uploads, weather city search/coordinates, appearance export/import and boot target.
- Remote organization: hold OK or press Menu for move, dock pinning, rename, folder, hide, custom
  icon reset, boot target and app information/uninstall through Android.
- Named direct HDMI dock tiles, monochrome device glyph choices, live silent HDMI background.
- Personalization: two rows/second-row peek, clock position/size, app names, glass plate, focus
  contrast, reduced motion, navigation sounds and full/automatic/lite effects (Full by default).
- Settings: network, Inputs, native volume/mute, brightness gated by Android's explicit write-settings
  access, picture settings, Spotify access, PIN, restore point, diagnostics and signed updates.
- Hidden-app lock: numeric or directional PIN, random salt and PBKDF2-HMAC-SHA256, 10-minute unlock,
  five-attempt throttling, lock on leaving Home. It is a launcher convenience lock, not an OS sandbox.
- Update mechanism: configure your own HTTPS manifest from the paired editor. The TV asks before
  download, checks package/version/current signing certificate, then hands off to Android's installer.
  No hosted release endpoint or production signing key is bundled.

## Use away from the TV

```sh
python3 preview/server.py
```

Open http://localhost:8765 and http://localhost:8765/phone/ side by side. The browser phone editor
and launcher share persisted demo state. The TV preview polls for changes while visible.
Right-click or hold Enter on an app for actions. Settings → Connect your phone shows a real QR
encoding the local editor address. That localhost QR is for this computer, not remote phone access.
Native pairing instead encodes the TV's LAN address and an expiring secret.

Aerial playback uses the host browser's codec support; local-video and photo upload work through the
editor. Android settings, input video, PIN protection, boot targets and APK installation are native
features and are not simulated as hardware capabilities in the browser. Preview data stays in
`preview/.local-data/` (ignored by Git). Removing that directory resets the demo.

## Build and verification

Local verification on 27 October 2026: 53 Android tests (including thirteen Robolectric integration
tests), 45 existing helper tests, 14 preview/API tests plus scene-queue JavaScript assertions, Android lint and debug APK build.
Native integration checks cover startup, persisted folders/order, PIN derivation, backup privacy,
missing-token/foreign-origin rejection, paired main-thread edits and the bundled font response.
Browser checks cover the editor, live scene changes, QR rendering, photo motion, glide and arrival.


Use a JDK 17/21 and the existing Android SDK:

```sh
./launcher/gradlew -p launcher testDebugUnitTest lintDebug assembleDebug --console=plain
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s preview -v
node --check preview/app.js
node --check preview/features.js
node --check launcher/app/src/main/assets/phone/phone.js
```

The debug APK is `launcher/app/build/outputs/apk/debug/app-debug.apk`. Building does not install it.
Robolectric runtime tests download Android test-runtime dependencies on their first run.

## Media and release format

Photo uploads: JPEG/PNG/WebP up to 12 MiB, decoded with sampling and stored as private JPEGs on TV.
Video uploads: up to 150 MiB, validated with the device's media retriever. Total private library:
400 MiB. The browser demo validates signatures; the TV additionally verifies decoding metadata.
Uploads are streamed, and completed files appear atomically. Keep your originals: these are TV-sized
copies, and deleting an upload removes it from Cinema Home.

Example manifest served by a release host you control:

```json
{"versionCode": 16, "apk": "https://your-release-host/cinema-home.apk"}
```

A release must be signed with the key already installed on the TV. Do not distribute a fresh debug-key
build as an in-place update for a differently signed installation. Use the existing setup/recovery
workflow when installing; this work has not changed a TV or disabled its stock launcher.

## Device acceptance still required

A browser and JVM tests cannot establish F&U decoder concurrency, HDMI passthrough support,
physical remote feel, brightness/panel behavior, standby wake or real firmware screensaver timing.
Verify those on the TV before calling the release hardware-validated. Full 4K UI rendering and
vendor-specific revival hacks are intentionally not enabled without evidence that this TV needs them.

## Scene library, remote and Settings update

- Browse 114 films with genuine Apple preview images, categories and favorites on TV and phone.
- Shuffle uses a bag with no repeats per cycle; Back follows playback history. Exclude individual films or whole categories, or play favorites only. Excluding everything falls back to photographs.
- Phone remote: launcher D-pad, OK, Back, Home, Menu and Settings; global volume/mute; authorized media-session play/pause and previous/next. Outside Home, D-pad/OK use the enabled accessibility service on Android 13+; Home/Back also work on older Android.
- Six Settings cards: Appearance, Scenes, Apps & Inputs, Connections, Sound & Picture, System. Short subpages replace the long action list; original dock icons, glide and entrance remain.
- Scene thumbnails are hosted by Apple and require internet access. The browser remote controls only the local demo; Android controls use public APIs; availability depends on Android and the enabled accessibility service.

Weather correction: the browser preview fetches current Open-Meteo temperature/conditions for the explicitly selected city with an hourly cache. Both clock sizes keep the city visible during loading and outages. Android's large-clock header and music spacing now accommodate the weather line; widget toggles apply immediately.

Settings polish: native and browser boolean rows now use sliding switches with a green enabled track and white thumb, checked-state accessibility and reduced-motion support. Changes apply in place and retain focus. Phone settings use the same switch styling. Appearance exposes Layout, Clock & widgets, and Motion & focus directly, and Back returns to the parent page. TV switches accept OK, Left (off) and Right (on); paired dialog navigation uses the focused native control.

## 4.0.1 · Aerial playback correction (28 October 2026)

The TV rejected Apple's scene host with `SSLHandshakeException: Trust anchor for certification path not found`. Apple's public Root CA is now bundled and trusted only for `sylvan.apple.com`, which still requires HTTPS and hostname verification. Other hosts retain system trust. This fixes both scene streaming and thumbnails on affected TV trust stores. Source and fingerprint are recorded in ASSETS.md. Explicit wallpaper selections start loading immediately instead of waiting ten seconds for idle motion.

Validation: 53 Android tests, including certificate identity/scope and immediate-selection regression checks; Android lint and debug build.

Physical-TV check: version 4.0.1 installed successfully. Native media diagnostics confirmed `OMX.MS.AVC.Decoder`, 1920×1088 coded video (1080p display), 549 rendered frames and zero dropped frames after the TLS fix. No broader testing was performed.

## 4.0.2 · Cross-app remote and continuous aerials (28 October 2026)

Paired D-pad and OK now use Android 13+ accessibility global actions outside Cinema Home. The service still cannot retrieve window contents. Home/Back retain their older-Android support; Menu and launcher Settings remain Home-only. Media and volume retain their existing APIs. The phone enables controls based on the TV's capabilities. Aerial/personal-video wallpapers continue through dock navigation and resume promptly on Home; opening Settings/browsing content or leaving Home still pauses them. Still mode and reduced motion remain respected.

4.0.2 validation: 56 Android tests and 14 preview/API tests pass; lint and debug build pass. On-TV dock navigation retained native player connection 17 while frames advanced from 561 to 570, with renderer paused=0 throughout. Accessibility service was already enabled and bound; cross-app global action routing is covered on Android API 33 in Robolectric. Live cross-app phone verification was left to the user once another app was playing. Re-pair after updating because temporary pairing tokens do not survive process replacement.

## 4.0.3 · Smooth video dock glass (28 October 2026)

Video dock glass now samples once per second and crossfades for one second between blurred textures, instead of replacing the background at irregular multi-second intervals. Transitions retain only two texture layers; the rim and sheen follow opacity so they do not flash during the blend. Hidden screensavers skip dock sampling, and reduced motion disables the crossfade. No second video decoder is used.

Validation: 58 Android tests pass, including bounded transition layers and native canvas opacity checks; lint and APK build pass. Installed with `adb install -r` while leaving the foreground app untouched. On-device visual smoothness is left for the user to assess.

## 4.0.4 · Correct the dock crossfade regression (28 October 2026)

4.0.3 was rolled back after visible blinking was reported. Fading both drawable layers exposed the video during the blend and replacing the View background amplified the effect. The dock now retains one FrostedPanelDrawable, draws the previous opaque texture beneath the incoming texture, and draws its sheen/rim exactly once. Frame updates mutate the existing drawable instead of replacing the background. Transparent source captures also have an opaque backing. Reduced motion swaps the texture directly.

Validation: 59 Android tests, lint and APK build pass. Native rendering regression checks cover constant alpha at 0/25/50/75/100 percent of a blend and midpoint color interpolation; a separate test verifies frame updates preserve the background object. Installed in place, leaving Nuvio playback untouched. The updated Home was not opened for visual inspection.

## 4.0.5 · Seamless video lifecycle and app handoff (28 October 2026)

Returning from an aerial screensaver to the same aerial wallpaper keeps the existing renderer. Continuous video also stays active through Continue Watching navigation, where poster backdrops are suppressed. Source changes and app handoff retain the latest video frame rather than exposing the static fallback. Frame sampling occurs during playback, not during the launch gesture. App handoff detaches decoder callbacks and releases the player/surface on the existing worker so native teardown does not block the UI. Same-source preparation is guarded against duplicate player creation. The worker drains queued resource cleanup when the scene closes.

App and Continue Watching launches use Android's native scale-up window transition from the selected tile, without an added delay; reduced motion uses the standard unanimated handoff. Boot, accessibility shortcuts and the installer now launch MAIN/HOME consistently. Updating an already-selected Home no longer reassigns its role, avoiding a redundant Home/task transition.

Validation: the three lifecycle regressions failed before the fix and passed afterward. 65 Android tests, 47 helper/installer tests, 14 preview/API tests, Android lint and the debug build pass. On the TV, player connection 66 stayed unchanged through Home (286 frames), saver (323), Home again (346) and Continue Watching navigation (399), all unpaused. The launcher pause recorded during a Nuvio launch was 61 ms; Nuvio's cold first window was 3544 ms. Native app startup remains distinct from launcher handoff. An initial installer role-query error was subsequently checked on the device: boot completed, version 4.0.5 installed and Cinema Home remained default. The final installer run used the corrected Home intent and completed successfully.

## 4.0.6 · Settings resume, movie backdrops and focus polish (28 October 2026)

Settings now holds the aerial decoder and texture in place, including while changing widgets. Closing the Settings hub resumes the retained player immediately. Preparation and seek callbacks respect a held scene. Movie backdrops are restored above the aerial layer: the current cover stays visible during the next request, then the incoming cover fades over an opaque previous cover. Stale requests are ignored and transitions retain at most two image layers. Once covered, the aerial pauses in place; returning to the dock resumes it before fading the cover away. App handoff keeps the visible movie cover until Home stops.

Continue Watching uses larger cards, tighter artwork corners, an inset progress line, a subtle focus scale and a shared white outline around the artwork. The existing dock glide remains unchanged. Spotify transport controls use centered vector symbols in fixed image buttons, removing font-baseline drift. Reduced motion and high-contrast focus remain supported. The localhost preview mirrors the focus and arrow changes and resumes video directly when Settings closes.

Validation: 69 Android tests, 14 preview/API tests, scene-policy checks, lint and debug build pass. Installed as version 4.0.6-cinema with the previous APK and preferences backed up. TV player connection 83 stayed intact through Home → Settings (paused=1) → Back (paused=0). Continue Watching held that same player at frame 2930 while VisionQuest changed to The Boys; returning to the dock resumed it at frame 2933. The new artwork outline and centered Spotify previous control were checked in device screenshots. Further low-memory optimizations are suggestions only; no preference reset or unrelated feature changes were applied.

## 4.0.7 · Quiet refreshes and retained aerial frames (28 October 2026)

Weather retains its forecast during refreshes and only replaces changed text. City/temperature use slightly larger medium-weight warm-white text with a soft letter shadow; conditions are quieter, and there is no background strip. Normal and big-clock layouts retain enough room for the forecast. Spotify updates changed metadata, artwork and play/pause state without rebuilding identical presentation. Identical embedded artwork received in a new metadata parcel is reused. Progress retains its lightweight timer and avoids setting an unchanged value.

A successful app launch suspends Home work immediately, including frame sampling, scheduled refreshes and artwork requests. Decoder disposal remains asynchronous, and wallpaper return previews are prepared when the main queue is idle instead of during onStop. Standard Android task launch behavior and direct Continue Watching intents are preserved.

Home's image cleanup now retains its tiny aerial frame and static Spotify symbols. Home arrival resumes eligible aerial playback immediately without waiting for input. A prepared incoming decoder cannot replace the retained frame until rendering starts. Video completion captures the outgoing frame before advancing; it stays visible while the next video prepares, then blends into rendered video. Settings and fully covering movie artwork still hold the existing decoder in place. Motion-off and reduced-motion settings remain respected.

Validation: 73 Android tests, 14 preview/API tests, scene-policy checks, lint and debug APK build pass. Regression checks failed before fixes for cleared return frames, duplicate metadata writes, forecast refresh flicker and premature empty-texture capture. Installed version 4.0.7-cinema after backing up 4.0.6 and preferences. On the TV, Nuvio released the aerial player while foreground; Home retained its process and showed the aerial frame while the replacement decoder prepared. Playback subsequently advanced without input (connection 101, 939 frames, paused=0). A scene change advanced connection 100 from 43 to 224 frames and returned from the saver on that same player at 239 frames. Screenshots confirm readable weather without a strip and Spotify arrows/brand mark surviving return. Observed Nuvio launches were cold (2788 ms and 2483 ms); these are observations, not a controlled before/after speed comparison. Network buffering may hold the outgoing frame briefly. Extended playback/low-memory soak testing remains with the user.

## 4.0.8 · Persistent aerial return image and Prime shortcut (28 October 2026)

Aerial/video Home now uses the last captured video frame as its actual base wallpaper while the decoder prepares. This replaces the previous path that still loaded the default misty wallpaper underneath the transient scene surface. The scene keeps that frame until rendered video blends over it; older wallpaper callbacks cannot overwrite the return image.

One small JPEG and its matching source, URI, index and position are stored atomically in private app storage. Checkpoint writes run on the existing scene worker when a film first renders and when leaving Home, avoiding repeated disk writes during playback. Fresh Home instances restore the bounded image before their first draw and select the corresponding eligible aerial when its catalogue loads. Corrupt/oversized files are ignored safely, and static wallpaper selections are unaffected.

The user's captured Prime Video button (Linux/Android scan code 504) is saved in private preferences as the Nuvio shortcut. The existing accessibility key filter consumes its down/repeat/up events and launches Nuvio once; unrelated remote keys retain their behavior. The mapping remains device-specific and is disabled by default on installations without a captured scan. Previous APK and preferences are backed up under backup/persisted-frame-prime-2026-10-28/.

Validation: the new fresh-Home regression failed before the change and passes afterward. All 76 Android tests, lint and APK build pass, including persisted frame/position round-trip, damaged-file handling, root wallpaper restoration, same-film resumption and shortcut repeat handling. On the TV, a roughly 11 KB saved file restored the aerial image after the launcher process was stopped; a screenshot taken before decoder frame output showed that saved image instead of the unrelated wallpaper. The replacement player then advanced to 1833 frames with paused=0. The user verified the physical Prime button opens Nuvio. The optional key-filter service is enabled and no window-content retrieval capability is requested. The saved still can appear softer while video buffers; extended playback soak testing was not repeated.
