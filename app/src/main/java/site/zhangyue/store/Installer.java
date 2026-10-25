package site.zhangyue.store;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;

import java.io.File;

/** Installer — 校验通过后唤起系统安装（content:// + ACTION_INSTALL_PACKAGE）；装完自动删包。 */
public final class Installer {

    public interface Result { void onResult(boolean success, String msg); }

    private static Result sPending;

    private Installer() {}

    public static void install(Context ctx, Model.App a, File apk, Result cb) {
        sPending = cb;
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(ctx.getPackageName() + ".apk")
                .appendPath(apk.getName())
                .build();
        try {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            // 安装成功（系统广播 PACKAGE_ADDED/REPLACED）→ 自动删除安装包
            registerInstallListener(ctx.getApplicationContext(), a, apk);
            // 无结果回执（ACTION_INSTALL_PACKAGE 的 EXTRA_RETURN_RESULT 需要
            // startActivityForResult；这里简化为"已唤起安装器"）
            done(true, null);
        } catch (Exception e) {
            // 未授予"安装未知应用"权限时会抛异常
            tryOpenInstallPermission(ctx, e);
        }
    }

    /** 监听目标包安装/覆盖完成 → 清理安装包并刷新界面。 */
    private static void registerInstallListener(Context app, Model.App a, File apk) {
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String p = i.getData() != null ? i.getData().getSchemeSpecificPart() : null;
                    if (p == null || !p.equals(a.pkg)) return;
                    Apps.invalidate();
                    apk.delete();
                    new File(apk.getAbsolutePath() + ".sha").delete();
                    DlManager.sweepInstalled(c);   // 顺带全量清扫一遍
                    DlManager.fireChange();
                    try { c.unregisterReceiver(this); } catch (Throwable ignore) { }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                app.registerReceiver(r, f);
            }
        } catch (Throwable ignore) { }
    }

    private static void tryOpenInstallPermission(Context ctx, Exception why) {
        // 引导用户去开"允许安装未知应用"，回来重试
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            done(false, "请允许「安装未知应用」后返回重试");
        } catch (Exception e2) {
            done(false, "请开启「安装未知应用」权限后重试");
        }
    }

    static void onInstallResult(boolean success, String msg) {
        if (sPending != null) {
            sPending.onResult(success, msg);
            sPending = null;
        }
    }

    private static void done(boolean ok, String msg) {
        if (sPending != null) {
            sPending.onResult(ok, msg);
            sPending = null;
        }
    }

    private static void fail(String msg) { done(false, msg); }
}
