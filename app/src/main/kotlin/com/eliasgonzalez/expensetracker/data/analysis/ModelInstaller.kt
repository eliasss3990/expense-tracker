package com.eliasgonzalez.expensetracker.data.analysis

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class ModelInstallStatus { NOT_INSTALLED, QUEUED, DOWNLOADING, PAUSED, DOWNLOADED, VERIFYING, INSTALLED, FAILED }

data class ModelInstallState(
    val status: ModelInstallStatus,
    val progressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val failureReason: Int? = null,
)

/**
 * Descarga explícita del modelo oficial desde Hugging Face.
 *
 * DownloadManager escribe en el directorio externo privado de la app (sin permisos
 * de almacenamiento). Se limita a Wi-Fi por defecto porque el archivo pesa ~2.59 GB.
 * Antes de marcarlo instalado se verifica SHA-256 contra el archivo fijado por commit.
 */
class ModelInstaller(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val modelFile: File = modelFile(appContext)

    fun enqueueDownload(): Long {
        check(state().status !in setOf(
            ModelInstallStatus.QUEUED,
            ModelInstallStatus.DOWNLOADING,
            ModelInstallStatus.PAUSED,
            ModelInstallStatus.VERIFYING,
            ModelInstallStatus.DOWNLOADED,
            ModelInstallStatus.INSTALLED,
        )) { "Ya existe una descarga o el modelo ya esta instalado" }

        val previousId = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (previousId != NO_DOWNLOAD) manager.remove(previousId)
        modelFile.parentFile?.mkdirs()
        modelFile.delete()
        val availableBytes = StatFs(modelFile.parentFile!!.absolutePath).availableBytes
        check(availableBytes >= MODEL_SIZE_BYTES + REQUIRED_FREE_SPACE_BYTES) {
            "Se necesitan al menos 2.72 GB libres para descargar y verificar el modelo"
        }

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Modelo IA de Expense Tracker")
            .setDescription("Descargando Gemma 4 E2B (aprox. 2.59 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, "models", MODEL_FILE_NAME)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        val id = manager.enqueue(request)
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putBoolean(KEY_INSTALLED, false)
            .putBoolean(KEY_VERIFYING, false)
            .apply()
        return id
    }

    fun isInstalled(): Boolean =
        preferences.getBoolean(KEY_INSTALLED, false) &&
            modelFile.isFile &&
            modelFile.length() == MODEL_SIZE_BYTES

    fun state(): ModelInstallState {
        if (preferences.getBoolean(KEY_INSTALLED, false)) {
            if (isInstalled()) {
                return ModelInstallState(ModelInstallStatus.INSTALLED, 100, modelFile.length(), modelFile.length())
            }
            preferences.edit().putBoolean(KEY_INSTALLED, false).apply()
        }
        if (preferences.getBoolean(KEY_VERIFYING, false)) {
            return ModelInstallState(ModelInstallStatus.VERIFYING)
        }

        val id = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (id == NO_DOWNLOAD) return ModelInstallState(ModelInstallStatus.NOT_INSTALLED)

        val query = DownloadManager.Query().setFilterById(id)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return ModelInstallState(ModelInstallStatus.NOT_INSTALLED)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val progress = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                null
            }
            val installStatus = when (status) {
                DownloadManager.STATUS_PENDING -> ModelInstallStatus.QUEUED
                DownloadManager.STATUS_RUNNING -> ModelInstallStatus.DOWNLOADING
                DownloadManager.STATUS_PAUSED -> ModelInstallStatus.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> ModelInstallStatus.DOWNLOADED
                DownloadManager.STATUS_FAILED -> ModelInstallStatus.FAILED
                else -> ModelInstallStatus.NOT_INSTALLED
            }
            return ModelInstallState(installStatus, progress, downloaded, total, reason)
        }
    }

    suspend fun verifyDownloadedModel(): Boolean = withContext(Dispatchers.IO) {
        preferences.edit().putBoolean(KEY_VERIFYING, true).apply()
        val valid = runCatching {
            if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES) return@runCatching false
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == MODEL_SHA256
        }.getOrDefault(false)

        if (valid) {
            preferences.edit()
                .putBoolean(KEY_INSTALLED, true)
                .putBoolean(KEY_VERIFYING, false)
                .remove(KEY_DOWNLOAD_ID)
                .apply()
        } else {
            modelFile.delete()
            preferences.edit()
                .putBoolean(KEY_INSTALLED, false)
                .putBoolean(KEY_VERIFYING, false)
                .remove(KEY_DOWNLOAD_ID)
                .apply()
        }
        valid
    }

    fun cancelDownload() {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (id != NO_DOWNLOAD) manager.remove(id)
        modelFile.delete()
        preferences.edit().clear().apply()
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val APPROXIMATE_SIZE = "2.59 GB"

        // URL fijada a un commit inmutable para que el checksum no cambie silenciosamente.
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/9262660a1676eed6d0c477ab1a86344430854664/gemma-4-E2B-it.litertlm"
        private const val MODEL_SIZE_BYTES = 2_588_147_712L
        private const val REQUIRED_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        private const val MODEL_SHA256 =
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

        private const val PREFERENCES = "on_device_ai_model"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_VERIFYING = "verifying"
        private const val NO_DOWNLOAD = -1L

        fun modelFile(context: Context): File {
            val directory = context.getExternalFilesDir("models")
                ?: File(context.filesDir, "models")
            return File(directory, MODEL_FILE_NAME)
        }
    }
}
