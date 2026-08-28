package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ObserveActivityTest {

    @Test
    fun `expone el mismo StateFlow que el repositorio, sin copiarlo`() {
        val activity = FakeActivityRepository()
        val observeActivity = ObserveActivity(activity)

        assertSame(activity.recent, observeActivity())
    }

    @Test
    fun `refleja cambios posteriores del repositorio`() = runTest {
        val activity = FakeActivityRepository()
        val observeActivity = ObserveActivity(activity)

        assertEquals(0, observeActivity().value.size)

        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_CREATED,
                expenseId = 1L,
                timestamp = 1L,
                summary = "algo",
            )
        )

        assertEquals(1, observeActivity().value.size)
    }
}
