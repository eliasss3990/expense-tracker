package com.eliasgonzalez.expensetracker.domain.analysis

/**
 * Detecta cuando el sistema (o una app como Gmail) vuelve a publicar la MISMA notificacion: misma clave del
 * sistema y mismo contenido, pero con otra hora de publicacion. Sin esto, cada re-publicacion generaba un evento
 * nuevo (la hora forma parte de la clave del evento) y se analizaba y avisaba otra vez.
 *
 * Solo compara contra lo ultimo visto con esa clave: si el contenido cambia (otra compra en la misma
 * notificacion) NO es repeticion. Memoria acotada (LRU); no es seguro para hilos por si solo, el llamador sincroniza.
 */
class RepostGuard(private val capacity: Int = 256) {
    private val lastSeen = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>) = size > capacity
    }

    /** true si [systemKey] ya se vio con exactamente este [content]; si no, lo recuerda y devuelve false. */
    @Synchronized
    fun isRepeat(systemKey: String, content: List<String>): Boolean {
        val fingerprint = content.joinToString("\u0000").hashCode()
        val previous = lastSeen.put(systemKey, fingerprint)
        return previous == fingerprint
    }
}
