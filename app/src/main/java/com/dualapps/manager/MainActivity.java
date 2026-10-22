package com.dualapps.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Simple root UI for the Dual Apps module: create the clone profile and toggle apps. */
public class MainActivity extends Activity {

    private static final Pattern PKG = Pattern.compile("[A-Za-z0-9_.]+");

    private static class App {
        String pkg;
        String label;
        Drawable icon;
    }

    private final List<App> apps = new ArrayList<>();
    private final Set<String> cloned = new HashSet<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView statusView;
    private BaseAdapter adapter;
    private boolean profileExists = false;

    // ---------- root helper ----------
    private static String su(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- UI ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(16) + bars.left, dp(12) + bars.top, dp(16) + bars.right, dp(8) + bars.bottom);
            return WindowInsets.CONSUMED;
        });

        TextView title = new TextView(this);
        title.setText("Dual Apps");
        title.setTextSize(24);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Checking...");
        statusView.setPadding(0, dp(4), 0, dp(8));
        root.addView(statusView);

        LinearLayout bar = new LinearLayout(this);
        bar.addView(button("Create", v -> run("dualapp create")), weight());
        bar.addView(button("Clone all", v -> run("dualapp install-all")), weight());
        bar.addView(button("Delete", v -> confirmRemove()), weight());
        root.addView(bar);

        ListView list = new ListView(this);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Button button(String text, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setOnClickListener(l);
        return bt;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Dual Apps space?")
                .setMessage("This removes the clone profile and ALL data of the cloned apps.")
                .setPositiveButton("Delete", (d, w) -> run("dualapp remove"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- actions ----------
    private void run(String cmd) {
        statusView.setText("Working...");
        io.execute(() -> {
            String out = su(cmd);
            ui.post(() -> {
                if (!out.isEmpty()) Toast.makeText(this, out, Toast.LENGTH_LONG).show();
                refresh();
            });
        });
    }

    private void refresh() {
        io.execute(() -> {
            String status = su("dualapp status");
            boolean exists = status.contains("Clone profile: user");
            Set<String> c = new HashSet<>();
            if (exists) {
                for (String line : su("dualapp list").split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && PKG.matcher(line).matches()) {
                        c.add(line);
                    }
                }
            }

            PackageManager pm = getPackageManager();
            List<App> found = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (ai.packageName.equals(getPackageName())) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.label = String.valueOf(pm.getApplicationLabel(ai));
                a.icon = pm.getApplicationIcon(ai);
                found.add(a);
            }
            Collections.sort(found, (x, y) -> x.label.compareToIgnoreCase(y.label));

            String shown;
            if (status.startsWith("ERR") || status.isEmpty()) {
                shown = "No root or module not active. Grant root and reboot after installing the module.";
            } else if (exists) {
                shown = "Dual Apps space is ready.";
            } else {
                shown = "No Dual Apps space yet. Tap Create.";
            }

            ui.post(() -> {
                profileExists = exists;
                cloned.clear();
                cloned.addAll(c);
                apps.clear();
                apps.addAll(found);
                statusView.setText(shown);
                adapter.notifyDataSetChanged();
            });
        });
    }

    // ---------- list ----------
    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            final App a = apps.get(pos);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(a.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

            TextView name = new TextView(MainActivity.this);
            name.setText(a.label);
            name.setTextSize(16);
            name.setPadding(dp(12), 0, dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            boolean isCloned = cloned.contains(a.pkg);

            Button open = new Button(MainActivity.this);
            open.setText("Open");
            open.setEnabled(isCloned);
            open.setOnClickListener(v -> run("dualapp launch " + a.pkg));
            row.addView(open);

            Switch sw = new Switch(MainActivity.this);
            sw.setChecked(isCloned);
            sw.setEnabled(profileExists);
            sw.setOnClickListener(v -> run("dualapp " + (sw.isChecked() ? "install " : "uninstall ") + a.pkg));
            row.addView(sw);

            return row;
        }
    }
}
