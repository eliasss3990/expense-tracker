package com.eliasgonzalez.expensetracker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * "Buscar actualizaciones" consulta primero la pagina web de la ultima release (que no
 * cuenta contra el cupo anonimo de la API de GitHub, de 60 consultas por hora por IP y
 * compartido entre todos los que salen por la misma IP publica) y arma todo desde la
 * etiqueta de la redireccion. Estos tests fijan esa derivacion.
 */
class ReleaseLinksTest {

    // --- etiqueta desde la cabecera Location ---------------------------------------

    @Test
    fun `extrae la etiqueta de una URL absoluta de GitHub`() {
        assertEquals("v0.5.3", tagFromReleasesLocation("https://github.com/eliasss3990/expense-tracker/releases/tag/v0.5.3"))
    }

    @Test
    fun `extrae la etiqueta de una ruta relativa`() {
        assertEquals("v1.0.0", tagFromReleasesLocation("/eliasss3990/expense-tracker/releases/tag/v1.0.0"))
    }

    @Test
    fun `ignora parametros, fragmento y barra final`() {
        assertEquals("v0.5.3", tagFromReleasesLocation("https://github.com/o/r/releases/tag/v0.5.3?expanded=true"))
        assertEquals("v0.5.3", tagFromReleasesLocation("https://github.com/o/r/releases/tag/v0.5.3#readme"))
        assertEquals("v0.5.3", tagFromReleasesLocation("https://github.com/o/r/releases/tag/v0.5.3/"))
    }

    @Test
    fun `acepta etiquetas con o sin v y con sufijo de pre-release`() {
        assertEquals("0.5.3", tagFromReleasesLocation("https://github.com/o/r/releases/tag/0.5.3"))
        assertEquals("v0.6.0-rc1", tagFromReleasesLocation("https://github.com/o/r/releases/tag/v0.6.0-rc1"))
        assertEquals("v1.2", tagFromReleasesLocation("https://github.com/o/r/releases/tag/v1.2"))
    }

    @Test
    fun `sin cabecera o sin etiqueta devuelve null en vez de inventar una version`() {
        assertNull(tagFromReleasesLocation(null))
        assertNull(tagFromReleasesLocation(""))
        assertNull(tagFromReleasesLocation("   "))
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases"))
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases/tag/"))
        assertNull(tagFromReleasesLocation("https://github.com/login?return_to=/o/r/releases/latest"))
    }

    @Test
    fun `una etiqueta que no parece una version se rechaza`() {
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases/tag/latest"))
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases/tag/nightly"))
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases/tag/v1"))
        assertNull(tagFromReleasesLocation("https://github.com/o/r/releases/tag/v1.x.2"))
    }

    // --- ReleaseInfo derivado de la etiqueta ---------------------------------------

    @Test
    fun `arma la version, la pagina y el APK desde la etiqueta`() {
        val release = releaseInfoFromTag("v0.5.3")

        assertEquals("0.5.3", release.versionName)
        assertEquals("https://github.com/eliasss3990/expense-tracker/releases/tag/v0.5.3", release.htmlUrl)
        assertEquals(
            "https://github.com/eliasss3990/expense-tracker/releases/download/v0.5.3/app-release.apk",
            release.apkDownloadUrl,
        )
    }

    @Test
    fun `la version derivada se compara bien contra la instalada`() {
        assertTrue(isNewerVersion(releaseInfoFromTag("v0.5.4").versionName, "0.5.3"))
        assertTrue(isNewerVersion(releaseInfoFromTag("v0.10.0").versionName, "0.9.9"))
        assertEquals(false, isNewerVersion(releaseInfoFromTag("v0.5.3").versionName, "0.5.3"))
    }

    // --- acople con el workflow de release -----------------------------------------

    @Test
    fun `el nombre del APK coincide con el que sube release_yml`() {
        // Si alguien renombra el APK en el workflow y no aca, el enlace de descarga derivado
        // daria 404. El test corre desde el modulo app (el workflow esta un nivel arriba).
        val workflow = File("../.github/workflows/release.yml")
        assumeTrue("no se encontro release.yml desde ${File(".").absolutePath}", workflow.exists())

        assertTrue(
            "release.yml debe subir $APK_ASSET_NAME",
            workflow.readText().contains("/$APK_ASSET_NAME"),
        )
    }

    @Test
    fun `el repositorio apunta a este proyecto`() {
        assertEquals("https://github.com/eliasss3990/expense-tracker/releases/latest", LATEST_PAGE_URL)
    }
}
