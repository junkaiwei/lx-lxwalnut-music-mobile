package com.lxwalnut.music.mobile.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import com.lxwalnut.music.mobile.BuildConfig;
import com.lxwalnut.music.mobile.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "MusicWidget";
    private static final String SUFFIX_PLAY_PAUSE = ".PLAY_PAUSE";
    private static final String SUFFIX_PREV = ".PREV";
    private static final String SUFFIX_NEXT = ".NEXT";
    private static final String SUFFIX_UPDATE = ".UPDATE";
    private static final String SUFFIX_INTERNAL_PLAY_PAUSE = ".INTERNAL_PLAY_PAUSE";
    private static final String SUFFIX_INTERNAL_PREV = ".INTERNAL_PREV";
    private static final String SUFFIX_INTERNAL_NEXT = ".INTERNAL_NEXT";

    private static final String PREFS_NAME = "MusicWidgetPrefs";
    private static final String KEY_TITLE = "widget_title";
    private static final String KEY_ARTIST = "widget_artist";
    private static final String KEY_IS_PLAYING = "widget_is_playing";
    private static final String KEY_ARTWORK_URL = "widget_artwork_url";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String actionPlayPause() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_PLAY_PAUSE; }
    public static String actionPrev() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_PREV; }
    public static String actionNext() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_NEXT; }
    public static String actionUpdateWidget() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_UPDATE; }
    public static String internalActionPlayPause() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_INTERNAL_PLAY_PAUSE; }
    public static String internalActionPrev() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_INTERNAL_PREV; }
    public static String internalActionNext() { return BuildConfig.APP_WIDGET_ACTION_PREFIX + SUFFIX_INTERNAL_NEXT; }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        if (actionPlayPause().equals(action) || actionPrev().equals(action) || actionNext().equals(action)) {
            String internalAction = actionPlayPause().equals(action)
                    ? internalActionPlayPause()
                    : actionPrev().equals(action) ? internalActionPrev() : internalActionNext();
            Intent serviceIntent = new Intent(internalAction);
            serviceIntent.setPackage(context.getPackageName());
            context.sendBroadcast(serviceIntent);
            return;
        }

        if (actionUpdateWidget().equals(action)) {
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
            String artworkUrl = intent.getStringExtra("artworkUrl");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (title != null) editor.putString(KEY_TITLE, title);
            if (artist != null) editor.putString(KEY_ARTIST, artist);
            editor.putBoolean(KEY_IS_PLAYING, isPlaying);
            if (artworkUrl != null) editor.putString(KEY_ARTWORK_URL, artworkUrl);
            editor.apply();

            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName widget = new ComponentName(context, MusicWidgetProvider.class);
            for (int id : manager.getAppWidgetIds(widget)) updateWidget(context, manager, id);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_music_4x1);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String title = prefs.getString(KEY_TITLE, "LX-X Music");
        String artist = prefs.getString(KEY_ARTIST, "未在播放");
        boolean isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false);
        String artworkUrl = prefs.getString(KEY_ARTWORK_URL, null);

        views.setTextViewText(R.id.widget_song_title, title);
        views.setTextViewText(R.id.widget_song_artist, artist);
        views.setImageViewResource(R.id.widget_btn_play,
                isPlaying ? R.drawable.widget_ic_pause : R.drawable.widget_ic_play);
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, actionPrev()));
        views.setOnClickPendingIntent(R.id.widget_btn_play, getPendingIntent(context, actionPlayPause()));
        views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, actionNext()));

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent != null) {
            PendingIntent launchPending = PendingIntent.getActivity(context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_info, launchPending);
            views.setOnClickPendingIntent(R.id.widget_album_art, launchPending);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
        if (artworkUrl != null && !artworkUrl.isEmpty()) loadArtworkAsync(context, appWidgetManager, appWidgetId, artworkUrl);
    }

    private void loadArtworkAsync(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String artworkUrl) {
        executor.execute(() -> {
            try {
                Bitmap bitmap;
                if (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://")) {
                    URL url = new URL(artworkUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.connect();
                    InputStream input = conn.getInputStream();
                    bitmap = BitmapFactory.decodeStream(input);
                    input.close();
                    conn.disconnect();
                } else if (artworkUrl.startsWith("file://")) {
                    bitmap = BitmapFactory.decodeFile(artworkUrl.replace("file://", ""));
                } else {
                    bitmap = BitmapFactory.decodeFile(artworkUrl);
                }

                if (bitmap != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true);
                    if (scaled != bitmap) bitmap.recycle();
                    mainHandler.post(() -> {
                        RemoteViews partial = new RemoteViews(context.getPackageName(), R.layout.widget_music_4x1);
                        partial.setImageViewBitmap(R.id.widget_album_art, scaled);
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partial);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load artwork: " + e.getMessage());
            }
        });
    }

    private PendingIntent getPendingIntent(Context context, String action) {
        Intent intent = new Intent(context, MusicWidgetProvider.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void updateAllWidgets(Context context, String title, String artist, boolean isPlaying, String artworkUrl) {
        Intent intent = new Intent(context, MusicWidgetProvider.class);
        intent.setAction(actionUpdateWidget());
        intent.putExtra("title", title);
        intent.putExtra("artist", artist);
        intent.putExtra("isPlaying", isPlaying);
        intent.putExtra("artworkUrl", artworkUrl);
        context.sendBroadcast(intent);
    }
}
