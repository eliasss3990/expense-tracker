package com.eliasgonzalez.expensetracker.domain.repository

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import kotlinx.coroutines.flow.StateFlow

interface ActivityRepository {
    val recent: StateFlow<List<ActivityEntry>>
    suspend fun record(entry: ActivityEntry)
}
