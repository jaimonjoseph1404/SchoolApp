package org.familytools.educationtracker.services

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/** One on-device LLM used for AI-assisted report scanning. [huggingFaceRepo]
 * + [huggingFaceFile] point at the community LiteRT-LM conversion on
 * Hugging Face (verified public, ungated) — [downloadUrl] is built from
 * those two, never hardcoded, so the source is traceable/auditable. */
enum class AiModel(
    val label: String,
    val fileName: String,
    val huggingFaceRepo: String,
    val huggingFaceFile: String,
    val approxSizeBytes: Long,
) {
    TEXT(
        label = "Text model (Qwen3 0.6B)",
        fileName = "qwen3-0.6b.litertlm",
        huggingFaceRepo = "litert-community/Qwen3-0.6B",
        huggingFaceFile = "Qwen3-0.6B.litertlm",
        approxSizeBytes = 614_236_160L,
    ),
    VISION(
        label = "Vision model (Gemma 4 E2B)",
        fileName = "gemma-4-e2b-vision.litertlm",
        huggingFaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
        huggingFaceFile = "gemma-4-E2B-it.litertlm",
        approxSizeBytes = 2_588_147_712L,
    ),
    ;

    val downloadUrl: String get() = "https://huggingface.co/$huggingFaceRepo/resolve/main/$huggingFaceFile"
}

data class DownloadProgress(
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val status: Int,
    val failedReason: Int? = null,
) {
    val fraction: Float get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    val isDone: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
}

/** Manages the two LiteRT-LM model files: where they live on disk, whether
 * they're present, and the two ways to get them there — manual import via
 * the system file picker (works regardless of network/gating, and lets a
 * model already downloaded by another app be reused), or an in-app download
 * via Android's built-in [DownloadManager] (resumable, survives app kill,
 * no extra dependency needed). Neither path is required for the rest of the
 * app to work — AI scanning is purely additive on top of the existing
 * regex-based OCR parser. */
object AiModelManager {
    private const val MODELS_DIR = "models"
    private const val MIN_VALID_FILE_BYTES = 10_000_000L // guards against a truncated/failed transfer

    fun localFile(context: Context, model: AiModel): File =
        File(context.getExternalFilesDir(MODELS_DIR), model.fileName)

    fun isReady(context: Context, model: AiModel): Boolean {
        val file = localFile(context, model)
        return file.exists() && file.length() >= MIN_VALID_FILE_BYTES
    }

    /** Copies a user-picked file (from [android.content.Intent.ACTION_OPEN_DOCUMENT])
     * into this app's own storage — Android's app sandboxing means another
     * app's private files can't be referenced directly, only read once via
     * the system picker, so this makes a local copy. */
    suspend fun importFromUri(context: Context, model: AiModel, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dest = localFile(context, model)
        val tmp = File(dest.parentFile, "${dest.name}.importing")
        try {
            dest.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { inStream -> tmp.outputStream().use { out -> inStream.copyTo(out) } }
            if (tmp.length() < MIN_VALID_FILE_BYTES) {
                tmp.delete()
                return@withContext false
            }
            tmp.delete() // clear any stale dest first (renameTo can fail if dest exists on some filesystems)
            dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    fun enqueueDownload(context: Context, model: AiModel): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle(model.label)
            .setDescription("Downloading AI model for report scanning")
            .setDestinationInExternalFilesDir(context, MODELS_DIR, model.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
        return dm.enqueue(request)
    }

    /** Polls [DownloadManager] (it has no listener/Flow API of its own) until
     * the download finishes or fails. If the query can't find the row at all
     * (e.g. cleared by the user from the system Downloads UI), reports it as
     * failed rather than hanging forever. */
    fun observeDownloadProgress(context: Context, downloadId: Long): Flow<DownloadProgress> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
            val progress = cursor.use {
                if (it.moveToFirst()) {
                    DownloadProgress(
                        bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        failedReason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    )
                } else {
                    DownloadProgress(0, 0, DownloadManager.STATUS_FAILED)
                }
            }
            emit(progress)
            if (progress.isDone || progress.isFailed) break
            delay(500)
        }
    }
}
