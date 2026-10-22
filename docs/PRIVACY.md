# Network, permissions and storage

Cinema Home has no analytics SDK, account system, ad service or telemetry endpoint. It does make
network requests for the features below. This is an intentional difference from the offline
cleanyourtv reference launcher. Independent player apps and optional receivers have their own policies.

## Network requests

| Destination | Purpose and data sent | When |
|---|---|---|
| `api.open-meteo.com` | Forecast for a location explicitly configured by the builder; coordinates and timezone request | Cached for an hour; refreshes only while Home is visible. No location is bundled in public source |
| `images.metahub.space` | Backdrop request containing the selected title's IMDb ID | After a 280 ms pause on a Continue Watching card; no neighbor prefetch |
| Artwork URLs published by supported media apps or Spotify | Fetch the provided artwork URI, which may identify a title or track | When the corresponding artwork is requested |

HTTPS redirects can contact the artwork provider's selected HTTPS destination. Remote artwork
loading rejects plaintext HTTP and bounds download size. Providers receive ordinary connection
metadata, including the requesting public IP. There is no upload of a watch-history database,
Spotify token or app-account database. A title-artwork request can nevertheless reveal which title
is displayed. Turning a feature off does not erase information already received by its provider.

Weather uses the private build configuration and a cached hourly request. Normal Home has no discovery or title-details requests, and no saved-title library. Continue Watching can request the selected title's backdrop after a brief focus dwell. The retained legacy ClassicActivity/AppAdapter can
still make catalog and poster requests if explicitly launched; normal Home does not use it.

## Permissions and optional services

| Permission or access | Why it exists |
|---|---|
| Internet | Published artwork and optional weather requests above |
| Network/Wi-Fi state | Local connection information and system-settings shortcuts |
| Fine location | Retained for reading Wi-Fi SSID on Android versions that require it; weather uses explicitly configured coordinates, not device GPS |
| TV listings / EPG read access | App-published Watch Next and preview entries; depends on vendor grants |
| Bluetooth | Local paired-device information/settings where firmware permits |
| Notification-listener access (optional) | Android media-session access for Spotify. The service does not override notification-content callbacks or store notifications |
| Accessibility service (optional) | Chosen remote shortcuts, Settings/blue-key routing and the system standby action; `canRetrieveWindowContent=false` |
| Diagnostics receiver | Protected by Android's DUMP permission; emits launcher health information for ADB diagnostics |

The optional accessibility and notification-listener services are declared in the app. Cinema Home
uses Spotify session callbacks and observes TV rows while Home is active. It stops those listeners,
UI ticks and queued artwork work when hidden. An already-blocked network read can take until its
existing timeout to finish. This is not a promise of zero resident process memory or zero background
activity from Android, enabled services or other apps.

## Local data

Preferences store wallpaper and dock choices. Existing legacy settings files can remain locally,
but Home no longer exposes settings exports, save-for-later or hidden-title lists. Published viewing
progress is held for presentation; private NUVIO/Stremio account databases are not read. User data
from the removed features is not uploaded or synchronized.

The main artwork disk cache is limited to48 MiB; metadata/weather caches live in the app cache directory.
Android may reclaim caches. Low-RAM devices use a4 MiB decoded-artwork LRU and one worker; other devices
use8 MiB and two workers. ImageViews and native/GPU allocations are additional memory, not part of that LRU limit.

Computer-side ADB keys, package/settings dumps and recovery records remain local. Never commit
`backup/`, `tools/adbkey/`, virtual environments, signing keys or exported preferences.
