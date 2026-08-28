package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericPurchaseParserTest {

    private fun notification(
        title: String = "",
        text: String = "",
        bigText: String = "",
        packageName: String = "com.example.somebank",
        applicationName: String = "SomeBank",
    ) = NotificationContext(
        packageName = packageName,
        applicationName = applicationName,
        title = title,
        text = text,
        bigText = bigText,
        timestamp = 0L,
    )

    // ---------- happy path ----------

    @Test
    fun `compra simple con Gs y comercio se parsea correctamente`() {
        val context = notification(text = "Compraste Gs. 50.000 en Farmacia Catedral")

        assertTrue(GenericPurchaseParser.canHandle(context))
        val result = GenericPurchaseParser.parse(context)!!

        assertEquals(50_000L, result.amount)
        assertEquals("Farmacia Catedral", result.merchant)
        assertTrue(result.merchantConfident)
        assertEquals("PYG", result.currency)
        assertEquals("generic-purchase-v1", result.parserId)
    }

    @Test
    fun `simbolo guarani unicode tambien es reconocido`() {
        val context = notification(text = "Pago de ₲25000 en Shell")

        assertTrue(GenericPurchaseParser.canHandle(context))
        assertEquals(25_000L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `codigo PYG tambien es reconocido`() {
        val context = notification(text = "PYG 12.000 en Kiosco Don Pepe")

        assertTrue(GenericPurchaseParser.canHandle(context))
        assertEquals(12_000L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `patron en ingles - in - tambien matchea el comercio`() {
        val context = notification(text = "Gs. 30.000 spent in Starbucks")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals("Starbucks", result.merchant)
        assertTrue(result.merchantConfident)
    }

    @Test
    fun `sin comercio detectable usa el nombre de la app como fallback`() {
        val context = notification(text = "Gs. 15.000 debitados de tu cuenta", applicationName = "MiBanco")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals("MiBanco", result.merchant)
        assertFalse(result.merchantConfident)
    }

    // ---------- vacios / espacios ----------

    @Test
    fun `texto vacio no matchea`() {
        val context = notification()

        assertFalse(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    @Test
    fun `solo espacios en blanco no matchea`() {
        val context = notification(title = "   ", text = "\t\n  ", bigText = "   ")

        assertFalse(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    @Test
    fun `texto sin monto no matchea aunque tenga comercio`() {
        val context = notification(text = "Compraste algo en Farmacia Catedral")

        assertFalse(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    // ---------- formato de monto ambiguo ----------

    @Test
    fun `punto como separador de miles se interpreta correctamente`() {
        val context = notification(text = "Gs. 1.234.567 en Supermercado")

        assertEquals(1_234_567L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `coma como separador de miles se interpreta correctamente`() {
        val context = notification(text = "Gs. 1,234,567 en Supermercado")

        assertEquals(1_234_567L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `coma como separador decimal se trata igual que separador de miles - ambiguedad documentada`() {
        // El parser no distingue separador decimal de separador de miles: simplemente
        // remueve todos los puntos y comas. "Gs. 10,50" (interpretable como 10.50) termina
        // siendo interpretado como 1050. Este test documenta el comportamiento real, no
        // necesariamente el deseado.
        val context = notification(text = "Gs. 10,50 en Kiosco")

        assertEquals(1050L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `punto como separador decimal tambien se trata como separador de miles`() {
        val context = notification(text = "Gs. 10.50 en Kiosco")

        assertEquals(1050L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `monto sin separadores se parsea tal cual`() {
        val context = notification(text = "Gs.5000 en Kiosco")

        assertEquals(5000L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `monto que desborda Long - canHandle true pero parse null`() {
        // canHandle solo verifica que exista un patron de monto, no que sea parseable
        // como Long. Un numero gigante hace que toLongOrNull() falle silenciosamente.
        val context = notification(text = "Gs. 999999999999999999999999999999 en Kiosco")

        assertTrue(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    @Test
    fun `monto en el limite de Long se parsea bien`() {
        val context = notification(text = "Gs. ${Long.MAX_VALUE} en Kiosco")

        assertEquals(Long.MAX_VALUE, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `numero negativo pegado al prefijo Gs produce un match vacio - bug documentado`() {
        // El '-' no pertenece al charset [\d.,]+, asi que el regex no puede capturar
        // "-5.000" completo. Pero como "Gs\.?" tiene el punto literal opcional, el motor
        // de regex backtrackea: no consume el "." de "Gs." como parte del literal y en
        // cambio lo deja disponible para que el propio grupo [\d.,]+ lo capture como
        // (unico) caracter valido. Resultado: canHandle() da true (hay "match"), pero el
        // grupo capturado es solo "." que tras remover puntos/comas queda vacio y
        // toLongOrNull() devuelve null.
        val context = notification(text = "Ajuste de Gs. -5.000 en tu cuenta")

        assertTrue(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    @Test
    fun `monto cero se parsea como cero`() {
        val context = notification(text = "Gs. 0 en Kiosco")

        assertEquals(0L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `multiples montos en el mismo texto toma el primero`() {
        val context = notification(text = "Gs. 10.000 en Kiosco, saldo restante Gs. 500.000")

        assertEquals(10_000L, GenericPurchaseParser.parse(context)!!.amount)
    }

    @Test
    fun `solo puntos y comas sin digitos igual matchea el patron de monto - bug documentado`() {
        // El charset del regex de monto es [\d.,]+: acepta "uno o mas" caracteres de digito,
        // punto O COMA, pero no exige que haya al menos un digito. "Gs. ..." matchea
        // canHandle() aunque no haya ningun numero real. Luego, en parse(), "..." sin
        // digitos falla toLongOrNull() y el resultado final es null igual.
        val context = notification(text = "Gs. ... en algún lugar")

        assertTrue(GenericPurchaseParser.canHandle(context))
        assertNull(GenericPurchaseParser.parse(context))
    }

    // ---------- comercio: casos raros ----------

    @Test
    fun `patron en aparece dos veces toma el primero`() {
        val context = notification(text = "Gs. 20.000 en Farmacia Catedral en Asuncion")

        val result = GenericPurchaseParser.parse(context)!!
        // El primer match de "en <algo>" consume todo lo que puede dentro del charset
        // permitido (incluye espacios), asi que en realidad se traga el segundo "en" tambien.
        assertEquals("Farmacia Catedral en Asuncion", result.merchant)
    }

    @Test
    fun `comercio con acentos y enie se preserva`() {
        val context = notification(text = "Gs. 20.000 en Panadería Ñandutí")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals("Panadería Ñandutí", result.merchant)
    }

    @Test
    fun `comercio con guiones apostrofes y ampersand`() {
        val context = notification(text = "Gs. 20.000 en Dean's Coffee & Co.")

        val result = GenericPurchaseParser.parse(context)!!
        // El punto final queda incluido por el charset y luego se recorta con trimEnd.
        assertEquals("Dean's Coffee & Co", result.merchant)
    }

    @Test
    fun `comercio con digitos`() {
        val context = notification(text = "Gs. 20.000 en Farmacia24")

        assertEquals("Farmacia24", GenericPurchaseParser.parse(context)!!.merchant)
    }

    @Test
    fun `comercio muy largo se trunca a 40 caracteres sin respetar limite de palabra`() {
        val longMerchant = "Supermercado La Gran Esquina del Barrio Norte Central"
        val context = notification(text = "Gs. 20.000 en $longMerchant")

        val result = GenericPurchaseParser.parse(context)!!
        // La captura cruda del regex son 40 caracteres exactos de longMerchant, pero
        // el ultimo cae justo en un espacio que el .trim() posterior recorta, dejando
        // el resultado final en 39 caracteres visibles.
        assertEquals(longMerchant.take(40).trim(), result.merchant)
        assertTrue(result.merchant.length <= 40)
    }

    @Test
    fun `comercio de un solo caracter matchea igual por el espacio final del body - bug documentado`() {
        // El body se arma concatenando title+text+bigText con espacios: "... en X ".
        // Ese espacio final entra en el charset del comercio y el trim() no lo penaliza
        // como caracter invalido: "X " cumple el minimo de 2 caracteres y luego se
        // recorta a "X". Resultado: un comercio de 1 sola letra SI resulta confiable
        // cuando queda al final del body, algo probablemente no intencional.
        val context = notification(text = "Gs. 20.000 en X")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals("X", result.merchant)
        assertTrue(result.merchantConfident)
    }

    @Test
    fun `mayusculas y minusculas mezcladas no afectan el match del monto`() {
        val context = notification(text = "gS. 20.000 EN Farmacia")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals(20_000L, result.amount)
        assertEquals("Farmacia", result.merchant)
    }

    @Test
    fun `html embebido corta la captura del comercio y cae al fallback`() {
        val context = notification(
            text = "Gs. 20.000 en <b>Farmacia Catedral</b>",
            applicationName = "BancoX",
        )

        val result = GenericPurchaseParser.parse(context)!!
        // El '<' no pertenece al charset del comercio, asi que no matchea nada usable
        // inmediatamente despues de "en " y cae al fallback del nombre de la app.
        assertFalse(result.merchantConfident)
        assertEquals("BancoX", result.merchant)
    }

    @Test
    fun `caracteres de control embebidos en el texto no rompen el parseo del monto`() {
        val context = notification(text = "Gs.  20.000 en Kiosco")

        assertTrue(GenericPurchaseParser.canHandle(context))
    }

    @Test
    fun `palabra que termina en en pero no es la palabra en no genera match espurio`() {
        val context = notification(text = "Gs. 20.000 restaurante Aristocrata")

        val result = GenericPurchaseParser.parse(context)!!
        assertFalse(result.merchantConfident)
    }

    @Test
    fun `body se arma con title text y bigText concatenados`() {
        val context = notification(
            title = "Notificacion",
            text = "Gs. 20.000",
            bigText = "en Farmacia Catedral",
        )

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals("Farmacia Catedral", result.merchant)
    }

    @Test
    fun `confidence y currency son siempre los mismos valores fijos`() {
        val context = notification(text = "Gs. 20.000 en Kiosco")

        val result = GenericPurchaseParser.parse(context)!!
        assertEquals(0.6, result.confidence, 0.0001)
        assertEquals("PYG", result.currency)
    }
}
