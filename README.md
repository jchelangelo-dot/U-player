# U-player

Android local music player focused on high-quality listening, parametric EQ, and Japanese/anime-song lyrics.

## V1 roadmap
1. Core local playback + MediaSession
2. Library / Player UI
3. 8-band PEQ, preamp, headroom and limiter
4. Lyrics: Japanese original + Korean pronunciation + Korean translation (in progress)
5. Presets and local cache

## Current milestone
Milestone 4 foundation: MediaStore library scan, search and sorting, persistent custom groups and favorites, Media3 background playback, embedded album artwork with an ultramarine fallback, full Player controls, an 8-band parametric EQ, and a full-screen local lyrics editor/cache. Album art opens lyrics with Japanese original, Korean pronunciation, Korean translation display modes, and per-line manual timing correction. Provider interfaces keep future lyrics search, pronunciation, and translation services replaceable.

Open the repository root in Android Studio, sync Gradle, grant audio permission, and run on an Android device.
