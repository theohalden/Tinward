package dev.kosh.financetracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FinanceTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Scheduling touches no data, so it is safe before unlock.
        dev.kosh.financetracker.feature.reminders.ReminderScheduler.ensureChannel(this)
        dev.kosh.financetracker.feature.reminders.ReminderScheduler.schedule(this)
    }
}
