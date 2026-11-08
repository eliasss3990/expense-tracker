package com.eliasgonzalez.expensetracker.data.analysis

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.eliasgonzalez.expensetracker.domain.analysis.LocalModelManager
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallState
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallStatus
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val TAG = "ModelManager"
private const val PREFERENCES = "local_model_state"
private const val NO_DOWNLOAD = -1L

/**
 * Descarga el modelo con el DownloadManager del sistema (sobrevive a que la app muera), solo por
 * Wi-Fi, y lo marca instalado unicamente si tamano y SHA-256 coinciden con el descriptor. El archivo va
 * al directorio externo privado de la app: no pide permisos de almacenamiento.
 */
class DownloadManagerModelManager(
    context: Context,
    private val descriptor: ModelDescriptor,
) : LocalModelManager {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("${PREFERENCES}_${descriptor.id}", Context.MODE_PRIVATE)
    private val verifyMutex = Mutex()

    override val displayName: String get() = descriptor.displayName
    override val approximateSize: String get() = descriptor.approximateSize

    val modelFile: File = ModelFiles.fileFor(appContext, descriptor)

    /** Barato (sin hashear): se llama en cada notificacion. La verificacion pesada ocurre una sola vez. */
    override fun isReady(): Boolean =
        preferences.getBoolean(KEY_INSTALLED, false) && modelFile.isFile && modelFile.length() == descriptor.sizeBytes

    override fun startDownload() {
        val current = state().status
        check(current == ModelInstallStatus.NOT_INSTALLED || current == ModelInstallStatus.FAILED) {
            "Ya hay una descarga en curso o el modelo ya esta instalado"
        }
        discardPreviousDownload()
        modelFile.parentFile?.mkdirs()
        val free = StatFs(modelFile.parentFile!!.absolutePath).availableBytes
        check(free >= descriptor.sizeBytes + REQUIRED_FREE_MARGIN_BYTES) {
            "Falta espacio libre: se necesitan unos ${descriptor.approximateSize} mas un margen"
        }
        val request = DownloadManager.Request(Uri.parse(descriptor.url))
            .setTitle("Modelo de IA de Expense Tracker")
            .setDescription("Descargando ${descriptor.displayName} (${descriptor.approximateSize})")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, ModelFiles.DIRECTORY, descriptor.fileName)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
        preferences.edit().putLong(KEY_DOWNLOAD_ID, manager.enqueue(request)).putBoolean(KEY_INSTALLED, false).apply()
        AppLog.i(TAG, "descarga iniciada modelo=${descriptor.id}")
    }

    override fun cancel() {
        discardPreviousDownload()
        preferences.edit().clear().apply()
        modelFile.delete()
        AppLog.i(TAG, "descarga cancelada modelo=${descriptor.id}")
    }

    override fun state(): ModelInstallState {
        if (preferences.getBoolean(KEY_INSTALLED, false)) {
            if (isReady()) return ModelInstallState(ModelInstallStatus.INSTALLED, 100, descriptor.sizeBytes, descriptor.sizeBytes)
            preferences.edit().putBoolean(KEY_INSTALLED, false).apply() // el archivo desaparecio
        }
        if (verifyMutex.isLocked) return ModelInstallState(ModelInstallStatus.VERIFYING)
        val id = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (id == NO_DOWNLOAD) return ModelInstallState(ModelInstallStatus.NOT_INSTALLED)

        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return ModelInstallState(ModelInstallStatus.NOT_INSTALLED)
            val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val status = when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING -> ModelInstallStatus.QUEUED
                DownloadManager.STATUS_RUNNING -> ModelInstallStatus.DOWNLOADING
                DownloadManager.STATUS_PAUSED -> ModelInstallStatus.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> ModelInstallStatus.DOWNLOADED
                DownloadManager.STATUS_FAILED -> ModelInstallStatus.FAILED
                else -> ModelInstallStatus.NOT_INSTALLED
            }
            val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null
            return ModelInstallState(status, progress, downloaded, total, reason)
        }
    }

    override suspend fun refresh(): ModelInstallState {
        if (state().status == ModelInstallStatus.DOWNLOADED) verify()
        return state()
    }

    private suspend fun verify() {
        // Un solo hash a la vez; una segunda llamada espera y encuentra el resultado ya guardado.
        verifyMutex.withLock {
            if (isReady()) return
            val valid = withContext(Dispatchers.IO) { checksumMatches() }
            if (valid) {
                preferences.edit().putBoolean(KEY_INSTALLED, true).remove(KEY_DOWNLOAD_ID).apply()
                AppLog.i(TAG, "modelo verificado e instalado modelo=${descriptor.id}")
            } else {
                modelFile.delete()
                preferences.edit().putBoolean(KEY_INSTALLED, false).remove(KEY_DOWNLOAD_ID).apply()
                AppLog.w(TAG, "el archivo descargado no coincide con tamano/SHA-256, se descarto modelo=${descriptor.id}")
            }
        }
    }

    private fun checksumMatches(): Boolean = try {
        if (!modelFile.isFile || modelFile.length() != descriptor.sizeBytes) {
            false
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == descriptor.sha256
        }
    } catch (e: Exception) {
        AppLog.w(TAG, "no se pudo verificar el modelo", e)
        false
    }

    private fun discardPreviousDownload() {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (id != NO_DOWNLOAD) manager.remove(id)
        modelFile.delete()
    }

    private companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_INSTALLED = "installed"
        const val REQUIRED_FREE_MARGIN_BYTES = 128L * 1024 * 1024
    }
}

internal object ModelFiles {
    const val DIRECTORY = "models"

    fun fileFor(context: Context, descriptor: ModelDescriptor): File {
        val dir = context.getExternalFilesDir(DIRECTORY) ?: File(context.filesDir, DIRECTORY)
        return File(dir, descriptor.fileName)
    }
}
