# JarvisDisplay

Photo frame Android app that displays messages on a flip-board style grid. Built for the NUMA PM project.

![Architecture](docs/architecture.svg)

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌────────────────┐
│   Backend       │     │  Android App     │     │  Photo Frame   │
│   (Synology /   │────▶│  JarvisDisplay   │────▶│  Hardware      │
│   any server)   │     │                  │     │               │
└─────────────────┘     └──────────────────┘     └────────────────┘
```

### Display System

The frame loops through a queue of **matrices** — each matrix is a 2D grid of characters (22 columns × 25 rows = 550 tiles).

Only tiles that change between two matrices animate. This creates the classic airport/aérogare flip-board effect where you see tiles flip one by one.

### Key Components

| File | Role |
|------|------|
| `Matrix.kt` | Data model for a display matrix (rows, duration, change set) |
| `MatrixQueue.kt` | Manages the playback loop — no server polling during playback |
| `FlipBoardView.kt` | Custom ViewGroup rendering the 22×25 tile grid |
| `DisplayTile.kt` | Individual flip tile with animation |
| `MainActivity.kt` | Entry point, admin tap zone, accent bar |

### Format API

The app polls a backend for matrices:

```json
GET /api/matrices

{
  "matrices": [
    {
      "id": "1",
      "rows": ["JARVIS ONLINE       ", "                    ", ...],
      "duration": 8
    }
  ]
}
```

- `id`: unique identifier (used for deduplication)
- `rows`: list of 25 strings, each 22 characters (padded with spaces)
- `duration`: how long to display this matrix in seconds

### Local Matrix Format

For test/debug mode, matrices can be defined locally:

```kotlin
Matrix(
    id = "1",
    rows = buildRows("JARVIS ONLINE", 25),
    durationSeconds = 6
)
```

## Building

```bash
# Requires JDK 8
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_31.jdk/Contents/Home
cd /path/to/JarvisDisplay
./gradlew assembleDebug
```

The APK is output to: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

```bash
adb install -r app-debug.apk
adb shell am start -n com.jarvis.display/.MainActivity
```

For persistent kiosk mode, flash the APK via recovery or sideload.

## Admin Mode

Tap the **bottom-right corner 5 times** to reveal the admin panel.

## Backend Server

See `server/` directory for the reference Python server implementation.

## License

Private — NUMA PM project.
