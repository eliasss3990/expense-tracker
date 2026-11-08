package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class InterpretationCodecTest {
    @Test
    fun `un JSON con valores de enum desconocidos o campos faltantes igual se lee`() {
        val json = """[{"v":9,"pass":"OTRA","modelId":"futuro","direction":"SIDEWAYS","status":"???",
            "amounts":[{"value":5,"role":"NUEVO_ROL"}],"doubts":["NUEVA_DUDA"]}]"""
        val r = InterpretationCodec.decode(json).single()
        assertEquals(MoneyDirection.UNKNOWN, r.interpretation.direction)
        assertEquals(OperationStatus.UNKNOWN, r.interpretation.status)
        assertEquals(AmountRole.OTHER, r.interpretation.amounts.single().role)
        assertEquals(setOf(ModelDoubt.OTHER), r.interpretation.doubts)
        assertEquals("futuro", r.modelId)
        assertEquals(null, r.interpretation.merchant)
    }

    @Test
    fun `una lista vacia se codifica y se lee`() {
        assertEquals(emptyList<Any>(), InterpretationCodec.decode(InterpretationCodec.encode(emptyList())))
    }
}
