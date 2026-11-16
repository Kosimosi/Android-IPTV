# RETROTV live content feeds

RETROTV discovers headlines and live sports without a paid API.

## Automatic news ticker

The app reads RSS item titles from these sources:

- Google News Bangladesh (Bangla): `https://news.google.com/rss?hl=bn&gl=BD&ceid=BD:bn`
- Google News Bangladesh (English): `https://news.google.com/rss?hl=en-BD&gl=BD&ceid=BD:en`
- JagoNews24: `https://www.jagonews24.com/rss/rss.xml`
- Bangla Tribune: `https://www.banglatribune.com/feed`
- RisingBD: `https://www.risingbd.com/rss/rss.xml`

Headlines are deduplicated, limited, and cached locally. If a feed fails, the app keeps the last successful ticker text. RSS failures never affect playback.

Tapping the ticker tries to open a matching Bangladeshi news channel from the active playlist; if the publisher cannot be matched, it opens the first non-offline channel in the News group.

## Automatic cricket detection

The app reads ESPNcricinfo's live-score RSS at `https://www.espncricinfo.com/rss/livescores.xml`. Bangladesh, BPL, ICC, Asia Cup, and IPL headlines are promoted only when a matching channel is marked ONLINE by RETROTV's health check.

The app refreshes while foregrounded, approximately every 150 seconds. It stops polling when the app is backgrounded. ESPN's feed is used for cricket; it is not a reliable source for live football fixtures or TV broadcast rights.

## Manual event overrides

Edit the root-level `live_events.json` file in GitHub to add major matches, football, national broadcasts, or special programmes. Changes are picked up remotely; changing this JSON does not require a new APK.

Example event:

```json
[
  {
    "id": "ucl-final-2027",
    "badge": "LIVE FOOTBALL",
    "title": "UEFA Champions League Final",
    "channelKeywords": ["Sony Sports Ten 2", "Sony Sports Ten 1"],
    "startUtc": "2027-06-05T19:00:00Z",
    "endUtc": "2027-06-05T22:30:00Z"
  }
]
```

Rules:

- Use UTC timestamps in ISO-8601 format ending in `Z`.
- `channelKeywords` are matched against channel names in the active M3U playlist, in the order provided.
- A card appears only between `startUtc` and `endUtc`.
- A channel must be marked ONLINE by the existing playlist health check. If no keyword matches an online channel, the event is hidden.
- Keep event IDs unique. Remove old events after they expire.
- Do not assume channel rights from the event title; verify the broadcaster before publishing an override.

## Existing highlights file

The app continues to read manual ticker headlines from `headlines` in `highlights.json`. The live event list is maintained separately in `live_events.json`.
