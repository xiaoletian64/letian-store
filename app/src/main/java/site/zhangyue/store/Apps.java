package site.zhangyue.store;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.security.MessageDigest;

/** Apps — 已安装状态与本地缓存的应用状态。 */
public final class Apps {
    public static final int NOT_INSTALLED = 0, UPDATABLE = 1, CURRENT = 2;

    private Apps() {}

    // Query the catalog package directly. Global/negative caches become stale after installs.
    private static PackageInfo info(Context ctx, String packageName) {
        try {
            return ctx.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
    }

    public static int status(Context ctx, Model.App app) {
        PackageInfo pi = info(ctx, app.pkg);
        if (pi == null) return NOT_INSTALLED;
        return version(pi) >= app.versionCode ? CURRENT : UPDATABLE;
    }

    public static long installedVersion(Context ctx, Model.App app) {
        PackageInfo pi = info(ctx, app.pkg);
        return pi == null ? 0 : version(pi);
    }

    private static long version(PackageInfo pi) {
        if (android.os.Build.VERSION.SDK_INT >= 28) return pi.getLongVersionCode();
        return pi.versionCode;
    }

    /** 该清单项是否有下载完待安装的 APK。 */
    public static File readyApk(Context ctx, Model.App a) {
        File f = DlManager.apkFile(ctx, a.slug);
        File receipt = new File(f.getPath() + ".sha");
        if (!f.isFile() || a.sha256 == null || !a.sha256.matches("(?i)[0-9a-f]{64}")
                || (a.bytes > 0 && f.length() != a.bytes) || !receipt.isFile() || receipt.length() > 128) return null;
        try {
            byte[] bytes = new byte[129];
            int count = 0;
            try (java.io.InputStream in = new java.io.FileInputStream(receipt)) {
                int n;
                while (count < bytes.length && (n = in.read(bytes, count, bytes.length - count)) != -1) count += n;
            }
            if (count > 128) return null;
            String completed = new String(bytes, 0, count, java.nio.charset.StandardCharsets.US_ASCII).trim();
            return a.sha256.equalsIgnoreCase(completed) ? f : null;
        } catch (java.io.IOException unreadable) { return null; }
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
