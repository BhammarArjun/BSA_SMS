# SMS → Transaction Tracker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Build the UI with `frontend-design:frontend-design`. Verify on a real device with `verify` / `run`.

**Goal:** A 100%-on-device Android app that reads incoming SMS, uses a free regex gate + a small local LLM (Qwen3-0.6B via LiteRT-LM) to extract transactions (amount, date, counterparty, debit/credit, category), stores them in Room, and shows a beautiful Material 3 ledger + analytics. Processing runs on a user-set schedule (battery-friendly), never loading the model when there's nothing to do.

**Architecture:** Clean layered app (data / llm / gate / domain / work / sms / ui) with Hilt DI. SMS arrive via a `BroadcastReceiver` that only *captures + stores raw text* (no model). A periodic `WorkManager` job (interval user-configurable, ≥15 min) wakes, and **only if** there are pending or re-verify-queued messages, loads the LLM once, processes the queue sequentially, writes transactions, then releases the model. The regex gate is high-recall; the LLM is the authority on `is_transaction` (regex false-positives that the model rejects are stored but excluded from analytics).

**Tech Stack:** Kotlin 2.3.0, Jetpack Compose + Material 3, Room, Hilt, WorkManager, Kotlin Coroutines/Flow, Vico (Compose charts), LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.13.1`.

> **NON-NEGOTIABLES (read first):**
> 1. **Testing is on a REAL Android phone, not the emulator.** The app is built in Android Studio and validated on a physical budget device over USB debugging. The emulator may be used for a quick compile/smoke check only — all functional, performance, battery, and acceptance testing (model load time, tok/s, RAM, scheduled-run behavior, SMS capture) **must** be done on the real phone, because emulator CPU/GPU/NPU and SMS behavior do not represent it.
> 2. **No security vulnerabilities.** This app reads the user's SMS — among the most sensitive data on the device. The build must contain no vulnerabilities: all data stays on-device, nothing is logged or transmitted off-device, and the app follows the security requirements in **§13**. Treat any deviation as a build failure.

---

## 0. Current State (what already exists — reuse it)

A working **spike** lives in `android/` (package `com.local.smsllm`). It already proves the hard parts:
- Builds against LiteRT-LM 0.13.1 with **Kotlin 2.3.0** (2.0.x fails: the AAR's metadata is 2.3.0) and the `compilerOptions` DSL (not `kotlinOptions`).
- Loads `qwen3_0_6b_mixed_int4.litertlm` and runs extraction. Verified output is clean parseable JSON.
- `android/app/src/main/java/com/local/smsllm/LlmEngine.kt` — working `Engine`/`EngineConfig`/`ConversationConfig`/`SamplerConfig` usage and the `Message.contents.contents.filterIsInstance<Content.Text>()` text extraction.
- `SampleData.kt`, `MainActivity.kt` — sample prompt + a Compose harness with an `--ez autorun true` Logcat benchmark (tag `SPIKE`).

**Validated facts (do not re-discover):**
- **`/no_think` is mandatory** in the Qwen3 prompt — without it the model emits a long `<think>` block and exhausts the token budget before producing JSON (this is why it appeared to "hang"). With it: clean JSON, ~45 tok/s, <1 s load on Mac, ~1–2 s TTFT.
- LiteRT-LM has **no batched multi-prompt generation** — one conversation at a time. Batch at the orchestration layer.
- The Android Kotlin `SamplerConfig(topK, topP, temperature)` works on-device (the desktop xcframework's CPU executor only implements TopP, but that's a desktop-only quirk; ignore for Android).

**Toolchain on this machine:** JDK 21 bundled at `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (set `JAVA_HOME` to it for CLI builds); `adb` at `~/Library/Android/sdk/platform-tools/adb`; emulator AVD `Medium_Phone_API_35` (arm64); build with `android/gradlew` (Gradle 8.9 already wrapped). Real budget phone is the true performance target.

**Dev tool for fast prompt iteration (no Android needed):** `/tmp/clitertlm/harness.c` + `CLiteRTLM_mac.xcframework` — a native Mac C harness. Recompile and run `./harness <model.litertlm> [nothink]` to test prompt changes against the real runtime in seconds. Re-create it from the C API header `engine.h` if `/tmp` was cleared (see git history of this conversation / the header is in the xcframework zip at the v0.13.1 GitHub release).

**Decision:** This plan converts the spike into the full app **in place** under `android/`. Keep `LlmEngine.kt`'s proven API calls; refactor into the structure below.

---

## 1. Fixed Category Taxonomy (Indian context, 20)

Define once in `domain/Category.kt`. The LLM must pick exactly one of these `id`s or null. The UI uses `label`, `icon`, and `defaultDirectionHint` for display only.

