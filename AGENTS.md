# Freesky

Android app (Jetpack Compose) + encrypt library. MLS-based encrypted community app.

## Project structure

```
:app      — Android application, entrypoint, Compose UI
:encrypt  — Android library, crypto layer (ECIES, HKDF, ECDSA, MLS stub)
:network  — Android library, OkHttp-based network layer (single source of truth)
```

- App namespace `com.wingsheep.freesky`, minSdk 24, targetSdk 36
- Kotlin 2.2.10, AGP 9.3.0, Gradle 9.5
- Compose Material3, version catalog at `gradle/libs.versions.toml`
- There is no local `docs/` directory in this checkout. The crypto and protocol
  plan lives in `/home/dani/opt/docs/freesky/` (see Boundaries).
- **Canonical protocol sync** at `/home/dani/opt/docs/freesky/` — truth for Android↔Server wire formats, curve choices, API shapes. Read `PROTOCOL_SYNC.md` first before any cross-boundary work (ECIES, signing, registration, Noise). Overrides any stale local notes if they conflict.

## Environment

JDK 17+ required (AGP 9.3.0, Kotlin 2.2.10). Use the Gradle cache JDK or
Android Studio bundle JDK — do **not** rely on a system `java`:

```sh
# Gradle cache JDK (Eclipse Temurin 17)
export JAVA_HOME=/home/dani/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2

# Android Studio JBR (alternative)
export JAVA_HOME=/home/dani/opt/android-studio/jbr

# Android SDK
export ANDROID_HOME=/home/dani/Android/Sdk
```

### Cleaning Gradle from memory

Stop all Gradle daemons before building to free RAM:

```sh
export JAVA_HOME=/home/dani/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
./gradlew --stop
```

## Build commands

```sh
export JAVA_HOME=/home/dani/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
export ANDROID_HOME=/home/dani/Android/Sdk

./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:test                   # unit tests (host JVM)
./gradlew :app:connectedAndroidTest   # instrumented tests (device/emulator)
./gradlew :encrypt:test               # encrypt module unit tests
./gradlew :encrypt:connectedAndroidTest
./gradlew build                       # full build all modules
```

## Testing

- JUnit4, `androidx.test.ext.junit.runners.AndroidJUnit4` for instrumented
- Instrumented runner: `androidx.test.runner.AndroidJUnitRunner`
- No Robolectric, no MockK, no special test harness
- Unit tests at `src/test/java/`, instrumented at `src/androidTest/java/`

## R8 / ProGuard

Uses AGP 9.x `keepRules/` pattern (NOT `proguard-rules.pro`):
- `app/src/main/keepRules/rules.keep`
- `encrypt/src/main/keepRules/rules.keep`
- `network/src/main/keepRules/rules.keep` — Gson parses the API models reflectively
  and they carry no `@SerializedName`, so R8 must not rename their fields; also
  keeps the Tor JNI classes. Inert until `isMinifyEnabled` is turned on.

## Boundaries

- **Server is off-limits.** Never edit files in `/home/dani/CLionProjects/freesky-server/`. Server changes are user's responsibility.
- Protocol reference only: `/home/dani/opt/docs/freesky/PROTOCOL_SYNC.md` — read for wire format decisions but never modify server code.

## Architecture notes

- `:encrypt` module contains the crypto layer — see `/home/dani/opt/docs/freesky/PROTOCOL_SYNC.md`
- `:network` module is the single source of truth for all HTTP — uses OkHttp client
- Key material must use AndroidKeyStore; never export raw private key bytes
- Transport security uses Noise NK; sessions are single-use and strictly
  request/response, one in flight at a time (see `NoiseManager`, `PROTOCOL_SYNC.md` §3.2)
- Content encryption uses a server-issued 32-byte group key with AES-256-GCM as a
  stand-in for MLS; real MLS is not wired on either side yet (see `MlsGroupManager`).
- EC curve is secp256r1 (AndroidKeyStore constraint). Do NOT add X25519/Ed25519 code — server must match us, not the other way.
- `:network` depends on `:encrypt` (KeyRotationHandler needs crypto primitives)
- DI: Hilt 2.60 (`@HiltAndroidApp`, `@HiltViewModel`, `@Module @InstallIn(SingletonComponent::class)`)
- Networking: OkHttp 4.12, configured centrally in `NetworkClient`; `:network` stays
  the single source of truth for all HTTP
- No navigation framework
