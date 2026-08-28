package com.eliasgonzalez.expensetracker.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import java.io.File

private const val APK_FILE_NAME = "expense-tracker-update.apk"

/**
 * Descarga el APK de una Release vía DownloadManager (soporta HTTPS,
 * progreso en la barra de notificaciones del sistema, reintentos) y al
 * terminar lanza el instalador del sistema con un Uri de FileProvider -
 * un Uri de archivo directo (file://) no funciona desde Android 7+
 * (FileUriExposedException).
 */
object ApkInstaller {
    fun downloadAndInstall(context: Context, downloadUrl: String) {
        val targetFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            APK_FILE_NAME,
        )
        targetFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Actualizando Expense Tracker")
            .setDestinationUri(Uri.fromFile(targetFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService<DownloadManager>() ?: return
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (finishedId != downloadId) return
                context.unregisterReceiver(this)
                installApk(context, targetFile)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "No se pudo descargar la actualización", Toast.LENGTH_SHORT).show()
            return
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