| id | label | typical examples |
|---|---|---|
| `food` | Food & Dining | Swiggy, Zomato, restaurants |
| `groceries` | Groceries | Blinkit, Zepto, BigBasket, kirana |
| `shopping` | Shopping | Amazon, Flipkart, Myntra, retail |
| `transport` | Transport | Ola, Uber, auto, metro, bus |
| `fuel` | Fuel | petrol, diesel, HPCL/IOCL |
| `bills_utilities` | Bills & Utilities | electricity, water, gas, broadband |
| `recharge` | Recharge | mobile, DTH, FASTag |
| `rent` | Rent | house/office rent |
| `emi_loan` | EMI & Loan | loan EMI, credit-card bill payment |
| `investment` | Investments | MF, SIP, stocks, Groww, Zerodha |
| `insurance` | Insurance | LIC, premiums |
| `health` | Health & Medical | pharmacy, hospital, Apollo, 1mg |
| `education` | Education | school/college fees, courses |
| `entertainment` | Entertainment | OTT, movies, gaming |
| `travel` | Travel | flights, hotels, IRCTC, MakeMyTrip |
| `transfer` | Transfer (P2P) | UPI/IMPS/NEFT to a person |
| `income_salary` | Income & Salary | salary, payroll credit |
| `refund_cashback` | Refund & Cashback | reversals, cashback |
| `cash_atm` | Cash & ATM | ATM withdrawal, cash |
| `other` | Other | anything that doesn't fit |

---

## 2. The Production LLM Prompt

Lives in `llm/PromptBuilder.kt`. The system instruction is fixed; the user message is the raw SMS. `/no_think` is appended **only for Qwen models** (gate on `ModelSpec.needsNoThink`).

```kotlin
// PromptBuilder.kt
object PromptBuilder {
    // 20-category id list, comma-joined, sourced from Category.entries to stay in sync.
    private val CATEGORY_IDS = Category.entries.joinToString(", ") { it.id }

    val SYSTEM_INSTRUCTION = """
        You extract structured data from ONE Indian bank/UPI transaction SMS.
        Output ONLY one line of minified JSON. No markdown, no prose, no extra text.

        Schema (use exactly these keys, in this order):
        {"is_transaction":<bool>,"direction":<"debit"|"credit"|null>,"amount":<number|null>,"currency":<string|null>,"date":<string|null>,"counterparty":<string|null>,"category":<string|null>,"confidence":<number 0..1>}

        Rules:
        1. is_transaction = true ONLY if the SMS reports actual money moving on a bank account, card, or wallet (debited, credited, spent, withdrawn, paid, sent, received, purchase of <amount>).
        2. is_transaction = false for: OTP codes, promotional/offer messages, EMI/bill DUE reminders (no money moved yet), balance-only alerts, FAILED/DECLINED transactions, and delivery/info messages. When false, set every other field to null and confidence = your certainty it is NOT a transaction.
        3. direction: "debit" when money leaves (debited/spent/withdrawn/paid/sent/purchase); "credit" when money arrives (credited/received/refund/salary).
        4. amount: number only, no commas or symbols (e.g. "Rs.2,499.00" -> 2499). currency is usually "INR".
        5. date: copy the date exactly as printed; if absent, null.
        6. counterparty: merchant, person, or VPA/UPI handle if present; else null.
        7. category: choose EXACTLY ONE id from this list, or null if unsure:
           $CATEGORY_IDS
        8. confidence: 0..1 overall certainty.

        Examples:
        SMS: Rs.350 debited from A/c XX12 to zomato@upi on 03-01-26. Avl Bal Rs.900
        JSON: {"is_transaction":true,"direction":"debit","amount":350,"currency":"INR","date":"03-01-26","counterparty":"zomato@upi","category":"food","confidence":0.97}
        SMS: INR 55000 credited to A/c XX77 by NEFT from ACME PAYROLL on 01-Jun-26.
        JSON: {"is_transaction":true,"direction":"credit","amount":55000,"currency":"INR","date":"01-Jun-26","counterparty":"ACME PAYROLL","category":"income_salary","confidence":0.95}
        SMS: Your OTP is 884213. Do not share. -SBI
        JSON: {"is_transaction":false,"direction":null,"amount":null,"currency":null,"date":null,"counterparty":null,"category":null,"confidence":0.99}
    """.trimIndent()

    fun systemInstruction(spec: ModelSpec): String =
        if (spec.needsNoThink) "$SYSTEM_INSTRUCTION\n/no_think" else SYSTEM_INSTRUCTION
}
```

> **Reliability lever:** also enable LiteRT-LM **constrained decoding** if exposed by the Kotlin `ConversationConfig` in 0.13.1 (the C API has `litert_lm_conversation_config_set_enable_constrained_decoding`; verify the Kotlin property name — likely `enableConstrainedDecoding`). It forces schema-valid JSON. If unavailable in Kotlin, the `ExtractionParser` repair step (Task 1.4) is the safety net.

---

## 3. File / Module Structure

All under `android/app/src/main/java/com/local/smsllm/`:

