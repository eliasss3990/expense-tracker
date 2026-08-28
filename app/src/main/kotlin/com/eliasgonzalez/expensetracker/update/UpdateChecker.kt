package com.eliasgonzalez.expensetracker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/eliasss3990/expense-tracker/releases/latest"

data class ReleaseInfo(val versionName: String, val htmlUrl: String)

/**
 * Chequea la última Release publicada en GitHub. Mientras el repo sea
 * privado, la API de Releases pide autenticación y esto va a fallar
 * silenciosamente (devuelve null) - es esperado, no un bug; empieza a
 * funcionar solo con hacer público el repo, sin tocar código.
 */
object UpdateChecker {
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.inputStream.use { stream ->
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val json = JSONObject(stream.bufferedReader().readText())
                ReleaseInfo(
                    versionName = json.getString("tag_name").removePrefix("v"),
                    htmlUrl = json.getString("html_url"),
                )
            }
        }.getOrNull()
    }
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
