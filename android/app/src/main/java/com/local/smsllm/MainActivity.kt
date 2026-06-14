package com.local.smsllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.local.smsllm.llm.BackendChoice
import com.local.smsllm.llm.BackendSelector
import com.local.smsllm.llm.LiteRtLmService
import com.local.smsllm.llm.ModelManager
import com.local.smsllm.ui.nav.AppNav
import com.local.smsllm.ui.theme.SmsLlmTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ── Debug perf harness (SPIKE benchmark) ─────────────────────────────────
    // Manually constructed here so it compiles independently of Hilt injection.
    // Only active in DEBUG builds via BuildConfig.DEBUG guard.
    // Activated by: am start ... --ez autorun true [--ez gpu true]
    // Logs to Logcat tag "SPIKE".
    private val benchModelManager by lazy { ModelManager(this) }
    private val benchBackendSelector by lazy { BackendSelector(this) }
    private val benchLlm by lazy { LiteRtLmService(this, benchModelManager, benchBackendSelector) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── DEBUG-only headless benchmark ─────────────────────────────────────
        // `am start -n com.local.smsllm/.MainActivity --ez autorun true [--ez gpu true]`
        // Loads the model and runs every SampleData entry; results logged to "SPIKE".
        // Stripped from release builds via BuildConfig.DEBUG.
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("autorun", false) == true) {
            val modelFile = File(getExternalFilesDir(null), SampleData.MODEL_FILENAME)
            val useGpu = intent?.getBooleanExtra("gpu", false) == true
            val backendPref = if (useGpu) BackendChoice.GPU else BackendChoice.CPU
            lifecycleScope.launch {
                runCatching {
                    val t0 = System.currentTimeMillis()
                    benchLlm.ensureLoaded(backendPref)
                    val loadMs = System.currentTimeMillis() - t0
                    android.util.Log.i(
                        "SPIKE",
                        "LOADED backend=${benchLlm.loadedBackend()} loadMs=$loadMs sizeMB=${modelFile.length() / (1024 * 1024)}"
                    )
                    SampleData.SAMPLES.forEachIndexed { i, sms ->
                        val t1 = System.currentTimeMillis()
                        val result = benchLlm.extract(sms)
                        val genMs = System.currentTimeMillis() - t1
                        val approxTokens = (result.raw.length / 4).coerceAtLeast(1)
                        val tokps = if (genMs > 0) approxTokens * 1000.0 / genMs else 0.0
                        android.util.Log.i(
                            "SPIKE",
                            "SAMPLE#$i genMs=$genMs tokps=%.1f json=%s".format(
                                tokps,
                                result.raw.replace("\n", " ")
                            )
                        )
                    }
                    android.util.Log.i("SPIKE", "DONE")
                }.onFailure {
                    android.util.Log.e("SPIKE", "FAIL: ${it.message}", it)
                }
            }
        }
        // ── End debug harness ─────────────────────────────────────────────────

        setContent {
            SmsLlmTheme {
                AppNav()
            }
        }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            runCatching { benchLlm.close() }
        }
        super.onDestroy()
    }
}
