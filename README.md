# WearBubbles

A Wear OS app that brings iMessage to your smartwatch via [BlueBubbles](https://bluebubbles.app/), plus a phone companion app for easy setup.

WearBubbles connects to a self-hosted BlueBubbles server to let you view conversations, read messages, send replies, receive photos/gifs, and receive notifications.

**Download:** [Watch APK](https://github.com/mdunlap/WearBubbles/releases/latest/download/WearBubbles-watch.apk) | [Phone APK](https://github.com/mdunlap/WearBubbles/releases/latest/download/WearBubbles-phone.apk) | [Changelog](CHANGELOG.md)

## Features

- **Conversation list** — Browse your iMessage chats with contact name resolution and unread indicators
- **Read & reply** — View full message threads and send replies using the on-watch keyboard
- **Image & GIF support** — View image and GIF attachments inline
- **Real-time updates** — WebSocket connection delivers new messages instantly
- **Background notifications** — Periodic sync checks for new messages and posts notifications even when the app is closed
- **Phone companion app** — Enter credentials on your phone and send them to the watch via the Wearable Data Layer API

## Screenshots

*Coming soon*

## Prerequisites

- A **BlueBubbles server** running on a Mac with iMessage configured ([setup guide](https://docs.bluebubbles.app/))
- The server must be reachable from your watch's network (e.g. via ngrok, Tailscale, or local network)
- A **Wear OS 3.0+** smartwatch (API 30 / Android 11)
- An **Android phone** (API 28+ / Android 9) for the companion app
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

This builds both modules:
- Watch APK: `app/build/outputs/apk/debug/app-debug.apk`
- Phone APK: `mobile/build/outputs/apk/debug/mobile-debug.apk`

> Adjust `JAVA_HOME` and `ANDROID_HOME` to match your system if not using Homebrew defaults.

### 4. Install

**Watch (via ADB):**

```bash
adb -s <watch-ip>:<port> install app/build/outputs/apk/debug/app-debug.apk
```

**Phone (via ADB):**

```bash
adb -s <phone-ip>:<port> install mobile/build/outputs/apk/debug/mobile-debug.apk
```

## Setup

### Option A: Phone companion app (recommended)

1. Install both APKs (watch + phone)
2. Open **WearBubbles** on your phone
3. Verify the watch connection status shows "Connected"
4. Enter your BlueBubbles server URL and password
5. Tap **Send to Watch** — credentials are sent via the Wearable Data Layer
6. The watch app will automatically connect and load your conversations

### Option B: Manual setup on watch

1. Launch **WearBubbles** on your watch
2. Enter your BlueBubbles **server URL**
3. Enter your BlueBubbles **password**
4. Tap **Connect**

## Architecture

```
                    Phone Companion App
                   (Credential Setup via
                    Wearable Data Layer)
                           |
                    MessageClient API
                           |
BlueBubbles Server         v
    |               Wear OS Watch App
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
| UI (Watch) | Jetpack Compose for Wear OS, Material 3 |
| UI (Phone) | Jetpack Compose, Material 3 |
| Navigation | Wear Compose Navigation with swipe-to-dismiss |
| State | ViewModels + Kotlin StateFlow |
| Networking | Retrofit 2 + OkHttp (REST), Socket.IO (WebSocket) |
| Persistence | Room (SQLite), DataStore (preferences) |
| Background sync | WorkManager (15-min interval) |
| Image loading | Coil (with GIF decoder) |
| Watch-phone sync | Wearable Data Layer API (MessageClient) |
| Language | Kotlin, JDK 17 target |

## Project Structure

```
WearBubbles/
├── app/                              # Wear OS watch module
│   └── src/main/java/com/wearbubbles/
│       ├── api/                      # Retrofit client, API interface, DTOs
│       ├── socket/                   # Socket.IO manager for real-time events
│       ├── db/                       # Room database, DAOs, entities
│       ├── data/                     # Repositories, DataStore settings
│       ├── ui/
│       │   ├── setup/                # Server connection setup screen
│       │   ├── conversations/        # Chat list screen
│       │   ├── messages/             # Message thread screen
│       │   └── theme/                # Wear OS color theme
│       ├── worker/                   # Background message sync (WorkManager)
│       ├── DataLayerListenerService.kt  # Receives credentials from phone
│       ├── MainActivity.kt           # Entry point
│       └── WearBubblesApp.kt         # Application class
│
└── mobile/                           # Phone companion module
    └── src/main/java/com/wearbubbles/companion/
        ├── ui/
        │   ├── MainScreen.kt         # Connection status, credential entry, send
        │   └── Theme.kt              # Material 3 dark theme
        ├── MainActivity.kt           # Entry point
        └── MainViewModel.kt          # Watch discovery, message sending
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

All configuration is done on-device through the setup screen or the phone companion app. Credentials are stored locally using Android DataStore (watch) and SharedPreferences (phone) and never leave your devices.

No config files or environment variables are needed at build time.

## License

This is a private project. All rights reserved.
