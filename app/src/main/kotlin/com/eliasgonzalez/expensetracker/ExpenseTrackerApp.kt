package com.eliasgonzalez.expensetracker

import android.app.Application
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.logging.LogcatSink

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Unico lugar que decide a donde van los logs: cambiar de logcat a otra cosa
        // es cambiar esta linea (y escribir otro LogSink).
        AppLog.install(LogcatSink())
        ServiceLocator.init(this)
    }
}