```
domain/        Category.kt, ProcessingStatus.kt, TxnDirection.kt, ExtractionResult.kt, ModelSpec.kt
gate/          RegexGate.kt
llm/           LlmService.kt (interface), LiteRtLmService.kt, BackendSelector.kt,
               ModelManager.kt, PromptBuilder.kt, ExtractionParser.kt
data/          AppDatabase.kt, SmsMessageEntity.kt, TransactionEntity.kt,
               SmsDao.kt, TransactionDao.kt, Converters.kt
repo/          SmsRepository.kt, TransactionRepository.kt, SettingsRepository.kt
sms/           SmsReceiver.kt, SmsImporter.kt
work/          ExtractionWorker.kt, WorkScheduler.kt
ui/            theme/ (Color, Type, Theme), nav/, onboarding/, dashboard/,
               transactions/, detail/, settings/, components/ (charts, cards, chips)
di/            AppModule.kt (Hilt)
App.kt (Application + Hilt), MainActivity.kt
```

Pure-Kotlin (JVM-unit-testable, do TDD): `Category`, `RegexGate`, `PromptBuilder`, `ExtractionParser`, DAOs (Robolectric/instrumented). Native/integration (manual + instrumented): `LiteRtLmService`, `BackendSelector`, workers, SMS, UI.

---

## 4. Data Model & Status Flow

**`SmsMessageEntity`** (raw capture — the source of truth for the queue):
`id:Long(auto)`, `sender:String`, `body:String`, `receivedAt:Long(epochMillis)`, `source:String("LIVE"|"IMPORT")`, `gatePassed:Boolean`, `status:ProcessingStatus`, `processedAt:Long?`, `error:String?`. Unique index `(sender, body, receivedAt)` to dedupe inbox imports.

**`TransactionEntity`** (LLM output — one row per processed message that the model saw):
`id:Long(auto)`, `smsId:Long(FK)`, `isTransaction:Boolean`, `direction:String?`, `amount:Double?`, `currency:String?`, `dateText:String?`, `dateEpoch:Long?`, `counterparty:String?`, `category:String?`, `confidence:Double`, `rawModelOutput:String`, `modelId:String`, `backend:String`, `userEdited:Boolean=false`, `includedInAnalytics:Boolean`, `createdAt:Long`, `updatedAt:Long`.

`includedInAnalytics = isTransaction && !userExcluded` (default). **Regex false-positive handling:** if the gate passes but the model returns `is_transaction=false`, the row is written with `isTransaction=false`, `includedInAnalytics=false`, and the SMS status becomes `NON_TXN` — so it never appears in analytics but is auditable.

**`ProcessingStatus` enum:** `NEW` (just captured, pre-gate) · `GATE_REJECTED` (regex says not a txn; never sent to model) · `PENDING` (gate passed, awaiting LLM) · `PROCESSED` (LLM confirmed txn) · `NON_TXN` (LLM rejected) · `NEEDS_REVERIFY` (user re-queued) · `ERROR` (LLM/parse failed; retry next run).

**Worker query:** process messages `WHERE status IN ('PENDING','NEEDS_REVERIFY','ERROR')`. If that set is empty → return without loading the model.

---

## 5. Backend Auto-Selection (CPU / GPU / NPU)

`BackendSelector` tries backends in priority order, catching init failures, and caches the winner in `SettingsRepository` for next launch. User can override (Auto/CPU/GPU/NPU) in Settings.

- Order when `Auto`: **NPU → GPU → CPU**.
- **NPU** is only attempted if (a) the user supplied/downloaded an NPU-specific model variant (e.g. `gemma…_qualcomm_sm8750.litertlm`, or a Qwen MediaTek build) **and** (b) `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` initializes without throwing. For the shipped generic `qwen3_0_6b_mixed_int4.litertlm`, NPU will fail fast → falls to GPU → CPU. That's expected on budget phones.
- Implementation: a `suspend fun select(modelPath): Pair<Engine, Backend>` loop that `try { Engine(config).also{it.initialize()} } catch { … next }`. Record which backend succeeded on the `TransactionEntity.backend` field for telemetry.

```kotlin
// BackendSelector.kt (contract)
enum class BackendChoice { AUTO, CPU, GPU, NPU }

class BackendSelector(private val context: Context) {
    /** Returns an initialized Engine + the backend name that worked, or throws if all fail. */
    suspend fun open(modelPath: String, cacheDir: String, pref: BackendChoice): Pair<Engine, String>
}
```
Order resolved from `pref`: `AUTO` → listOf(NPU, GPU, CPU); else just that one. Wrap each attempt; on the last failure rethrow.

---

## 6. Pipeline (end to end)

