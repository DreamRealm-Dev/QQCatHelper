package io.github.qqcathelper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Best-effort Shizuku integration.
 *
 * <p>When Shizuku is running and has granted permission, this helper asks the
 * system to whitelist the app from battery restrictions and background limits.
 * The Shizuku API exposes the process creation method as private reflection;
 * failures are intentionally ignored so the app still works in normal mode.</p>
 */
public final class ShizukuHelper {

    private static final String TAG = "QQCatHelper";

    private ShizukuHelper() {
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku request failed", t);
        }
    }

    public static void applyBestEffort(Context context) {
        try {
            if (!isAvailable() || !hasPermission()) {
                Log.d(TAG, "Shizuku not available or not granted");
                return;
            }

            String pkg = context.getPackageName();
            run("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "allow");
            run("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "allow");
            run("cmd", "deviceidle", "whitelist", "+" + pkg);
            run("cmd", "appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow");
            Log.d(TAG, "Shizuku keep-alive rules applied");
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku keep-alive failed", t);
        }
    }

    private static void run(String... command) throws Exception {
        Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
        Method newProcess = shizukuClass.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
        newProcess.setAccessible(true);
        Process process = (Process) newProcess.invoke(null, command, null, null);
        if (process != null) {
            process.waitFor();
        }
    }
}
