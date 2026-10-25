package site.zhangyue.store;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** UpdateChecker — 后台自动更新检测（updates.json 直读，失败回落 apps.json 全量比对）。 */
public final class UpdateChecker {

    private UpdateChecker() {}

    /**
     * 返回需要更新的清单应用。
     * updates.json（与 apps.json 同目录）格式：[{"slug":"xx","vc":10},…]
     * —— 服务端只推有更新的条目，客户端按 vc 与已装版本比对；
     * updates.json 缺失/解析失败时回落为 apps.json 全量 versionCode 比对。
     */
    public static List<Model.App> pending(Context ctx, Model.Catalog c, String updatesJson) {
        List<Model.App> out = new ArrayList<>();
        if (c == null) return out;
        if (updatesJson != null) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(updatesJson);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    Model.App a = Repo.find(o.optString("slug"));
                    if (a == null) continue;
                    long vc = o.optLong("vc", 0);
                    boolean newer = vc > 0
                            ? vc > Apps.installedVersion(ctx, a)
                            : Apps.status(ctx, a) == Apps.UPDATABLE;
                    if (newer && DlManager.get(a.slug) == null) out.add(a);
                }
                return out;
            } catch (Throwable ignore) { }
        }
        for (Model.App a : c.apps) {
            if (Apps.status(ctx, a) == Apps.UPDATABLE && DlManager.get(a.slug) == null) out.add(a);
        }
        return out;
    }
}
