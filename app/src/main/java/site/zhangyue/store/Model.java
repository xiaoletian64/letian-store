package site.zhangyue.store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Model — apps.json 数据模型与解析。 */
public final class Model {

    public static final class App {
        public String slug, name, pkg, versionName, summary, notes, icon, url, urlFallback, sha256, category;
        public long versionCode, bytes;
        public boolean force;
        public String[] tags = new String[0];
        public String[] features = new String[0];
        public String web, source;
        public String updated;

        public String sizeText() {
            if (bytes <= 0) return "";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576f);
        }
    }

    public static final class Catalog {
        public String updated;
        public List<App> apps = new ArrayList<>();
    }

    public interface Cb { void onDone(Catalog c, Exception e); }

    public static void fetch(String indexUrl, final Cb cb) {
        final String u = indexUrl;
        new Thread(() -> {
            try {
                String body = httpGet(u);
                Catalog c = parse(body);
                cb.onDone(c, null);
            } catch (Exception e) {
                cb.onDone(null, e);
            }
        }, "repo-fetch").start();
    }

    public static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "LetianStore/3.0 (Android)");
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code + " " + url);
        InputStream is = conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        return sb.toString();
    }

    public static Catalog parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        Catalog c = new Catalog();
        c.updated = root.optString("updated", null);
        JSONArray arr = root.optJSONArray("apps");
        if (arr == null) {
            // 兼容：直接给数组的老格式
            arr = new JSONArray(body);
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            App a = new App();
            a.slug = o.optString("slug");
            a.name = o.optString("name", a.slug);
            a.pkg = o.optString("package", o.optString("pkg", ""));
            a.versionName = o.optString("versionName", o.optString("version", ""));
            a.versionCode = o.optLong("versionCode", 0);
            a.summary = o.optString("summary", "");
            a.notes = o.optString("notes", "");
            a.icon = o.optString("icon", null);
            a.url = o.optString("url", null);
            a.urlFallback = o.optString("urlFallback", null);
            a.sha256 = o.optString("sha256", null);
            a.bytes = o.optLong("bytes", 0);
            a.category = o.optString("category", "应用");
            a.force = o.optBoolean("force", false);
            a.updated = o.optString("updated", null);
            a.web = o.optString("web", null);
            a.source = o.optString("source", null);
            JSONArray tags = o.optJSONArray("tags");
            if (tags != null) {
                String[] ts = new String[tags.length()];
                for (int j = 0; j < ts.length; j++) ts[j] = tags.optString(j);
                a.tags = ts;
            }
            JSONArray feats = o.optJSONArray("features");
            if (feats != null) {
                String[] fs = new String[feats.length()];
                for (int j = 0; j < fs.length; j++) fs[j] = feats.optString(j);
                a.features = fs;
            }
            if (a.slug != null && a.url != null) c.apps.add(a);
        }
        Collections.sort(c.apps, (x, y) -> {
            int cx = order(x), cy = order(y);
            if (cx != cy) return cx - cy;
            return x.name.compareTo(y.name);
        });
        return c;
    }

    /** 排序：商店自己、官方应用靠前，其余按更新时间新→旧。 */
    private static int order(App a) {
        if (a.slug.equals(SelfUpdate.SELF_SLUG_HINT)) return 0;
        if (a.source != null) return 1;      // 开源/上游应用
        return 2;
    }

    private Model() {}
}
