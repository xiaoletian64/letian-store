package site.zhangyue.store;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/** Net — 简单网络状态判断。 */
public final class Net {
    private Net() {}

    public static boolean isOnline(Context c) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }
}
