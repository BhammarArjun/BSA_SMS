# On-Device SMS → Transaction Tracker

A privacy-first Android app that turns your bank/UPI **SMS into a clean transaction ledger** — entirely **on-device**, **offline**, using a small local LLM. No servers, no accounts, no data ever leaves your phone.

> **Status: feature-complete, pending on-device validation.** The full app is implemented per [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) — capture, scheduled extraction, ledger, analytics, and settings all build and pass the unit suite. Performance, battery, and live-SMS behavior still need validation on a real phone (the model runtime can't be exercised on the emulator).

## What it does

- Reads incoming SMS (and, optionally, your existing inbox).
- A free **regex gate** instantly discards OTPs/promos so the model only ever sees likely transactions.
- A small on-device LLM (**Qwen3-0.6B** via Google **LiteRT-LM**) extracts: amount, date, counterparty, debit/credit, and a category — as structured JSON.
- Stores everything in a local database and shows a **Material 3 dashboard** (spend over time, by category, debit vs credit, top counterparties).
- A date-grouped **transactions list** with reactive search/filter, and a **detail view** to fix a category, edit fields, mark something "not a transaction" (excluded from analytics), or **re-verify** it — your edits survive re-verification.
- **Settings** for the processing interval, battery/charging constraints, backend preference (Auto/CPU/GPU/NPU), confidence threshold, **inbox import**, and **CSV export**.
- Runs on a **user-set schedule** to save battery; the model is loaded only when there's something to process — and never when the queue is empty.

## Privacy

Everything runs locally. The **only** network access is a **one-time download of the model file** (a public, open-weights model) on first launch — after that the app works in airplane mode. No telemetry, no analytics, no cloud. See the hardening rules in [`IMPLEMENTATION_PLAN.md` §13](./IMPLEMENTATION_PLAN.md).

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Room · Hilt · WorkManager · [LiteRT-LM](https://developers.google.com/edge/litert-lm) (`com.google.ai.edge.litertlm`) · Qwen3-0.6B (`.litertlm`).

## Build

Requires Android Studio (JDK 17+). The 475 MB model is **not** in the repo — it downloads on first run (or `adb push` it for development).

```bash
# from the android/ project
./gradlew assembleDebug      # debug APK
./gradlew testDebugUnitTest  # JVM unit + Robolectric tests
./gradlew assembleRelease    # R8-minified, resource-shrunk release APK
```

The release build is signed with your release key if `android/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) is present, and **falls back to the debug key** if it isn't — so contributors can build a release APK without any secrets. Keep the keystore and `keystore.properties` out of git (already `.gitignore`d).

> **Testing is done on a real Android phone, not the emulator** — on-device CPU/GPU/NPU and SMS behavior don't match the emulator. The emulator is only for quick compile/smoke checks.

## Install / Distribution

This app can't go on the Google Play Store (Play restricts SMS-reading apps to default-SMS-handler use cases). It's distributed by **open-source sideloading**:

- **GitHub Releases** — download the signed APK and install (you'll enable "install from unknown sources" once; Play Protect may warn — expected for a sideloaded SMS app, and the source here is fully auditable).
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — add this repo's URL for automatic updates from GitHub Releases.

See [`IMPLEMENTATION_PLAN.md` §15](./IMPLEMENTATION_PLAN.md) for signing and release details.

## Repository layout

```
android/                 Android Studio project (Kotlin, Gradle)
IMPLEMENTATION_PLAN.md    Full build plan & spec (the source of truth)
docs.md                   Original research notes
```

## License

[MIT](./LICENSE) © 2026 Arjun Bhammar.

Model and runtime are third-party: Qwen3-0.6B (Apache-2.0, Alibaba) and LiteRT-LM (Google) retain their own licenses.
