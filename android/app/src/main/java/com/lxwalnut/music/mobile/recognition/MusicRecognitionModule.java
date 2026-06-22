package com.lxwalnut.music.mobile.recognition;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Outline;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

public class MusicRecognitionModule extends ReactContextBaseJavaModule implements PermissionListener {
    private static final String TAG = "MusicRecognition";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORD_DURATION_SECONDS = 10;

    private final ReactApplicationContext reactContext;
    private final Handler mainHandler;
    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;
    private WindowManager windowManager;
    private View floatingContainer;
    private LinearLayout expandedPanel;
    private TextView statusTextView;
    private LinearLayout resultsContainer;
    private ScrollView debugScrollView;
    private TextView debugLogView;
    private WaveformView waveformView;
    private TextView audioInfoView;
    private boolean isExpanded = false;
    private String lastWavPath = null;

    public MusicRecognitionModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public String getName() {
        return "MusicRecognitionModule";
    }

    private void log(String msg) {
        Log.i(TAG, msg);
    }

    private void logToJS(String msg) {
        Log.i(TAG, msg);
        mainHandler.post(() -> {
            try {
                WritableMap params = Arguments.createMap();
                params.putString("type", "log");
                params.putString("message", msg);
                reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("MusicRecognitionEvent", params);
            } catch (Exception e) {
                Log.e(TAG, "sendEvent failed", e);
            }
        });
    }

