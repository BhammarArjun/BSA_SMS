# On-Device SMS → Transaction Tracker

A privacy-first Android app that turns your bank/UPI **SMS into a clean transaction ledger** — entirely **on-device**, **offline**, using a small local LLM. No servers, no accounts, no data ever leaves your phone.

> **Status: early.** A working spike (model + extraction) is proven; the full app is being built per [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md). Not yet feature-complete.

## What it does

- Reads incoming SMS (and, optionally, your existing inbox).
- A free **regex gate** instantly discards OTPs/promos so the model only ever sees likely transactions.
- A small on-device LLM (**Qwen3-0.6B** via Google **LiteRT-LM**) extracts: amount, date, counterparty, debit/credit, and a category — as structured JSON.
- Stores everything in a local database and shows a **Material 3 dashboard** (spend over time, by category, debit vs credit, top counterparties).
- Runs on a **user-set schedule** to save battery; the model is loaded only when there's something to process.

## Privacy

Everything runs locally. The **only** network access is a **one-time download of the model file** (a public, open-weights model) on first launch — after that the app works in airplane mode. No telemetry, no analytics, no cloud. See the hardening rules in [`IMPLEMENTATION_PLAN.md` §13](./IMPLEMENTATION_PLAN.md).

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Room · Hilt · WorkManager · [LiteRT-LM](https://developers.google.com/edge/litert-lm) (`com.google.ai.edge.litertlm`) · Qwen3-0.6B (`.litertlm`).

## Build

Requires Android Studio (JDK 17+). The 475 MB model is **not** in the repo — it downloads on first run (or `adb push` it for development).

```bash
# from the android/ project
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # signed release (see Distribution)
```

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
