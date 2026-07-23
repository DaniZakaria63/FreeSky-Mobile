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
- Crypto plan in `docs/` — read before implementing encryption features

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

## Architecture notes

- `:encrypt` module contains the crypto layer — see `docs/android-encryption-guide.md`
- `:network` module is the single source of truth for all HTTP — uses OkHttp client
- Key material must use AndroidKeyStore; never export raw private key bytes
- Transport security uses Noise IK (see `docs/android-encryption-guide.md` §1)
- Content encryption uses MLS via `kotlin-mls` (see `docs/android-encryption-guide.md`)
- `:network` depends on `:encrypt` (KeyRotationHandler needs crypto primitives)
- No DI framework, no navigation framework, no networking library added yet
