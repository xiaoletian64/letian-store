package site.zhangyue.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/**
 * RuntimeConfig — 配置优先级：
 *   1. 注入值（StoreConfig，打包时写死）
 *   2. 构建时写入 assets 的 store.properties（可选，key=value）
 *   3. /sdcard/Android/data/<pkg>/files/store.properties（运行时覆盖，调机用）
 *   4. 默认：与 INDEX_URL 同源的 /apps/ 目录
 * 同源推导规则：https://a.example/app/list.json → base https://a.example/app/
 */
public final class RuntimeConfig {
    private static final String PREF = "store_cfg";
    private static String sBase;      // 以 / 结尾的清单目录
    private static String sIndexUrl;
    private static String sWebUrl;
    private static String sCdnBase;

    private RuntimeConfig() {}

    public static synchronized void init(Context ctx) {
        if (sIndexUrl != null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String injected = StoreConfig.clean(StoreConfig.INDEX_URL);
        String url = sp.getString("index_url", injected);
        java.util.Properties props = readProps(ctx, "store.properties");
        if (url == null && props != null) {
            url = props.getProperty("index_url", null);
        }
        if (url == null) {
            // 最后兜底：同目录下 apps.json（离线调试场景）
            url = "https://example.invalid/apps/apps.json";
        }
        sIndexUrl = url;
        sBase = baseOf(url);
        String web = StoreConfig.clean(StoreConfig.WEB_URL);
        if (web == null && props != null) web = props.getProperty("web_url", null);
        sWebUrl = web;
        String cdn = StoreConfig.clean(StoreConfig.CDN_BASE);
        if (cdn == null && props != null) cdn = props.getProperty("cdn_base", null);
        sCdnBase = cdn;
    }

    /** 读取覆盖配置（assets 内置 → 外部 files 目录）。 */
    static java.util.Properties readProps(Context ctx, String name) {
        java.util.Properties p = new java.util.Properties();
        try {
            android.content.res.AssetManager am = ctx.getAssets();
            if (am != null) {
                try (java.io.InputStream is = am.open(name)) {
                    p.load(is);
                    return p.isEmpty() ? null : p;
                } catch (Exception ignore) { }
            }
        } catch (Throwable ignore) { }
        try {
            File ext = new File(ctx.getExternalFilesDir(null), name);
            if (ext.exists()) {
                try (FileInputStream is = new FileInputStream(ext)) {
                    p.load(is);
                    return p.isEmpty() ? null : p;
                }
            }
        } catch (Throwable ignore) { }
        return null;
    }

    static String baseOf(String indexUrl) {
        int i = indexUrl.lastIndexOf('/');
        return i > 8 ? indexUrl.substring(0, i + 1) : indexUrl;
    }

    public static String indexUrl(Context ctx) { init(ctx); return sIndexUrl; }
    /** 清单同源目录（icons、相对 url 解析基准）。 */
    public static String base(Context ctx) { init(ctx); return sBase; }
    public static String webUrl(Context ctx) { init(ctx); return sWebUrl; }

    /** 大文件下载域：优先注入值；否则若清单里任何 url 命中 cdn 域则用之。 */
    public static String cdnBase(Context ctx) {
        init(ctx);
        return sCdnBase;
    }

    /** 把清单里的相对 url 解析成绝对地址。 */
    public static String abs(Context ctx, String u) {
        if (u == null) return null;
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        String b = base(ctx);
        if (u.startsWith("/")) {
            int i = b.indexOf('/', 8);
            return (i > 0 ? b.substring(0, i) : b) + u;
        }
        return b + u;
    }

    /** 下载域映射：清单主站的 apk 按路径映射到 cdn 域（加速）。 */
    public static String accelerate(Context ctx, String url) {
        String cdn = cdnBase(ctx);
        if (cdn == null || url == null) return url;
        if (!url.startsWith("https://")) return url;
        if (url.startsWith(cdn)) return url;
        if (url.endsWith(".apk") || url.endsWith(".apks")) {
            int i = url.indexOf('/', 8);
            if (i > 0) return cdn + url.substring(i + 1);
        }
        return url;
    }

    public static void overrideIndex(Context ctx, String url) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("index_url", url).apply();
        sIndexUrl = null; // 触发重新 init
        init(ctx);
    }
}