    private void appendDebugLog(String msg) {
        mainHandler.post(() -> {
            if (debugLogView != null) {
                String time = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(new java.util.Date());
                debugLogView.append(time + " " + msg + "\n");
                if (debugScrollView != null) {
                    debugScrollView.post(() -> debugScrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        });
    }

    @ReactMethod
    public void checkOverlayPermission(Promise promise) {
        log("检查悬浮窗权限...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
            log("悬浮窗权限未授予");
            promise.reject("PERMISSION_DENIED", "Overlay permission not granted");
        } else {
            log("悬浮窗权限已授予");
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void openOverlayPermissionActivity(Promise promise) {
        log("打开悬浮窗权限设置...");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + reactContext.getApplicationContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                reactContext.startActivity(intent);
            }
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void checkMicrophonePermission(Promise promise) {
        log("检查麦克风权限...");
        if (ContextCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            log("麦克风权限未授予，请求中...");
            Activity activity = getCurrentActivity();
            if (activity instanceof PermissionAwareActivity) {
                ((PermissionAwareActivity) activity).requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CODE,
                        (requestCode, permissions, grantResults) -> {
                            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                log("麦克风权限已授予");
                                promise.resolve(true);
                            } else {
                                log("麦克风权限被拒绝");
                                promise.reject("PERMISSION_DENIED", "Microphone permission denied");
                            }
                            return true;
                        }
                );
            } else {
                promise.reject("NO_ACTIVITY", "No current activity");
            }
        } else {
            log("麦克风权限已授予");
            promise.resolve(true);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        return false;
    }

    @ReactMethod
    public void showFloatingButton() {
        log("显示悬浮按钮...");
        mainHandler.post(() -> {
            try {
                if (floatingContainer != null) {
                    log("悬浮按钮已存在，跳过");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
                    log("无悬浮窗权限，无法显示");
                    return;
                }

                windowManager = (WindowManager) reactContext.getSystemService(Context.WINDOW_SERVICE);

                int layoutFlag;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
                }

                floatingContainer = createFloatingView();

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        layoutFlag,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                params.x = 0;
                params.y = 0;

                windowManager.addView(floatingContainer, params);
                log("悬浮按钮显示成功");
            } catch (Exception e) {
                Log.e(TAG, "Failed to show floating button", e);
                log("显示悬浮按钮失败: " + e.getMessage());
            }
        });
    }

    @ReactMethod
    public void hideFloatingButton() {
        log("隐藏悬浮按钮...");
        mainHandler.post(() -> {
            try {
                if (floatingContainer != null && windowManager != null) {
                    windowManager.removeView(floatingContainer);
                    floatingContainer = null;
                    expandedPanel = null;
                    statusTextView = null;
                    resultsContainer = null;
                    debugLogView = null;
                    debugScrollView = null;
                    waveformView = null;
                    audioInfoView = null;
                    isExpanded = false;
                    isRecording = false;
                    log("悬浮按钮已隐藏");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide floating button", e);
            }
        });
    }

    @ReactMethod
    public void getLastRecordingPath(Promise promise) {
        if (lastWavPath != null) {
            promise.resolve(lastWavPath);
        } else {
            promise.reject("NO_RECORDING", "没有录制的音频文件");
        }
    }

    @ReactMethod
    public void playLastRecording() {
        if (lastWavPath == null) {
            log("没有可播放的录音");
            return;
        }
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            player.setDataSource(lastWavPath);
            player.prepare();
            player.start();
            player.setOnCompletionListener(mp -> {
                mp.release();
                log("播放完成");
            });
            log("开始播放录音: " + lastWavPath);
        } catch (Exception e) {
            log("播放失败: " + e.getMessage());
        }
    }

    private String saveAsWav(byte[] pcmData) {
        try {
            java.io.File dir = reactContext.getExternalFilesDir(null);
            if (dir == null) dir = reactContext.getFilesDir();
            java.io.File file = new java.io.File(dir, "recording_" + System.currentTimeMillis() + ".wav");
            
            int totalDataLen = pcmData.length + 36;
            int channels = 1;
            int bitsPerSample = 16;
            int byteRate = SAMPLE_RATE * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;

            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            java.io.DataOutputStream dos = new java.io.DataOutputStream(fos);

            dos.writeBytes("RIFF");
            dos.writeInt(Integer.reverseBytes(totalDataLen));
            dos.writeBytes("WAVE");
            dos.writeBytes("fmt ");
            dos.writeInt(Integer.reverseBytes(16));
            dos.writeShort(Short.reverseBytes((short) 1));
            dos.writeShort(Short.reverseBytes((short) channels));
            dos.writeInt(Integer.reverseBytes(SAMPLE_RATE));
            dos.writeInt(Integer.reverseBytes(byteRate));
            dos.writeShort(Short.reverseBytes((short) blockAlign));
            dos.writeShort(Short.reverseBytes((short) bitsPerSample));
            dos.writeBytes("data");
            dos.writeInt(Integer.reverseBytes(pcmData.length));
            dos.write(pcmData);

            dos.flush();
            dos.close();
            fos.close();

            log("WAV 文件已保存: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            return file.getAbsolutePath();
        } catch (Exception e) {
            log("保存 WAV 失败: " + e.getMessage());
            return null;
        }
    }

    private FrameLayout createFloatingView() {
        FrameLayout container = new FrameLayout(reactContext);

        ImageView logoButton = new ImageView(reactContext);
        try {
            int resId = reactContext.getResources().getIdentifier("ic_launcher", "mipmap", reactContext.getPackageName());
            if (resId != 0) {
                logoButton.setImageResource(resId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load launcher icon", e);
        }

        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(140, 140);
        logoButton.setLayoutParams(logoParams);
        logoButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoButton.setBackgroundColor(0xFF6C8CFF);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            logoButton.setClipToOutline(true);
            logoButton.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }

        container.addView(logoButton);

        expandedPanel = createExpandedPanel();
        expandedPanel.setVisibility(View.GONE);
        container.addView(expandedPanel);

        final float[] lastX = new float[1];
        final float[] lastY = new float[1];
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] moved = new boolean[1];

        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX[0] = event.getRawX();
                    lastY[0] = event.getRawY();
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    moved[0] = false;
                    return false; // 不消费，让子视图可以接收
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - lastX[0];
                    float dy = event.getRawY() - lastY[0];
                    if (Math.abs(event.getRawX() - downX[0]) > 10 || Math.abs(event.getRawY() - downY[0]) > 10) {
                        moved[0] = true;
                    }
                    if (moved[0] && windowManager != null) {
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
                        params.x += (int) dx;
                        params.y += (int) dy;
                        try {
                            windowManager.updateViewLayout(container, params);
                        } catch (Exception e) {
                            Log.e(TAG, "Update layout failed", e);
                        }
                        lastX[0] = event.getRawX();
                        lastY[0] = event.getRawY();
                        return true; // 拖拽时消费
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        togglePanel();
                    }
                    return false; // 不消费，让子视图触发 click
            }
            return false;
        });

        return container;
    }

    private LinearLayout createExpandedPanel() {
        LinearLayout panel = new LinearLayout(reactContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF0FFFFFF);
        panel.setPadding(40, 32, 40, 32);

        // Title
        TextView title = new TextView(reactContext);
        title.setText("听歌识曲 [调试模式]");
        title.setTextSize(18);
        title.setTextColor(0xFF333333);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        // Start button
        TextView startBtn = new TextView(reactContext);
        startBtn.setText("开始识别");
        startBtn.setTextSize(15);
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setBackgroundColor(0xFF6C8CFF);
        startBtn.setPadding(40, 20, 40, 20);
        startBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = 20;
        startBtn.setLayoutParams(btnParams);
        startBtn.setOnClickListener(v -> onStartRecognizeClick());
        panel.addView(startBtn);

        // Status
        statusTextView = new TextView(reactContext);
        statusTextView.setText("点击开始识别");
        statusTextView.setTextSize(13);
        statusTextView.setTextColor(0xFF999999);
        statusTextView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = 16;
        statusTextView.setLayoutParams(statusParams);
        panel.addView(statusTextView);

        // Audio info
        audioInfoView = new TextView(reactContext);
        audioInfoView.setText("采样率: " + SAMPLE_RATE + "Hz | 位深: 16bit | 声道: 单声道");
        audioInfoView.setTextSize(11);
        audioInfoView.setTextColor(0xFF666666);
        audioInfoView.setBackgroundColor(0xFFF5F5F5);
        audioInfoView.setPadding(16, 8, 16, 8);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoParams.topMargin = 12;
        audioInfoView.setLayoutParams(infoParams);
        panel.addView(audioInfoView);

        // Waveform view
        waveformView = new WaveformView(reactContext);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
        );
        waveParams.topMargin = 12;
        waveformView.setLayoutParams(waveParams);
        waveformView.setBackgroundColor(0xFF1A1A2E);
        panel.addView(waveformView);

        // Debug log title
        TextView debugTitle = new TextView(reactContext);
        debugTitle.setText("▼ 调试日志");
        debugTitle.setTextSize(12);
        debugTitle.setTextColor(0xFF333333);
        LinearLayout.LayoutParams debugTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        debugTitleParams.topMargin = 16;
        debugTitle.setLayoutParams(debugTitleParams);
        panel.addView(debugTitle);

        // Debug log scroll view
        debugScrollView = new ScrollView(reactContext);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300
        );
        scrollParams.topMargin = 8;
        debugScrollView.setLayoutParams(scrollParams);
        debugScrollView.setBackgroundColor(0xFF0D1117);

