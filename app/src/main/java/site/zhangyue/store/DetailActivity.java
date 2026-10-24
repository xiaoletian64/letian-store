package site.zhangyue.store;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/** DetailActivity — 应用详情页。 */
public class DetailActivity extends Activity {

    static final int BLUE = 0xFF2F6FED, RED = 0xFFE5484D, GREY = 0xFFEDF0F5, TXT = 0xFF17181A, SUB = 0xFF6B7078;

    private Model.App app;
    private TextView actionBtn, stateText;
    private LinearLayout progressWrap;
    private TextView progressText;
    private android.widget.ProgressBar progress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String slug = getIntent().getStringExtra("slug");
        app = Repo.find(slug);
        if (app == null) {
            Toast.makeText(this, "应用不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        build();
        REFRESH_ALL.add(iconRefresher);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        REFRESH_ALL.remove(iconRefresher);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F6F8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        // 头部：icon + 名字 + 版本行
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(72)));
        android.graphics.Bitmap ic = IconCache.get(this, app.slug, app.icon);
        if (ic != null) icon.setImageBitmap(ic);
        else {
            icon.setImageDrawable(TextDrawable.forApp(app.name));
            IconCache.addRefresh(() -> {
                android.graphics.Bitmap b2 = IconCache.get(DetailActivity.this, app.slug, app.icon);
                if (b2 != null) icon.setImageBitmap(b2);
            });
        }
        head.addView(icon);
        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(14), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(app.name);
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(TXT);
        headText.addView(name);
        TextView meta = new TextView(this);
        meta.setText(Apps.versionText(app) + (app.bytes > 0 ? " · " + app.sizeText() : ""));
        meta.setTextSize(13);
        meta.setTextColor(SUB);
        headText.addView(meta);
        head.addView(headText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(head);

        // 标签行
        if (app.tags.length > 0 || app.category != null) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(10), 0, 0);
            if (app.category != null) chips.addView(chip(app.category, BLUE));
            for (String t : app.tags) chips.addView(chip(t, SUB));
            root.addView(chips);
        }

        // 主操作按钮
        actionBtn = new TextView(this);
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setPadding(0, dp(13), 0, dp(13));
        actionBtn.setTextSize(16);
        actionBtn.setTypeface(Typeface.DEFAULT_BOLD);
        actionBtn.setTextColor(Color.WHITE);
        actionBtn.setBackground(round(BLUE, 12));
        actionBtn.setClickable(true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(16);
        root.addView(actionBtn, ap);
        actionBtn.setOnClickListener(v -> onAction());

        stateText = new TextView(this);
        stateText.setTextSize(12);
        stateText.setTextColor(SUB);
        stateText.setPadding(dp(4), dp(6), dp(4), 0);
        stateText.setGravity(Gravity.CENTER);
        root.addView(stateText);

        // 进度条
        progressWrap = new LinearLayout(this);
        progressWrap.setOrientation(LinearLayout.VERTICAL);
        progressWrap.setVisibility(View.GONE);
        progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(10000);
        progressWrap.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        progressText = new TextView(this);
        progressText.setTextSize(12);
        progressText.setTextColor(SUB);
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(4), 0, 0);
        progressWrap.addView(progressText);
        root.addView(progressWrap);

        // 简介
        addSection(root, "简介", app.summary);
        if (app.notes != null && !app.notes.isEmpty()) addSection(root, "更新说明", app.notes);
        if (app.features != null && app.features.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String f : app.features) sb.append("· ").append(f).append("\n");
            addSection(root, "功能", sb.toString().trim());
        }

        // 次级链接
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setPadding(0, dp(14), 0, 0);
        links.setGravity(Gravity.CENTER);
        if (app.web != null) links.addView(link("网页版 ↗", app.web));
        if (app.source != null) links.addView(link("开源仓库 ↗", app.source));
        links.addView(link("校验信息", "sha"));
        root.addView(links);
    }

    public String[] features() { return app.features; }

    private void addSection(LinearLayout root, String title, String body) {
        if (body == null || body.isEmpty()) return;
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextSize(15);
        t1.setTextColor(TXT);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.topMargin = dp(20);
        root.addView(t1, p1);
        TextView t2 = new TextView(this);
        t2.setText(body);
        t2.setTextSize(14);
        t2.setTextColor(SUB);
        t2.setLineSpacing(dp(3), 1);
        root.addView(t2);
    }

    private TextView chip(String text, int color) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(11);
        c.setTextColor(color);
        c.setPadding(dp(8), dp(3), dp(8), dp(3));
        c.setBackground(round((color & 0x00FFFFFF) | 0x14000000, 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        c.setLayoutParams(p);
        return c;
    }

    private TextView link(String text, final String what) {
        TextView l = new TextView(this);
        l.setText(text);
        l.setTextSize(13);
        l.setTextColor(BLUE);
        l.setPadding(dp(10), dp(6), dp(10), dp(6));
        l.setClickable(true);
        l.setOnClickListener(v -> {
            if ("sha".equals(what)) {
                File f = DlManager.apkFile(DetailActivity.this, app.slug);
                String msg = f.exists()
                        ? "本地 SHA256：\n" + Apps.sha256(f)
                        : "清单 SHA256：\n" + (app.sha256 != null ? app.sha256 : "未提供");
                new android.app.AlertDialog.Builder(this)
                        .setTitle("完整性校验").setMessage(msg)
                        .setPositiveButton("好", null).show();
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(what)));
                } catch (Exception ignore) { }
            }
        });
        return l;
    }

    private void onAction() {
        int st = Apps.status(this, app);
        File ready = Apps.readyApk(this, app);
        if (ready != null && st != Apps.CURRENT) {
            install(ready);
            return;
        }
        if (st == Apps.CURRENT) {
            open();
            return;
        }
        DlManager.Task t = DlManager.get(app.slug);
        if (t != null && (t.state == 0 || t.state == 1)) return; // 下载中忽略
        DlManager.enqueue(this, app);
        Repo.preferDownloadHost(this, app);
    }

    private void install(File apk) {
        Installer.install(this, app, apk, (ok, msg) -> runOnUiThread(() -> {
            if (!ok && msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            refresh();
        }));
    }

    private void open() {
        Intent i = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (i == null) {
            Toast.makeText(this, "无法启动：" + app.pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(i);
    }

    private final DlManager.Listener dlListener = () -> DetailActivity.onUiInvalidated();

    @Override
    protected void onResume() {
        super.onResume();
        DlManager.addListener(dlListener);
        REFRESH_ALL.add(iconRefresher);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DlManager.removeListener(dlListener);
        REFRESH_ALL.remove(iconRefresher);
        IconCache.clearRefresh(iconRefresher);
    }

    private final Runnable iconRefresher = this::refresh;

    private void refresh() {
        if (app == null || actionBtn == null) return;
        int st = Apps.status(this, app);
        File ready = Apps.readyApk(this, app);
        DlManager.Task t = DlManager.get(app.slug);
        String name = "安装";
        int color = BLUE;
        boolean clickable = true;
        if (t != null && t.state == 1) {
            name = "下载中…";
            color = GREY;
            progressWrap.setVisibility(View.VISIBLE);
            long total = t.total > 0 ? t.total : Math.max(t.app.bytes, 1);
            progress.setProgress((int) (10000 * t.soFar / total));
            progressText.setText(DlManager.fmt(t.soFar) + " / " + DlManager.fmt(total)
                    + "（" + (100 * t.soFar / total) + "%）");
            stateText.setText("");
        } else if (t != null && t.state == 0) {
            name = "排队中…";
            color = GREY;
            progressWrap.setVisibility(View.GONE);
        } else if (t != null && t.state == 3) {
            name = "重试下载";
            color = RED;
            progressWrap.setVisibility(View.GONE);
            stateText.setText("上次失败：" + t.error);
        } else if (ready != null && st != Apps.CURRENT) {
            name = st == Apps.UPDATABLE ? "安装已下载的更新" : "安装已下载";
            color = 0xFF2F9E5F;
            progressWrap.setVisibility(View.GONE);
        } else if (st == Apps.UPDATABLE) {
            name = "更新 · v" + app.versionName;
            progressWrap.setVisibility(View.GONE);
        } else if (st == Apps.CURRENT) {
            name = "打开";
            color = 0xFF2F9E5F;
            progressWrap.setVisibility(View.GONE);
        } else {
            progressWrap.setVisibility(View.GONE);
        }
        actionBtn.setText(name);
        actionBtn.setBackground(round(color == GREY ? 0xFFC9CED6 : color, 12));
        actionBtn.setAlpha(color == GREY ? 0.85f : 1f);
        actionBtn.setClickable(clickable);
    }

    static void onUiInvalidated() {
        for (Runnable r : REFRESH_ALL) r.run();
    }

    static final Set<Runnable> REFRESH_ALL = new LinkedHashSet<>();

    {
        REFRESH_ALL.add(this::refresh);
    }
}
