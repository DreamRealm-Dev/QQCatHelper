package io.github.qqcathelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Keep-alive foreground service.
 *
 * <p>Modes:
 * <ul>
 *   <li>off - no keep-alive</li>
 *   <li>general - foreground notification refreshed every 5 seconds</li>
 *   <li>overlay - foreground notification + small floating window</li>
 *   <li>shizuku - foreground notification + Shizuku whitelist commands</li>
 * </ul>
 *
 * <p>The service also registers a fake "now playing" media session so the app
 * appears in the system media player area, which helps a lot on aggressive
 * Chinese Android ROMs.</p>
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "qqcat_helper_keepalive";
    private static final int NOTIFICATION_ID = 1001;
    private static final long TICK_INTERVAL_MS = 5000;

    private WindowManager windowManager;
    private View overlayView;
    private boolean overlayAdded = false;

    private Handler tickHandler;
    private Runnable tickRunnable;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        initMediaSession();
        startTicker();
        applyMode(CatConfig.load(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        if (mediaSession == null) {
            initMediaSession();
        }
        startTicker();
        applyMode(CatConfig.load(this));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopTicker();
        releaseMediaSession();
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTicker() {
        if (tickHandler == null) {
            tickHandler = new Handler(Looper.getMainLooper());
        }
        if (tickRunnable != null) {
            tickHandler.removeCallbacks(tickRunnable);
        }
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification());
                    updateMediaSession();
                } catch (Throwable ignored) {
                }
                tickHandler.postDelayed(this, TICK_INTERVAL_MS);
            }
        };
        tickHandler.post(tickRunnable);
    }

    private void stopTicker() {
        if (tickHandler != null && tickRunnable != null) {
            tickHandler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }

    private void initMediaSession() {
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            mediaSession = new MediaSession(this, "QQCatHelper");
            mediaSession.setActive(true);
            updateMediaSession();
        } catch (Throwable ignored) {
            mediaSession = null;
        }
    }

    private void updateMediaSession() {
        if (mediaSession == null) {
            return;
        }
        try {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.app_name))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.keep_alive_notification_text))
                    .build();
            mediaSession.setMetadata(metadata);

            PlaybackState state = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                    .build();
            mediaSession.setPlaybackState(state);
            mediaSession.setActive(true);
        } catch (Throwable ignored) {
        }
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Throwable ignored) {
            }
            mediaSession = null;
        }
    }

    private void applyMode(CatConfig config) {
        String mode = config.keepAliveMode == null
                ? CatConfig.KEEP_ALIVE_GENERAL : config.keepAliveMode;

        if (CatConfig.KEEP_ALIVE_OVERLAY.equals(mode)) {
            addOverlayIfPossible();
            removeOverlayIfNotPermitted();
        } else if (CatConfig.KEEP_ALIVE_SHIZUKU.equals(mode)) {
            removeOverlay();
            ShizukuHelper.applyBestEffort(this);
        } else {
            removeOverlay();
        }
    }

    private void addOverlayIfPossible() {
        if (overlayAdded || !Settings.canDrawOverlays(this)) {
            return;
        }
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            TextView view = new TextView(this);
            view.setText("喵");
            view.setTextSize(18);
            view.setGravity(Gravity.CENTER);
            view.setBackgroundColor(0x66FF9800);
            view.setPadding(dp(8), dp(8), dp(8), dp(8));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    dp(56), dp(56),
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = dp(12);
            params.y = dp(120);
            windowManager.addView(view, params);
            overlayView = view;
            overlayAdded = true;
        } catch (Exception e) {
            overlayAdded = false;
            overlayView = null;
        }
    }

    private void removeOverlayIfNotPermitted() {
        if (overlayAdded && !Settings.canDrawOverlays(this)) {
            removeOverlay();
        }
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
        overlayView = null;
        overlayAdded = false;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, launchIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.keep_alive_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setMediaSession(mediaSession.getSessionToken());
            builder.setStyle(style);
        }

        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.keep_alive_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    public static void start(Context context) {
        CatConfig config = CatConfig.load(context);
        if (CatConfig.KEEP_ALIVE_OFF.equals(config.keepAliveMode)) {
            stop(context);
            return;
        }
        try {
            Intent intent = new Intent(context, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, KeepAliveService.class));
    }
}
