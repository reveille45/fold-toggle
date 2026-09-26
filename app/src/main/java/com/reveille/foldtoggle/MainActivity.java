package com.reveille.foldtoggle;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Setup and status screen: support check, permissions, tile, and device reports. */
public class MainActivity extends Activity {
    static final String REPO = "https://github.com/reveille45/fold-toggle";

    private TextView status, diagnostics;
    private Button switchBtn, tileBtn, notifBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        diagnostics = findViewById(R.id.diagnostics);
        switchBtn = findViewById(R.id.btn_switch);
        tileBtn = findViewById(R.id.btn_tile);
        notifBtn = findViewById(R.id.btn_notif);

        switchBtn.setOnClickListener(v -> startActivity(new Intent(this, ToggleActivity.class)));
        tileBtn.setOnClickListener(v -> addTile());
        notifBtn.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyDiagnostics());
        findViewById(R.id.btn_report).setOnClickListener(v -> report());
        findViewById(R.id.btn_source).setOnClickListener(v -> open(REPO));
        findViewById(R.id.btn_privacy).setOnClickListener(v ->
                open("https://reveille45.github.io/fold-toggle/privacy.html"));

        if (!notificationsAllowed()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = Fold.supported(this);
        status.setText(ok ? R.string.status_supported : R.string.status_unsupported);
        diagnostics.setText(Fold.diagnostics(this));
        switchBtn.setEnabled(ok);
        tileBtn.setEnabled(ok);
        notifBtn.setVisibility(notificationsAllowed() ? View.GONE : View.VISIBLE);
        HoldService.start(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        onResume(); // refresh button state and repost the notification
    }

    private boolean notificationsAllowed() {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void addTile() {
        getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, ToggleTile.class),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_fold),
                getMainExecutor(),
                result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(this, R.string.tile_already_added, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void copyDiagnostics() {
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("Fold Toggle diagnostics", Fold.diagnostics(this)));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    /** Opens a pre-filled GitHub issue form (fields match .github/ISSUE_TEMPLATE/device-report.yml). */
    private void report() {
        Uri uri = Uri.parse(REPO + "/issues/new").buildUpon()
                .appendQueryParameter("template", "device-report.yml")
                .appendQueryParameter("title", "Device report: " + android.os.Build.MANUFACTURER
                        + " " + android.os.Build.MODEL)
                .appendQueryParameter("diagnostics", Fold.diagnostics(this))
                .build();
        open(uri.toString());
    }

    private void open(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
