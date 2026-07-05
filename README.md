# PdfVault

An Android app that is three things in one: a **file manager**, an **S3 backup tool**,
and a **PDF reader**. You point it at an Amazon S3 bucket; it shows your folders and
files, lets you upload PDFs (backup), and opens PDFs for reading — all kept in sync with
the bucket.

Built with **Kotlin + Jetpack Compose + AWS SDK for Kotlin**.

## How it works

On first launch the app asks for:

1. **Access key ID** and **secret access key** — stored encrypted on-device via
   `EncryptedSharedPreferences` (AES-256, key in the Android Keystore). They never leave
   the phone except to talk to AWS.
2. **Region** (defaults to `us-east-1`, picked from a dropdown or typed).
3. A **bucket** — choose an existing one, or create a new one inline.

After that you get a file browser backed directly by S3. There are no local copies of the
folder tree; every screen lists live from the bucket.

## Features

- **Browse** folders and files. S3 has no real folders, so "folders" are derived from
  object key prefixes using the `/` delimiter. Tapping a folder drills in; device back /
  the up arrow walks back out.
- **Upload (backup)** any PDF from the device into the current folder.
- **Create folder** (stored as a zero-byte `prefix/` placeholder object).
- **Delete** files or folders.
- **Read PDFs** rendered page-by-page with the platform `android.graphics.pdf.PdfRenderer`
  — no third-party PDF library. Files are streamed to the cache and rendered lazily.

## Project layout

```
app/src/main/java/com/pdfvault/
├─ PdfVaultApp.kt          Application + container accessor
├─ AppContainer.kt         Manual DI: credential store + active S3Repository
├─ MainActivity.kt         Compose host, decides start screen
├─ data/
│  ├─ CredentialStore.kt   Encrypted credential persistence
│  ├─ model/               S3Config, S3Item (Folder / File)
│  └─ s3/
│     ├─ S3Factory.kt      Builds S3Client from static credentials
│     ├─ S3Setup.kt        listBuckets / createBucket / resolveBucketRegion
│     └─ S3Repository.kt   Bucket-scoped: list / upload / download / createFolder / delete
├─ pdf/PdfDocument.kt      PdfRenderer wrapper (mutex-serialised page rendering)
└─ ui/
   ├─ navigation/          Routes + NavHost
   ├─ setup/               Credentials & bucket selection screen
   ├─ browser/             File-manager screen
   └─ viewer/              PDF reader screen
```

Architecture is MVVM: each screen has an `AndroidViewModel` exposing a `StateFlow` of UI
state; the S3 work lives in `S3Repository`, run on `Dispatchers.IO` via coroutines.

## Building

The Android SDK location is read from `local.properties` (`sdk.dir`). Then:

```bash
./gradlew :app:assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # build + install to a connected device/emulator
```

- compileSdk / targetSdk **35**, minSdk **26** (Android 8.0+)
- Kotlin 2.0.21, AGP 8.7.2, Gradle 8.9, JDK 17+

## Required IAM permissions

The supplied credentials need, at minimum:
`s3:ListAllMyBuckets`, `s3:ListBucket`, `s3:GetBucketLocation`, `s3:GetObject`,
`s3:PutObject`, `s3:DeleteObject`, and `s3:CreateBucket` (only if you create buckets in-app).

## Status / next steps

This is a working first version. Natural follow-ups:

- Download/share a PDF out of the app, and an "open with" intent.
- Pull-to-refresh and multi-select delete.
- Background sync + offline cache of recently read PDFs.
- Optional SSE-KMS / server-side encryption settings.
- Move/rename objects (S3 copy + delete).
