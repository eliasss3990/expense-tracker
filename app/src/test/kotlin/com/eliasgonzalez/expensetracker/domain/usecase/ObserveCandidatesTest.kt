package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ObserveCandidatesTest {

    @Test
    fun `expone el mismo StateFlow que el repositorio, sin copiarlo`() {
        val candidates = FakeCandidateRepository()
        val observeCandidates = ObserveCandidates(candidates)

        assertSame(candidates.candidates, observeCandidates())
    }

    @Test
    fun `refleja cambios posteriores del repositorio`() = runTest {
        val candidates = FakeCandidateRepository()
        val observeCandidates = ObserveCandidates(candidates)

        assertEquals(0, observeCandidates().value.size)

        candidates.save(
            ExpenseCandidate(
                amount = 5_000,
                merchant = "Kiosco",
                categorySuggestion = "other",
                status = CandidateStatus.PENDING,
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        assertEquals(1, observeCandidates().value.size)
    }
}
