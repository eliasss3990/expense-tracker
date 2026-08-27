package com.eliasgonzalez.expensetracker

import android.app.Application
import com.eliasgonzalez.expensetracker.di.ServiceLocator

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
