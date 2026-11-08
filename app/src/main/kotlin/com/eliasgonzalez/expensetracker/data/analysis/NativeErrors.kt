package com.eliasgonzalez.expensetracker.data.analysis

import java.util.concurrent.CancellationException

/**
 * El runtime avisa una cancelacion con `java.util.concurrent.CancellationException`, que en Kotlin ES la misma clase
 * que la cancelacion de una corrutina. Si el motor cancela por su cuenta (sin que la app lo pida) y esa excepcion sale
 * tal cual, el llamador cree que lo cancelaron a el: aborta el drain y el aviso queda trabado. Una cancelacion que NO
 * pidio la app es un fallo del motor, no una cancelacion.
 */
internal object NativeErrors {
    fun map(error: Throwable, cancelRequestedByApp: Boolean): Throwable =
        if (error is CancellationException && !cancelRequestedByApp) {
            IllegalStateException("El motor cancelo la inferencia por su cuenta", error)
        } else {
            error
        }
}
