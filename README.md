# Fenpo Cast

Fenpo Cast is an Android TV app that receives an iPhone's screen, music and video. Nothing gets installed on the iPhone. You pick the TV from Control Center > Screen Mirroring, or from the audio output button of whatever is playing.

It comes from the same people as [Fenpo](https://github.com/Milvasoft/fenpo), which links a phone to a Windows PC, but it is a separate app and doesn't need Fenpo. There is no server. Everything stays on the local network.

## What it does

| | |
|---|---|
| Screen mirroring | The iPhone sends H.264, or H.265 on a 4K TV. A hardware decoder shows it with low latency. Sound is AAC-ELD |
| Music | Audio from music and podcast apps (ALAC, AAC), with cover art, track, artist and progress |
| Video | Apps such as YouTube hand the video stream over to the TV. The TV plays it on its own and the iPhone can be locked |
| Connection code | Optional. Every connection shows a 4-digit code on the TV |
| Ready while open | The TV shows up in the iPhone's list while Fenpo Cast is open. When you leave the app, the receiver stops at once if nothing is playing. If music is playing, it keeps going until the iPhone ends the session |

Settings cover the TV's name, picture quality (automatic, 1080p, 720p), whether videos play on the TV by themselves, theme and language (English or Turkish).

DRM-protected content (Netflix, Disney+, Apple TV+) cannot be sent. That is a FairPlay Streaming restriction, and no third-party receiver gets around it.

The iPhone and the TV need to be on the same network. The app needs Android 9 or later and runs on Android TV and Google TV.

## Building

You need JDK 21, the Android SDK (platform 37), NDK `30.0.16248370` and CMake `4.1.2`.

BoringSSL is a git submodule, so clone with:

```bash
git clone --recurse-submodules https://github.com/Milvasoft/fenpocast.git
```

```bash
./gradlew :app:assembleRelease
```

The output is `app/build/outputs/apk/release/app-release.apk`. Without an upload key it is signed with the debug key, which is fine for sideloading. Installing on a TV and what to try there: [docs/TESTING.md](docs/TESTING.md) (Turkish).

## Layout

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt     BoringSSL + libplist + ALAC + UxPlay core → libfenpocast.so
│   ├── receiver/          JNI bridge, Android mDNS backend, ALAC wrapper
│   └── third_party/       UxPlay lib/, libplist, ALAC, BoringSSL (submodule)
├── kotlin/com/fenpo/cast/
│   ├── receiver/          NativeReceiver (JNI), ReceiverController, NsdManager advertising,
│   │                      display profile, low-latency Wi-Fi lock
│   ├── media/             MediaCodec video, AudioTrack audio, Media3 stream player, DMAP
│   ├── settings/          DataStore settings, device identity
│   └── ui/                Compose for TV screens (home, session, settings), theme
└── res/                   English and Turkish strings, icons, banner
docs/                      architecture, decisions, testing, Play listing
store/                     generator for the Play Store graphics
```

More detail, in Turkish: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DECISIONS.md](docs/DECISIONS.md).

## License

GNU GPL v3 ([LICENSE](LICENSE)). The receiver core comes from [UxPlay](https://github.com/FDH2/UxPlay). The components, their versions, licenses and local changes are listed in [THIRD_PARTY.md](THIRD_PARTY.md) (Turkish).

"AirPlay", "Apple TV" and "iPhone" are trademarks of Apple Inc. Fenpo Cast is not affiliated with Apple.
