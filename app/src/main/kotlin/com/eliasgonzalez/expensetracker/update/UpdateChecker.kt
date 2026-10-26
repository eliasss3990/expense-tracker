package com.eliasgonzalez.expensetracker.update

import com.eliasgonzalez.expensetracker.domain.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/eliasss3990/expense-tracker/releases/latest"

data class ReleaseInfo(val versionName: String, val htmlUrl: String, val apkDownloadUrl: String?)

/**
 * Chequea la última Release publicada en GitHub. La API de Releases solo
 * responde sin autenticar si el repo es público, y aun así GitHub limita las
 * consultas anónimas a 60 por hora por IP (compartida en redes con NAT, como
 * muchos datos móviles): por eso el resultado distingue POR QUÉ falló en vez
 * de devolver un null mudo.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    suspend fun fetchLatestReleaseResult(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            try {
                // Se lee el codigo ANTES que el cuerpo: pedir el inputStream de un 403/404
                // lanza IOException y se perderia el codigo y el header del limite.
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                    val reason = classifyHttpFailure(code, remaining)
                    AppLog.w(TAG, "GitHub respondió HTTP $code (cupo restante: ${remaining ?: "?"}) -> $reason")
                    return@withContext UpdateCheckResult.Failed(reason, "HTTP $code")
                }
                val json = JSONObject(connection.inputStream.use { it.bufferedReader().readText() })
                val assets = json.optJSONArray("assets")
                val assetNamesToUrls = buildList {
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            add(asset.optString("name") to asset.optString("browser_download_url"))
                        }
                    }
                }
                UpdateCheckResult.Found(
                    ReleaseInfo(
                        versionName = json.getString("tag_name").removePrefix("v"),
                        htmlUrl = json.getString("html_url"),
                        apkDownloadUrl = findApkAssetUrl(assetNamesToUrls),
                    )
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: JSONException) {
            AppLog.w(TAG, "La respuesta de GitHub no se pudo interpretar", e)
            UpdateCheckResult.Failed(UpdateFailure.BAD_RESPONSE, e.javaClass.simpleName)
        } catch (e: Exception) {
            val reason = classifyException(e)
            AppLog.w(TAG, "No se pudo consultar la última versión -> $reason", e)
            UpdateCheckResult.Failed(reason, e.javaClass.simpleName)
        }
    }

    /** Versión simple: la release, o null si falló por cualquier motivo. */
    suspend fun fetchLatestRelease(): ReleaseInfo? =
        (fetchLatestReleaseResult() as? UpdateCheckResult.Found)?.release
}

/**
 * Busca entre los assets de la Release el .apk a descargar. Si hay más
 * de uno (no debería pasar con el workflow actual, que sube un solo
 * app-release.apk) toma el primero - null si no hay ningún .apk, para
 * que el banner caiga de nuevo a abrir la página del Release en el
 * navegador en vez de romper.
 *
 * Recibe pares (nombre, url) ya extraídos del JSON en vez de un
 * org.json.JSONObject - el org.json que trae el android.jar de test
 * lanza "not mocked" en unit tests JVM (ver ExportBackupTest), así que
 * mantener esta parte pura y sin JSONObject permite testearla sin
 * Robolectric.
 */
internal fun findApkAssetUrl(assetNamesToUrls: List<Pair<String, String>>): String? {
    val apkAsset = assetNamesToUrls.firstOrNull { (name, _) -> name.endsWith(".apk") } ?: return null
    return apkAsset.second.ifBlank { null }
}

/**
 * Compara versiones "x.y.z" numéricamente segmento a segmento (no
 * alfabéticamente - "0.9.0" no puede parecer "mayor" que "0.10.0").
 * Segmentos no numéricos o faltantes cuentan como 0.
 *
 * El núcleo numérico se compara sin el sufijo de pre-release (todo lo
 * que sigue al primer "-", ej. "-beta.1" en "1.2.0-beta.1") - antes ese
 * sufijo se colaba dentro de un segmento normal ("0-beta".toIntOrNull()
 * -> null -> 0), así que a un tag pre-release le "faltaba" ese segmento
 * y el remoto quedaba comparando 1 (segmento propio) contra 0 (el
 * segmento que le faltaba al actual), dando un falso "hay actualización"
 * aunque las dos versiones fueran la misma o el remoto fuera un beta más
 * viejo. Si el núcleo empata, una versión CON sufijo de pre-release
 * nunca cuenta como más nueva que la misma versión SIN sufijo.
 */
fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val (remoteCore, remoteIsPreRelease) = remoteVersion.splitVersionCore()
    val (currentCore, currentIsPreRelease) = currentVersion.splitVersionCore()
    val remote = remoteCore.split(".").map { it.toIntOrNull() ?: 0 }
    val current = currentCore.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (r != c) return r > c
    }
    if (remoteIsPreRelease != currentIsPreRelease) return !remoteIsPreRelease
    return false
}

private fun String.splitVersionCore(): Pair<String, Boolean> {
    val dashIndex = indexOf('-')
    return if (dashIndex >= 0) substring(0, dashIndex) to true else this to false
}
