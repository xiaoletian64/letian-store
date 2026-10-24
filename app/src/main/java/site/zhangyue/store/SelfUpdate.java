package site.zhangyue.store;

import android.content.Context;

import org.json.JSONObject;

/** SelfUpdate — 商店自身更新检测。 */
public final class SelfUpdate {
    static String SELF_SLUG_HINT = initHint();

    private SelfUpdate() {}

    private static String initHint() {
        String s = StoreConfig.clean(StoreConfig.SELF_SLUG);
        return s != null ? s : "letian-store";
    }

    private static boolean sHasUpdate;
    private static Model.App sSelf;
    private static long sInstalledVc = -1;

    /** 目录刷新后调用：判断商店自身是否有新版。 */
    public static void noteCatalog(Context ctx, Model.Catalog c) {
        sHasUpdate = false;
        sSelf = null;
        for (Model.App a : c.apps) {
            if (SelfUpdateHint.matches(a)) {
                sSelf = a;
                break;
            }
        }
        if (sSelf == null) return;
        { // Read the real version after returning from the system installer.
            android.content.pm.PackageInfo pi = null;
            try {
                pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            } catch (Exception ignore) { }
            if (pi != null) {
                try {
                    sInstalledVc = pi.getLongVersionCode();
                } catch (Throwable t) {
                    sInstalledVc = pi.versionCode;
                }
            } else {
                sInstalledVc = 0;
            }
        }
        sHasUpdate = sSelf.versionCode > sInstalledVc;
    }

    public static boolean hasUpdate() { return sHasUpdate; }
    public static Model.App self() { return sSelf; }

    private static final class SelfUpdateHint {
        static boolean matches(Model.App a) {
            String hint = SELF_SLUG_HINT;
            return a.slug.equals(hint)
                    || (a.pkg != null && a.pkg.equals("site.zhangyue.store"));
        }
    }
}
