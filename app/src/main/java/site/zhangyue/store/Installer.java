package site.zhangyue.store;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;

/** Installer — 校验通过后唤起系统安装（content:// + ACTION_INSTALL_PACKAGE）。 */
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
            // 无结果回执（ACTION_INSTALL_PACKAGE 的 EXTRA_RETURN_RESULT 需要
            // startActivityForResult；这里简化为"已唤起安装器"）
            done(true, null);
        } catch (Exception e) {
            // 未授予"安装未知应用"权限时会抛异常
            tryOpenInstallPermission(ctx, e);
        }
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