1. **Capture:** `SmsReceiver` (registered for `android.provider.Telephony.SMS_RECEIVED`) parses `Telephony.Sms.Intents.getMessagesFromIntent(intent)`, concatenates multipart bodies, runs `RegexGate.passes(sender, body)` (cheap), and inserts `SmsMessageEntity(status = if (pass) PENDING else GATE_REJECTED, gatePassed = pass)`. **No model work here.** Then calls `WorkScheduler.ensureScheduled()` (idempotent) — it does NOT run the model now, just guarantees the periodic job exists.
2. **Import (one-time / on demand):** `SmsImporter` reads `Telephony.Sms.Inbox` via `ContentResolver`, applies the same gate, bulk-inserts with `source="IMPORT"` (dedup via unique index). Triggered from onboarding/settings.
3. **Scheduled processing:** `ExtractionWorker` (periodic, interval from settings, ≥15 min; optional `requiresCharging`/`requiresBatteryNotLow` constraints):
   - Query pending set. **If empty → return `Result.success()` without loading the model.**
   - Else: `BackendSelector.open(...)` once. For each pending message (oldest first, capped per run e.g. 50 to bound runtime): build prompt, `conversation.sendMessage`, `ExtractionParser.parse`, upsert `TransactionEntity`, set SMS status (`PROCESSED`/`NON_TXN`/`ERROR`). Sequential (no batch API).
   - `engine.close()` in a `finally`. Update `lastRunAt`, pending count.
4. **Re-verify queue:** user action sets `SmsMessageEntity.status = NEEDS_REVERIFY` (and optionally bumps a `reverifyRequestedAt`). The next worker run reprocesses it exactly like PENDING and overwrites the `TransactionEntity` (unless `userEdited=true` for fields the user manually changed — re-verify replaces model fields but preserves a user-set category if `userEdited`). Provide a **"Process now"** button that enqueues a one-time `ExtractionWorker` (expedited) so users don't wait for the interval.

---

## 7. Settings (user-controllable)

`SettingsRepository` backed by DataStore (Preferences):
- `processingIntervalMinutes:Int` (default 30, min 15 — clamp; changing it reschedules the periodic work).
- `requiresCharging:Boolean` (default false), `requiresBatteryNotLow:Boolean` (default true).
- `backendPreference:BackendChoice` (default AUTO).
- `modelId:String` (default `qwen3_0_6b`; swappable — see ModelManager).
- `maxMessagesPerRun:Int` (default 50).
- `confidenceThreshold:Double` (default 0.0 — include all model-confirmed txns; expose a slider 0–1 to hide low-confidence ones from analytics without deleting).

Changing the interval calls `WorkScheduler.reschedule(intervalMinutes, constraints)` which enqueues a `PeriodicWorkRequest` with `ExistingPeriodicWorkPolicy.UPDATE`.

---

## 8. Model Delivery (`ModelManager`)

The 475 MB model is too large for the APK. On first run (or model switch), **download from Hugging Face** to `getExternalFilesDir(null)`:
`https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm`
Show a progress UI in onboarding. Verify final size matches expected (~497,664,000 bytes) before marking ready. Requires `INTERNET` permission (download only — all inference stays offline). For development, allow skipping download if the file was `adb push`ed already. `ModelSpec` registry maps `modelId → {filename, url, expectedBytes, needsNoThink, npuVariants}`.

---

## 9. UI / UX Spec (build beautiful — Material 3)

Use `frontend-design:frontend-design`. Material 3 + dynamic color, but with a clear **fintech identity**: credit = green, debit = amber/red, monospace for amounts, rounded cards, smooth motion, dark-mode first. Charts via **Vico**.

**Screens & acceptance criteria:**
- **Onboarding / Permissions:** explains privacy ("everything stays on your phone"), requests `RECEIVE_SMS`+`READ_SMS` (and `POST_NOTIFICATIONS` on API 33+), offers "Import existing messages" and triggers model download with progress. *Accept:* app is unusable until model ready + permission granted; graceful denial state.
- **Dashboard (home):** period selector (This month / Last month / Custom range); summary cards (Spent, Received, Net) with monospace amounts; charts — spend-over-time (area/line), category breakdown (donut, tap a slice to filter), debit-vs-credit, top counterparties (bar). Recent transactions preview. A status strip: pending count, last run time, **Process now** button. *Accept:* all analytics exclude `includedInAnalytics=false` rows; empty state when no data; updates reactively via Flow.
- **Transactions:** date-grouped list; row = category icon, counterparty, amount (colored by direction), category chip, confidence dot. Search (counterparty/body) + filters (category, direction, date range, min confidence). Tap → detail. *Accept:* search/filter are reactive; large lists scroll smoothly (paging or lazy).
- **Transaction detail:** all fields + raw SMS + model/backend/confidence; actions: **Edit category** (picker from the 20), **Edit amount/direction/date/counterparty** (sets `userEdited=true`), **Mark as not a transaction** (sets `includedInAnalytics=false`, status `NON_TXN`), **Re-verify** (status `NEEDS_REVERIFY` + toast "queued"). *Accept:* edits persist and analytics update; re-verify visibly re-queues.
- **Settings:** interval slider (15–240 min), charging/battery constraints, backend preference, model swap + re-download, confidence threshold, export CSV, privacy note. *Accept:* changing interval reschedules; export produces a valid CSV of transactions.

---

## 10. Task Breakdown

> Each phase ends with a green build + commit. Pure-logic tasks use TDD (JUnit, JVM). Run JVM tests with: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" android/gradlew -p android :app:testDebugUnitTest`. Build APK with `:app:assembleDebug`. Instrumented/manual steps note the device action.

