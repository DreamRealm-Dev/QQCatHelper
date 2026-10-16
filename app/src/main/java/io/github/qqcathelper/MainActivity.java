package io.github.qqcathelper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Material 3 settings screen for QQCatHelper.
 */
public class MainActivity extends AppCompatActivity {

    private static final int SHIZUKU_PERMISSION_REQUEST = 9001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 9002;

    private CatConfig config;
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;

    private TextView statusText;
    private MaterialButton toggleServiceButton;
    private MaterialRadioButton modePunctuationRadio;
    private MaterialRadioButton modeRealtimeRadio;
    private MaterialRadioButton keepAliveOffRadio;
    private MaterialRadioButton keepAliveGeneralRadio;
    private MaterialRadioButton keepAliveOverlayRadio;
    private MaterialRadioButton keepAliveShizukuRadio;
    private SwitchMaterial switchMeow;
    private SwitchMaterial switchWo;
    private SwitchMaterial switchNi;
    private SwitchMaterial switchKaomoji;
    private TextInputEditText editCustomKaomoji;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = CatConfig.load(this);

        statusText = findViewById(R.id.statusText);
        toggleServiceButton = findViewById(R.id.toggleServiceButton);
        modePunctuationRadio = findViewById(R.id.modePunctuationRadio);
        modeRealtimeRadio = findViewById(R.id.modeRealtimeRadio);
        keepAliveOffRadio = findViewById(R.id.keepAliveOffRadio);
        keepAliveGeneralRadio = findViewById(R.id.keepAliveGeneralRadio);
        keepAliveOverlayRadio = findViewById(R.id.keepAliveOverlayRadio);
        keepAliveShizukuRadio = findViewById(R.id.keepAliveShizukuRadio);
        switchMeow = findViewById(R.id.switchMeow);
        switchWo = findViewById(R.id.switchWo);
        switchNi = findViewById(R.id.switchNi);
        switchKaomoji = findViewById(R.id.switchKaomoji);
        editCustomKaomoji = findViewById(R.id.editCustomKaomoji);

