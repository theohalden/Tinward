package dev.kosh.financetracker.security

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Runs in its own process (see the manifest) so it can kill the main process and relaunch the app
 * cleanly after a restore. Starting an activity from a background alarm is blocked on modern
 * Android; starting it from here, while the app is still on screen, is not.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        val launch = intent.getParcelableExtra<Intent>(EXTRA_LAUNCH)
        if (pid > 0) Process.killProcess(pid)
        if (launch != null) startActivity(launch)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_PID = "pid"
        const val EXTRA_LAUNCH = "launch"
    }
}
