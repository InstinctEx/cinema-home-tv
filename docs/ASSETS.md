# Credits and asset origins

The code carries the original MIT license in [LICENSE](../LICENSE), including Gathin's copyright.
This fork includes work from [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)
and [benfirad/tv-vibehack](https://github.com/benfirad/tv-vibehack).
The personalized launcher started from tv-vibehack revision
`7e73b01d68e2c84574e4aee88a3a92f722e3e15d`; imported cleanup tools and scenes came from
cleanyourtv revision `cd40dc0b86bb99c95623d447215f661cc2e46eb5`.

## Bundled assets

- **Outfit:** the upstream typeface, under SIL Open Font License1.1. The full notice is retained in
  [launcher/FONT-LICENSE-OFL.txt](../launcher/FONT-LICENSE-OFL.txt).
- **Day/night images, original launcher icon/banner and legacy illustrations:** retained from the
  benfirad launcher tree; its upstream attribution and license remain applicable.
- **Six Cinema Home scenes:** copied from cleanyourtv's `themes/aurora/wallpaper.jpg` and
  `themes/scenes/wallpapers/{01-dusk-peaks,02-golden-coast,03-moon-lake,05-dunes,06-above-the-clouds}.jpg`
  at the revision above. They are not claimed as original photography by this fork.
- **Navigation tones:** synthesized50 ms mono PCM tones for this project,620 Hz for movement and840 Hz
  for selection, with a sine/decay envelope. They contain no sampled music. The reproducible source is
  [tools/generate_sounds.py](../tools/generate_sounds.py).
- **Brand marks:** simple vector identification marks in `BrandMarkDrawable`; names and marks belong
  to their respective owners and do not imply endorsement.

## Online artwork and screenshots

Movie/series posters and backgrounds are requested at runtime from the providers documented in
[PRIVACY.md](PRIVACY.md). Spotify art is supplied by its media session or published artwork URI.
This repository does not grant a license to redistribute those providers' catalogs or media.


## Quiet Home update

The shared dock, spare header and photographic composition take visual inspiration from
[CleanFrame](https://github.com/nthnclrk/CleanFrame) at revision
`fa537d1892b71d2de2bf94c24cbff3812e9f70d5`. Its Tizen runtime is not included.

`launcher/app/src/main/assets/wallpapers/misty.jpg` is the bundled Misty coast photograph
from that revision, credited to **Ganapathy Kumar** ([original photo](https://unsplash.com/photos/n4VMQZIkgDg)),
under the [Unsplash License](https://unsplash.com/license). CleanFrame's source and credit register
is [here](https://github.com/nthnclrk/CleanFrame/blob/fa537d1892b71d2de2bf94c24cbff3812e9f70d5/docs/asset-sources.md).
The photo is packaged locally and displayed through the existing bounded artwork decoder.

## Version 4

The aerial URL/caption catalogue comes from [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv)
at `83567858cad09a2e3b57d16296c88e249ac9cef3`, derived there from Apple's public aerial catalogue.
The upstream MIT notice is retained in `launcher/app/src/main/assets/AERIAL-LICENSE.txt`.
No Apple video files are bundled; footage remains Apple's property and streams from its server.
Bing images remain subject to their source rights. Personal uploads are supplied by the owner.
QR encoding uses ZXing core (Apache 2.0); the full notice is bundled as
`launcher/app/src/main/assets/ZXING-LICENSE.txt`.
Device glyphs are drawn locally in our cream style rather than importing Aerial's colored tiles.

Scene thumbnail URLs are Apple's `previewImage` entries from the tvOS 26 `resources-atv-23J-2` catalogue, matched by ID to all 114 bundled scene records. Images are fetched on demand; they are not bundled. Browser cards show a connection fallback if the host cannot be reached.

`res/raw/apple_root_ca.pem` is the public Apple Inc. Root certificate downloaded from https://www.apple.com/appleca/AppleIncRootCertificate.cer (listed at https://www.apple.com/certificateauthority/). SHA-256: `b0b1730ecbc7ff4505142c49f1295e6eda6bcaed7e2c68c5be91b5a11001f024`. The network policy adds it only for the exact host `sylvan.apple.com`; system trust and hostname verification remain active, and that host requires HTTPS.
