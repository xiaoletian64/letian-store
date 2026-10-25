package site.zhangyue.store;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.List;

/**
 * UpdateService — 商店后台自动更新服务（无前台、无通知，静默补装）。
 *
 * 行为：
 *   · 启动后先探测 CDN 可用性（Repo.probeDownloadHost），再拉目录比对版本；
 *   · 有更新 → 逐个 DlManager.enqueue（自动 CDN + 断点续传 + 失败重试由 DlManager 负责）；
 *   · 下载完成 → 静默 Installer.install（Android 12+ 会弹系统确认，用户点一下即装）；
 *   · force=true 的更新在 GET_UPDATE 时静默唤起安装器（用户可感知弹窗）；
 *   · 节流：同一清单版本只拉一次（PREF updates_seen）；
 *   · 空闲兜底重扫：START_ID_SCAN，常驻场景下周期兜底；
 *   · 应用自更新走 SelfUpdate 自有通道。
 *
 * 调度（v3.0.2 用最小改动方案）：
 *   · MainActivity onResume → startService（补拉当次更新，服务自己节流防重）；
 *   · 系统空闲时由 UpdateBootReceiver 估算时长后决定是否 startService（需要空闲兜底时再启用）。
 */
public class UpdateService extends Service {

    public static final String CHANNEL = "upd";
    public static final int NID_CHECK = 43;
    public static final int NID_DONE = 44;

    static final String ACT_CHECK = "site.zhangyue.store.CHECK_UPDATES";
    static final String ACT_GET_UPDATE = "site.zhangyue.store.GET_UPDATE";
    static final String ACT_SCAN = "site.zhangyue.store.SCAN_UPDATES";
    static final String EXTRA_SLUG = "slug";

    private static final long MIN_INTERVAL = 30 * 60_000L;   // 同一清单 30 分钟内不重复拉

    private static boolean sRunning;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mStarted;

    public static void start(Context c) {
        try {
            c.startService(new Intent(c, UpdateService.class).setAction(ACT_CHECK));
        } catch (Throwable ignore) { }
    }

    public static boolean running() { return sRunning; }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "自动更新",
                    NotificationManager.IMPORTANCE_MIN));
        }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String act = i != null ? i.getAction() : null;
        if (ACT_SCAN.equals(act)) {
            // 节流兜底重扫：距上次成功检查不足 30 分钟直接退出
            SharedPreferences sp = getSharedPreferences("store_cfg", MODE_PRIVATE);
            long last = sp.getLong("upd_ok_at", 0);
            if (System.currentTimeMillis() - last < MIN_INTERVAL) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (!mStarted) {
            mStarted = true;
            mHandler.post(this::run);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        super.onDestroy();
    }

    private void run() {
        if (!Net.isOnline(this)) { finishWork(); return; }
        // 先探测下载域（影响本次任务的 url 选择），再拉目录
        Repo.refresh(this, (ok, err) -> {
            if (!ok) { finishWork(); return; }
            android.content.SharedPreferences sp =
                    getSharedPreferences("store_cfg", MODE_PRIVATE);
            sp.edit().putLong("upd_ok_at", System.currentTimeMillis()).apply();
            List<Model.App> pend = UpdateChecker.pending(this, Repo.catalog(),
                    Http.quiet(Repo.updatesUrl(this)));
            if (pend.isEmpty()) { finishWork(); return; }
            // 去掉商店自更新（走 SelfUpdate 通道 + 用户确认，不静默）
            for (Model.App a : new java.util.ArrayList<>(pend)) {
                if (a.slug.equals(Repo.selfSlug())) pend.remove(a);
            }
            if (pend.isEmpty()) { finishWork(); return; }
            final java.util.concurrent.atomic.AtomicInteger left =
                    new java.util.concurrent.atomic.AtomicInteger(pend.size());
            for (final Model.App a : pend) {
                DlManager.Listener l = new DlManager.Listener() {
                    @Override public void onTasksChanged() {
                        DlManager.Task t = DlManager.get(a.slug);
                        if (t == null) {
                            DlManager.removeListener(this);
                            return;
                        }
                        if (t.state == 2) {
                            DlManager.removeListener(this);
                            // force 更新：下载完直接唤起安装器（用户可感知弹窗）
                            if (a.force) {
                                Installer.install(UpdateService.this, a,
                                        DlManager.apkFile(UpdateService.this, a.slug), null);
                            }
                            if (left.decrementAndGet() == 0) maybeNotify();
                        } else if (t.state == 3) {
                            DlManager.removeListener(this);
                            if (left.decrementAndGet() == 0) maybeNotify();
                        }
                    }
                };
                DlManager.addListener(l);
                DlManager.enqueue(this, a);
            }
        });
    }

    private void maybeNotify() {
        mHandler.post(() -> {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification b = new Notification.Builder(this,
                    Build.VERSION.SDK_INT >= 26 ? CHANNEL : null)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("自动更新已就绪")
                    .setContentText("更新的应用已下载完成，点击安装")
                    .setAutoCancel(true)
                    .setContentIntent(android.app.PendingIntent.getActivity(this, 0,
                            new Intent(this, DownloadActivity.class),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                    | android.app.PendingIntent.FLAG_IMMUTABLE))
                    .build();
            nm.notify(NID_DONE, b);
        });
    }

    private void finishWork() {
        mHandler.post(this::stopSelf);
    }
}
