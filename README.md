# Lume TV for Android TV / Fire Stick

An unofficial Android TV and Amazon Fire TV IPTV player, inspired by
[Lume](https://github.com/bilipp/Lume) for Apple devices. Written from scratch in Kotlin.

Lume TV is a player only. It contains no channels or content. Use it with a provider you are
entitled to watch.

## What it does (version 0.1)

- Sign in with **Xtream Codes** (server, username, password) or an **M3U / M3U8 link**
- **Live TV** by category, with channel logos and now & next guide (Xtream)
- **Movies** and **Series** with posters, plot, cast and ratings, plus search within a category
- Season and episode lists, with **next episode plays automatically**
- **Resume where you left off**, Continue Watching, and progress bars
- **My List**: hold OK on any channel, film or series to add it
- Player: channel up/down zapping, left/right to skip, subtitles and audio track choice

## Remote control

| Button | Live TV | Films and series |
| --- | --- | --- |
| OK | Show controls | Show controls |
| Up / Down | Change channel | - |
| Left / Right | - | Back 10s / forward 30s |
| Hold OK (in lists) | Add to My List | Add to My List |
| Back | Close player | Close player |

## Getting the app onto a Fire Stick

### Part 1: build the APK (once, about 10 minutes)

1. Create a free account at github.com if you don't have one.
2. Create a new repository called `LumeTV` (Public or Private both work).
3. Upload every file and folder from this project into it ("Add file" > "Upload files",
   drag the whole folder in). Make sure the hidden `.github` folder is included.
4. Open the **Actions** tab. A "Build APK" job starts by itself. Wait for the green tick
   (about 5 to 8 minutes).
5. Open **Releases** on the right of the repo page. "Latest build" has `LumeTV.apk`.
   Copy that download link.

### Part 2: install on the Fire Stick

1. On the Fire Stick: **Settings > My Fire TV > Developer options**. Turn on
   **Install unknown apps** (or **Apps from Unknown Sources**).
   If you can't see Developer options: Settings > My Fire TV > About, then press OK on the
   device name 7 times.
2. Install the free **Downloader** app from the Amazon Appstore.
3. Open Downloader, enter the APK link from Part 1, and press Go. Choose **Install**.
4. Open Lume TV from your apps and enter your provider details.

If the repository is private, the Releases link needs a login. A public repo is simplest, or
shorten the link with a URL shortener before typing it into Downloader.

### Updating

Upload changed files to the repo. The Actions job builds a new APK and replaces
"Latest build". Repeat Part 2, step 3, and your favourites and resume points are kept.

## Building on a computer instead

Open the folder in Android Studio (Koala or newer) and press Run, or from a terminal with
Gradle 8.9 and the Android SDK installed:

```
gradle assembleRelease
```

## Licence

Released under the GNU AGPL-3.0, the same licence as the original Lume project.
