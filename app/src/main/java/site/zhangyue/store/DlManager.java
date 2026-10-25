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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** DlManager — 串行下载队列 + 进度通知 + SHA256 校验 + 自动重试 + 备用源回落 + 已装清理。 */
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
        volatile int retries;           // 已重试次数
        volatile boolean viaFallback;   // 本次是否走了备用源
    }

    private static final int MAX_RETRIES = 3;             // 单源失败自动重试次数（含首试共4次）
    private static final ConcurrentHashMap<String, Task> TASKS = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static Thread sWorker;
    public static volatile int lastSuccessCount;          // 最近一轮自动更新成功数（诊断用）

    private DlManager() {}

    public static synchronized void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static synchronized void removeListener(Listener l) { LISTENERS.remove(l); }

    private static void fire() {
        for (Listener l : LISTENERS) l.onTasksChanged();
    }

    /** 供外部（如安装完成回调）触发界面刷新。 */
    public static void fireChange() { fire(); }

    public static File apkFile(Context ctx, String slug) {
        return new File(ctx.getExternalFilesDir("apks"), slug + ".apk");
    }

    public static Task get(String slug) { return TASKS.get(slug); }

    public static boolean busy() {
        for (Task t : TASKS.values()) if (t.state == 0 || t.state == 1) return true;
        return false;
    }

    /**
     * 清理已安装完成的安装包：包已安装且已装版本号 ≥ 安装包版本号 → 删 apk + .sha。
     * 安装包版本号优先读 sidecar 第二行 "vc N"（下载时写入），旧文件回落清单版本。
     */
    public static void sweepInstalled(Context ctx) {
        File dir = ctx.getExternalFilesDir("apks");
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".apk")) continue;
            String slug = n.substring(0, n.length() - 4);
            Model.App a = Repo.find(slug);
            if (a == null) continue;                 // 已下架应用的手动包，不动
            File side = new File(f.getAbsolutePath() + ".sha");
            long apkVc = -1;
            if (side.exists()) {
                try {
                    for (String l : new String(java.nio.file.Files.readAllBytes(side.toPath()),
                            StandardCharsets.UTF_8).split("\n")) {
                        if (l.startsWith("vc ")) {
                            apkVc = Long.parseLong(l.substring(3).trim());
                        }
                    }
                } catch (Throwable ignore) { }
            }
            if (apkVc < 0) apkVc = a.versionCode;
            if (Apps.installedVersion(ctx, a) >= apkVc) {
                f.delete();
                side.delete();
            }
        }
    }

    public static synchronized void enqueue(Context ctx, Model.App a) {
        if (TASKS.containsKey(a.slug)) {
            Task t = TASKS.get(a.slug);
            if (t.state == 3 || t.state == 2) TASKS.remove(a.slug);
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
        t.error = null;
        fire();
        // 下载后完整文件里的旧 .sha 已不可信，删除（校验以清单 sha256 为准）
        new File(t.file.getAbsolutePath() + ".sha").delete();

        int attempt = 0;
        boolean useFallback = false;
        String lastErr = null;
        while (attempt <= MAX_RETRIES) {
            String url = t.url;
            String fb = t.app.urlFallback;
            if (useFallback && fb != null && !fb.equals(t.url)) url = fb;
            try {
                downloadOnce(ctx, t, url, useFallback);
                lastSuccessCount++;
                t.state = 2;
                notifyProgress(ctx, t, t.total, t.total, true);
                return;
            } catch (Exception e) {
                lastErr = e.getMessage();
                attempt++;
                // 首源失败次数用完且存在备用源 → 切备用源重新计重试次数
                if (!useFallback && attempt > MAX_RETRIES && t.app.urlFallback != null
                        && !t.app.urlFallback.equals(t.url)) {
                    useFallback = true;
                    attempt = 0;
                    continue;
                }
                if (attempt <= MAX_RETRIES) {
                    try { Thread.sleep(Math.min(3000L * attempt, 9000L)); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
        t.state = 3;
        t.error = lastErr != null ? lastErr : "下载失败";
        t.viaFallback = useFallback;
        notifyProgress(ctx, null, 0, 0, true);
    }

    /** 单次下载尝试（支持从 .part 断点续传；服务器不支持 206 时自动清 part 重来）。 */
    private static void downloadOnce(Context ctx, Task t, String url, boolean viaFallback)
            throws Exception {
        t.viaFallback = viaFallback;
        File tmp = new File(t.file.getAbsolutePath() + ".part");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
        if (code == 416) {                       // part 已超长（换源/换文件）：清掉重来
            tmp.delete();
            conn.disconnect();
            throw new Exception("续传偏移失效，重试中");
        }
        if (code == 206) {
            total = tmp.length() + parseTotal(conn.getHeaderField("Content-Range"));
            is = conn.getInputStream();
        } else if (code == 200) {
            total = conn.getContentLengthLong();
            is = conn.getInputStream();
            tmp.delete(); // 服务器不支持续传，重下
        } else {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        t.total = total > 0 ? total : Math.max(t.app.bytes, 1);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long written = tmp.exists() ? tmp.length() : 0;
        t.soFar = written;
        // 续传时先补算已有部分的摘要，保证最终 SHA256 可校验
        if (written > 0) {
            try (java.io.InputStream pin = new java.io.FileInputStream(tmp)) {
                byte[] pbuf = new byte[65536];
                int pn;
                while ((pn = pin.read(pbuf)) > 0) md.update(pbuf, 0, pn);
            }
        }
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
            tmp.delete();                        // 内容不对，part 留着只会一直失败
            throw new Exception("SHA256 校验失败");
        }
        // 完整下载：sidecar 记 sha + 版本号（供安装后清理判断）
        java.io.PrintWriter pw = new java.io.PrintWriter(t.file.getAbsolutePath() + ".sha", "UTF-8");
        pw.println(hex);
        pw.println("vc " + t.app.versionCode);
        pw.close();
        if (!tmp.renameTo(t.file)) {
            // rename 失败兜底拷贝
            java.nio.file.Files.copy(tmp.toPath(), t.file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tmp.delete();
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
                .setContentTitle(t.app.name + " " + fmt(so) + "/" + fmt(total)
                        + (t.viaFallback ? "（备用线路）" : ""))
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
