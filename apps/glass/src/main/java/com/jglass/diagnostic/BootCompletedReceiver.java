package com.jglass.diagnostic;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;

import java.util.Date;

public final class BootCompletedReceiver extends BroadcastReceiver {
    private static final long POST_BOOT_DELAY_MILLIS = 3_000L;
    private static final int BOOT_LAUNCH_REQUEST_CODE = 6001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (HomeRole.isJGlassDefaultHome(context)) return;

        StartupGreetingStore store = new StartupGreetingStore(context);
        boolean debuggable = (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        boolean force = debuggable && store.debugReplayAlways();
        String today = StartupGreetingRules.localDate(new Date());
        if (!StartupGreetingRules.shouldStart(store.lastGreetingDate(), today, force)) return;

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        launch.putExtra(MainActivity.EXTRA_STARTUP_LAUNCH_REASON,
                MainActivity.STARTUP_LAUNCH_REASON_BOOT);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingLaunch = PendingIntent.getActivity(
                context, BOOT_LAUNCH_REQUEST_CODE, launch, pendingFlags);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            context.startActivity(launch);
            return;
        }
        alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + POST_BOOT_DELAY_MILLIS,
                pendingLaunch);
    }
}
