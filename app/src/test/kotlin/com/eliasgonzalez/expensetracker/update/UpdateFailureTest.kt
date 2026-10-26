package com.eliasgonzalez.expensetracker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Antes cualquier fallo al buscar actualizaciones era indistinguible (un limite de
 * GitHub, un celular sin red y una fecha mal puesta daban lo mismo). Estos tests fijan
 * que cada causa se clasifica por separado: el aviso al usuario es generico y el
 * motivo concreto queda en el log (etiqueta UpdateChecker).
 */
class UpdateFailureTest {

    // --- respuestas HTTP -----------------------------------------------

    @Test
    fun `un 403 con el cupo agotado es el limite de la API, no un error cualquiera`() {
        assertEquals(UpdateFailure.RATE_LIMITED, classifyHttpFailure(403, "0"))
        assertEquals(UpdateFailure.RATE_LIMITED, classifyHttpFailure(403, " 0 "))
    }

    @Test
    fun `un 429 siempre es limite`() {
        assertEquals(UpdateFailure.RATE_LIMITED, classifyHttpFailure(429, null))
    }

    @Test
    fun `un 403 con cupo restante NO es limite`() {
        assertEquals(UpdateFailure.HTTP_ERROR, classifyHttpFailure(403, "42"))
        assertEquals(UpdateFailure.HTTP_ERROR, classifyHttpFailure(403, null))
    }

    @Test
    fun `otros codigos son error HTTP generico`() {
        assertEquals(UpdateFailure.HTTP_ERROR, classifyHttpFailure(404, null))
        assertEquals(UpdateFailure.HTTP_ERROR, classifyHttpFailure(500, "0"))
        assertEquals(UpdateFailure.HTTP_ERROR, classifyHttpFailure(503, null))
    }

    // --- excepciones ---------------------------------------------------

    @Test
    fun `sin red se distingue de un timeout`() {
        assertEquals(UpdateFailure.NO_NETWORK, classifyException(UnknownHostException("api.github.com")))
        assertEquals(UpdateFailure.NO_NETWORK, classifyException(ConnectException("refused")))
        assertEquals(UpdateFailure.NO_NETWORK, classifyException(NoRouteToHostException("no route")))
        assertEquals(UpdateFailure.TIMEOUT, classifyException(SocketTimeoutException("timeout")))
    }

    @Test
    fun `un fallo de TLS se distingue (tipicamente fecha u hora incorrecta en el celular)`() {
        assertEquals(UpdateFailure.TLS_ERROR, classifyException(SSLHandshakeException("Chain validation failed")))
    }

    @Test
    fun `cualquier otra excepcion es desconocida, no se disfraza de otra cosa`() {
        assertEquals(UpdateFailure.UNKNOWN, classifyException(IOException("raro")))
        assertEquals(UpdateFailure.UNKNOWN, classifyException(IllegalStateException("raro")))
    }
}
