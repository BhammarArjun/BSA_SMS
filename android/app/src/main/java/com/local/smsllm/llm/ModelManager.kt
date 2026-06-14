package com.local.smsllm.llm

import android.content.Context
import com.local.smsllm.domain.ModelSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Progress events emitted by [ModelManager.download]. */
sealed interface DownloadProgress {
    /** Download in progress: [bytes] received so far out of [total] (-1 if unknown). */
    data class Progress(val bytes: Long, val total: Long) : DownloadProgress

    /** Download completed and size verified; model file is now ready. */
    data object Done : DownloadProgress

    /** Download or verification failed with [error] message; temp file cleaned up. */
    data class Failed(val error: String) : DownloadProgress
}

/**
 * Manages the on-device model file lifecycle: path resolution, readiness check, download.
 *
 * Model files are stored in app-scoped external storage ([Context.getExternalFilesDir]).
 * The model weights are a public HuggingFace asset — not user data — so external storage
 * is appropriate (avoids filling internal storage with a ~500 MB file).
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns the [File] where [spec] is (or will be) stored. */
    fun modelFile(spec: ModelSpec): File =
        File(context.getExternalFilesDir(null), spec.filename)

    /**
     * Returns true when the model file exists on disk and its size matches [ModelSpec.expectedBytes].
     * Size mismatch indicates a partial/corrupt download.
     */
    fun isReady(spec: ModelSpec): Boolean {
        val file = modelFile(spec)
        return file.exists() && file.length() == spec.expectedBytes
    }

    /**
     * Returns the absolute path of the model file.
     * @throws IllegalStateException if the model is not ready (file missing or wrong size).
     */
    fun resolveModelPath(spec: ModelSpec): String {
        check(isReady(spec)) { "Model not downloaded: ${spec.filename}" }
        return modelFile(spec).absolutePath
    }

    /**
     * Downloads [spec] from its HTTPS URL to disk, emitting [DownloadProgress] events.
     *
     * Security:
     * - HTTPS only: throws if the URL scheme is not "https".
     * - Downloads to a temp file; renames to the final filename only after verifying
     *   the downloaded size matches [ModelSpec.expectedBytes].
     * - No HTTP library — uses [java.net.HttpURLConnection] from the JDK (for an https URL
     *   the JDK returns an [javax.net.ssl.HttpsURLConnection] subtype at runtime).
     *
     * Runs on [Dispatchers.IO].
     */
    fun download(spec: ModelSpec): Flow<DownloadProgress> = flow {
        val url = URL(spec.url)
        require(url.protocol == "https") {
            "Refusing to download over non-HTTPS URL (got: ${url.protocol})"
        }

        val destFile = modelFile(spec)
        val tempFile = File(destFile.parent, "${spec.filename}.tmp")

        // Ensure the parent directory exists.
        destFile.parentFile?.mkdirs()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                emit(DownloadProgress.Failed("HTTP $responseCode from ${spec.url}"))
                return@flow
            }

            val total = conn.contentLengthLong // -1 if unknown

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var received = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        received += read
                        emit(DownloadProgress.Progress(received, total))
                    }
                }
            }

            // Verify size before committing.
            val downloadedBytes = tempFile.length()
            if (downloadedBytes != spec.expectedBytes) {
                tempFile.delete()
                emit(
                    DownloadProgress.Failed(
                        "Size mismatch: expected ${spec.expectedBytes} bytes, " +
                            "got $downloadedBytes bytes. File deleted."
                    )
                )
                return@flow
            }

            // Atomic rename — replaces any stale file.
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.delete()
                emit(DownloadProgress.Failed("Failed to rename temp file to ${destFile.name}"))
                return@flow
            }

            emit(DownloadProgress.Done)
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadProgress.Failed("Download error: ${e.message}"))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
