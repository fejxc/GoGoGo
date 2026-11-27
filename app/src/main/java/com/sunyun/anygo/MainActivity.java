package com.sunyun.anygo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sunyun.anygo.service.ServiceGo;
import com.sunyun.anygo.database.DataBaseHistoryLocation;
import com.sunyun.anygo.database.DataBaseHistorySearch;
import com.sunyun.anygo.utils.PreconditionChecker;
import com.sunyun.anygo.utils.ShareUtils;
import com.sunyun.anygo.utils.GoUtils;
import com.sunyun.anygo.utils.MapUtils;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /* 更新检测总开关：待本仓库发布稳定版本后置为 true 接入（checkUpdateVersion 指向本仓库 Releases） */
    private static final boolean UPDATE_CHECK_ENABLED = false;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    public final static BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static AMap mAMap = null;
    private static LatLng mMarkLatLngMap = new LatLng(36.547743718042415, 117.07018449827267); // 当前标记的地图点（GCJ-02 坐标系）
    private static String mMarkName = null;
    private GeocodeSearch mGeocodeSearch;
    private View mPoiPanel;
    private LocationSource.OnLocationChangedListener mLocationChangedListener;
    private TextView mStatusChip;
    private static Marker mUserMarker;     // 用户选点标记（图钉）
    private static Marker mMockMarker;     // 模拟位置标记（蓝点，运行期间常驻）
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private AMapLocationClient mLocClient = null;
    private double mCurrentLat = 0.0;       // 当前位置的纬度（GCJ-02）
    private double mCurrentLon = 0.0;       // 当前位置的经度（GCJ-02）
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isMockServStart = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    private Inputtips mInputtips;
    /*============================== 更新 相关 ==============================*/
    private DownloadManager mDownloadManager = null;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        initMap(savedInstanceState);

        initMapLocation();

        initMapButton();

        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder)service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        initStoreHistory();

        initSearchView();

        if (UPDATE_CHECK_ENABLED) {
            initUpdateVersion();

            checkUpdateVersion(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // ServiceGo 与 App 同进程：静态标记可靠。Activity 被回收重建后恢复"模拟中" UI
        if (ServiceGo.sRunning) {
            isMockServStart = true;
            mButtonStart.setImageResource(R.drawable.ic_fly);
            bindService(new Intent(this, ServiceGo.class), mConnection, BIND_AUTO_CREATE);
            ServiceGo.setStatusListener(mMockStatusListener);
            onMockPositionUpdate(ServiceGo.sCurLng, ServiceGo.sCurLat);
        }
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mMapView.onResume();
        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isMockServStart) {
            unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }
        if (UPDATE_CHECK_ENABLED) {
            unregisterReceiver(mDownloadBdRcv);
        }

        ServiceGo.setStatusListener(null);

        mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        mLocClient.stopLocation();
        mLocClient.onDestroy();
        // 关闭定位图层
        mAMap.setMyLocationEnabled(false);
        mMapView.onDestroy();

        //close db
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new  MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                //展示搜索历史
                List<Map<String, Object>> data = getSearchHistory();

                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_item,
                            new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[] {R.id.search_key,
                                    R.id.search_description,
                                    R.id.search_timestamp,
                                    R.id.search_isLoc,
                                    R.id.search_longitude,
                                    R.id.search_latitude});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }

                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);// 设置searchView处于展开状态
        searchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                try {
                    requestSearchSuggestion(query);
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    mAMap.clear();
                    mSearchLayout.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);

                if (newText != null && !newText.isEmpty()) {
                    requestSearchSuggestion(newText);
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);

                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                    }
                }
            /* 检测更新入口已随菜单项移除，待稳定版发布后恢复（预留 checkUpdateVersion） */
            } else if (id == R.id.nav_feedback) {
                // 问题反馈：跳转本仓库 Issues
                Uri issueUri = Uri.parse("https://github.com/fejxc/GoGoGo/issues");
                startActivity(new Intent(Intent.ACTION_VIEW, issueUri));
            } else if (id == R.id.nav_contact) {
                // 联系作者：跳转本仓库主页
                Uri contactUri = Uri.parse("https://github.com/fejxc/GoGoGo");
                startActivity(new Intent(Intent.ACTION_VIEW, contactUri));
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(GoUtils.getVersionName(this));
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap(Bundle savedInstanceState) {
        // 地图初始化（高德）
        mMapView = findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        mAMap = mMapView.getMap();
        mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
        // 定位蓝点图层：位置由 initMapLocation 中的高德定位回调经 LocationSource 送入
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);   // 蓝色箭头随手机朝向旋转
        myLocationStyle.interval(1000);
        mAMap.setMyLocationStyle(myLocationStyle);
        mAMap.setLocationSource(new LocationSource() {
            @Override
            public void activate(OnLocationChangedListener onLocationChangedListener) {
                mLocationChangedListener = onLocationChangedListener;
            }

            @Override
            public void deactivate() {
                mLocationChangedListener = null;
            }
        });
        mAMap.setMyLocationEnabled(true);
        mAMap.setOnMapClickListener(point -> {
            mMarkLatLngMap = point;
            markMap();
        });
        mAMap.setOnMapLongClickListener(point -> {
            mMarkLatLngMap = point;
            markMap();
            mGeocodeSearch.getFromLocationAsyn(new RegeocodeQuery(new LatLonPoint(point.latitude, point.longitude), 200, GeocodeSearch.AMAP));
        });
        // 注：高德地图双击默认即为放大操作，无需额外监听；POI 点点击并入普通地图点击

        // 逆地理编码（搜索 SDK），长按选点后展示位置信息面板
        try {
        mGeocodeSearch = new GeocodeSearch(this);
        mGeocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @Override
            public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
                if (rCode != 1000 || regeocodeResult == null || regeocodeResult.getRegeocodeAddress() == null) {
                    XLog.i("逆地理位置失败! rCode=" + rCode);
                    return;
                }

                String address = regeocodeResult.getRegeocodeAddress().getFormatAddress();
                mMarkName = address;
                if (mMarkLatLngMap != null) {
                    TextView poiLatitude = mPoiPanel.findViewById(R.id.poi_latitude);
                    TextView poiLongitude = mPoiPanel.findViewById(R.id.poi_longitude);
                    poiLatitude.setText(String.valueOf(mMarkLatLngMap.latitude));
                    poiLongitude.setText(String.valueOf(mMarkLatLngMap.longitude));
                }
                TextView poiAddress = mPoiPanel.findViewById(R.id.poi_address);
                poiAddress.setText(address);
                mPoiPanel.setVisibility(View.VISIBLE);
            }

            @Override
            public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult geocodeResult, int i) {

            }
        });
        } catch (Exception e) {
            XLog.e("ERROR: initGeocodeSearch " + e.getMessage());
        }

        // 选中位置的信息面板（百度版为 InfoWindow，高德 InfoWindow 不支持子控件点击，改为地图底部面板展示）
        mPoiPanel = findViewById(R.id.poi_panel);
        TextView poiAddress = mPoiPanel.findViewById(R.id.poi_address);
        TextView poiLongitude = mPoiPanel.findViewById(R.id.poi_longitude);
        TextView poiLatitude = mPoiPanel.findViewById(R.id.poi_latitude);
        ImageButton ibSave = mPoiPanel.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            if (mMarkLatLngMap != null) {
                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_save));
            }
        });
        ImageButton ibCopy = mPoiPanel.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", mMarkLatLngMap.toString());
            // 将 ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);

            GoUtils.DisplayToast(this,  getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = mPoiPanel.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", poiLongitude.getText()+","+poiLatitude.getText()));
        ImageButton ibFly = mPoiPanel.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);

        mStatusChip = findViewById(R.id.status_chip);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    //开启地图的定位图层
    private void initMapLocation() {
        try {
            // 定位初始化（高德，返回 GCJ-02 坐标，与地图一致）
            mLocClient = new AMapLocationClient(getApplicationContext());
            mLocClient.setLocationOption(getLocationClientOption());
            mLocClient.setLocationListener(amapLocation -> {
                if (!(amapLocation instanceof AMapLocation) || mMapView == null) {  // mapview 销毁后不在处理新接收的位置
                    return;
                }

                AMapLocation location = (AMapLocation) amapLocation;
                if (location.getErrorCode() != 0) {
                    // 错误码参见 https://lbs.amap.com/api/android-location-sdk/guide/utilities/errorcode
                    XLog.i("AMapLocation ERROR: " + location.getErrorCode() + "-" + location.getErrorInfo());
                    return;
                }

                mCurrentCity = location.getCity();
                mCurrentLat = location.getLatitude();
                mCurrentLon = location.getLongitude();

                // 通过 LocationSource 回调刷新定位蓝点；bearing 取自传感器方向，蓝点随之旋转
                location.setBearing(mCurrentDirection);
                if (mLocationChangedListener != null) {
                    mLocationChangedListener.onLocationChanged(location);
                }

                if (isFirstLoc) {
                    isFirstLoc = false;
                    // 这里记录高德地图返回的位置（GCJ-02）
                    mMarkLatLngMap = new LatLng(mCurrentLat, mCurrentLon);
                    CameraUpdate update = CameraUpdateFactory.newCameraPosition(new CameraPosition(mMarkLatLngMap, 18.0f, 0, 0));
                    mAMap.animateCamera(update);

                    XLog.i("First AMap LatLng: " + mMarkLatLngMap);
                }
            });
            //开始定位
            mLocClient.startLocation();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }

    @NonNull
    private static AMapLocationClientOption getLocationClientOption() {
        AMapLocationClientOption locationOption = new AMapLocationClientOption();
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //可选，默认1000ms，设置连续定位的时间间隔
        locationOption.setInterval(1000);
        //可选，设置是否需要地址信息，默认不需要（用于获取当前城市）
        locationOption.setNeedAddress(true);
        //可选，默认false，设置是否使用 GPS 优先
        locationOption.setGpsFirst(false);
        //可选，设置是否允许模拟软件伪造位置，默认 false
        locationOption.setMockEnable(false);
        return locationOption;
    }

    //地图上各按键的监听
    private void initMapButton() {
        RadioGroup mGroupMapType = this.findViewById(R.id.RadioGroupMapType);
        mGroupMapType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mapNormal) {
                mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
            }

            if (checkedId == R.id.mapSatellite) {
                mAMap.setMapType(AMap.MAP_TYPE_SATELLITE);
            }

            if (checkedId == R.id.mapNight) {
                mAMap.setMapType(AMap.MAP_TYPE_NIGHT);
            }
        });

        ImageButton curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomIn()));

        ImageButton zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mAMap.animateCamera(CameraUpdateFactory.zoomOut()));

        ImageButton inputPosBtn = this.findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("请输入经度和纬度");
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            RadioButton rbGCJ = view.findViewById(R.id.pos_type_bd);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                String dialog_lng_str = dialog_lng.getText().toString();
                String dialog_lat_str = dialog_lat.getText().toString();

                if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                    GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.app_error_input));
                } else {
                    double dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    double dialog_lat_double = Double.parseDouble(dialog_lat_str);

                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                        GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_longitude));
                    } else {
                        if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                            GoUtils.DisplayToast(MainActivity.this,  getResources().getString(R.string.app_error_latitude));
                        } else {
                            if (rbGCJ.isChecked()) {
                                // 高德 GCJ-02 坐标，直接使用
                                mMarkLatLngMap = new LatLng(dialog_lat_double, dialog_lng_double);
                            } else {
                                // GPS(WGS-84) 坐标，转换为 GCJ-02 后展示
                                double[] gcjLonLat = MapUtils.wgs2gcj02(dialog_lng_double, dialog_lat_double);
                                mMarkLatLngMap = new LatLng(gcjLonLat[1], gcjLonLat[0]);
                            }
                            mMarkName = "手动输入的坐标";

                            markMap();

                            CameraUpdate cameraUpdate = CameraUpdateFactory.changeLatLng(mMarkLatLngMap);
                            mAMap.moveCamera(cameraUpdate);

                            dialog.dismiss();
                        }
                    }
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap != null) {
            if (mUserMarker != null) {
                mUserMarker.remove();
            }
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
            mUserMarker = mAMap.addMarker(ooA);
        }
    }

    private void resetMap() {
        if (mUserMarker != null) {
            mUserMarker.remove();
            mUserMarker = null;
        }
        // 模拟标记（mMockMarker）保留：模拟仍在运行
        mMarkLatLngMap = null;

        // 定位蓝点由 LocationSource 回调自动刷新，这里只需移动视野
        // 未完成首次定位时当前坐标为 (0,0)，不做移动，避免视野飞到赤道几内亚湾
        if (mCurrentLat != 0.0 || mCurrentLon != 0.0) {
            CameraUpdate update = CameraUpdateFactory.newCameraPosition(new CameraPosition(new LatLng(mCurrentLat, mCurrentLon), 18.0f, 0, 0));
            mAMap.animateCamera(update);
        } else {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_locating));
        }
    }

    // 在地图上显示位置（参数坐标系：GCJ-02，来源为历史记录 CUSTOM 列）
    public static boolean showLocation(String name, String longitude, String latitude) {
        boolean ret = true;

        try {
            if (!longitude.isEmpty() && !latitude.isEmpty()) {
                mMarkName = name;
                mMarkLatLngMap = new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude));
                MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
                if (mUserMarker != null) {
                    mUserMarker.remove();
                }
                mUserMarker = mAMap.addMarker(ooA);
                CameraUpdate cameraUpdate = CameraUpdateFactory.changeLatLng(mMarkLatLngMap);
                mAMap.moveCamera(cameraUpdate);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    /** 模拟位置回调（ServiceGo 子线程）：切主线程更新状态条与模拟标记，坐标 WGS84 -> GCJ02 显示 */
    private final ServiceGo.OnMockStatusListener mMockStatusListener = (lng, lat, alt) -> runOnUiThread(() ->
            onMockPositionUpdate(lng, lat));

    private void onMockPositionUpdate(double lng, double lat) {
        if (!ServiceGo.sRunning) {
            return;
        }

        double[] gcj = MapUtils.gcj2wgs(lng, lat);
        LatLng pos = new LatLng(gcj[1], gcj[0]);
        if (mMockMarker == null) {
            mMockMarker = mAMap.addMarker(new MarkerOptions().position(pos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .title(getResources().getString(R.string.mock_marker_title)));
        } else {
            mMockMarker.setPosition(pos);
        }

        mStatusChip.setText(getResources().getString(R.string.status_mocking,
                String.format("%.5f, %.5f", lng, lat)));
        mStatusChip.setVisibility(View.VISIBLE);
    }

    private void startGoLocation() {
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);    // 绑定服务和活动，之后活动就可以去调服务的方法了
        double[] latLng = MapUtils.gcj2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
        serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
        double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        startForegroundService(serviceGoIntent);
        XLog.d("startForegroundService: ServiceGo");

        ServiceGo.setStatusListener(mMockStatusListener);
        mStatusChip.setVisibility(View.VISIBLE);

        isMockServStart = true;
    }

    private void stopGoLocation() {
        unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
        ServiceGo.setStatusListener(null);

        if (mMockMarker != null) {
            mMockMarker.remove();
            mMockMarker = null;
        }
        mStatusChip.setVisibility(View.GONE);
    }

    private void doGoLocation(View v) {
        // 前置条件自检：未通过则弹出检查清单（未满足项可点击直达对应设置页）
        List<PreconditionChecker.Item> unmet = PreconditionChecker.checkUnmet(this);
        if (!unmet.isEmpty()) {
            showPreconditionDialog(unmet);
            return;
        }

        if (isMockServStart) {
            if (mMarkLatLngMap == null) {
                stopGoLocation();
                Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                mButtonStart.setImageResource(R.drawable.ic_position);
            } else {
                double[] latLng = MapUtils.gcj2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
                mServiceBinder.setPosition(latLng[0], latLng[1], alt);
                Snackbar.make(v, "已传送到新位置", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                mAMap.clear();
                mMarkLatLngMap = null;
                onMockPositionUpdate(ServiceGo.sCurLng, ServiceGo.sCurLat);

                if (GoUtils.isWifiEnabled(MainActivity.this)) {
                    GoUtils.showDisableWifiDialog(MainActivity.this);
                }
            }
        } else {
            if (mMarkLatLngMap == null) {
                Snackbar.make(v, "请先点击地图位置或者搜索位置", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            } else {
                startGoLocation();
                mButtonStart.setImageResource(R.drawable.ic_fly);
                Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                mAMap.clear();
                mMarkLatLngMap = null;
                onMockPositionUpdate(ServiceGo.sCurLng, ServiceGo.sCurLat);

                if (GoUtils.isWifiEnabled(MainActivity.this)) {
                    GoUtils.showDisableWifiDialog(MainActivity.this);
                }
            }
        }
    }

    /** 启动前置条件清单：未满足项标红，点击直达对应设置页；支持"重新检测" */
    private void showPreconditionDialog(List<PreconditionChecker.Item> unmet) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        container.setPadding(pad, pad / 2, pad, 0);

        for (PreconditionChecker.Item item : unmet) {
            TextView row = new TextView(this);
            row.setText("✗  " + item.name + "（点击前往设置）");
            row.setTextColor(0xFFD32F2F);
            row.setTextSize(14);
            row.setPadding(0, pad / 2, 0, pad / 2);
            if (item.fixIntent != null) {
                row.setOnClickListener(v -> {
                    try {
                        startActivity(item.fixIntent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_open_settings));
                    }
                });
            }
            container.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.precheck_title))
                .setView(container)
                .setNeutralButton(R.string.precheck_recheck, (d, w) ->
                        doGoLocation(findViewById(R.id.faBtnStart)))
                .setNegativeButton(R.string.precheck_close, null)
                .show();
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        //参数坐标系：GCJ-02
        double[] latLng = MapUtils.gcj2wgs(lng, lat);
        final String wgsLng = String.valueOf(latLng[0]);
        final String wgsLat = String.valueOf(latLng[1]);

        // 高德逆地理编码（搜索 SDK 使用地图同款 Key，无需额外 Web 服务 Key）
        GeocodeSearch geocodeSearch;
        try {
            geocodeSearch = new GeocodeSearch(this);
        } catch (Exception e) {
            XLog.e("ERROR: new GeocodeSearch " + e.getMessage());
            insertLocationHistory(mMarkName == null ? getResources().getString(R.string.history_location_default_name) : mMarkName, wgsLng, wgsLat, lng, lat);
            return;
        }
        geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @Override
            public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
                String location;
                if (rCode == 1000 && regeocodeResult != null && regeocodeResult.getRegeocodeAddress() != null) {
                    location = regeocodeResult.getRegeocodeAddress().getFormatAddress();
                } else {
                    location = mMarkName == null ? getResources().getString(R.string.history_location_default_name) : mMarkName;
                }
                insertLocationHistory(location, wgsLng, wgsLat, lng, lat);
            }

            @Override
            public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult geocodeResult, int i) {

            }
        });
        geocodeSearch.getFromLocationAsyn(new RegeocodeQuery(new LatLonPoint(lat, lng), 200, GeocodeSearch.AMAP));
    }

    // 位置历史插表
    private void insertLocationHistory(String location, String wgsLng, String wgsLat, double lng, double lat) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, location);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, wgsLng);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, wgsLat);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));

        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            CameraUpdate cameraUpdate = CameraUpdateFactory.changeLatLng(mMarkLatLngMap);
            mAMap.moveCamera(cameraUpdate);

            markMap();

            double[] latLng = MapUtils.gcj2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                // mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                CameraUpdate cameraUpdate = CameraUpdateFactory.changeLatLng(mMarkLatLngMap);
                mAMap.moveCamera(cameraUpdate);

                markMap();

                double[] latLng = MapUtils.gcj2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            //删除成功
                            //展示搜索历史
                            List<Map<String, Object>> data = getSearchHistory();

                            if (!data.isEmpty()) {
                                SimpleAdapter simAdapt = new SimpleAdapter(
                                        MainActivity.this,
                                        data,
                                        R.layout.search_item,
                                        new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                                                DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                                DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                                DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM}, // 与下面数组元素要一一对应
                                        new int[] {R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude});
                                mSearchHistoryList.setAdapter(simAdapt);
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
    }

    // 请求搜索建议（高德输入提示），结果回调后刷新候选列表
    private void requestSearchSuggestion(String keyword) {
        try {
            InputtipsQuery query = new InputtipsQuery(keyword, mCurrentCity == null ? "" : mCurrentCity);
            query.setCityLimit(false);      // 不严格限定当前城市
            mInputtips = new Inputtips(getApplicationContext(), query);
            mInputtips.setInputtipsListener((tips, rCode) -> {
                // rCode 1000 表示请求成功，参见 AMapException.CODE_AMAP_SUCCESS
                if (rCode == 1000 && tips != null && !tips.isEmpty()) {
                    List<Map<String, Object>> data = getMapList(tips);

                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this,
                            data,
                            R.layout.search_poi_item,
                            new String[] {POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE}, // 与下面数组元素要一一对应
                            new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                    mSearchList.setAdapter(simAdapt);
                    // mSearchList.setVisibility(View.VISIBLE);
                    mSearchLayout.setVisibility(View.VISIBLE);
                } else if (rCode == 1000) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_search_null));
                } else {
                    XLog.e("搜索失败 rCode=" + rCode);
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                }
            });
            mInputtips.requestInputtipsAsyn();
        } catch (Exception e) {
            GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
            XLog.d(getResources().getString(R.string.app_error_search));
        }
    }

    @NonNull
    private static List<Map<String, Object>> getMapList(List<Tip> tips) {
        List<Map<String, Object>> data = new ArrayList<>();

        for (Tip tip : tips) {
            if (tip.getPoint() == null) {
                continue;
            }

            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(POI_NAME, tip.getName());
            poiItem.put(POI_ADDRESS, tip.getDistrict());
            poiItem.put(POI_LONGITUDE, "" + tip.getPoint().getLongitude());
            poiItem.put(POI_LATITUDE, "" + tip.getPoint().getLatitude());
            data.add(poiItem);
        }
        return data;
    }

    /*============================== 更新 相关 ==============================*/
    private void initUpdateVersion() {
        mDownloadManager =(DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE);

        // 用于监听下载完成后，转到安装界面
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installNewVersion();
            }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean result) {
        String mapApiUrl = "https://api.github.com/repos/fejxc/GoGoGo/releases/latest";

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.i("更新检测失败");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String resp = responseBody.string();
                    // 注意，该请求在子线程，不能直接操作界面
                    runOnUiThread(() -> {
                        try {
                            JSONObject getRetJson = new JSONObject(resp);
                            String curVersion = GoUtils.getVersionName(MainActivity.this);

                            if (curVersion != null
                                    && (!getRetJson.getString("name").contains(curVersion)
                                    || !getRetJson.getString("tag_name").contains(curVersion))) {
                                final android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.show();
                                alertDialog.setCancelable(false);
                                Window window = alertDialog.getWindow();
                                if (window != null) {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);      // 防止出现闪屏
                                    window.setContentView(R.layout.update);
                                    window.setGravity(Gravity.CENTER);
                                    window.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);

                                    TextView updateTitle = window.findViewById(R.id.update_title);
                                    updateTitle.setText(getRetJson.getString("name"));
                                    TextView updateTime = window.findViewById(R.id.update_time);
                                    updateTime.setText(getRetJson.getString("created_at"));
                                    TextView updateCommit = window.findViewById(R.id.update_commit);
                                    updateCommit.setText(getRetJson.getString("target_commitish"));

                                    TextView updateContent = window.findViewById(R.id.update_content);
                                    final Markwon markwon = Markwon.create(MainActivity.this);
                                    markwon.setMarkdown(updateContent, getRetJson.getString("body"));

                                    Button updateCancel = window.findViewById(R.id.update_ignore);
                                    updateCancel.setOnClickListener(v -> alertDialog.cancel());

                                    /* 这里用来保存下载地址 */
                                    JSONArray jsonArray = new JSONArray(getRetJson.getString("assets"));
                                    JSONObject jsonObject = jsonArray.getJSONObject(0);
                                    String download_url = jsonObject.getString("browser_download_url");
                                    mUpdateFilename = jsonObject.getString("name");

                                    Button updateAgree = window.findViewById(R.id.update_agree);
                                    updateAgree.setOnClickListener(v -> {
                                        alertDialog.cancel();
                                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_downloading));
                                        downloadNewVersion(download_url);
                                    });
                                }
                            } else {
                                if (result) {
                                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.update_last));
                                }
                            }
                        } catch (JSONException e) {
                            XLog.e("ERROR: resolve json");
                        }
                    });
                }
            }
        });
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setAllowedOverRoaming(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(GoUtils.getAppName(this));
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");

        // DownloadManager不会覆盖已有的同名文件，需要自己来删除已存在的文件
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists()) {
            if(!file.delete()) {
                return;
            }
        }
        request.setDestinationUri(Uri.fromFile(file));

        mDownloadId = mDownloadManager.enqueue(request);
    }

    private void installNewVersion() {
        Intent install = new Intent(Intent.ACTION_VIEW);
        Uri downloadFileUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (downloadFileUri != null) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // 在Broadcast中启动活动需要添加Intent.FLAG_ACTIVITY_NEW_TASK
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    //添加这一句表示对目标应用临时授权该Uri所代表的文件
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(MainActivity.this, file), "application/vnd.android.package-archive");
            startActivity(install);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            intent.addCategory("android.intent.category.DEFAULT");
            startActivity(intent);
        }
    }
}
