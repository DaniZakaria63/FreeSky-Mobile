# Freesky

Your words. Your community. Your rules.

---

I built Freesky because I don't care about the server. It's just a mailbox that stores sealed envelopes. It can't read what you write, it can't track who you are, and it sure as hell won't decide what you're allowed to say.

Every post is encrypted on your phone, before it touches the network. The server gets a blob of meaningless noise. Only your people have the key.

This is not a product. This is a middle finger to surveillance.

---

## The Idea

Most "private" apps ask you to trust them. Freesky doesn't ask for trust — it makes trust irrelevant. The server operator could be malicious, compromised, or serving a government subpoena, and it still wouldn't matter. There's nothing to seize. Nothing to hand over. Every byte on that server is ciphertext that only your community's devices can read.

**Your phone generates a secp256r1 keypair in AndroidKeyStore.** That key never leaves your device. Not for backup, not for sync, not ever. It's the anchor for everything: signing your posts, decrypting your group keys, authenticating your Noise sessions.

**Registration is a key exchange, not an account creation.** You send your public key over Tor (anonymized), the server gives back a group key wrapped in ECIES. Only your private key can unwrap it. The server also hands you its Noise static key so you can build an encrypted tunnel for all future communication.

**The group key is shared knowledge.** Every member of your community has it. It's the secret that lets you read each other's posts. It's also rotatable — if someone leaves or gets compromised, the server pushes a new one. Old posts stay readable with the old epoch's key.

**Writing a post is a three-step dance.** Encrypt the content with AES-256-GCM using the group key. Sign the ciphertext with your ECDSA key so everyone knows it's really you (well, your pseudonym). Ship the whole thing through a Noise NK encrypted tunnel — ChaCha20Poly1305 over TCP, forward-secret, authenticated.

**Comments are threaded.** Every post can have nested replies. The `parent_id` field links replies to their parent. A SQLite trigger validates parent existence. The `thread` endpoint uses a recursive CTE to fetch a post and all its descendants in one query. The Android client groups replies under their parent post with a terminal-style tree indentation (`┃`, `┗`, `┣`).

**Reading the feed is the same dance in reverse.** The Noise tunnel brings you blobs. Verify each author's signature using their public key. Decrypt with the group key. Derive their display name from their public key via SHA-256. Render. Repeat.

**Everything you see on screen was decrypted on your device.** End to end. No exceptions.

---

## The Tech

The curve is secp256r1. Not because it's the best, but because AndroidKeyStore mandates it and that's the hill I'll die on. No Ed25519, no X25519 — the server implements what the phone needs, not the other way around.

Content encryption runs on AES-256-GCM with random 12-byte nonces per post. The group key exchange uses ECIES: ECDH to agree on a shared secret, HKDF fed through HMAC-SHA256 to stretch it, then AES-256-GCM to encrypt the actual key material.

Transport security is Noise NK with P256, ChaChaPoly, and BLAKE2s. The NK pattern means the client knows the server's static key in advance (learned during registration). The prologue is your APK signing certificate hash — tying the app binary to the session.

Tor runs in-process via Guardian Project's TorService. All HTTP traffic routes through the SOCKS proxy. Nobody gets your IP.

The UI is Jetpack Compose with Material3, dressed in a Nord terminal color scheme. Monospace typography. Black background. Green text. It looks like you're reading a feed inside a terminal emulator from 1993, because that's the vibe.

---

## What You'll Find in the Repo

Three Gradle modules, each with one job.

`app` is the Android entrypoint. Registration screen, community feed, viewmodels, DataStore persistence. It depends on `network` which depends on `encrypt`. That's the whole dependency chain.

`encrypt` is the crypto layer with no networking. AndroidKeyStore wrappers, ECIES, HKDF, the MLS stand-in (AES-256-GCM for now), identity derivation. Hilt-wired. No OkHttp, no Tor, no JSON.

`network` is the single source of truth for HTTP. OkHttpClient provider, Noise protocol implementation (raw TCP with the full NK handshake), session management, key rotation handler, Tor lifecycle. Every API call, whether REST or Noise, goes through this module.

No DI framework beyond Hilt. No navigation framework. No networking library beyond OkHttp and raw sockets for Noise.

---

## Building

JDK 17 is non-negotiable. AGP 9.3 needs it. The Gradle cache has Eclipse Temurin 17 pre-bundled — use that, not whatever Java you have on your system.

```bash
export JAVA_HOME=/home/dani/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
export ANDROID_HOME=/home/dani/Android/Sdk
./gradlew :app:assembleDebug
```

Running low on RAM? Kill the Gradle daemon before it kills you:

```bash
export JAVA_HOME=/home/dani/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
./gradlew --stop
```

Tests are JUnit4. Instrumented tests run on AndroidJUnit4. No Robolectric, no MockK, no ceremony. Unit tests live in `src/test/java/`, instrumented tests in `src/androidTest/java/`. R8 rules live in `keepRules/` directories — AGP 9.x style, not the old proguard-rules.pro pattern.

---

## The Point

Freesky exists because speech needs a place to live that isn't owned by anyone. The server is infrastructure, not authority. The encryption isn't a feature — it's the entire point. If you can change the server without asking permission, if your community persists regardless of who runs the hardware, then you've won.

The server doesn't matter. You do.

---

## License

Whatever keeps this free.