### Phase 0 — Project upgrade & dependencies

**Files:** Modify `android/app/build.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml` (create), `android/app/src/main/AndroidManifest.xml`, create `App.kt`.

- [ ] **0.1** Add plugins/deps: `com.google.dagger.hilt.android` (2.52), `org.jetbrains.kotlin.plugin.serialization` (2.3.0), `androidx.room` (2.6.1) + KSP (`com.google.devtools.ksp` 2.3.0-x), `androidx.work:work-runtime-ktx` (2.9.1), `androidx.hilt:hilt-work` (1.2.0), `androidx.datastore:datastore-preferences` (1.1.1), `com.patrykandpatrick.vico:compose-m3` (2.0.x), `org.jetbrains.kotlinx:kotlinx-serialization-json` (1.7.x), test deps `junit`, `kotlinx-coroutines-test`, `androidx.room:room-testing`, `org.robolectric:robolectric`. Keep `litertlm-android:0.13.1`. Enable KSP for Room + Hilt.
- [ ] **0.2** Add permissions to manifest: `RECEIVE_SMS`, `READ_SMS`, `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (to reschedule after reboot). Add `App : Application(), Configuration.Provider` with Hilt (`@HiltAndroidApp`) and a `HiltWorkerFactory`.
- [ ] **0.3** Create the package folders from §3. Build: `:app:assembleDebug` → expect SUCCESS. Commit `chore: scaffold full app deps + DI`.

### Phase 1 — Domain core + gate + prompt + parser (TDD)

**Files:** Create `domain/Category.kt`, `domain/ProcessingStatus.kt`, `domain/TxnDirection.kt`, `domain/ExtractionResult.kt`, `domain/ModelSpec.kt`, `gate/RegexGate.kt`, `llm/PromptBuilder.kt`, `llm/ExtractionParser.kt`. Tests under `android/app/src/test/java/com/local/smsllm/`.

- [ ] **1.1 Category enum.** Define `enum class Category(val id:String, val label:String, val emoji:String)` with the 20 rows from §1, plus `companion fun fromId(id:String?): Category?`. Test: `fromId("food")==FOOD`, `fromId("nope")==null`, `entries.size==20`.
- [ ] **1.2 ProcessingStatus / TxnDirection enums.** As in §4. Test round-trip `valueOf`.
- [ ] **1.3 RegexGate (high recall).**
  - Test cases (write first, must fail): HDFC debit → pass; ICICI credit → pass; OTP → reject; promo → reject; UPI debit → pass; "EMI due" reminder → pass (gate is permissive; model decides); empty body → reject.
  - Implement: `passes(sender:String, body:String):Boolean` = `hasAmount(body) && hasKeyword(body)`.
    - `AMOUNT = Regex("(?i)(?:rs\\.?|inr|₹)\\s?\\d[\\d,]*(?:\\.\\d{1,2})?")`
    - `KEYWORDS = Regex("(?i)\\b(debited|credited|debit|credit|spent|withdrawn|withdrawal|paid|payment|sent|received|txn|transaction|purchase|a/c|upi|imps|neft|rtgs)\\b")`
  - Keep deliberately permissive — recall over precision; the LLM filters false positives.
- [ ] **1.4 ExtractionParser (parse + repair).**
  - `data class ExtractionResult(isTransaction, direction:TxnDirection?, amount:Double?, currency:String?, dateText:String?, counterparty:String?, category:Category?, confidence:Double, raw:String)`.
  - `fun parse(modelOutput:String): ExtractionResult`:
    1. Strip any `<think>…</think>` block and markdown fences.
    2. Extract the first `{…}` substring; `Json { ignoreUnknownKeys = true; isLenient = true }` decode.
    3. **Repair** common small-model glitches before decode: collapse `,""` → `,"`, `""` → `"`, trailing commas, smart-quotes → `"`. (Gemma's int4 build emitted `"debit,""amount"` — repair handles it.)
    4. Coerce: amount string→Double (strip commas/symbols); map category via `Category.fromId`; map direction via enum; clamp confidence to 0..1; if `isTransaction` false, null the other fields.
    5. On total failure return `ExtractionResult(isTransaction=false, …, confidence=0, raw=modelOutput)` and let caller mark `ERROR`.
  - Tests: the 3 example outputs from §2 parse correctly; the malformed `{"direction":"debit,""amount":2499,…}` repairs to a valid debit; `<think>\n\n</think>\n{…}` parses; garbage → safe default.
- [ ] **1.5** Run all Phase-1 tests green. Commit `feat: domain core, regex gate, prompt, robust parser (TDD)`.

### Phase 2 — Data layer (Room)

**Files:** Create entities, DAOs, `AppDatabase.kt`, `Converters.kt`, repositories. Tests: instrumented or Robolectric DAO tests.