        debugLogView = new TextView(reactContext);
        debugLogView.setTextSize(10);
        debugLogView.setTextColor(0xFF00FF00);
        debugLogView.setPadding(16, 12, 16, 12);
        debugLogView.setTypeface(android.graphics.Typeface.MONOSPACE);
        debugLogView.setText("等待操作...\n");
        debugScrollView.addView(debugLogView);
        panel.addView(debugScrollView);

        // Results
        resultsContainer = new LinearLayout(reactContext);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resultsParams.topMargin = 16;
        resultsContainer.setLayoutParams(resultsParams);
        panel.addView(resultsContainer);

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(700, LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.setLayoutParams(panelParams);

        return panel;
    }

    private void togglePanel() {
        if (expandedPanel == null) return;
        isExpanded = !isExpanded;
        expandedPanel.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        if (isExpanded) {
            log("面板已展开");
        }
    }

    private void setStatus(String text) {
        mainHandler.post(() -> {
            if (statusTextView != null) {
                statusTextView.setText(text);
            }
        });
        Log.i(TAG, "状态: " + text);
    }

    private void onStartRecognizeClick() {
        if (isRecording) return;
        log("点击开始识别");

        if (ContextCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            log("麦克风权限未授予，请求权限...");
            Activity activity = getCurrentActivity();
            if (activity instanceof PermissionAwareActivity) {
                ((PermissionAwareActivity) activity).requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CODE,
                        (requestCode, permissions, grantResults) -> {
                            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                log("麦克风权限已授予");
                                mainHandler.post(this::startRecording);
                            } else {
                                log("麦克风权限被拒绝");
                                mainHandler.post(() -> setStatus("需要麦克风权限"));
                            }
                            return true;
                        }
                );
            } else {
                setStatus("需要麦克风权限");
            }
            return;
        }

        startRecording();
    }

    private void startRecording() {
        log("初始化录音...");
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        log("最小缓冲区大小: " + bufferSize + " 字节");

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            log("录音缓冲区初始化失败: " + bufferSize);
            setStatus("录音初始化失败");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                log("录音设备状态: " + audioRecord.getState() + " (期望: " + AudioRecord.STATE_INITIALIZED + ")");
                setStatus("录音设备初始化失败");
                return;
            }

            log("录音设备初始化成功");
            log("开始录音... 采样率=" + SAMPLE_RATE + "Hz, 时长=" + RECORD_DURATION_SECONDS + "秒");
            isRecording = true;
            audioRecord.startRecording();
            setStatus("录制中... " + RECORD_DURATION_SECONDS + "秒");

            new Thread(() -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[bufferSize];
                int totalBytes = SAMPLE_RATE * RECORD_DURATION_SECONDS * 2;
                int bytesRead = 0;
                int readCount = 0;
                long maxAmplitude = 0;
                long sumAmplitude = 0;
                int sampleCount = 0;

                log("总字节数: " + totalBytes + ", 缓冲区: " + bufferSize);

                while (isRecording && bytesRead < totalBytes) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        baos.write(buffer, 0, read);
                        bytesRead += read;
                        readCount++;

                        // 计算音频振幅
                        for (int i = 0; i < read - 1; i += 2) {
                            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                            long amplitude = Math.abs(sample);
                            if (amplitude > maxAmplitude) maxAmplitude = amplitude;
                            sumAmplitude += amplitude;
                            sampleCount++;
                        }

                        int remaining = (totalBytes - bytesRead) / (SAMPLE_RATE * 2);
                        long avgAmplitude = sampleCount > 0 ? sumAmplitude / sampleCount : 0;

                        final long finalMax = maxAmplitude;
                        final long finalAvg = avgAmplitude;
                        final int finalBytesRead = bytesRead;
                        final int finalTotal = totalBytes;
                        final int readBuf = read;
                        final int finalSampleCount = sampleCount;

                        mainHandler.post(() -> {
                            setStatus("录制中... " + remaining + "秒 | 已读: " + finalBytesRead + "/" + finalTotal);
                            audioInfoView.setText("PCM: " + finalBytesRead + " bytes | 采样: " + finalSampleCount
                                    + " | 最大振幅: " + finalMax + " | 平均振幅: " + finalAvg
                                    + " | 本帧: " + readBuf);
                            waveformView.addAmplitude(finalMax);
                        });

                        if (readCount % 20 == 0) {
                            log("录制进度: " + bytesRead + "/" + totalBytes + " bytes | 读取次数: " + readCount
                                    + " | 最大振幅: " + maxAmplitude + " | 平均振幅: " + (sampleCount > 0 ? sumAmplitude / sampleCount : 0));
                        }
                    } else {
                        log("录音读取失败: " + read);
                    }
                }

                isRecording = false;
                try {
                    audioRecord.stop();
                    audioRecord.release();
                    log("录音停止并释放");
                } catch (Exception e) {
                    Log.e(TAG, "Stop recording failed", e);
                    log("停止录音失败: " + e.getMessage());
                }
                audioRecord = null;

                byte[] pcmData = baos.toByteArray();
                logToJS("录音完成: " + pcmData.length + " bytes | 采样数: " + sampleCount
                        + " | 最大振幅: " + maxAmplitude + " | 平均振幅: " + (sampleCount > 0 ? sumAmplitude / sampleCount : 0));

                // 显示前几个采样点
                StringBuilder sampleStr = new StringBuilder("前20个采样点: ");
                for (int i = 0; i < Math.min(40, pcmData.length); i += 2) {
                    short s = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
                    sampleStr.append(s).append(" ");
                }
                logToJS(sampleStr.toString());

                // 保存为 WAV 文件
                lastWavPath = saveAsWav(pcmData);
                if (lastWavPath != null) {
                    logToJS("WAV 已保存: " + lastWavPath);
                }

                setStatus("识别中...");
                sendToApi(pcmData);
            }).start();

        } catch (SecurityException e) {
            log("录音权限异常: " + e.getMessage());
            setStatus("录音权限被拒绝");
        } catch (Exception e) {
            log("录音异常: " + e.getMessage());
            setStatus("录音失败: " + e.getMessage());
        }
    }

    private void sendToApi(byte[] pcmData) {
        new Thread(() -> {
            try {
                String fpid = String.valueOf(System.currentTimeMillis());
                String dfid = "8N56O9BOG0BLY16UGKZ4KK2M";
                String mid = "5f5ff6b534ce4f1702c642b779648ed3";
                String appid = "1005";
                String clientver = "20489";
                String clienttime = String.valueOf(System.currentTimeMillis() / 1000);

                // 构建参数（按key排序生成签名）
                java.util.TreeMap<String, String> paramMap = new java.util.TreeMap<>();
                paramMap.put("appid", appid);
                paramMap.put("area_code", "1");
                paramMap.put("clienttime", clienttime);
                paramMap.put("clientver", clientver);
                paramMap.put("dfid", dfid);
                paramMap.put("fpid", fpid);
                paramMap.put("include_unpublish", "1");
                paramMap.put("mid", mid);
                paramMap.put("multi_result", "1");
                paramMap.put("useid", "1898352613");

                // 生成签名：MD5(salt + sorted_params_string + data + salt)
                // 注意：对于二进制数据（PCM），签名需要包含数据本身
                StringBuilder paramsString = new StringBuilder();
                for (java.util.Map.Entry<String, String> entry : paramMap.entrySet()) {
                    paramsString.append(entry.getKey()).append("=").append(entry.getValue());
                }

                String salt = "OIlwieks28dk2k092lksi2UIkp";
                // 用字节数组拼接签名：salt + paramsString + pcmData + salt
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                md.update(salt.getBytes("UTF-8"));
                md.update(paramsString.toString().getBytes("UTF-8"));
                md.update(pcmData);
                md.update(salt.getBytes("UTF-8"));
                byte[] digest = md.digest();
                StringBuilder sigBuilder = new StringBuilder();
                for (byte b : digest) {
                    sigBuilder.append(String.format("%02x", b));
                }
                String signature = sigBuilder.toString();

                // 构建 URL
                StringBuilder urlBuilder = new StringBuilder("https://gateway.kugou.com/fingerprint.service/v1/music_trackid_mulit?");
                for (java.util.Map.Entry<String, String> entry : paramMap.entrySet()) {
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                urlBuilder.append("signature=").append(signature);

                String apiUrl = urlBuilder.toString();
                logToJS("=== 开始 API 请求 ===");
                logToJS("签名参数: " + paramsString.toString());
                logToJS("签名: " + signature);
                logToJS("URL: " + apiUrl);
                logToJS("PCM: " + pcmData.length + " bytes");

                setStatus("正在请求 API...");

                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("User-Agent", "KuGou/11490 (Android)");
                conn.setRequestProperty("Cookie", "KugooID=1898352613; userid=1898352613; token=0f09b5ab0836bba4d76f12406401479e4535ae0097491274892a876c4fe1b93a; dfid=8N56O9BOG0BLY16UGKZ4KK2M; mid=5f5ff6b534ce4f1702c642b779648ed3");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                setStatus("正在发送音频数据...");
                long startTime = System.currentTimeMillis();
                
                java.io.OutputStream os = conn.getOutputStream();
                os.write(pcmData);
                os.flush();
                os.close();

                setStatus("等待服务器响应...");
                int responseCode = conn.getResponseCode();
                long elapsed = System.currentTimeMillis() - startTime;
                logToJS("响应码: " + responseCode + " | 耗时: " + elapsed + "ms");

                String response = null;
                try {
                    java.io.InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) {
                        ByteArrayOutputStream responseBaos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            responseBaos.write(buf, 0, len);
                        }
                        response = responseBaos.toString("UTF-8");
                        is.close();
                    }
                } catch (Exception e) {
                    logToJS("读取响应失败: " + e.getMessage());
                }

                conn.disconnect();

                if (response != null && response.length() > 0) {
                    logToJS("响应长度: " + response.length() + " bytes");
                    logToJS("响应: " + response.substring(0, Math.min(response.length(), 1000)));
                    parseResults(response);
                } else {
                    logToJS("响应为空 | HTTP " + responseCode);
                    setStatus("请求失败: HTTP " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "API error", e);
                String errorDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
                logToJS("!!! API 错误: " + errorDetail);
                setStatus("网络错误: " + errorDetail);
            }
        }).start();
    }

    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "MD5 error", e);
            return "";
        }
    }

    private void parseResults(String json) {
        log("解析响应...");
        try {
            if (json == null || json.isEmpty()) {
                log("响应为空");
                mainHandler.post(() -> setStatus("响应为空"));
                return;
            }

            JSONObject obj = new JSONObject(json);
            int status = obj.optInt("status", 0);
            log("status=" + status);

            if (status != 1) {
                log("未识别到歌曲 (status != 1)");
                String errorMsg = obj.optString("msg", "未知错误");
                log("错误信息: " + errorMsg);
                mainHandler.post(() -> setStatus("未识别到歌曲: " + errorMsg));
                return;
            }

            JSONArray dataArray = obj.optJSONArray("data");
            if (dataArray == null || dataArray.length() == 0) {
                log("data 数组为空");
                mainHandler.post(() -> setStatus("未识别到歌曲"));
                return;
            }

            log("识别到 " + dataArray.length() + " 个结果");
            // 打印第一条结果的完整字段用于调试
            if (dataArray.length() > 0) {
                log("第一条结果完整JSON: " + dataArray.getJSONObject(0).toString());
            }
            int count = Math.min(dataArray.length(), 5);
            // [0]=songname, [1]=singername, [2]=album, [3]=dist, [4]=hash, [5]=duration
            final String[][] songs = new String[count][6];

            for (int i = 0; i < count; i++) {
                try {
                    JSONObject item = dataArray.getJSONObject(i);
                    songs[i][0] = item.optString("songname", "未知");
                    songs[i][1] = item.optString("singername", "未知歌手");
                    // dist 可能是 number 或 string
                    try {
                        double distVal = item.optDouble("dist", 0);
                        songs[i][3] = String.valueOf(distVal);
                    } catch (Exception e) {
                        songs[i][3] = item.optString("dist", "0");
                    }
                    songs[i][4] = item.optString("hash", "");
                    songs[i][5] = String.valueOf(item.optLong("timelength_128", 0));
                    String album = "";
                    JSONArray albumArr = item.optJSONArray("album");
                    if (albumArr != null && albumArr.length() > 0) {
                        album = albumArr.getJSONObject(0).optString("albumname", "");
                    }
                    songs[i][2] = album;
                    log("结果 " + (i + 1) + ": " + songs[i][0] + " - " + songs[i][1]
                            + (album.isEmpty() ? "" : " | " + album)
                            + " | dist=" + songs[i][3] + " | hash=" + songs[i][4]);
                } catch (Exception e) {
                    log("解析第 " + (i + 1) + " 个结果失败: " + e.getMessage());
                    songs[i][0] = "解析失败";
                    songs[i][1] = "";
                    songs[i][2] = "";
                    songs[i][3] = "";
                }
            }

            final int finalCount = count;
            mainHandler.post(() -> {
                try {
                    if (resultsContainer == null) return;
                    resultsContainer.removeAllViews();

                    for (int i = 0; i < finalCount; i++) {
                        final int idx = i;

                        // 横向布局：封面 + 文字信息
                        LinearLayout row = new LinearLayout(reactContext);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(16, 12, 16, 12);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        row.setClickable(true);
                        row.setFocusable(true);

                        // 封面图
                        ImageView cover = new ImageView(reactContext);
                        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(120, 120);
                        coverParams.setMarginEnd(16);
                        cover.setLayoutParams(coverParams);
                        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        cover.setBackgroundColor(0xFFE0E0E0);
                        // 加载封面
                        String coverUrlRaw = "";
                        try {
                            JSONObject item = dataArray.getJSONObject(i);
                            coverUrlRaw = item.optString("union_cover", "");
                            if (coverUrlRaw.isEmpty()) coverUrlRaw = item.optString("image", "");
                        } catch (Exception ignored) {}
                        final String coverUrl = coverUrlRaw;
                        if (!coverUrl.isEmpty()) {
                            new Thread(() -> {
                                try {
                                    java.net.URL imgUrl = new java.net.URL(coverUrl.replace("{size}", "200"));
                                    java.net.HttpURLConnection imgConn = (java.net.HttpURLConnection) imgUrl.openConnection();
                                    imgConn.setConnectTimeout(5000);
                                    imgConn.setReadTimeout(5000);
                                    java.io.InputStream imgIs = imgConn.getInputStream();
                                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(imgIs);
                                    imgIs.close();
                                    imgConn.disconnect();
                                    if (bmp != null) {
                                        mainHandler.post(() -> cover.setImageBitmap(bmp));
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Load cover failed", e);
                                }
                            }).start();
                        }
                        row.addView(cover);

                        // 文字区域
                        LinearLayout textArea = new LinearLayout(reactContext);
                        textArea.setOrientation(LinearLayout.VERTICAL);
                        textArea.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                        TextView songName = new TextView(reactContext);
                        songName.setText(songs[i][0]);
                        songName.setTextSize(15);
                        songName.setTextColor(0xFF333333);
                        songName.setMaxLines(1);
                        songName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        textArea.addView(songName);

                        TextView artist = new TextView(reactContext);
                        String artistText = songs[i][1] + (songs[i][2].isEmpty() ? "" : " · " + songs[i][2]);
                        artist.setText(artistText);
                        artist.setTextSize(12);
                        artist.setTextColor(0xFF999999);
                        artist.setMaxLines(1);
                        textArea.addView(artist);

                        // 匹配度
                        try {
                            double distVal = Double.parseDouble(songs[i][3]);
                            if (distVal > 0) {
                                TextView distView = new TextView(reactContext);
                                distView.setText(String.format("%.0f%%", distVal * 100));
                                distView.setTextSize(11);
                                distView.setTextColor(0xFF6C8CFF);
                                textArea.addView(distView);
                            }
                        } catch (NumberFormatException e) { /* ignore */ }

                        row.addView(textArea);

                        // 点击事件
                        row.setOnClickListener(v -> {
                            logToJS("=== 点击歌曲 ===");
                            logToJS("歌曲: " + songs[idx][0] + " - " + songs[idx][1]);
                            logToJS("hash: " + songs[idx][4] + " | 封面: " + coverUrl);
                            try {
                                WritableMap event = Arguments.createMap();
                                event.putString("action", "play");
                                event.putString("songname", songs[idx][0]);
                                event.putString("singername", songs[idx][1]);
                                event.putString("album", songs[idx][2]);
                                event.putString("hash", songs[idx][4]);
                                event.putString("duration", songs[idx][5]);
                                event.putString("cover", coverUrl);
                                reactContext
                                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                    .emit("MusicRecognitionEvent", event);
                                logToJS("play 事件已发送到 JS");
                            } catch (Exception e) {
                                Log.e(TAG, "emit play event failed", e);
                                logToJS("emit 失败: " + e.getMessage());
                            }
                        });

                        resultsContainer.addView(row);

                        if (i < finalCount - 1) {
                            View divider = new View(reactContext);
                            divider.setBackgroundColor(0xFFEEEEEE);
                            divider.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
                            resultsContainer.addView(divider);
                        }
                    }
                    setStatus("识别完成，共 " + finalCount + " 个结果");
                } catch (Exception e) {
                    Log.e(TAG, "UI update error", e);
                    log("UI 更新错误: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Parse error", e);
            log("解析错误: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log("原始响应: " + (json != null ? json.substring(0, Math.min(json.length(), 200)) : "null"));
            mainHandler.post(() -> setStatus("解析结果失败: " + e.getMessage()));
        }
    }

    // 波形视图
    static class WaveformView extends View {
        private final Paint paint;
        private final float[] amplitudes = new float[100];
        private int amplitudeIndex = 0;

        public WaveformView(Context context) {
            super(context);
            paint = new Paint();
            paint.setColor(0xFF6C8CFF);
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.FILL);
        }

        public void addAmplitude(long amplitude) {
            amplitudes[amplitudeIndex % amplitudes.length] = amplitude;
            amplitudeIndex++;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFF0D1117);

            float width = getWidth();
            float height = getHeight();
            float barWidth = width / amplitudes.length;

            for (int i = 0; i < amplitudes.length; i++) {
                int idx = (amplitudeIndex - amplitudes.length + i + amplitudes.length * 2) % amplitudes.length;
                float amp = amplitudes[idx];
                float normalizedAmp = Math.min(amp / 32768f, 1f);
                float barHeight = normalizedAmp * height * 0.8f;

                float x = i * barWidth;
                float y = (height - barHeight) / 2;

                if (normalizedAmp > 0.8f) {
                    paint.setColor(0xFFFF4444);
                } else if (normalizedAmp > 0.5f) {
                    paint.setColor(0xFFFFAA00);
                } else {
                    paint.setColor(0xFF6C8CFF);
                }

                canvas.drawRect(x + 1, y, x + barWidth - 1, y + barHeight, paint);
            }

            // 中线
            paint.setColor(0xFF333355);
            paint.setStrokeWidth(1);
            canvas.drawLine(0, height / 2, width, height / 2, paint);
        }
    }
}
