package dev.kosh.financetracker.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.kosh.financetracker.R
import dev.kosh.financetracker.core.finance.OccurrenceStatus
import dev.kosh.financetracker.core.finance.Recurrence
import dev.kosh.financetracker.core.finance.RecurringKind
import dev.kosh.financetracker.data.repository.PlanningRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders must work while the app is locked, when the encrypted database cannot be opened. So the
 * database never feeds the notification directly: while unlocked, [ReminderSync] writes only due
 * dates and lead times (no names, amounts or accounts) to this small cache, and the daily worker
 * reads just that.
 */
object ReminderCache {
    private const val FILE = "tinward_reminders"
    private const val KEY = "entries"
    private const val KEY_LAST = "last_notified_day"

    /** One entry per unsettled bill occurrence: (due day, days of notice). */
    data class Entry(val dueEpochDay: Long, val remindDays: Int)

    fun write(context: Context, entries: List<Entry>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, entries.joinToString(";") { "${it.dueEpochDay}:${it.remindDays}" }).apply()
    }

    fun read(context: Context): List<Entry> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().split(';').mapNotNull {
            val p = it.split(':'); if (p.size != 2) null else Entry(p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: return@mapNotNull null)
        }

    /** Entries whose reminder window has started and whose bill is not more than 3 days overdue. */
    fun dueNow(entries: List<Entry>, today: LocalDate): List<Entry> {
        val day = today.toEpochDay()
        return entries.filter { it.remindDays > 0 && day >= it.dueEpochDay - it.remindDays && day <= it.dueEpochDay + 3 }
    }

    fun alreadyNotifiedToday(context: Context, today: LocalDate) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_LAST, -1) == today.toEpochDay()

    fun markNotified(context: Context, today: LocalDate) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY_LAST, today.toEpochDay()).apply()
    }
}

/** Keeps [ReminderCache] current while the app is unlocked. */
@Singleton
class ReminderSync @Inject constructor(
    private val planning: PlanningRepository,
    private val transactions: TransactionRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(planning.observeRecurring(), transactions.observeTransactions()) { items, txs ->
                val today = LocalDate.now()
                items.filter { it.kind == RecurringKind.BILL }.flatMap { item ->
                    Recurrence.pending(item, txs, today, today.plusDays(45)).map { ReminderCache.Entry(it.date.toEpochDay(), item.remindDaysBefore) }
                }
            }.collect { ReminderCache.write(context, it) }
        }
    }
}

object ReminderScheduler {
    const val CHANNEL = "bill_reminders"
    private const val WORK = "bill_reminders_daily"

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!first.isAfter(now)) first = first.plusDays(1)
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Debug builds only: fires the real worker once so the notification can be checked. */
    fun sendTest(context: Context) {
        WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<BillReminderWorker>().setInputData(androidx.work.workDataOf(BillReminderWorker.KEY_TEST to true)).build(),
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A heads-up when a bill you added is coming due"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Once a day, tells you bills are coming due. Deliberately generic: no names or amounts on a lock screen. */
class BillReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val today = LocalDate.now()
        val test = inputData.getBoolean(KEY_TEST, false)
        val due = if (test) listOf(ReminderCache.Entry(today.toEpochDay(), 1)) else ReminderCache.dueNow(ReminderCache.read(ctx), today)
        if (due.isEmpty() || (!test && ReminderCache.alreadyNotifiedToday(ctx, today)) || !ReminderScheduler.canNotify(ctx)) return Result.success()
        ReminderScheduler.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (due.size == 1) "A bill is coming due. Open Tinward to see it." else "${due.size} bills are coming due. Open Tinward to see them."
        val notification = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tinward")
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Tinward").setContentText("Reminder").build(),
            )
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4101, notification) }
        if (!test) ReminderCache.markNotified(ctx, today)
        return Result.success()
    }

    companion object { const val KEY_TEST = "test" }
}
