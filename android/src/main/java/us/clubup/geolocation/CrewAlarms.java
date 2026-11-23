package us.clubup.geolocation;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;


/** Inexact alarms that run the crew tick while the phone is still; no exact alarm permission is needed. */
final class CrewAlarms {

    private static final String ACTION_HEARTBEAT = "us.clubup.geolocation.CREW_HEARTBEAT";
    private static final String ACTION_STOP_TIME = "us.clubup.geolocation.CREW_STOP_TIME";

    private CrewAlarms() {
    }

    static void scheduleHeartbeat(Context context, @Nullable Long atMs) {

        schedule(context, ACTION_HEARTBEAT, atMs);
    }

    static void scheduleStopTime(Context context, @Nullable Long atMs) {

        schedule(context, ACTION_STOP_TIME, atMs);
    }

    static void cancelAll(Context context) {

        schedule(context, ACTION_HEARTBEAT, null);
        schedule(context, ACTION_STOP_TIME, null);
    }

    private static void schedule(Context context, String action, @Nullable Long atMs) {

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarm = alarmIntent(context, action);
        if (atMs == null) {
            alarmManager.cancel(alarm);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, alarm);
        }
        else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, atMs, alarm);
        }
    }

    private static PendingIntent alarmIntent(Context context, String action) {

        Intent intent = new Intent(context, CrewAlarmReceiver.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
