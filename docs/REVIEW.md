# Resource review

The 3.8-simple review preserves the existing interface and focuses on unnecessary retained memory and stale work.

- Poster callbacks use weak targets and a row generation check, so a slow image request does not keep detached cards alive.
- Leaving or changing a Continue Watching backdrop cancels queued requests and prevents stale running results from being decoded or delivered after cancellation is observed. A blocked read can still last until its timeout.
- App click handlers retain the component name instead of the full app record and drawable. The app discovery map is cleared when Home stops.
- The content worker’s idle thread may time out after 20 seconds.
- Pause immediately invalidates pending image/content delivery. Resume reloads interrupted wallpaper requests, including pause/resume transitions that do not reach stop.
- Row refreshes preserve focus on attached header controls, and content signatures avoid a duplicate allocation.

The Windows installation wrapper now uses the same verified installer and private recovery record as the Unix wrapper, instead of unconditionally disabling a guessed stock launcher. The Windows wrapper itself was reviewed on macOS, not executed on Windows.

Computer validation: 30 Android JVM tests, Android lint, debug APK assembly and 45 Python helper tests. No separate test application is required. These checks do not measure live total memory or establish a percentage reduction. Runtime verification is kept separate from repository contents and contains no household data here.

Low-RAM artwork policy remains one worker and a 4 MiB decoded-image cache; the 48 MiB disk cache is storage rather than RAM. ImageViews, native graphics and enabled Android services have additional allocations.
