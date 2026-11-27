package com.sunyun.anygo.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.sunyun.anygo.MainActivity;
import com.sunyun.anygo.R;
import com.sunyun.anygo.joystick.JoyStick;

public class ServiceGo extends Service {
    // 定位相关变量
    public static final double DEFAULT_LAT = 36.667662;
    public static final double DEFAULT_LNG = 117.027707;
    public static final double DEFAULT_ALT = 55.0D;

    /* 进程内静态状态：Activity 被回收重建后据此恢复"模拟中"UI（ServiceGo 与 App 同进程，静态标记可靠） */
    public static volatile boolean sRunning = false;
    public static double sCurLng = DEFAULT_LNG;
    public static double sCurLat = DEFAULT_LAT;
    public static double sCurAlt = DEFAULT_ALT;
    private static OnMockStatusListener sStatusListener;
    private static int sTick = 0;
    private static boolean sFusedChecked = false;    // 当前机型是否已探测过 fused Provider
    private static boolean sFusedAvailable = true;   // fused Provider 是否可用

    /** 模拟位置状态回调（约 1s 一次，子线程回调，消费方自行切主线程） */
    public interface OnMockStatusListener {
        void onMockPosition(double lng, double lat, double alt);
    }

    public static void setStatusListener(OnMockStatusListener l) {
        sStatusListener = l;
    }

    public static boolean isRunning() {
        return sRunning;
    }
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    /* 模拟数据合理性：摇杆位移平滑步进 + 速度按实际位移计算 + 精度/卫星数随机化 */
    private final java.util.Random mRandom = new java.util.Random();
    private double mStepLng = 0;        // 每 100ms 步进的经度增量（摇杆 1s 位移的 1/10）
    private double mStepLat = 0;
    private int mStepsRemaining = 0;
    private double mPrevLng = 0;        // 上一次注入的位置（用于计算实际位移）
    private double mPrevLat = 0;
    private boolean mJumpFlag = true;   // 初始化/传送跳变后，首个 tick 速度置 0
    private float mCurSpeed = 0f;       // 当前 tick 注入的速度
    private static final int HANDLER_MSG_ID = 0;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private boolean isStop = false;
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关
    private JoyStick mJoyStick;

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        removeTestProviderNetwork();
        addTestProviderNetwork();

        removeTestProviderGPS();
        addTestProviderGPS();

        // 融合定位 Provider（国产 ROM 与微信/Keep 等常用），部分机型不存在该 Provider，失败忽略
        removeTestProviderFused();
        addTestProviderFused();

        sRunning = true;
        sCurLng = mCurLng;
        sCurLat = mCurLat;
        sCurAlt = mCurAlt;

        initGoLocation();

        initNotification();

