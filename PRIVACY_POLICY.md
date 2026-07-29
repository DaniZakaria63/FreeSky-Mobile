# Freesky Privacy Policy

*Last updated: 2026-07-29*

## 1. Introduction

Freesky is an end-to-end encrypted community messaging application. This policy describes what information is collected, how it is used, and your rights regarding your data.

**Contact:** walawe.fun/freesky-privacy-policy

---

## 2. Information We Collect

### 2.1 Information You Provide

- **Device public key** — A secp256r1 public key generated on your device during registration. Used as your identity on the network.
- **APK signing certificate SHA-1** — A hash of the app's signing certificate, sent during registration and used as the Noise protocol handshake prologue.
- **Post content** — Text you submit is encrypted on your device with AES-256-GCM before transmission. The server never sees plaintext content.
- **Report data** — If you report a post, your device public key and the reported post ID are sent to the server.

### 2.2 Information Collected Automatically

- **Device IP address** — Your local network IP is displayed in the app UI for connection status. It is **not** transmitted to the server.
- **Device information** — Manufacturer, model, Android version, and build string are displayed locally in the App Info dialog. They are **not** transmitted.
- **Connection metadata** — The server observes your public key and connection timestamp to associate posts with identities.

### 2.3 No Collection Of

Freesky does **not** collect, transmit, or store:

- Real names, email addresses, or phone numbers
- Location data (GPS, WiFi triangulation)
- Contact lists
- Photos, camera, or microphone data
- Device advertising ID (GAID/AAID)
- Biometric data
- Usage analytics or crash reports
- Any data from third-party tracking services

---

## 3. How Information Is Used

- **Identity** — Your device public key is the sole identifier on the network. Usernames are derived deterministically from the public key hash.
- **Encryption** — Your key is used for ECDH key agreement (ECIES) to receive the group key, and for ECDSA signing to authenticate your posts.
- **Service operation** — The server stores encrypted posts and delivers them to connected clients.

---

## 4. Data Storage and Security

### 4.1 On Your Device

| Data | Storage | Protection |
|---|---|---|
| Device private key | AndroidKeyStore | Hardware-backed (TEE/StrongBox on supported devices), never exported |
| Group key (AES-256) | DataStore + filesDir | Encrypted at rest via Android file-based encryption |
| Registration data | DataStore | Encrypted at rest |
| Noise session keys | Memory only | Cleared on disconnect |

### 4.2 On the Server

| Data | Storage | Format |
|---|---|---|
| Device public key | SQLite (`devices` table) | Raw bytes |
| Encrypted group key | SQLite (`devices` table) | ECIES-encrypted (per-device) |
| Posts | SQLite (`posts` table) | AES-256-GCM ciphertext (server cannot read) |
| Reports | SQLite (`reports` table) | Plaintext metadata |

The server **cannot** read post content. All content is encrypted end-to-end with a group key known only to registered devices.

### 4.3 In Transit

- **Registration** — HTTP via Tor SOCKS5 proxy when available.
- **All other operations** — Noise NK protocol over TCP (secp256r1 ECDH, ChaCha20-Poly1305, BLAKE2s). End-to-end encrypted transport.
- **APK cert SHA-1** doubles as the Noise protocol prologue, binding the connection to this specific app build.

---

## 5. Third-Party Services

Freesky uses no analytics, crash reporting, advertising, or tracking SDKs. Dependencies are limited to infrastructure libraries:

| Library | Purpose |
|---|---|
| OkHttp | HTTP client |
| Gson | JSON serialization |
| Hilt | Dependency injection |
| Tor Android | SOCKS5 proxy for HTTP traffic |
| Bouncy Castle | BLAKE2s hash, ChaCha20-Poly1305 |
| Jetpack DataStore | Local key-value persistence |
| Jetpack Compose | UI framework |

No Firebase, Google Analytics, Crashlytics, Sentry, or any telemetry SDK is included.

---

## 6. Permissions

Freesky requests one permission:

- **INTERNET** — Required for all network communication (HTTP + Noise TCP).

No camera, microphone, location, contacts, storage, phone state, or biometric permissions are used.

---

## 7. Data Retention

### 7.1 Local Data

Registration data and group keys persist on your device until you:
- Clear app data via system settings
- Uninstall the app
- Re-register (overwrites existing data)

MLS group state files persist indefinitely on device storage.

### 7.2 Server Data

The server stores:
- Device public keys and encrypted group keys indefinitely (until a device is banned)
- Posts indefinitely (the server has no automatic deletion)
- Reports pending resolution

There is currently **no user-facing mechanism** to request deletion of your data from the server.

---

## 8. Data Backup

Android backup is enabled (`android:allowBackup="true"`). Your local data (including group keys) may be included in Google Drive backups if you opt into Android's backup service.

---

## 9. Children's Privacy

Freesky is not directed at children under 13. We do not knowingly collect personal information from children.

---

## 10. Your Rights

Depending on your jurisdiction, you may have the right to:

- **Access** — Know what data is stored about your device
- **Deletion** — Request removal of your data from the server (contact the operator directly)
- **Object** — Object to data processing (uninstall the app)

To exercise these rights, contact the server operator at walawe.fun/freesky-privacy-policy.

---

## 11. Changes to This Policy

Policy updates will be posted at walawe.fun/freesky-privacy-policy. Continued use after changes constitutes acceptance.

---

## 12. Security Considerations

- **Group key in debug logs** — In debug builds, the group key is written to logcat (`RegistrationHandler.kt`). Anyone with `adb` access can recover it. Release builds strip these logs.
- **Group key at rest** — Stored without app-level encryption on device (relies on Android file-based encryption).
- **Noise traffic not Tor-routed** — Post-registration Noise TCP traffic goes directly to the server, bypassing Tor. IP metadata is visible to the server and network intermediaries.
- **Server is self-hosted** — The server operator has full access to encrypted post data (though cannot decrypt it without the group key).
