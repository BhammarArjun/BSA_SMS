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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val llm = LlmEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val modelFile = File(getExternalFilesDir(null), SampleData.MODEL_FILENAME)
        val cacheDir = cacheDir.absolutePath

        // Headless benchmark: `am start ... --ez autorun true [--ez gpu true]`
        // Loads the model and runs every sample, logging to Logcat tag "SPIKE".
        if (intent?.getBooleanExtra("autorun", false) == true) {
            val useGpu = intent?.getBooleanExtra("gpu", false) == true
            lifecycleScope.launch {
                runCatching {
                    llm.load(modelFile.absolutePath, cacheDir, useGpu)
                    android.util.Log.i("SPIKE", "LOADED backend=${llm.loadedBackend} loadMs=${llm.loadMs} sizeMB=${modelFile.length() / (1024 * 1024)}")
                    SampleData.SAMPLES.forEachIndexed { i, sms ->
                        val r = llm.extract(sms)
                        android.util.Log.i("SPIKE", "SAMPLE#$i genMs=${r.genMs} tokps=%.1f json=%s".format(r.tokensPerSec, r.text.replace("\n", " ")))
                    }
                    android.util.Log.i("SPIKE", "DONE")
                }.onFailure { android.util.Log.e("SPIKE", "FAIL: ${it.message}", it) }
            }
        }

        setContent {
            MaterialTheme {
                SpikeScreen(
                    modelPath = modelFile.absolutePath,
                    modelExists = modelFile.exists(),
                    modelSizeMb = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0,
                    onLoad = { useGpu, onResult ->
                        lifecycleScope.launch {
                            runCatching { llm.load(modelFile.absolutePath, cacheDir, useGpu) }
                                .onSuccess { onResult("Model loaded on ${llm.loadedBackend} in ${llm.loadMs} ms", true) }
                                .onFailure { onResult("LOAD ERROR: ${it.message}", false) }
                        }
                    },
                    onRun = { sms, onResult ->
                        lifecycleScope.launch {
                            runCatching { llm.extract(sms) }
                                .onSuccess { r ->
                                    onResult(
                                        buildString {
                                            appendLine(r.text)
                                            appendLine()
                                            appendLine("— gen ${r.genMs} ms · ~${r.approxTokens} tok · ~%.1f tok/s".format(r.tokensPerSec))
                                        }
                                    )
                                }
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
}

@Composable
private fun SpikeScreen(
    modelPath: String,
    modelExists: Boolean,
    modelSizeMb: Long,
    onLoad: (useGpu: Boolean, onResult: (String, Boolean) -> Unit) -> Unit,
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
            onLoad(false) { msg, ok -> status = msg; loaded = ok; busy = false }
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
                        onLoad(false) { msg, ok -> status = msg; loaded = ok; busy = false }
                    },
                ) { Text("Load CPU") }
                OutlinedButton(
                    enabled = modelExists && !busy,
                    onClick = {
                        busy = true; loaded = false; status = "Loading on GPU…"
                        onLoad(true) { msg, ok -> status = msg; loaded = ok; busy = false }
                    },
                ) { Text("Load GPU") }
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
