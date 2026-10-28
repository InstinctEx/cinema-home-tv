# Network, permissions and storage

Cinema Home has no analytics SDK, account system, ad service or telemetry endpoint. It does make
network requests for the features below. This is an intentional difference from the offline
cleanyourtv reference launcher. Independent player apps and optional receivers have their own policies.

## Network requests

| Destination | Purpose and data sent | When |
|---|---|---|
| `api.open-meteo.com` | Forecast for a location explicitly chosen in the paired editor or configured by the builder; coordinates and timezone request | Cached for an hour; refreshes only while Home is visible. No location is bundled in public source |
| `geocoding-api.open-meteo.com` | City name typed into the phone editor | Only on explicit city search |
| `sylvan.apple.com` | Scene thumbnails and selected aerial video streams; no launcher account | Thumbnails while browsing scenes; streams only when a scene or film preview is selected |
| `www.bing.com` | Daily image metadata and photograph | Only when daily wallpaper is selected |
| User-configured HTTPS release host and its HTTPS redirects | Version manifest and requested APK | Only when checking/downloading an update |
| `images.metahub.space` | Backdrop request containing the selected title's IMDb ID | After a 280 ms pause on a Continue Watching card; no neighbor prefetch |
| Artwork URLs published by supported media apps or Spotify | Fetch the provided artwork URI, which may identify a title or track | When the corresponding artwork is requested |

HTTPS redirects can contact the artwork provider's selected HTTPS destination. Remote artwork
loading rejects plaintext HTTP and bounds download size. Providers receive ordinary connection
metadata, including the requesting public IP. There is no upload of a watch-history database,
Spotify token or app-account database. A title-artwork request can nevertheless reveal which title
is displayed. Turning a feature off does not erase information already received by its provider.

Weather uses an explicitly saved location or private build configuration and a cached hourly request. Normal Home has no discovery or title-details requests, and no saved-title library. Continue Watching can request the selected title's backdrop after a brief focus dwell. The retained legacy ClassicActivity/AppAdapter can
still make catalog and poster requests if explicitly launched; normal Home does not use it.

## Permissions and optional services

| Permission or access | Why it exists |
|---|---|
| Internet | Artwork, optional scenes/weather/updates, and the temporary LAN editor |
| Write settings (optional special access) | Brightness; the user grants it through Android settings |
| Request install packages | Hand an explicitly requested, verified launcher update to Android’s installer |
| Boot completed | Start only when Cinema Home is already the selected Android HOME app |
| Network/Wi-Fi state | Local connection information and system-settings shortcuts |
| Fine location | Retained for reading Wi-Fi SSID on Android versions that require it; weather uses explicitly configured coordinates, not device GPS |
| TV listings / EPG read access | App-published Watch Next and preview entries; depends on vendor grants |
| Bluetooth | Local paired-device information/settings where firmware permits |
| Notification-listener access (optional) | Android media-session access for Spotify. The service does not override notification-content callbacks or store notifications |
| Accessibility service (optional) | Chosen remote shortcuts, paired Home/Back and Android 13+ D-pad actions, Settings/blue-key routing and system standby; `canRetrieveWindowContent=false` |
| Diagnostics receiver | Protected by Android's DUMP permission; emits launcher health information for ADB diagnostics |

The optional accessibility and notification-listener services are declared in the app. Cinema Home
uses Spotify session callbacks and observes TV rows while Home is active. It stops those listeners,
UI ticks and queued artwork work when hidden. An already-blocked network read can take until its
existing timeout to finish. This is not a promise of zero resident process memory or zero background
activity from Android, enabled services or other apps.

## Local data

Preferences store visual settings, app layout/names/folders, boot target and optional weather location.
Appearance exports exclude PINs, pairing tokens, local media paths, weather coordinates and release hosts.
A hidden-app PIN is stored with a random salt using PBKDF2-HMAC-SHA256. It is a local convenience lock.
The paired editor can see installed apps, chosen layout, personal media and explicitly saved weather
location. Treat its QR URL as a temporary private credential. Pairing lasts 15 minutes, can be revoked
in Settings or its foreground notification, and remains available across apps until expiry. HTTP stays on the local network; it is not internet-encrypted.
Photo/video uploads stay in private app storage with a 400 MiB total limit and per-file limits.
The code has no cloud synchronization for these files. Published viewing
progress is held for presentation; private NUVIO/Stremio account databases are not read. User data
from the removed features is not uploaded or synchronized.

The main artwork disk cache is limited to48 MiB; metadata/weather caches live in the app cache directory.
Android may reclaim caches. Low-RAM devices use a4 MiB decoded-artwork LRU and one worker; other devices
use8 MiB and two workers. ImageViews and native/GPU allocations are additional memory, not part of that LRU limit.

Computer-side ADB keys, package/settings dumps and recovery records remain local. Never commit
`backup/`, `tools/adbkey/`, virtual environments, signing keys or exported preferences.

A small video return frame and its playback position are stored in private app files so Home can
restore the same image after its process restarts. The snapshot stays on the TV and is replaced
when a new film renders or Home leaves for another app. Learned remote-button mappings remain local.
