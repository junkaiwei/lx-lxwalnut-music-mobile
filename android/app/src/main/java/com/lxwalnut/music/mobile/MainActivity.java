package com.lxwalnut.music.mobile;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.reactnativenavigation.NavigationActivity;

import java.util.Arrays;

public class MainActivity extends NavigationActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
    }

    @Override
    protected void onStart() {
        super.onStart();
        pushShortcuts();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forwardIntentToReactNative(intent);
    }

    private void setupEdgeToEdge() {
        Window window = getWindow();
        String tag = "LX_CUTOUT";

        // Allow content to extend into cutout area (notch) on short edges
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
            android.util.Log.i(tag, "CutoutMode set to SHORT_EDGES (API " + Build.VERSION.SDK_INT + ")");
        }

        // Enable edge-to-edge: content draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false);
        android.util.Log.i(tag, "setDecorFitsSystemWindows(false)");

        // Make status bar and navigation bar transparent
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(0x00000000);
        window.setNavigationBarColor(0x00000000);
        android.util.Log.i(tag, "Status/nav bar set to transparent");

        // Log window dimensions
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        window.getWindowManager().getDefaultDisplay().getMetrics(dm);
        android.util.Log.i(tag, "Window size: " + dm.widthPixels + "x" + dm.heightPixels + " density=" + dm.density);

        // Log cutout info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.graphics.Rect cutoutBounds = window.getDecorView().getRootWindowInsets() != null
                ? window.getDecorView().getRootWindowInsets().getDisplayCutout() != null
                    ? null : null : null;
            // Use reflection-free approach
            android.view.DisplayCutout cutout = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowInsets insets = window.getDecorView().getRootWindowInsets();
                if (insets != null) {
                    cutout = insets.getDisplayCutout();
                }
            }
            if (cutout != null) {
                android.util.Log.i(tag, "Cutout safe insets: top=" + cutout.getSafeInsetTop()
                    + " left=" + cutout.getSafeInsetLeft()
                    + " right=" + cutout.getSafeInsetRight()
                    + " bottom=" + cutout.getSafeInsetBottom());
            } else {
                android.util.Log.w(tag, "Cutout is null (API " + Build.VERSION.SDK_INT + ")");
            }
        }

        // Intercept window insets on the content frame to prevent react-native-navigation
        // ComponentLayout (extends CoordinatorLayout) from adding padding for cutout/system bars
        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
                android.util.Log.i(tag, "ContentFrame onApplyWindowInsets - consumed");
                // Don't apply padding, just consume insets
                // This prevents ComponentLayout from reading insets and adding padding
                return WindowInsetsCompat.CONSUMED;
            });
            android.util.Log.i(tag, "Insets listener set on contentFrame");
        }
    }

    private void forwardIntentToReactNative(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        String url = intent.getData().toString();
        if (url == null || url.isEmpty()) return;

        try {
            MainApplication app = (MainApplication) getApplication();
            app.getReactNativeHost()
                .getReactInstanceManager()
                .getCurrentReactContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("url", url);
        } catch (Exception e) {
            // React context not ready yet, will be handled by getInitialURL
        }
    }

    private void pushShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;

        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;

        Intent recognitionIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("lxmusic://recognition"), this, MainActivity.class);
        recognitionIntent.putExtra("shortcut_id", "recognition");

        Intent settingIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("lxmusic://nav?target=setting"), this, MainActivity.class);
        settingIntent.putExtra("shortcut_id", "setting");

        ShortcutInfo recognitionShortcut = new ShortcutInfo.Builder(this, "recognition")
                .setShortLabel("识曲")
                .setLongLabel("听歌识曲")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_recognition))
                .setIntent(recognitionIntent)
                .build();

        ShortcutInfo settingShortcut = new ShortcutInfo.Builder(this, "setting")
                .setShortLabel("设置")
                .setLongLabel("打开设置")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_setting))
                .setIntent(settingIntent)
                .build();

        shortcutManager.setDynamicShortcuts(Arrays.asList(
                recognitionShortcut,
                settingShortcut
        ));
    }
}
