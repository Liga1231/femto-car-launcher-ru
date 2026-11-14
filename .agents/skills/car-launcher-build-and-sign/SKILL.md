---
name: car-launcher-build-and-sign
description: >-
  Procedure for compiling, testing, signing with Android v3 signature scheme via apksigner, and publishing releases for Femto Car Launcher RU.
  Use when the user asks to build a release APK, verify unit tests, create a release, or publish to GitHub.
---

# Car Launcher Build and Release Runbook

This skill is the canonical procedure for compiling, verifying, and signing production release APKs for the Femto Car Launcher RU project.

## 1. Prerequisites and Environment

1. **JDK 21**: Must be Java 21 (e.g. `C:\Users\Liga.inc\.jdks\jbr-21.0.11` or `C:\Program Files\Java\jdk-21`).
2. **Android SDK**: `local.properties` must point to `sdk.dir=C:/Users/Liga.inc/AppData/Local/Android/Sdk`.
3. **Build Tools & apksigner**: Located at `C:\Users\Liga.inc\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat`.
4. **Keystore**: `C:\Users\Liga.inc\.android\debug.keystore` (alias: `androiddebugkey`, password: `android`).

## 2. Running Unit Tests

Run full test suite before any release build:

```powershell
.\gradlew.bat testStableDebugUnitTest
```

All 1600+ unit tests must pass (`BUILD SUCCESSFUL`).

## 3. Building Release APK

Assemble the optimized, minified release APK:

```powershell
.\gradlew.bat assembleStableRelease
```

Unsigned output location:
`app/build/outputs/apk/stable/release/app-stable-release-unsigned.apk`

## 4. Signing APK (v3 Signature Scheme)

Use `apksigner` to sign the APK. Android 10+ head units require v2/v3 signature:

```powershell
$apksigner = "C:\Users\Liga.inc\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat"
$keystore  = "C:\Users\Liga.inc\.android\debug.keystore"
$unsigned  = "app\build\outputs\apk\stable\release\app-stable-release-unsigned.apk"
$signed    = "..\femto-car-launcher-ru-v2.1.0.apk"

& $apksigner sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signed $unsigned
& $apksigner verify --verbose $signed
```

Verify output reports:
`Verified using v3 scheme (APK Signature Scheme v3): true`

## 5. Publishing to GitHub Releases

Use stored GitHub credentials (`git credential fill` with host `github.com`) to query the GitHub REST API:
- Create or update release at `POST https://api.github.com/repos/Liga1231/femto-car-launcher-ru/releases`.
- Upload signed APK as asset with `Content-Type: application/vnd.android.package-archive`.
- Upload Magisk battery module if updated.
