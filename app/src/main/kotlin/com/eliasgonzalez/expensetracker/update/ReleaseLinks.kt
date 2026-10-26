package com.eliasgonzalez.expensetracker.update

/*
 * Enlaces de las Releases de GitHub derivados de la etiqueta de version (sin tocar la red, para
 * poder probarlos en la JVM). Existen porque la API de Releases (api.github.com) limita las
 * consultas anonimas a 60 por hora por IP, y esa cuota se comparte con todo el que salga a
 * internet por la misma IP publica (Wi-Fi o datos moviles con NAT): se agota sola y "Buscar
 * actualizaciones" falla sin que la app tenga ningun problema. La pagina web
 * github.com/<repo>/releases/latest, en cambio, no cuenta contra ese cupo: responde con una
 * redireccion a .../releases/tag/<etiqueta>, y de la etiqueta alcanza para armar todo lo demas.
 */

internal const val REPO_SLUG = "eliasss3990/expense-tracker"

/** Nombre del APK que sube release.yml (ReleaseLinksTest vigila que coincida). */
internal const val APK_ASSET_NAME = "app-release.apk"

internal const val LATEST_PAGE_URL = "https://github.com/$REPO_SLUG/releases/latest"

// vX.Y, vX.Y.Z o vX.Y.Z.W, con sufijo de pre-release opcional; la "v" tambien es opcional.
private val TAG_PATTERN = Regex("""^v?\d+(\.\d+){1,3}([-+][0-9A-Za-z.\-]+)?$""")

/**
 * Etiqueta de la cabecera `Location` de la redireccion de /releases/latest
 * (ej. "https://github.com/o/r/releases/tag/v0.5.3" -> "v0.5.3"). Null si no hay
 * cabecera, no apunta a una etiqueta o la etiqueta no parece una version.
 */
internal fun tagFromReleasesLocation(location: String?): String? {
    if (location.isNullOrBlank()) return null
    val marker = "/releases/tag/"
    val start = location.indexOf(marker)
    if (start < 0) return null
    val tag = location.substring(start + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
    return tag.takeIf { TAG_PATTERN.matches(it) }
}

internal fun releaseInfoFromTag(tag: String) = ReleaseInfo(
    versionName = tag.removePrefix("v"),
    htmlUrl = "https://github.com/$REPO_SLUG/releases/tag/$tag",
    apkDownloadUrl = "https://github.com/$REPO_SLUG/releases/download/$tag/$APK_ASSET_NAME",
)
