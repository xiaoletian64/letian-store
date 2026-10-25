package site.zhangyue.store;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** MainActivity — 商店主界面。 */
public class MainActivity extends Activity {


    private LinearLayout listContainer;
    private EditText search;
    private TextView updatesBadge, downloadingBadge;
    private ScrollView scroll;
    private String query = "";
    private TextView loading;

    private final DlManager.Listener dlListener = () -> DetailActivity.onUiInvalidated();
    private final Runnable refresher = this::renderList;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.refreshPalette(this);
        Ui.requestNotifPerm(this);
        RuntimeConfig.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.applyWindow(this, root);

        // ===== 顶栏 =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(14), dp(16), dp(4));
        root.addView(top);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this);
        String sn = StoreConfig.clean(StoreConfig.STORE_NAME);
        brand.setText((sn == null ? "乐天应用商店" : sn));
        brand.setTextSize(22);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(Ui.TXT);
        titleRow.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // 更新角标
        updatesBadge = badge("● 更新", Ui.GREEN);
        updatesBadge.setOnClickListener(v -> showUpdates());
        titleRow.addView(updatesBadge);
        // 下载中角标
        downloadingBadge = badge("↓ 0", Ui.BLUE);
        downloadingBadge.setOnClickListener(v -> startActivity(
                new Intent(this, DownloadActivity.class)));
        titleRow.addView(downloadingBadge);
        top.addView(titleRow);

        TextView subtitle = new TextView(this);
        subtitle.setText("自建应用 · 一键安装更新");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Ui.SUB);
        subtitle.setPadding(0, dp(2), 0, dp(4));
        top.addView(subtitle);

        // ===== 搜索框 =====
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setBackground(round(Ui.SOFT, 12));
        searchWrap.setPadding(dp(14), dp(2), dp(10), dp(2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(16); sp.rightMargin = dp(16); sp.topMargin = dp(6); sp.bottomMargin = dp(4);
        root.addView(searchWrap, sp);
        TextView glass = new TextView(this);
        glass.setText("⌕ ");
        glass.setTextSize(16);
        glass.setTextColor(Ui.SUB);
        searchWrap.addView(glass);
        search = new EditText(this);
        search.setBackground(null);
        search.setHint("搜索应用");
        search.setTextSize(15);
        search.setTextColor(Ui.TXT);
        search.setHintTextColor(Ui.HINT);
        search.setSingleLine(true);
        searchWrap.addView(search, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                renderList();
            }
        });

        // ===== 列表 =====
        scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), dp(4), dp(12), dp(28));
        scroll.addView(listContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // loading 行
        loading = new TextView(this);
        loading.setText("正在加载应用目录…");
        loading.setTextColor(Ui.SUB);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(48), 0, 0);
        listContainer.addView(loading);

        DetailActivity.REFRESH_ALL.add(refresher);
        IconCache.addRefresh(refresher);

        Repo.refresh(this, (ok, err) -> {
            if (!ok && err != null) {
                Toast.makeText(this, "目录加载失败：" + err, Toast.LENGTH_LONG).show();
            }
            renderList();
        });
    }

    private TextView badge(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(round(color, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        t.setLayoutParams(p);
        t.setVisibility(View.GONE);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    @Override
    protected void onResume() {
        super.onResume();
        DlManager.addListener(dlListener);
        renderList();
        UpdateService.start(this);   // 后台自动更新检查（服务内部节流：30 分钟内不重复拉目录）
        if (Repo.stale()) {
            Repo.refresh(this, (ok, e) -> renderList());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        DlManager.removeListener(dlListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DetailActivity.REFRESH_ALL.remove(refresher);
        IconCache.clearRefresh(refresher);
    }

    // ===== 渲染 =====

    private void renderList() {
        if (listContainer == null) return;
        Model.Catalog c = Repo.catalog();
        runOnUiThread(() -> {
            updatesBadge.setVisibility(SelfUpdate.hasUpdate() ? View.VISIBLE : View.GONE);
            int dlCount = 0;
            for (DlManager.Task t : new ArrayList<>(tasks())) {
                if (t.state == 0 || t.state == 1) dlCount++;
            }
            downloadingBadge.setText("↓ " + dlCount);
            downloadingBadge.setVisibility(dlCount > 0 ? View.VISIBLE : View.GONE);

            listContainer.removeAllViews();
            if (c == null) {
                TextView err = new TextView(this);
                err.setText(Repo.lastError == null
                        ? "正在加载…" : "加载失败：" + Repo.lastError + "\n\n下拉重试：退出重进");
                err.setTextColor(Ui.SUB);
                err.setGravity(Gravity.CENTER);
                err.setPadding(0, dp(48), 0, 0);
                listContainer.addView(err);
                return;
            }
            List<Model.App> apps = new ArrayList<>();
            for (Model.App a : c.apps) {
                if (matches(a)) apps.add(a);
            }
            renderCategorys(apps);
        });
    }

    private List<DlManager.Task> tasks() {
        // DlManager.TASKS 不可直接访问，通过 slug 遍历清单 + readyApk
        List<DlManager.Task> out = new ArrayList<>();
        Model.Catalog c = Repo.catalog();
        if (c != null) {
            for (Model.App a : c.apps) {
                DlManager.Task t = DlManager.get(a.slug);
                if (t != null) out.add(t);
            }
        }
        return out;
    }

    private boolean matches(Model.App a) {
        if (query.isEmpty()) return true;
        String q = query.toLowerCase();
        if (a.name.toLowerCase().contains(q)) return true;
        if (a.slug != null && a.slug.contains(q)) return true;
        if (a.summary != null && a.summary.toLowerCase().contains(q)) return true;
        for (String t : a.tags) if (t.toLowerCase().contains(q)) return true;
        return false;
    }

    private void renderCategorys(List<Model.App> apps) {
        if (apps.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("没有匹配的应用");
            none.setTextColor(Ui.SUB);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(40), 0, 0);
            listContainer.addView(none);
            return;
        }
        // 分组：搜索时平铺；浏览时按 category 分节
        if (!query.isEmpty()) {
            for (Model.App a : apps) listContainer.addView(appRow(a));
            return;
        }
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (Model.App a : apps) cats.add(a.category == null ? "应用" : a.category);
        // 「更新」组提前：有更新/可安装的应用置顶展示
        List<Model.App> updatable = new ArrayList<>();
        for (Model.App a : apps) {
            int st = Apps.status(this, a);
            File ready = Apps.readyApk(this, a);
            if (st == Apps.UPDATABLE || (ready != null && st != Apps.CURRENT)) updatable.add(a);
        }
        if (!updatable.isEmpty()) {
            listContainer.addView(sectionHeader("待更新 · " + updatable.size()));
            for (Model.App a : updatable) listContainer.addView(appRow(a));
        }
        for (String cat : cats) {
            listContainer.addView(sectionHeader(cat));
            for (Model.App a : apps) {
                String cc = a.category == null ? "应用" : a.category;
                if (cc.equals(cat)) listContainer.addView(appRow(a));
            }
        }
    }

    private View sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Ui.SUB);
        t.setPadding(dp(6), dp(16), dp(6), dp(6));
        return t;
    }

    private View appRow(Model.App a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bgc = new GradientDrawable();
        bgc.setColor(Ui.CARD);
        bgc.setCornerRadius(dp(14));
        card.setBackground(bgc);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        card.setLayoutParams(p);
        card.setClickable(true);
        card.setOnClickListener(v -> {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(search.getWindowToken(), 0);
            Intent i = new Intent(this, DetailActivity.class);
            i.putExtra("slug", a.slug);
            startActivity(i);
        });

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        Bitmap ic = IconCache.get(this, a.slug, a.icon);
        if (ic != null) icon.setImageDrawable(new BitmapDrawable(getResources(), ic));
        else icon.setImageDrawable(TextDrawable.forApp(a.name));
        card.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView name = new TextView(this);
        name.setText(a.name);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Ui.TXT);
        info.addView(name);
        TextView desc = new TextView(this);
        String d = a.summary == null ? "" : a.summary;
        if (d.length() > 42) d = d.substring(0, 42) + "…";
        desc.setText(d);
        desc.setTextSize(12);
        desc.setTextColor(Ui.SUB);
        info.addView(desc);
        TextView ver = new TextView(this);
        int st = Apps.status(this, a);
        File ready = Apps.readyApk(this, a);
        String vs = "v" + a.versionName;
        if (a.bytes > 0) vs += " · " + a.sizeText();
        if (st == Apps.UPDATABLE) vs += " · 可更新";
        else if (ready != null) vs += " · 待安装";
        else if (st == Apps.CURRENT) vs += " · 已安装";
        ver.setText(vs);
        ver.setTextSize(11);
        ver.setTextColor(st == Apps.UPDATABLE || ready != null ? Ui.GREEN : Ui.HINT);
        info.addView(ver);
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView action = new TextView(this);
        action.setTextSize(13);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setTextColor(Color.WHITE);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), dp(7), dp(14), dp(7));
        DlManager.Task t = DlManager.get(a.slug);
        if (t != null && (t.state == 0 || t.state == 1)) {
            long total = t.total > 0 ? t.total : Math.max(a.bytes, 1);
            action.setText(t.state == 0 ? "排队" : (t.total > 0 ? (int) (100 * t.soFar / total) + "%" : "…"));
            action.setBackground(round(Ui.DISABLED, 10));
            action.setOnClickListener(null);
        } else if (t != null && t.state == 3) {
            action.setText("重试");
            action.setBackground(round(Ui.RED, 10));
            action.setOnClickListener(v -> {
                DlManager.enqueue(this, a);
                Repo.preferDownloadHost(this, a);
            });
        } else if (st == Apps.CURRENT) {
            action.setText("打开");
            action.setBackground(round(Ui.SOFT, 10));
            action.setTextColor(Ui.BLUE);
            action.setOnClickListener(v -> {
                Intent li = getPackageManager().getLaunchIntentForPackage(a.pkg);
                if (li != null) startActivity(li);
                else Toast.makeText(this, "无法启动", Toast.LENGTH_SHORT).show();
            });
        } else if (ready != null) {
            action.setText("安装");
            action.setBackground(round(Ui.GREEN, 10));
            action.setOnClickListener(v -> Installer.install(this, a, ready, (ok, msg) -> {
                if (!ok && msg != null) {
                    runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
                }
            }));
        } else {
            boolean up = st == Apps.UPDATABLE;
            action.setText(up ? "更新" : "安装");
            action.setBackground(round(Ui.BLUE, 10));
            action.setOnClickListener(v -> {
                DlManager.enqueue(this, a);
                Repo.preferDownloadHost(this, a);
            });
        }
        card.addView(action);
        return card;
    }

    // ===== 商店自更新 =====

    private void showUpdates() {
        Model.Catalog c = Repo.catalog();
        if (c == null) return;
        StringBuilder sb = new StringBuilder();
        for (Model.App a : c.apps) {
            int st = Apps.status(this, a);
            File ready = Apps.readyApk(this, a);
            if (st == Apps.UPDATABLE || (ready != null && st != Apps.CURRENT)) {
                sb.append("· ").append(a.name).append(" → v").append(a.versionName).append('\n');
            }
        }
        if (sb.length() == 0) {
            Toast.makeText(this, "全部都是最新版", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("可更新")
                .setMessage(sb.toString().trim() + "\n\n到各应用详情页逐个更新。")
                .setPositiveButton("好", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (search != null && !search.getText().toString().isEmpty()) {
            search.setText("");
            return;
        }
        super.onBackPressed();
    }
}
