package site.zhangyue.store;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** IconCache — 磁盘图标缓存（内存 LRU + 文件缓存）。 */
public final class IconCache {
    private static final int MEM_MAX = 64;
    private static final android.util.LruCache<String, android.graphics.Bitmap> MEM =
            new android.util.LruCache<String, android.graphics.Bitmap>(MEM_MAX) {
                @Override protected int sizeOf(String k, android.graphics.Bitmap v) {
                    return v.getByteCount() / 1024;
                }
            };

    private IconCache() {}

    public static File fileFor(Context ctx, String slug) {
        return new File(ctx.getCacheDir(), "icon_" + slug + ".png");
    }

    public static android.graphics.Bitmap get(Context ctx, String slug, String url) {
        synchronized (MEM) {
            android.graphics.Bitmap b = MEM.get(slug);
            if (b != null) return b;
        }
        File f = fileFor(ctx, slug);
        if (f.exists()) {
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) {
                synchronized (MEM) { MEM.put(slug, b); }
                return b;
            }
        }
        if (url == null) return null;
        // 后台拉取，完成后通知重绘
        new Thread(() -> {
            try {
                byte[] raw = Http.bytes(url);
                android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (b != null) {
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                    b.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fo);
                    fo.close();
                    synchronized (MEM) { MEM.put(slug, b); }
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    for (Runnable r : REFRESH) h.post(r);
                }
            } catch (Exception ignore) { }
        }, "icon-" + slug).start();
        return null;
    }

    static final List<Runnable> REFRESH = new ArrayList<>();

    public static void addRefresh(Runnable r) {
        if (!REFRESH.contains(r)) REFRESH.add(r);
    }

    public static void clearRefresh(Runnable r) { REFRESH.remove(r); }
}
