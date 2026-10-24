package site.zhangyue.store;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** DlManager — 串行下载队列 + 进度通知 + SHA256 校验。 */
public final class DlManager {
    public static final String CHANNEL = "dl";
    public static final int NOTIFY_ID = 42;

    public interface Listener { void onTasksChanged(); }

    public static final class Task {
        public Model.App app;
        public String url;
        public volatile int state;      // 0 queued,1 running,2 done,3 failed
        public volatile long soFar, total;
        public volatile String error;
        public File file;
    }

    private static final ConcurrentHashMap<String, Task> TASKS = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static Thread sWorker;

    private DlManager() {}

    public static synchronized void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static synchronized void removeListener(Listener l) { LISTENERS.remove(l); }

    private static void fire() {
        for (Listener l : LISTENERS) l.onTasksChanged();
    }

    public static File apkFile(Context ctx, String slug) {
        return new File(ctx.getExternalFilesDir("apks"), slug + ".apk");
    }

    public static Task get(String slug) { return TASKS.get(slug); }

    public static boolean busy() {
        for (Task t : TASKS.values()) if (t.state == 0 || t.state == 1) return true;
        return false;
    }

    public static synchronized void enqueue(Context ctx, Model.App a) {
        if (TASKS.containsKey(a.slug)) {
            Task t = TASKS.get(a.slug);
            if (t.state == 3) TASKS.remove(a.slug);
            else return;
        }
        Task t = new Task();
        t.app = a;
        t.url = a.url;
        t.state = 0;
        t.file = apkFile(ctx, a.slug);
        TASKS.put(a.slug, t);
        ensureChannel(ctx);
        startWorker(ctx.getApplicationContext());
        fire();
    }

    private static synchronized void startWorker(final Context ctx) {
        if (sWorker != null && sWorker.isAlive()) return;
        sWorker = new Thread(() -> {
            while (true) {
                Task next = null;
                for (Task t : TASKS.values()) if (t.state == 0 && (next == null)) next = t;
                if (next == null) {
                    // 全部完成/失败：清掉终态，下次重新入队
                    TASKS.values().removeIf(t -> t.state == 2 || t.state == 3);
                    notifyProgress(ctx, null, 0, 0, true);
                    fire();
                    return;
                }
                run(ctx, next);
                fire();
            }
        }, "dl-worker");
        sWorker.start();
    }

    private static void run(Context ctx, Task t) {
        t.state = 1;
        t.soFar = 0;
        fire();
        File tmp = new File(t.file.getAbsolutePath() + ".part");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(t.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "LetianStore/3.0 (Android)");
            if (tmp.exists() && tmp.length() > 0) {
                // 断点续传
                conn.setRequestProperty("Range", "bytes=" + tmp.length() + "-");
            }
            int code = conn.getResponseCode();
            long total;
            InputStream is;
            if (code == 206) {
                total = tmp.length() + parseTotal(conn.getHeaderField("Content-Range"));
                is = conn.getInputStream();
            } else if (code == 200) {
                total = conn.getContentLengthLong();
                is = conn.getInputStream();
                tmp.delete(); // 服务器不支持续传，重下
            } else {
                throw new Exception("HTTP " + code);
            }
            t.total = total > 0 ? total : Math.max(t.app.bytes, 1);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long written = tmp.exists() ? tmp.length() : 0;
            t.soFar = written;
            FileOutputStream out = new FileOutputStream(tmp, true);
            byte[] buf = new byte[65536];
            int n;
            long lastFire = 0;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                written += n;
                t.soFar = written;
                long now = System.currentTimeMillis();
                if (now - lastFire > 400) {
                    lastFire = now;
                    fire();
                    notifyProgress(ctx, t, written, t.total, false);
                }
            }
            out.close();
            is.close();
            conn.disconnect();
            if (t.total > 0 && written != t.total) throw new Exception("长度不符 " + written + "/" + t.total);
            String hex = hex(md.digest());
            if (t.app.sha256 != null && !t.app.sha256.isEmpty()
                    && !hex.equalsIgnoreCase(t.app.sha256)) {
                throw new Exception("SHA256 校验失败");
            }
            // 完整下载：记录 sha 到 sidecar 供断点续传场景二次校验
            java.io.FileWriter fw = new java.io.FileWriter(t.file.getAbsolutePath() + ".sha");
            fw.write(hex);
            fw.close();
            if (!tmp.renameTo(t.file)) {
                // rename 失败兜底拷贝
                java.nio.file.Files.copy(tmp.toPath(), t.file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
            t.state = 2;
            notifyProgress(ctx, t, written, t.total, true);
        } catch (Exception e) {
            t.state = 3;
            t.error = String.valueOf(e.getMessage());
            notifyProgress(ctx, null, 0, 0, true);
        }
    }

    private static long parseTotal(String contentRange) {
        if (contentRange == null) return 0;
        int i = contentRange.lastIndexOf('/');
        if (i < 0) return 0;
        try { return Long.parseLong(contentRange.substring(i + 1)); }
        catch (Exception e) { return 0; }
    }

    private static String hex(byte[] d) {
        char[] HEX = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        return sb.toString();
    }

    static String fmt(long v) {
        if (v < 1024 * 1024) return v / 1024 + " KB";
        return String.format(Locale.US, "%.1f MB", v / 1048576f);
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "下载进度",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    private static void notifyProgress(Context ctx, Task t, long so, long total, boolean silent) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (t == null) {
            nm.cancel(NOTIFY_ID);
            return;
        }
        android.app.Notification.Builder b =
                Build.VERSION.SDK_INT >= 26
                        ? new android.app.Notification.Builder(ctx, CHANNEL)
                        : new android.app.Notification.Builder(ctx);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(t.app.name + " " + fmt(so) + "/" + fmt(total))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total > 0 ? (int) Math.min(total, Integer.MAX_VALUE) : 0,
                        (int) Math.min(so, Integer.MAX_VALUE), total <= 0);
        Intent open = new Intent(ctx, DownloadActivity.class);
        b.setContentIntent(PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        nm.notify(NOTIFY_ID, b.build());
    }
}
