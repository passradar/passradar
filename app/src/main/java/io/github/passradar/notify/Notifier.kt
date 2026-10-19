package io.github.passradar.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import io.github.passradar.MainActivity
import io.github.passradar.R
import io.github.passradar.data.PolitietApi
import io.github.passradar.domain.NewSlots
import io.github.passradar.domain.WaitTime
import io.github.passradar.ui.Format
import io.github.passradar.ui.withAppLanguage
import java.time.LocalDate
import java.util.Locale

class Notifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    fun createChannel() {
        val res = context.withAppLanguage().resources
        val channel = NotificationChannelCompat.Builder(CHANNEL_NEW_SLOTS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(res.getString(R.string.channel_slots_name))
            .setDescription(res.getString(R.string.channel_slots_description))
            .build()
        manager.createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    /** One notification per office; several at once are bundled under a summary. */
    @SuppressLint("MissingPermission") // checked in canNotify()
    fun showNewSlots(found: List<Pair<NewSlots, List<String>>>, serviceId: String, today: LocalDate) {
        if (found.isEmpty() || !canNotify()) return
        val res = context.withAppLanguage().resources
        val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
        val grouped = found.size > 1 && Build.VERSION.SDK_INT >= 24

        for ((slots, times) in found.sortedBy { it.first.earliest }) {
            val wait = waitPhrase(res, WaitTime.days(today, slots.earliest))
            val lines = buildList {
                val date = Format.dayDate(slots.earliest, locale)
                add(if (times.isEmpty()) date else res.getString(R.string.notif_date_times, date, times.take(MAX_TIMES).joinToString(", ")))
                slots.previousEarliest?.let { add(res.getString(R.string.notif_previous, waitPhrase(res, WaitTime.days(today, it)))) }
                if (slots.newDates.size > 1) {
                    add(res.getQuantityString(R.plurals.notif_new_dates, slots.newDates.size, slots.newDates.size))
                }
            }
            val requestCode = slots.officeId.hashCode()
            val builder = baseBuilder()
                .setContentTitle(res.getString(R.string.notif_title, wait, slots.officeName))
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(openOffice(slots.officeId, requestCode))
                .addAction(
                    0, res.getString(R.string.notif_action_book),
                    PendingIntentCompat.getActivity(
                        context, requestCode + 1,
                        Intent(Intent.ACTION_VIEW, PolitietApi.bookingUrl(slots.officeId, serviceId).toUri()),
                        PendingIntent.FLAG_UPDATE_CURRENT, false,
                    ),
                )
            // Earliest appointment first inside the bundle; ISO dates sort the same way as text.
            if (grouped) builder.setGroup(GROUP).setSortKey(slots.earliest.toString())
            manager.notify(requestCode, builder.build())
        }

        if (grouped) {
            val title = res.getQuantityString(R.plurals.notif_summary_title, found.size, found.size)
            val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
            for ((slots, _) in found.sortedBy { it.first.earliest }) {
                inbox.addLine(res.getString(R.string.notif_summary_line, slots.officeName, waitPhrase(res, WaitTime.days(today, slots.earliest))))
            }
            val summary = baseBuilder()
                .setContentTitle(title)
                .setStyle(inbox)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setContentIntent(PendingIntentCompat.getActivity(context, SUMMARY_ID, MainActivity.watchesIntent(context), PendingIntent.FLAG_UPDATE_CURRENT, false))
                .build()
            manager.notify(SUMMARY_ID, summary)
        }
    }

    private fun baseBuilder() = NotificationCompat.Builder(context, CHANNEL_NEW_SLOTS)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setColor(ContextCompat.getColor(context, R.color.brand))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)

    private fun openOffice(officeId: String, requestCode: Int): PendingIntent? = PendingIntentCompat.getActivity(
        context, requestCode, MainActivity.officeIntent(context, officeId), PendingIntent.FLAG_UPDATE_CURRENT, false,
    )

    private fun waitPhrase(res: Resources, days: Long): String = when (days) {
        0L -> res.getString(R.string.today)
        1L -> res.getString(R.string.tomorrow)
        else -> res.getQuantityString(R.plurals.in_days, days.toInt(), days.toInt())
    }

    private companion object {
        const val CHANNEL_NEW_SLOTS = "new_slots"
        const val GROUP = "new_slots"
        const val SUMMARY_ID = 1
        const val MAX_TIMES = 8
    }
}
