package com.lxwalnut.music.mobile.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class MusicWidgetModule extends ReactContextBaseJavaModule {

    private static final String TAG = "MusicWidgetModule";
    private final ReactApplicationContext reactContext;
    private BroadcastReceiver widgetActionReceiver;

    MusicWidgetModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        registerWidgetActionReceiver();
    }

    @Override
    public String getName() {
        return "Music