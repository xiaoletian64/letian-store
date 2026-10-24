package site.zhangyue.store;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Explicit, non-exported system session callback; the random session token is checked too. */
public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) { Installer.acceptResult(c, intent); }
}