- [ ] **2.1** `SmsMessageEntity` + `TransactionEntity` per §4 (annotate FK + indices).
- [ ] **2.2** `SmsDao`: `insert`, `insertIgnore` (for dedupe), `pendingForProcessing(limit:Int): List<SmsMessageEntity>` (status in PENDING/NEEDS_REVERIFY/ERROR), `setStatus(id, status, processedAt, error)`, `countPending(): Flow<Int>`. `TransactionDao`: `upsertForSms(smsId, …)`, `observeAll(): Flow<List<TransactionEntity>>`, analytics queries (`sumByDirection`, `byCategory`, `byMonth`) all filtered `WHERE includedInAnalytics=1`, `setCategory`, `setExcluded`, `markUserEdited`.
- [ ] **2.3** `AppDatabase` (Room, version 1, exportSchema true). Hilt-provide a singleton.
- [ ] **2.4** `SmsRepository`, `TransactionRepository` wrapping DAOs with domain types.
- [ ] **2.5** DAO test: insert sms → pendingForProcessing returns it; setStatus PROCESSED removes it from pending; dedupe on (sender,body,receivedAt); analytics excludes `includedInAnalytics=0`. Commit `feat: Room data layer + repositories`.

### Phase 3 — LLM service

**Files:** `llm/LlmService.kt`, `llm/LiteRtLmService.kt`, `llm/BackendSelector.kt`, `llm/ModelManager.kt`. (Refactor the spike's `LlmEngine.kt` into these; then delete `LlmEngine.kt`.)

- [ ] **3.1** `interface LlmService { suspend fun ensureLoaded(pref:BackendChoice); suspend fun extract(sms:String): ExtractionResult; fun loadedBackend():String?; fun close() }`.
- [ ] **3.2** `LiteRtLmService` impl: `ensureLoaded` uses `BackendSelector.open(modelPath, cacheDir, pref)`; `extract` creates a `ConversationConfig(systemInstruction = Contents.of(PromptBuilder.systemInstruction(spec)), samplerConfig = SamplerConfig(topK=1, topP=0.95, temperature=0.1) [, enableConstrainedDecoding=true if available])`, `engine.createConversation(cfg).use { sendMessage(sms) }`, reads text via `message.contents.contents.filterIsInstance<Content.Text>().joinToString(""){it.text}` (proven in spike), then `ExtractionParser.parse`. **Run on `Dispatchers.Default`.**
- [ ] **3.3** `BackendSelector.open` per §5 (try NPU→GPU→CPU on AUTO, catch + fall back, return backend name).
- [ ] **3.4** `ModelManager` per §8 (resolve model file path, download with progress Flow, size check, `isReady():Boolean`).
- [ ] **3.5 Manual verify (REAL PHONE):** reuse the spike's autorun pattern — temporary instrumented test or `--ez autorun true` that loads via `LiteRtLmService` and logs an extraction for the §2 examples; confirm clean JSON + a backend was selected. Emulator is acceptable only for a compile/smoke check; the backend selection and timing numbers that matter must come from the physical device. Commit `feat: LiteRT-LM service + backend auto-select + model manager`.

### Phase 4 — SMS ingestion

**Files:** `sms/SmsReceiver.kt`, `sms/SmsImporter.kt`, permission UI hook in onboarding.

- [ ] **4.1** `SmsReceiver : BroadcastReceiver` registered in manifest for `SMS_RECEIVED` with `RECEIVE_SMS`. In `onReceive`: extract messages, concat multipart, run `RegexGate`, insert via `SmsRepository` (use `goAsync()` for the DB write), then `WorkScheduler.ensureScheduled()`.
- [ ] **4.2** `SmsImporter.importInbox()` via `ContentResolver` over `Telephony.Sms.Inbox` (columns ADDRESS, BODY, DATE); gate + bulk `insertIgnore`. Bound to a coroutine; report progress.
- [ ] **4.3 Manual verify:** `adb emu sms send <num> "<bank sms>"` → row appears with correct gate decision; run import → existing inbox populated, no duplicates. Commit `feat: SMS receiver + inbox import with regex gating`.

### Phase 5 — Background processing

**Files:** `work/ExtractionWorker.kt`, `work/WorkScheduler.kt`, `repo/SettingsRepository.kt`.

- [ ] **5.1** `SettingsRepository` (DataStore) per §7.
- [ ] **5.2** `ExtractionWorker : CoroutineWorker` (Hilt `@HiltWorker`): query pending (limit = `maxMessagesPerRun`); **if empty → `Result.success()` (no model load)**; else `llmService.ensureLoaded(pref)`, loop sequentially → parse → upsert txn (`includedInAnalytics = result.isTransaction`) → set SMS status; `finally { llmService.close() }`; update `lastRunAt`. Return `Result.retry()` on transient load failure.
- [ ] **5.3** `WorkScheduler`: `ensureScheduled()` (enqueue unique periodic with `KEEP`), `reschedule(minutes, constraints)` (`UPDATE`), `runNow()` (one-time expedited). Clamp interval ≥15. Constraints from settings.
- [ ] **5.4** Reschedule on boot: a `BOOT_COMPLETED` receiver calling `ensureScheduled()`.
- [ ] **5.5 Manual verify:** seed pending messages, `runNow()`, confirm transactions written, model released, pending drained; with zero pending confirm the model is NOT loaded (no LiteRT logs). Commit `feat: scheduled batch extraction worker (load-once, skip-if-empty)`.

### Phase 6 — UI (use frontend-design)

**Files:** `ui/theme/*`, `ui/nav/*`, `ui/onboarding/*`, `ui/dashboard/*`, `ui/transactions/*`, `ui/detail/*`, `ui/settings/*`, `ui/components/*`, ViewModels per screen.

- [ ] **6.1** Theme/design system (§9 identity): colors (credit-green, debit-amber/red), monospace amount style, M3 dynamic color, dark-first.
- [ ] **6.2** Nav graph + scaffold (bottom nav: Dashboard / Transactions / Settings) + onboarding gate (block until permission + model ready).
- [ ] **6.3** Onboarding/permissions + model download progress.
- [ ] **6.4** Dashboard + Vico charts + summary cards + status strip + "Process now".
- [ ] **6.5** Transactions list + search/filters.
- [ ] **6.6** Transaction detail + edit/exclude/re-verify actions.
- [ ] **6.7** Settings (interval slider reschedules; backend pref; model swap; CSV export).
- [ ] **6.8 Verify on device** end to end (Phase 7). Commit per screen.

### Phase 7 — End-to-end verification & polish (ON THE REAL PHONE)

> All functional/acceptance testing in this phase runs on a **physical Android phone over USB debugging**, never the emulator (see Non-Negotiables). The emulator is only for earlier compile/smoke checks.

- [ ] **7.1** CSV export of transactions.
- [ ] **7.2** On the **real budget phone** (USB debugging): grant permissions, download model, import inbox, send a live test SMS (actually text the phone), confirm capture → scheduled run → classification → analytics; measure model load time, tok/s, RAM, and battery over several real scheduled runs. Record the numbers.
- [ ] **7.3** Accuracy pass: collect ~30 real (redacted) SMS, run the Mac harness (`/tmp/clitertlm/harness.c`) to iterate the prompt cheaply; tune examples/rules until `is_transaction` and category accuracy are acceptable; mirror final prompt into `PromptBuilder`.
- [ ] **7.4** If 0.6B accuracy is insufficient, switch `modelId` to `Gemma3-1B-IT` via `ModelManager` (one config change) and re-measure on the real phone — the architecture is model-agnostic.
- [ ] **7.5 Security review:** run through every item in §13 and fix anything that fails before declaring done. Optionally run the `security-review` skill on the diff.

---

## 11. Open Items to Verify During Build (don't assume)
- Exact Kotlin name for **constrained decoding** in `ConversationConfig` 0.13.1 (C API confirms the feature exists; Kotlin property may be `enableConstrainedDecoding`). If absent, rely on `ExtractionParser` repair.
- Whether `SamplerConfig` on-device prefers TopK vs TopP for stable JSON (spike used `topK=1`; A/B if output is unstable).
- KSP/AGP/Kotlin 2.3.0 version alignment for Room + Hilt (pick matching KSP `2.3.0-x`).
- WorkManager expedited one-time quota on the target OEM (for "Process now"); fall back to a normal one-time request if expedited is throttled.

## 13. Security & Privacy Hardening (mandatory — no vulnerabilities)

This app reads SMS, some of the most sensitive data on a phone. The build must ship with **zero security vulnerabilities**. Every item below is a requirement, not a suggestion; treat a failure as a build failure (and verify in Task 7.5).

**Data stays on-device:**
- No analytics/telemetry/crash-reporting SDKs. The **only** network call permitted is the one-time model download from Hugging Face over HTTPS (§8); after that the app must function fully in airplane mode.
- Never log SMS bodies, parsed amounts, counterparties, or the model's raw output at any level shipped to release. The spike's `SPIKE`/Logcat output and the `--ez autorun` benchmark are **debug-only** — strip or guard them behind `BuildConfig.DEBUG` before release.
- No `INTERNET`-based backups of the DB. Set `android:allowBackup="false"` and `android:dataExtractionRules`/`fullBackupContent` to exclude the database and model from cloud/ADB backup so SMS-derived data can't leak via backup.

**Storage & secrets:**
- The Room DB lives in app-internal storage; consider SQLCipher (passphrase via Android Keystore) as a follow-up, but at minimum keep it in internal storage (not external/shared) and out of backups.
- The model file may sit in `getExternalFilesDir` (app-scoped) — acceptable (it's a public model, not user data). User data (DB, DataStore) must be internal only.
- No hardcoded secrets/tokens. The HF model URL is public; no auth token in the app.

**Components & surface area:**
- `SmsReceiver` must require the `BROADCAST_SMS` system permission via `android:permission="android.permission.BROADCAST_SMS"` on the receiver so only the OS can deliver to it. All other components `android:exported="false"` unless they truly need to be exported (only the launcher activity is exported).
- No exported `ContentProvider`/`Service` exposing transactions. WorkManager's components stay internal.
- Validate/contain all model output: treat it as untrusted text. Parse defensively (already covered by `ExtractionParser`); never `eval`/render it as HTML/JS; cap output tokens.
- Use explicit `Intent`s only; mark `PendingIntent`s `FLAG_IMMUTABLE`.

**Network (model download only):**
- HTTPS only; reject cleartext (`android:usesCleartextTraffic="false"`, add a network-security-config that disallows cleartext). Verify the downloaded file size (and ideally a known hash) before use.

**Permissions:**
- Request only `RECEIVE_SMS`, `READ_SMS`, `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. No `SEND_SMS`, no `CALL_LOG`, no location, no contacts. Request SMS perms at runtime with a clear rationale screen; the app degrades gracefully if denied.

**Build hygiene:**
- Enable R8/minify + resource shrinking for release; keep rules minimal.
- Keep dependencies current (no known-CVE versions); pin versions.
- This is a **personal sideload** build — do not add anything that would require Google Play SMS-permission declarations, and document that publishing would need that review.

## 14. Acceptance Criteria (definition of done)
1. Fresh install → onboarding grants SMS perms + downloads model; inbox import populates history.
2. A live bank SMS is captured, and within one scheduled run is extracted into a correct transaction; OTP/promo never become transactions.
3. A regex false-positive that the model rejects is stored but **excluded from analytics**.
4. With no pending messages, a scheduled run does **not** load the model (verified via logs/battery).
5. User can change the interval (≥15 min), mark a txn "not a transaction", edit a category, and re-verify (re-queues + reprocesses).
6. Dashboard analytics, transaction list, search/filter all work and stay reactive; everything runs offline after model download.
7. Backend is auto-selected (NPU→GPU→CPU) and recorded per transaction; user can override.
8. **All functional, performance, and acceptance testing was done on a real physical phone**, not the emulator; load time / tok/s / RAM / battery numbers are recorded from the device.
9. **Security: every item in §13 holds** — no telemetry, airplane-mode-functional after download, no sensitive logging in release, `allowBackup=false`, receiver protected by `BROADCAST_SMS`, non-launcher components not exported, cleartext disabled, minimal permissions. (Run `security-review` on the diff.)

## 15. Distribution & Open-Source Release

This app cannot be published on the Google Play Store (Play restricts `READ_SMS`/`RECEIVE_SMS` to default-SMS-handler use cases; a transaction parser is not eligible — regardless of quality). That restriction only applies to the Play Store, so distribution is via **self-hosted, open-source sideloading**. Being open-source is also a trust feature for an app that reads SMS (§13).

**Repository:** MIT-licensed, hosted on GitHub. Code is fully open so anyone can audit that it does nothing off-device. Keep the repo clean (see `.gitignore`): never commit build outputs, `local.properties`, or signing secrets.

**Signed release build (required for distribution & updates):**
- Generate a release keystore once: `keytool -genkeypair -v -keystore release.keystore -alias smsllm -keyalg RSA -keysize 2048 -validity 10000`.
- **Keep `release.keystore` and passwords OUT of git.** Put them in a git-ignored `android/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and read it in `app/build.gradle.kts` `signingConfigs`; fall back to debug signing if the file is absent (so contributors can still build).
- Build: `./gradlew assembleRelease` (APK for direct download). Enable R8/minify + resource shrinking (§13).
- **Sign every release with the SAME key** so updates install over previous versions and users can trust authorship.

**Versioning:** semantic `versionName` (e.g. `1.2.0`) + monotonically increasing `versionCode`. Tag releases `vX.Y.Z`.

**Channel 1 — GitHub Releases (primary):** create a GitHub Release per tag, attach the signed `app-release.apk` and its `SHA-256` checksum, and write release notes. Users download the APK and install (Android prompts for "install from unknown sources" once per source, then the normal SMS runtime permission). Because the 475 MB model is **downloaded at runtime** (§8), the APK stays small (~tens of MB).

**Channel 2 — Obtainium (recommended for auto-updates):** users add the GitHub repo URL in [Obtainium](https://github.com/ImranR98/Obtainium); it polls Releases and auto-installs new signed APKs. Zero server cost, gives a Play-like update experience.

**Channel 3 — F-Droid (optional, max trust):** F-Droid builds from source and is the gold standard for FOSS Android apps. **Caveat:** F-Droid's main repo requires a 100% FOSS dependency tree, and the **LiteRT-LM AAR (`com.google.ai.edge.litertlm`) is a closed-source Google binary**, which will likely disqualify it from the official F-Droid repo. Options: (a) ship via GitHub + Obtainium (works today), or (b) run a **self-hosted F-Droid repo** (`fdroidserver`) which has no such purity requirement. Document this; don't block on official F-Droid.

**User-facing notes to include in the README:**
- How to enable "install from unknown sources" and that **Play Protect may warn** about an unverified/SMS app — expected for sideloaded apps; the source is auditable.
- The privacy promise: everything stays on the phone; the only network use is the one-time model download.

**Do not** add any distribution mechanism that re-introduces Play Store SMS-policy exposure or that requires uploading user data anywhere.
