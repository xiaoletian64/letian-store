package site.zhangyue.store;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** DownloadActivity — 下载管理页：进度、重试、安装、删除。 */
public class DownloadActivity extends Activity {

    private LinearLayout list;
    private final List<DlManager.Listener> bound = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.refreshPalette(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(15);
        back.setTextColor(Ui.BLUE);
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText("  下载管理");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TXT);
        bar.addView(title);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        Ui.applyWindow(this, root);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DlManager.Listener l = this::render;
        DlManager.addListener(l);
        bound.add(l);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (DlManager.Listener l : bound) DlManager.removeListener(l);
        bound.clear();
    }

    private void render() {
        runOnUiThread(() -> {
            if (list == null) return;
            list.removeAllViews();
            boolean any = false;
            // 当前清单的应用 + 下载任务 + 已下载文件
            java.util.LinkedHashSet<String> slugs = new java.util.LinkedHashSet<>();
            Model.Catalog c = Repo.catalog();
            if (c != null) for (Model.App a : c.apps) slugs.add(a.slug);
            File dir = getExternalFilesDir("apks");
            if (dir != null) {
                for (File f : dir.listFiles()) {
                    if (f.getName().endsWith(".apk")) {
                        slugs.add(f.getName().substring(0, f.getName().length() - 4));
                    }
                }
            }
            for (String slug : slugs) {
                Model.App a = Repo.find(slug);
                File apk = DlManager.apkFile(this, slug);
                DlManager.Task t = DlManager.get(slug);
                if (a == null && !apk.exists() && t == null) continue;
                if (a == null && apk.exists()) {
                    // 清单已下架但本地有包
                    a = new Model.App();
                    a.slug = slug;
                    a.name = slug + "（已下架）";
                    a.pkg = "";
                    a.versionName = "?";
                }
                if (a != null) {
                    any = true;
                    list.addView(row(a, apk, t));
                }
            }
            if (!any) {
                TextView empty = new TextView(this);
                empty.setText("还没有下载任务");
                empty.setTextColor(Ui.SUB);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(60), 0, 0);
                list.addView(empty);
            }
        });
    }

    private View row(Model.App a, File apk, DlManager.Task t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(roundWhite());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = dp(6);
        p.leftMargin = dp(12); p.rightMargin = dp(12); p.topMargin = m; p.bottomMargin = m;
        row.setLayoutParams(p);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        android.graphics.Bitmap ic = IconCache.get(this, a.slug, a.icon);
        icon.setImageDrawable(ic != null
                ? new android.graphics.drawable.BitmapDrawable(getResources(), ic)
                : TextDrawable.forApp(a.name));
        row.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView name = new TextView(this);
        name.setText(a.name);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Ui.TXT);
        info.addView(name);
        TextView sub = new TextView(this);
        if (t != null && t.state == 1) {
            long total = t.total > 0 ? t.total : Math.max(a.bytes, 1);
            sub.setText(DlManager.fmt(t.soFar) + " / " + DlManager.fmt(total)
                    + "（" + (100 * t.soFar / total) + "%）");
        } else if (t != null && t.state == 3) {
            sub.setText("失败：" + t.error);
            sub.setTextColor(Ui.RED);
        } else if (apk.exists()) {
            sub.setText("已下载 · " + DlManager.fmt(apk.length()) + " · 待安装");
            sub.setTextColor(Ui.GREEN);
        } else if (t != null && t.state == 0) {
            sub.setText("排队中…");
        } else {
            sub.setText("v" + a.versionName);
        }
        sub.setTextSize(12);
        sub.setTextColor(Ui.SUB);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // 操作按钮
        TextView btn = new TextView(this);
        btn.setTextSize(13);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setBackground(round(Ui.BLUE, 10));
        btn.setTextColor(Color.WHITE);
        btn.setClickable(true);
        if (t != null && (t.state == 0 || t.state == 1)) {
            btn.setText("下载中");
            btn.setBackground(round(Ui.DISABLED, 10));
            btn.setOnClickListener(null);
        } else if (t != null && t.state == 3) {
            btn.setText("重试");
            btn.setBackground(round(Ui.RED, 10));
            btn.setOnClickListener(v -> {
                DlManager.Task old = DlManager.get(a.slug);
                // 失败任务重新入队（enqueue 内部会清失败态）
                DlManager.enqueue(DownloadActivity.this, a);
                Repo.preferDownloadHost(DownloadActivity.this, a);
                render();
            });
        } else if (apk.exists()) {
            btn.setText("安装");
            btn.setOnClickListener(v -> Installer.install(DownloadActivity.this, a, apk,
                    (ok, msg) -> runOnUiThread(() -> {
                        if (!ok && msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        render();
                    })));
        } else {
            btn.setText("下载");
            btn.setOnClickListener(v -> {
                DlManager.enqueue(DownloadActivity.this, a);
                Repo.preferDownloadHost(DownloadActivity.this, a);
                render();
            });
        }
        row.addView(btn);

        // 删除已下载
        if (apk.exists() && (t == null || t.state >= 2)) {
            TextView del = new TextView(this);
            del.setText(" 删");
            del.setTextSize(13);
            del.setTextColor(Ui.RED);
            del.setClickable(true);
            del.setPadding(dp(8), dp(6), 0, dp(6));
            del.setOnClickListener(v -> {
                apk.delete();
                new File(apk.getAbsolutePath() + ".sha").delete();
                render();
            });
            row.addView(del);
        }
        row.setOnClickListener(v -> {
            if (Repo.find(a.slug) != null) {
                android.content.Intent i = new android.content.Intent(this, DetailActivity.class);
                i.putExtra("slug", a.slug);
                startActivity(i);
            }
        });
        return row;
    }

    private android.graphics.drawable.GradientDrawable roundWhite() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(12));
        return g;
    }

    private android.graphics.drawable.GradientDrawable round(int color, float r) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }
}
