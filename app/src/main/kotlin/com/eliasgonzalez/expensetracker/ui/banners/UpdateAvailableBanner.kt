package com.eliasgonzalez.expensetracker.ui.banners

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.update.ApkInstaller
import com.eliasgonzalez.expensetracker.update.ReleaseInfo

@Composable
internal fun UpdateAvailableBanner(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun openRelease() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
    }
    // Si la Release no tiene ningún asset .apk (no debería pasar con el
    // workflow actual), cae a abrir la página del Release en el
    // navegador en vez de no hacer nada.
    fun downloadOrOpenRelease() {
        val apkUrl = release.apkDownloadUrl
        if (apkUrl != null) {
            ApkInstaller.downloadAndInstall(context, apkUrl)
        } else {
            openRelease()
        }
    }
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // clickable acá porque el texto dice explícitamente "tocá
            // para instalarla" - antes solo el botón "Ver" hacía algo,
            // tocar el título/subtítulo no tenía ningún efecto.
            Column(Modifier.weight(1f).clickable(onClick = ::downloadOrOpenRelease)) {
                Text(
                    "Hay una nueva versión disponible",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v${release.versionName} — tocá para instalarla",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("Ahora no") }
            TextButton(onClick = ::downloadOrOpenRelease) { Text("Ver") }
        }
    }
}
