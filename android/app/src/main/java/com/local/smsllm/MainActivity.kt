package com.local.smsllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.local.smsllm.domain.ExtractionResult
import com.local.smsllm.llm.BackendChoice
import com.local.smsllm.llm.BackendSelector
import com.local.smsllm.llm.LiteRtLmService
import com.local.smsllm.llm.ModelManager
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    // Manual construction for the throwaway spike harness — Hilt injection into the Activity
    // is skipped intentionally; this Activity is a temporary benchmark screen.
    private val modelManager by lazy { ModelManager(this) }
    private val backendSelector by lazy { BackendSelector(this) }
    private val llm by lazy { LiteRtLmService(this, modelManager, backendSelector) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val modelFile = File(getExternalFilesDir(null), SampleData.MODEL_FILENAME)
        val cacheDir = cacheDir.absolutePath

        // Headless benchmark: `am start ... --ez autorun true [--ez gpu true]`
        // Loads the model and runs every sample, logging to Logcat tag "SPIKE".
        // Stripped from release builds via BuildConfig.DEBUG.
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("autorun", false) == true) {
            val useGpu = intent?.getBooleanExtra("gpu", false) == true
            val backendPref = if (useGpu) BackendChoice.GPU else BackendChoice.CPU
            lifecycleScope.launch {
                runCatching {
                    val t0 = System.currentTimeMillis()
                    llm.ensureLoaded(backendPref)
                    val loadMs = System.currentTimeMillis() - t0
                    android.util.Log.i(
                        "SPIKE",
                        "LOADED backend=${llm.loadedBackend()} loadMs=$loadMs sizeMB=${modelFile.length() / (1024 * 1024)}"
                    )
                    SampleData.SAMPLES.forEachIndexed { i, sms ->
                        val t1 = System.currentTimeMillis()
                        val result = llm.extract(sms)
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

        setContent {
            MaterialTheme {
                SpikeScreen(
                    modelPath = modelFile.absolutePath,
                    modelExists = modelFile.exists(),
                    modelSizeMb = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0,
                    onLoad = { backendPref, onResult ->
                        lifecycleScope.launch {
                            val t0 = System.currentTimeMillis()
                            runCatching { llm.ensureLoaded(backendPref) }
                                .onSuccess {
                                    val loadMs = System.currentTimeMillis() - t0
                                    onResult("Model loaded on ${llm.loadedBackend()} in $loadMs ms", true)
                                }
                                .onFailure { onResult("LOAD ERROR: ${it.message}", false) }
                        }
                    },
                    onRun = { sms, onResult ->
                        lifecycleScope.launch {
                            runCatching { llm.extract(sms) }
                                .onSuccess { result -> onResult(formatResult(result)) }
                                .onFailure { onResult("RUN ERROR: ${it.message}") }
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        llm.close()
        super.onDestroy()
    }

    private fun formatResult(result: ExtractionResult): String = buildString {
        appendLine(result.raw)
        appendLine()
        appendLine("— transaction=${result.isTransaction}")
        if (result.isTransaction) {
            appendLine("  direction=${result.direction} amount=${result.amount} ${result.currency}")
            appendLine("  date=${result.dateText} counterparty=${result.counterparty}")
            appendLine("  category=${result.category} confidence=${"%.2f".format(result.confidence)}")
        }
    }
}

@Composable
private fun SpikeScreen(
    modelPath: String,
    modelExists: Boolean,
    modelSizeMb: Long,
    onLoad: (backendPref: BackendChoice, onResult: (String, Boolean) -> Unit) -> Unit,
    onRun: (sms: String, onResult: (String) -> Unit) -> Unit,
) {
    var status by remember { mutableStateOf(if (modelExists) "Model file found (${modelSizeMb} MB)." else "MODEL MISSING — adb push it first.") }
    var output by remember { mutableStateOf("—") }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }

    // Auto-load on first launch so the user doesn't have to tap Load before Extract.
    LaunchedEffect(modelExists) {
        if (modelExists && !loaded && !busy) {
            busy = true; status = "Auto-loading model on CPU…"
            onLoad(BackendChoice.CPU) { msg, ok -> status = msg; loaded = ok; busy = false }
        }
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Qwen3-0.6B · LiteRT-LM spike", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(modelPath, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(status, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = modelExists && !busy,
                    onClick = {
                        busy = true; loaded = false; status = "Loading on CPU…"
                        onLoad(BackendChoice.CPU) { msg, ok -> status = msg; loaded = ok; busy = false }
                    },
                ) { Text("Load CPU") }
                OutlinedButton(
                    enabled = modelExists && !busy,
                    onClick = {
                        busy = true; loaded = false; status = "Loading on GPU…"
                        onLoad(BackendChoice.GPU) { msg, ok -> status = msg; loaded = ok; busy = false }
                    },
                ) { Text("Load GPU") }
                OutlinedButton(
                    enabled = modelExists && !busy,
                    onClick = {
                        busy = true; loaded = false; status = "Loading AUTO (NPU→GPU→CPU)…"
                        onLoad(BackendChoice.AUTO) { msg, ok -> status = msg; loaded = ok; busy = false }
                    },
                ) { Text("Load AUTO") }
            }

            Text("Sample SMS", fontWeight = FontWeight.SemiBold)
            SampleData.SAMPLES.forEachIndexed { i, sms ->
                val isSel = i == selected
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selected = i },
                ) {
                    Text(
                        (if (isSel) "▶ " else "   ") + sms,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Button(
                enabled = loaded && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true; output = "Running…"
                    onRun(SampleData.SAMPLES[selected]) { output = it; busy = false }
                },
            ) { Text(if (!loaded) "Load model first…" else if (busy) "Working…" else "Extract → JSON") }

            if (busy) {
                Row { CircularProgressIndicator() }
            }

            Spacer(Modifier.height(4.dp))
            Text("Output", fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    output,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
