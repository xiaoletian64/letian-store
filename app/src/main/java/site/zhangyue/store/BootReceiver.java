package site.zhangyue.store;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** BootReceiver — 开机后经 AlarmManager 唤起 UpdateService 做首轮自动更新检查。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent svc = new Intent(ctx, UpdateService.class).setAction(UpdateService.ACT_SCAN);
            PendingIntent pi = PendingIntent.getService(ctx, 1001, svc,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 15_000L, pi);
        } catch (Throwable ignore) { }
    }
}
