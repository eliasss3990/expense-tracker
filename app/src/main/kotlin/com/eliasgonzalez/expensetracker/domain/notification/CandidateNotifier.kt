package com.eliasgonzalez.expensetracker.domain.notification

/**
 * Puerto de salida: avisa que un candidato ya fue resuelto (aceptado,
 * editado o rechazado) para que cualquier notificacion pendiente sobre
 * el quede obsoleta y se retire. Lo llaman los casos de uso, no la UI:
 * asi la regla "resuelto implica notificacion fuera" vale venga la
 * accion de donde venga (notificacion, Bandeja o pantalla de edicion)
 * y el dominio sigue sin conocer Android.
 */
interface CandidateNotifier {
    fun dismiss(candidateId: Long)

    object None : CandidateNotifier {
        override fun dismiss(candidateId: Long) = Unit
    }
}
