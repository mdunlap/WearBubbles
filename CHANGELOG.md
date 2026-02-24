# Changelog

All notable changes to WearBubbles will be documented in this file.

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
