# WearBubbles

A Wear OS app that brings iMessage to your smartwatch via [BlueBubbles](https://bluebubbles.app/).

WearBubbles connects to a self-hosted BlueBubbles server to let you view conversations, read messages, send replies, and receive notifications — all from your wrist.

## Features

- **Conversation list** — Browse your iMessage chats with contact name resolution and unread indicators
- **Read & reply** — View full message threads and send replies using the on-watch keyboard
- **Image & GIF support** — View image and GIF attachments inline
- **Real-time updates** — WebSocket connection delivers new messages instantly
- **Background notifications** — Periodic sync checks for new messages and posts notifications even when the app is closed
- **Standalone** — Runs entirely on the watch with no companion phone app required

## Screenshots

*Coming soon*

## Prerequisites

- A **BlueBubbles server** running on a Mac with iMessage configured ([setup guide](https://docs.bluebubbles.app/))
- The server must be reachable from your watch's network (e.g. via ngrok, Tailscale, or local network)
- A **Wear OS 3.0+** smartwatch (API 30 / Android 11)
- **Android SDK** with build tools installed
- **JDK 17**

## Building from Source

### 1. Install dependencies

**macOS (Homebrew):**

```bash
brew install openjdk@17
brew install --cask android-commandlinetools
```

Accept the Android SDK licenses:

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | $ANDROID_HOME/bin/sdkmanager --licenses
```

**Linux / other:**

Install [Android Studio](https://developer.android.com/studio) or the [command-line tools](https://developer.android.com/studio#command-line-tools-only), plus JDK 17 from your package manager.

### 2. Clone the repository

```bash
git clone https://github.com/mdunlap/WearBubbles.git
cd WearBubbles
```

### 3. Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

> Adjust `JAVA_HOME` and `ANDROID_HOME` to match your system if not using Homebrew defaults.

### 4. Install on watch

**Via USB/wireless ADB:**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

To connect to a watch over Wi-Fi:

```bash
adb connect <watch-ip>:<port>
```

## Setup

1. Launch **WearBubbles** on your watch
2. Enter your BlueBubbles **server URL** (e.g. `https://xxxx.ngrok-free.app`)
3. Enter your BlueBubbles **password**
4. Tap **Connect** — the app will validate the connection and load your conversations

## Architecture

```
BlueBubbles Server
    |
    ├── REST API (Retrofit) ──────────┐
    └── WebSocket (Socket.IO) ────────┤
                                      v
                              Repository Layer
                             (Chat / Message / Contact)
                                      |
                                  Room Database
                                      |
                                  ViewModels (StateFlow)
                                      |
                              Jetpack Compose UI
                             (Wear OS Material 3)
```

| Layer | Tech |
|---|---|
| UI | Jetpack Compose for Wear OS, Material 3 |
| Navigation | Wear Compose Navigation with swipe-to-dismiss |
| State | ViewModels + Kotlin StateFlow |
| Networking | Retrofit 2 + OkHttp (REST), Socket.IO (WebSocket) |
| Persistence | Room (SQLite), DataStore (preferences) |
| Background sync | WorkManager (15-min interval) |
| Image loading | Coil (with GIF decoder) |
| Language | Kotlin, JDK 17 target |

## Project Structure

```
app/src/main/java/com/wearbubbles/
├── api/                  # Retrofit client, API interface, DTOs
├── socket/               # Socket.IO manager for real-time events
├── db/                   # Room database, DAOs, entities
├── data/                 # Repositories, DataStore settings
├── ui/
│   ├── setup/            # Server connection setup screen
│   ├── conversations/    # Chat list screen
│   ├── messages/         # Message thread screen
│   └── theme/            # Wear OS color theme
├── worker/               # Background message sync (WorkManager)
├── MainActivity.kt       # Entry point
└── WearBubblesApp.kt     # Application class
```

## BlueBubbles API Usage

WearBubbles uses the following BlueBubbles REST endpoints:

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/ping` | Validate server connection |
| `POST /api/v1/chat/query` | Fetch conversation list |
| `GET /api/v1/chat/{guid}/message` | Fetch messages for a chat |
| `POST /api/v1/message/text` | Send a message (via AppleScript) |
| `POST /api/v1/chat/{guid}/read` | Mark a conversation as read |
| `GET /api/v1/contact` | Fetch contacts for name resolution |
| `GET /api/v1/attachment/{guid}/download` | Download image attachments |

Real-time events are received over Socket.IO: `new-message`, `updated-message`, `typing-indicator`.

## Configuration

All configuration is done on-device through the setup screen. Credentials are stored locally using Android DataStore and never leave the watch.

No config files or environment variables are needed at build time.

## License

This is a private project. All rights reserved.
