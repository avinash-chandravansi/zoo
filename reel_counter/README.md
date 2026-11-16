# Reel Counter

A tiny Android app that counts how many Reels / Shorts you scroll each day and shows a
floating 🧠 counter bubble while you watch, inspired by the BrainPal reel.

- Counts in Instagram Reels, YouTube Shorts, Facebook, Snapchat, Moj, Josh and TikTok
- Floating bubble (drag to move) that bumps on every new reel and turns red at your daily limit
- Daily limit with a vibration + message when you reach it
- Today's count per app and a 7-day chart
- No internet permission: it only counts swipes, nothing you watch is read or stored

## Install

1. Download `ReelCounter.apk` from the **Reel Counter (latest)** release on GitHub
   (or from the build's artifacts under the *Actions* tab).
2. Open it on your Android phone (Android 8 or newer) and allow *Install unknown apps* when asked.
3. Open **Reel Counter** → **Turn on counting** → *Installed apps* (or *Downloaded apps*) →
   **Reel Counter** → On.
4. On Android 13+, if that switch is greyed out: in Reel Counter tap **Open App info** →
   ⋮ (top right) → **Allow restricted settings**, then repeat step 3.
5. Open Instagram Reels or YouTube Shorts and start swiping; the bubble appears top-right.

## How counting works

Reel players are full-screen vertical pagers. When a swipe settles, the app's pager sends an
accessibility "view scrolled" event with exactly one item visible; a change in that item's
index is counted as one reel. Normal feeds show several posts at once, so they are not
counted. For apps that don't report indexes, scrolling about one screen height counts as a
reel. Apps change their layouts, so counts can occasionally be off by one or two.

## Build

```
cd reel_counter
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
```

The checked-in `app/debug.keystore` is a plain debug key (not a secret). It is fixed so that
every CI build can be installed over the previous one.
