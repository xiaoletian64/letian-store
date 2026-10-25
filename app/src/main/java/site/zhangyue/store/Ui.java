package site.zhangyue.store;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

/**
 * Ui — 全局调色板 + 系统栏适配 + 通知权限引导。
 *
 * 自适应策略（无 AndroidX 依赖，纯框架能力，兼容各厂商 ROM）：
 *   · DayNight：主题用 framework Theme.DeviceDefault.DayNight（values-v29），
 *     深浅色跟随系统；本类在 Activity onCreate 时刷新调色板供代码视图使用；
 *   · 全面屏：API 30+ 给根视图挂 WindowInsets 监听，按系统栏+挖孔内缩 padding，
 *     Android 15 强制 edge-to-edge 时内容不再顶进状态栏/手势条；
 *   · 自适应图标：mipmap-anydpi-v26 双层图标，各桌面（含澎湃OS）自动成形；
 *   · 通知权限：Android 13+ 首次启动请求一次 POST_NOTIFICATIONS。
 */
public final class Ui {

    // ===== 固定强调色（深浅两态各定一版）=====
    public static int BLUE, GREEN, RED;
    // ===== 随深浅色切换的表面色 =====
    public static int BG, CARD, TXT, SUB, SOFT, DISABLED, HINT, GREY;

    static {
        refreshPaletteStatic(false);
    }

    private Ui() {}

    /** 在每个 Activity 的 onCreate 最先调用：按系统深浅色刷新调色板。 */
    public static void refreshPalette(Context c) {
        boolean night = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        refreshPaletteStatic(night);
    }

    private static void refreshPaletteStatic(boolean night) {
        if (night) {
            BG = 0xFF111315;
            CARD = 0xFF1C1F23;
            TXT = 0xFFECEDEE;
            SUB = 0xFF9AA3AC;
            SOFT = 0xFF262B31;
            DISABLED = 0xFF3A4048;
            HINT = 0xFF8A9099;
            GREY = 0xFF2A2E33;
            BLUE = 0xFF6B93F5;
            GREEN = 0xFF4CC07A;
            RED = 0xFFF26B70;
        } else {
            BG = 0xFFF5F6F8;
            CARD = 0xFFFFFFFF;
            TXT = 0xFF17181A;
            SUB = 0xFF6B7078;
            SOFT = 0xFFECEEF2;
            DISABLED = 0xFFC9CED6;
            HINT = 0xFF9AA0A8;
            GREY = 0xFFEDF0F5;
            BLUE = 0xFF2F6FED;
            GREEN = 0xFF2F9E5F;
            RED = 0xFFE5484D;
        }
    }

    /**
     * 全面屏适配：给根视图挂 insets 监听（API 30+），
     * 系统栏/刘海区域用 padding 内缩；低版本由主题 statusBarColor 兜底。
     */
    public static void applyWindow(Activity a, View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets s = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(s.left, s.top, s.right, s.bottom);
                return android.view.WindowInsets.CONSUMED;
            });
            // 请求一次分发（视图树已就绪时补发）
            root.requestApplyInsets();
        } catch (Throwable ignore) { }
    }

    /** Android 13+ 通知权限：整个生命周期只请求一次。 */
    public static void requestNotifPerm(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            android.content.SharedPreferences sp =
                    a.getSharedPreferences("store_cfg", Context.MODE_PRIVATE);
            if (sp.getBoolean("notif_asked", false)) return;
            String p = android.Manifest.permission.POST_NOTIFICATIONS;
            if (a.checkSelfPermission(p)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                sp.edit().putBoolean("notif_asked", true).apply();
                return;
            }
            sp.edit().putBoolean("notif_asked", true).apply();
            a.requestPermissions(new String[]{p}, 1);
        } catch (Throwable ignore) { }
    }
}