        initJoyStick();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY：进程被杀后系统以 null Intent 重启服务，沿用重启前的位置继续模拟
        if (intent != null) {
            mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
            mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
            mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);
            mJumpFlag = true;
            mStepsRemaining = 0;
        }
        sCurLng = mCurLng;
        sCurLat = mCurLat;
        sCurAlt = mCurAlt;

        mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isStop = true;
        mLocHandler.removeMessages(HANDLER_MSG_ID);
        mLocHandlerThread.quit();

        mJoyStick.destroy();

        removeTestProviderNetwork();
        removeTestProviderGPS();
        removeTestProviderFused();

        sRunning = false;
        sStatusListener = null;

        unregisterReceiver(mActReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        registerReceiver(mActReceiver, filter);

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID, SERVICE_GO_NOTE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

        //准备intent
        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent showIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        PendingIntent showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent hideIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        PendingIntent hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_service_tips))
                .setContentIntent(clickPI)
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_show), showPendingPI))
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_hide), hidePendingPI))
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(SERVICE_GO_NOTE_ID, notification);
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(this);
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
                mSpeed = speed;
                // 根据当前的经纬度和距离，计算下一个经纬度
                // Latitude: 1 deg = 110.574 km // 纬度的每度的距离大约为 110.574km
                // Longitude: 1 deg = 111.320*cos(latitude) km  // 经度的每度的距离从0km到111km不等
                // 具体见：http://wp.mlab.tw/?p=2200
                // 1s 位移拆成 10 个 100ms 步进：位置连续变化，速度曲线真实（原先每秒跳变一次）
                mStepLng = disLng / (111.320 * Math.cos(Math.abs(mCurLat) * Math.PI / 180)) / 10.0;
                mStepLat = disLat / 110.574 / 10.0;
                mStepsRemaining = 10;
                mCurBea = (float) angle;
            }

            @Override
            public void onPositionInfo(double lng, double lat, double alt) {
                mCurLng = lng;
                mCurLat = lat;
                mCurAlt = alt;
            }
        });
        mJoyStick.show();
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                try {
                    Thread.sleep(100);

                    if (!isStop) {
                        // 推进摇杆平滑步进（每 tick 1/10 秒位移）
                        if (mStepsRemaining > 0) {
                            mCurLng += mStepLng;
                            mCurLat += mStepLat;
                            mStepsRemaining--;
                        }

                        // 速度按实际位移计算：静止=0，摇杆移动≈设定速度，传送跳变=0
                        double disp = distanceMeters(mPrevLat, mPrevLng, mCurLat, mCurLng);
                        if (mJumpFlag) {
                            mCurSpeed = 0f;
                            mJumpFlag = false;
                        } else {
                            mCurSpeed = (float) (disp / 0.1);
                        }
                        mPrevLng = mCurLng;
                        mPrevLat = mCurLat;

                        setLocationNetwork();
                        setLocationGPS();
                        setLocationFused();

                        // 约 1s 节流：同步静态位置并通知监听方（主界面状态条/模拟点标记）
                        sTick++;
                        if (sTick % 10 == 0) {
                            sCurLng = mCurLng;
                            sCurLat = mCurLat;
                            sCurAlt = mCurAlt;
                            OnMockStatusListener l = sStatusListener;
                            if (l != null) {
                                l.onMockPosition(mCurLng, mCurLat, mCurAlt);
                            }
                        }

                        sendEmptyMessage(HANDLER_MSG_ID);
                    }
                } catch (InterruptedException e) {
                    XLog.e("SERVICEGO: ERROR - handleMessage");
                    Thread.currentThread().interrupt();
                }
            }
        };

        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    private void setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(4f + mRandom.nextFloat() * 10f);   // 精度随机 4~14 米（真实 GPS 量级，恒 1 米过于完美）
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed(mCurSpeed);                    // 实际位移速度（静止为 0）
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 5 + mRandom.nextInt(8));   // 卫星数随机 5~12
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS");
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void removeTestProviderFused() {
        if (!sFusedAvailable) {
            return;
        }
        try {
            if (mLocManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.FUSED_PROVIDER);
            }
        } catch (Exception e) {
            // 部分机型无 fused Provider，忽略
            XLog.e("SERVICEGO: ERROR - removeTestProviderFused");
        }
    }

    @SuppressLint("wrongconstant")
    private void addTestProviderFused() {
        if (!sFusedAvailable && sFusedChecked) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.FUSED_PROVIDER, true, true,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.FUSED_PROVIDER, true, true,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            }
            mLocManager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, true);
            sFusedChecked = true;
        } catch (Exception e) {
            // 部分机型无 fused Provider：只记一次日志，之后静默跳过（原先每 100ms 刷一条）
            sFusedChecked = true;
            sFusedAvailable = false;
            XLog.e("SERVICEGO: fused Provider 不可用，已停用融合定位模拟");
        }
    }

    private void setLocationFused() {
        if (!sFusedAvailable) {
            return;
        }
        try {
            Location loc = new Location(LocationManager.FUSED_PROVIDER);
            loc.setAccuracy(4f + mRandom.nextFloat() * 10f);   // 精度随机 4~14 米
            loc.setAltitude(mCurAlt);
            loc.setBearing(mCurBea);
            loc.setLatitude(mCurLat);
            loc.setLongitude(mCurLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed(mCurSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.FUSED_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationFused");
        }
    }

    private void setLocationNetwork() {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(20f + mRandom.nextFloat() * 40f);  // 精度随机 20~60 米（真实基站/WiFi 量级）
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed(mCurSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork");
        }
    }

    /** 两点间球面距离（米），用于按实际位移计算速度 */
    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
                    mJoyStick.show();
                }

                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
                    mJoyStick.hide();
                }
            }
        }
    }

    public class ServiceGoBinder extends Binder {
        public void setPosition(double lng, double lat, double alt) {
            mLocHandler.removeMessages(HANDLER_MSG_ID);
            mCurLng = lng;
            mCurLat = lat;
            mCurAlt = alt;
            mJumpFlag = true;           // 传送跳变：下一 tick 速度置 0，不产生瞬移速度尖峰
            mStepsRemaining = 0;
            sCurLng = lng;
            sCurLat = lat;
            sCurAlt = alt;
            mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
            mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);

            // 立即通知监听方，传送后界面无需等待下一个 1s 节流周期
            OnMockStatusListener l = sStatusListener;
            if (l != null) {
                l.onMockPosition(lng, lat, alt);
            }
        }
    }
}


