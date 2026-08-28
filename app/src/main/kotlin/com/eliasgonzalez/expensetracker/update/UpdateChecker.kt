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
 */
fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val remote = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrElse(i) { 0 }
        val c = current.getOrElse(i) { 0 }
        if (r != c) return r > c
    }
    return false
}
