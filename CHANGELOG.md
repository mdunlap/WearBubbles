# Changelog

All notable changes to WearBubbles will be documented in this file.

## [0.4.2] - 2026-02-27

### Watch app
- Fullscreen image viewer: tap any image to view full screen with crown zoom, finger pan, and tap to exit
- Fixed duplicate notifications from redundant socket listeners
- Fixed reactions (tapbacks) showing as phantom messages in conversation list

---

## [0.4.1] - 2026-02-26

### Watch app
- MessagingStyle notifications (conversation layout matching Google Messages)
- Inline reply from notifications without opening the app
- Multiple messages from the same chat stack into one notification
- Load all conversations at once instead of paginated (fixes stale/missing chats)
- Removed "Load more" button from conversation list

---

## [0.4.0] - 2026-02-25

### Watch app
- Photo/Video/Attachment labels in conversation preview (instead of blank for image-only messages)
- Message send failure with retry: failed messages show red with "Failed — tap to retry" instead of silently disappearing
- Update checker in Settings screen

### Phone companion app
- Fixed crash when tapping "Send to Watch" (Compose BOM version mismatch causing R8 to strip animation methods)

### Bug fixes
- Fixed R8 stripping Gson deserialization for credential sync payload (watch reported error on setup)
- Added ProGuard keep rules for both watch and phone release builds

---

## [0.3.0] - 2026-02-24

### Watch app
- Foreground service keeps socket alive for real-time notifications even when app is closed
- "Load earlier" chip to load older messages when scrolling up
- Photo prefetch: preloads recent attachment thumbnails on the conversation list for instant display
- Fixed photos not loading (switched to correct BlueBubbles message query API)
- Fixed photos disappearing when navigating away and back
- Runtime POST_NOTIFICATIONS permission request for Android 13+
- Improved socket reconnection (infinite retries, websocket-only transport, shared OkHttpClient)

### Removed
- Long-press heart react (unreliable on Wear OS scroll surfaces)

---

## [0.2.0] - 2026-02-24

### Watch app
- EXIF rotation fix for photo attachments
- Real-time notifications via Socket.IO (instant delivery, not 15-min polling)
- Haptic feedback (double-tap vibration) on incoming messages
- Settings screen with haptics toggle, server status, and watch reset
- Heart react: long-press any message to send a love tapback
- Attachment-only messages now show "Attachment" preview in conversation list
- New message compose screen
- Custom app icon

### Phone companion app
- Watch status dashboard (server connection, conversation count, unread count, last message time)
- Release build signing for sideloading

---

## [0.1.0] - 2026-02-24

First pre-release.

### Watch app
- Conversation list with contact name resolution and unread indicators
- Message threads with send/reply
- Image and GIF attachment support (via Coil)
- Real-time updates via Socket.IO WebSocket
- Background sync and notifications via WorkManager
- Horologist-powered scrollable lists with rotary input
- Contact caching and message pagination
- DataStore-based credential storage

### Phone companion app
- Enter server URL and password on phone, send to watch via Wearable Data Layer
- Watch connection status with node discovery
- Reset watch app and clear local data remotely
- Material 3 dark theme