        toggleServiceButton.setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.saveButton).setOnClickListener(v -> saveConfig());
        findViewById(R.id.testButton).setOnClickListener(v -> showTestDialog());

        bindConfigToUi();
        updateServiceStatus();
        KeepAliveService.start(this);

        modePunctuationRadio.setOnCheckedChangeListener((button, checked) -> {
            if (checked) saveConfig();
        });
        modeRealtimeRadio.setOnCheckedChangeListener((button, checked) -> {
            if (checked) saveConfig();
        });
        keepAliveGeneralRadio.setOnCheckedChangeListener((button, checked) -> {
            if (checked) requestNotificationPermissionIfNeeded();
        });
        keepAliveOverlayRadio.setOnCheckedChangeListener((button, checked) -> {
            if (checked) requestOverlayPermission();
        });
        keepAliveShizukuRadio.setOnCheckedChangeListener((button, checked) -> {
            if (checked) requestShizukuPermission();
        });

        shizukuPermissionListener = (requestCode, grantResult) -> {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ShizukuHelper.applyBestEffort(this);
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Shizuku 未授权，仍会使用普通保活", Toast.LENGTH_LONG).show();
                }
            }
        };
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        KeepAliveService.start(this);
    }

    @Override
    protected void onDestroy() {
        if (shizukuPermissionListener != null) {
            try {
                Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }

    private void bindConfigToUi() {
        boolean realtime = CatConfig.MODE_REALTIME.equals(config.rewriteMode);
        modeRealtimeRadio.setChecked(realtime);
        modePunctuationRadio.setChecked(!realtime);

        String keepAlive = config.keepAliveMode == null
                ? CatConfig.KEEP_ALIVE_GENERAL : config.keepAliveMode;
        keepAliveOffRadio.setChecked(CatConfig.KEEP_ALIVE_OFF.equals(keepAlive));
        keepAliveGeneralRadio.setChecked(CatConfig.KEEP_ALIVE_GENERAL.equals(keepAlive));
        keepAliveOverlayRadio.setChecked(CatConfig.KEEP_ALIVE_OVERLAY.equals(keepAlive));
        keepAliveShizukuRadio.setChecked(CatConfig.KEEP_ALIVE_SHIZUKU.equals(keepAlive));

        switchMeow.setChecked(config.addMeowSuffix);
        switchWo.setChecked(config.replaceWo);
        switchNi.setChecked(config.replaceNi);
        switchKaomoji.setChecked(config.appendKaomoji);

        StringBuilder sb = new StringBuilder();
        if (config.customKaomoji != null) {
            for (int i = 0; i < config.customKaomoji.length; i++) {
                if (i > 0) sb.append('\n');
                sb.append(config.customKaomoji[i]);
            }
        }
        editCustomKaomoji.setText(sb.toString());
    }

    private void saveConfig() {
        config.addMeowSuffix = switchMeow.isChecked();
        config.replaceWo = switchWo.isChecked();
        config.replaceNi = switchNi.isChecked();
        config.appendKaomoji = switchKaomoji.isChecked();
        config.rewriteMode = modeRealtimeRadio.isChecked()
                ? CatConfig.MODE_REALTIME : CatConfig.MODE_PUNCTUATION;
        if (keepAliveOffRadio.isChecked()) {
            config.keepAliveMode = CatConfig.KEEP_ALIVE_OFF;
        } else if (keepAliveOverlayRadio.isChecked()) {
            config.keepAliveMode = CatConfig.KEEP_ALIVE_OVERLAY;
        } else if (keepAliveShizukuRadio.isChecked()) {
            config.keepAliveMode = CatConfig.KEEP_ALIVE_SHIZUKU;
        } else {
            config.keepAliveMode = CatConfig.KEEP_ALIVE_GENERAL;
        }

        String text = editCustomKaomoji.getText() == null
                ? "" : editCustomKaomoji.getText().toString();
        String[] lines = text.split("\n");
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        config.customKaomoji = list.toArray(new String[0]);

        config.save(this);
        KeepAliveService.start(this);

        if (CatConfig.KEEP_ALIVE_OVERLAY.equals(config.keepAliveMode)) {
            requestOverlayPermission();
        } else if (CatConfig.KEEP_ALIVE_SHIZUKU.equals(config.keepAliveMode)) {
            requestShizukuPermission();
        } else if (CatConfig.KEEP_ALIVE_GENERAL.equals(config.keepAliveMode)) {
            requestNotificationPermissionIfNeeded();
        } else {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.keep_alive_overlay_permission_required, Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void requestShizukuPermission() {
        if (!ShizukuHelper.isAvailable()) {
            Toast.makeText(this, "Shizuku 未运行，请先启动 Shizuku", Toast.LENGTH_LONG).show();
        } else if (!ShizukuHelper.hasPermission()) {
            ShizukuHelper.requestPermission(SHIZUKU_PERMISSION_REQUEST);
        } else {
            ShizukuHelper.applyBestEffort(this);
            Toast.makeText(this, "Shizuku 已授权并应用保活规则", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void showTestDialog() {
        CatConfig testConfig = new CatConfig();
        testConfig.addMeowSuffix = switchMeow.isChecked();
        testConfig.replaceWo = switchWo.isChecked();
        testConfig.replaceNi = switchNi.isChecked();
        testConfig.appendKaomoji = switchKaomoji.isChecked();
        testConfig.rewriteMode = modeRealtimeRadio.isChecked()
                ? CatConfig.MODE_REALTIME : CatConfig.MODE_PUNCTUATION;

        String raw = editCustomKaomoji.getText() == null
                ? "" : editCustomKaomoji.getText().toString();
        String[] lines = raw.split("\n");
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        testConfig.customKaomoji = list.toArray(new String[0]);

        String input = getString(R.string.test_input);
        String output = TextProcessor.process(input, testConfig);
        String mode = modeRealtimeRadio.isChecked()
                ? getString(R.string.mode_realtime) : getString(R.string.mode_punctuation);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.test_title)
                .setMessage(getString(R.string.test_message_format, mode, input, output))
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    private void updateServiceStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            statusText.setText(R.string.status_enabled);
            statusText.setTextColor(0xFF2E7D32);
            toggleServiceButton.setText(R.string.service_close);
        } else {
            statusText.setText(R.string.status_disabled);
            statusText.setTextColor(0xFFB71C1C);
            toggleServiceButton.setText(R.string.service_open);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String expected = getPackageName() + "/" + CatAccessibilityService.class.getName();
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices == null) return false;
            for (String name : enabledServices.split(":")) {
                if (name.equalsIgnoreCase(expected)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
