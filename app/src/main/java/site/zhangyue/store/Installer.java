package site.zhangyue.store;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Verified, user-confirmed system sessions. Only the system result means installed. */
public final class Installer {
    public interface Result { void onResult(boolean success, String msg); }
    private static final Map<Integer, Result> CALLBACKS = new ConcurrentHashMap<>();
    private static final String PREFS = "install-sessions";
    private Installer() {}

    static SharedPreferences state(Context c) { return c.getSharedPreferences(PREFS, 0); }
    private static long version(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }
    private static String digest(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return out.toString();
    }
    private static Set<String> certs(Signature[] signatures) throws Exception {
        Set<String> out = new HashSet<>();
        if (signatures != null) for (Signature s : signatures) out.add(digest(s.toByteArray()));
        if (out.isEmpty()) throw new SecurityException("APK缺少可验证签名");
        return out;
    }
    private static void verifySigner(PackageInfo before, PackageInfo archive) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            if (before.signingInfo == null || archive.signingInfo == null)
                throw new SecurityException("无法读取签名信息");
            Set<String> installed = certs(before.signingInfo.getApkContentsSigners());
            if (before.signingInfo.hasMultipleSigners() || archive.signingInfo.hasMultipleSigners()) {
                if (!installed.equals(certs(archive.signingInfo.getApkContentsSigners())))
                    throw new SecurityException("签名不兼容，请保留现有应用");
            } else if (!certs(archive.signingInfo.getSigningCertificateHistory()).containsAll(installed)) {
                throw new SecurityException("签名不兼容，请保留现有应用");
            }
        } else if (!certs(before.signatures).equals(certs(archive.signatures))) {
            throw new SecurityException("签名不兼容，请保留现有应用");
        }
    }
    static void validate(Context c, Model.App app, File file) throws Exception {
        File dir = c.getExternalFilesDir("apks");
        if (dir == null || !file.isFile() || !file.getAbsoluteFile().equals(file.getCanonicalFile())
                || !file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))
            throw new SecurityException("只允许安装已下载并校验的文件");
        if (app.sha256 == null || !app.sha256.matches("(?i)[0-9a-f]{64}") || app.versionCode <= 0)
            throw new SecurityException("目录缺少有效版本或SHA256，停止安装");
        if (app.bytes > 0 && file.length() != app.bytes) throw new SecurityException("安装包大小不匹配");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = c.getPackageManager().getPackageArchiveInfo(file.getPath(), flags);
        if (archive == null || !archive.packageName.equals(app.pkg) || version(archive) != app.versionCode)
            throw new SecurityException("安装包身份或版本与目录不符");
        try {
            PackageInfo before = c.getPackageManager().getPackageInfo(app.pkg, flags);
            if (version(archive) <= version(before)) throw new SecurityException("当前版本不需要覆盖或降级");
            verifySigner(before, archive);
        } catch (PackageManager.NameNotFoundException freshInstall) { /* System verifies fresh APK signatures too. */ }
    }
    public static void install(Context context, Model.App app, File file, Result callback) {
        Context c = context.getApplicationContext();
        new Thread(() -> {
            PackageInstaller installer = c.getPackageManager().getPackageInstaller();
            int id = -1;
            try {
                validate(c, app, file);
                if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
                    Intent permission = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(permission);
                    callback.onResult(false, "请在系统设置允许此来源安装，再返回重试；不要关闭安全保护");
                    return;
                }
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.pkg);
                params.setSize(file.length());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    MessageDigest hash = MessageDigest.getInstance("SHA-256");
                    long size = 0;
                    try (InputStream in = new FileInputStream(file); OutputStream out = session.openWrite("base.apk", 0, file.length())) {
                        byte[] buffer = new byte[65536]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            out.write(buffer, 0, count); hash.update(buffer, 0, count); size += count;
                        }
                        session.fsync(out);
                    }
                    StringBuilder actual = new StringBuilder();
                    for (byte b : hash.digest()) actual.append(String.format(java.util.Locale.US, "%02x", b & 255));
                    if (!actual.toString().equalsIgnoreCase(app.sha256) || size != file.length()
                            || (app.bytes > 0 && size != app.bytes)) throw new SecurityException("写入系统安装会话后的摘要或大小不符");
                    String token = UUID.randomUUID().toString();
                    if (!state(c).edit().putString(id + ":token", token).putString(id + ":package", app.pkg)
                            .putLong(id + ":version", app.versionCode).putString(id + ":state", "committed")
                            .putInt("last-session", id).commit()) throw new IOException("无法保存安装会话");
                    Intent result = new Intent(c, InstallReceiver.class).setAction(c.getPackageName() + ".INSTALL_RESULT")
                            .setData(new Uri.Builder().scheme("store-install").authority("result")
                                    .appendPath(String.valueOf(id)).appendPath(token).build());
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                    PendingIntent pending = PendingIntent.getBroadcast(c, id, result, flags);
                    CALLBACKS.put(id, callback);
                    session.commit(pending.getIntentSender());
                }
            } catch (Exception failure) {
                if (id >= 0) {
                    try { installer.abandonSession(id); } catch (Exception ignored) {}
                    CALLBACKS.remove(id);
                    state(c).edit().remove(id + ":token").putString(id + ":state", "failed").apply();
                }
                callback.onResult(false, "未安装：" + failure.getMessage());
            }
        }, "store-install").start();
    }
    static void acceptResult(Context c, Intent intent) {
        Uri uri = intent.getData();
        if (!((c.getPackageName() + ".INSTALL_RESULT").equals(intent.getAction())) || uri == null
                || !"store-install".equals(uri.getScheme()) || !"result".equals(uri.getAuthority())
                || uri.getPathSegments().size() != 2) return;
        int id;
        try { id = Integer.parseInt(uri.getPathSegments().get(0)); } catch (NumberFormatException bad) { return; }
        SharedPreferences prefs = state(c);
        String token = prefs.getString(id + ":token", "");
        if (token.isEmpty() || !token.equals(uri.getPathSegments().get(1))
                || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                prefs.edit().putString(id + ":state", "awaiting-user").commit();
                try {
                    c.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception noUi) { /* Keep failure visible, never report installed. */ }
            }
            status = PackageInstaller.STATUS_FAILURE;
        }
        boolean success = status == PackageInstaller.STATUS_SUCCESS;
        if (success) {
            try {
                success = version(c.getPackageManager().getPackageInfo(prefs.getString(id + ":package", ""), 0))
                        >= prefs.getLong(id + ":version", Long.MAX_VALUE);
            } catch (Exception missing) { success = false; }
        }
        String message = success ? "安装完成" : "系统未完成安装，请保留原应用；取消或安全拦截都不会记作成功";
        prefs.edit().putString(id + ":state", success ? "installed" : "failed").putString(id + ":message", message)
                .remove(id + ":token").commit();
        Result callback = CALLBACKS.remove(id);
        if (callback != null) callback.onResult(success, message);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(DetailActivity::onUiInvalidated);
    }
}
