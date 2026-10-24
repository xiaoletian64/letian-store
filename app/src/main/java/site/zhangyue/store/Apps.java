package site.zhangyue.store;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Apps — 已安装状态与本地缓存的应用状态。 */
public final class Apps {
    private static Map<String, PackageInfo> sInstalled;

    public static final int NOT_INSTALLED = 0, UPDATABLE = 1, CURRENT = 2;

    private Apps() {}

    public static synchronized Map<String, PackageInfo> installed(Context ctx) {
        if (sInstalled == null) {
            sInstalled = new HashMap<>();
            PackageManager pm = ctx.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                sInstalled.put(pi.packageName, pi);
            }
        }
        return sInstalled;
    }

    public static synchronized void invalidate() { sInstalled = null; }

    /** @return NOT_INSTALLED / UPDATABLE / CURRENT */
    public static int status(Context ctx, Model.App a) {
        PackageInfo pi = installed(ctx).get(a.pkg);
        if (pi == null) return NOT_INSTALLED;
        long vc;
        try {
            vc = pi.getLongVersionCode();     // API 28+
        } catch (Throwable t) {
            vc = pi.versionCode;              // API 24-27
        }
        return vc >= a.versionCode ? CURRENT : UPDATABLE;
    }

    public static long installedVersion(Context ctx, Model.App a) {
        PackageInfo pi = installed(ctx).get(a.pkg);
        if (pi == null) return 0;
        try {
            return pi.getLongVersionCode();
        } catch (Throwable t) {
            return pi.versionCode;
        }
    }

    /** 该清单项是否有下载完待安装的 APK。 */
    public static File readyApk(Context ctx, Model.App a) {
        File f = DlManager.apkFile(ctx, a.slug);
        return f.exists() ? f : null;
    }

    public static String versionText(Model.App a) {
        return "v" + a.versionName + " (" + a.updated + ")";
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
