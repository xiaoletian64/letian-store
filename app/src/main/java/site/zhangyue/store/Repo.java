package site.zhangyue.store;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Repo — 目录缓存 + 刷新 + 下载域探测加速。 */
public final class Repo {
    private static Model.Catalog sCatalog;
    private static final List<Cb> S_CB = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static String lastError;
    public static long lastFetchAt;

    public interface Cb { void onCatalog(boolean ok, String error); }

    private Repo() {}

    /** 自更新 slug 提示（SelfUpdate 用）。 */
    static String selfSlug() {
        String s = StoreConfig.clean(StoreConfig.SELF_SLUG);
        return s != null ? s : "letian-store";
    }

    public static Model.App find(String slug) {
        if (sCatalog == null || slug == null) return null;
        for (Model.App a : sCatalog.apps) if (slug.equals(a.slug)) return a;
        return null;
    }

    public static Model.Catalog catalog() { return sCatalog; }

    public static synchronized void refresh(Context ctx, Cb cb) {
        if (cb != null && !S_CB.contains(cb)) S_CB.add(cb);
        Model.fetch(RuntimeConfig.indexUrl(ctx), (c, e) -> MAIN.post(() -> {
            if (c != null) {
                sCatalog = c;
                lastError = null;
                lastFetchAt = System.currentTimeMillis();
                SelfUpdate.noteCatalog(ctx, c);
                probeDownloadHost(ctx, c);
            } else {
                lastError = String.valueOf(e != null ? e.getMessage() : "unknown");
            }
            List<Cb> done = new ArrayList<>(S_CB);
            S_CB.clear();
            for (Cb x : done) x.onCatalog(c != null, lastError);
        }));
    }

    public static boolean stale() {
        return sCatalog == null || System.currentTimeMillis() - lastFetchAt > 30 * 60_000L;
    }

    /**
     * 下载域探测：清单若仍指旧域，测一次新 CDN 域的 HEAD，通了本次会话内
     * 把 .apk 下载映射到 CDN（RuntimeConfig.accelerate）。
     */
    private static void probeDownloadHost(Context ctx, Model.Catalog c) {
        String cdn = RuntimeConfig.cdnBase(ctx);
        if (cdn == null) return;
        String probe = null;
        for (Model.App a : c.apps) {
            if (a.url != null && a.url.endsWith(".apk")) { probe = a.url; break; }
        }
        if (probe == null || probe.startsWith(cdn)) return;
        final String p = RuntimeConfig.accelerate(ctx, probe);
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(p).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200 || code == 206) {
                    android.content.SharedPreferences sp =
                            ctx.getSharedPreferences("store_cfg", Context.MODE_PRIVATE);
                    sp.edit().putLong("cdn_ok_at", System.currentTimeMillis()).apply();
                }
            } catch (Throwable ignore) { }
        }, "cdn-probe").start();
    }

    /** 点下载时若 CDN 探测通过，把任务的 url 换成 CDN 域。 */
    public static void preferDownloadHost(Context ctx, Model.App a) {
        android.content.SharedPreferences sp =
                ctx.getSharedPreferences("store_cfg", Context.MODE_PRIVATE);
        long okAt = sp.getLong("cdn_ok_at", 0);
        if (okAt > 0 && System.currentTimeMillis() - okAt < 6 * 3600_000L) {
            DlManager.Task t = DlManager.get(a.slug);
            if (t != null) {
                String fast = RuntimeConfig.accelerate(ctx, t.url);
                if (fast != null) t.url = fast;
            }
        }
    }
}
