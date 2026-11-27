package com.sunyun.anygo;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.elvishew.xlog.XLog;
import com.sunyun.anygo.database.DataBaseRoute;
import com.sunyun.anygo.service.ServiceGo;
import com.sunyun.anygo.utils.GpxUtils;
import com.sunyun.anygo.utils.GoUtils;
import com.sunyun.anygo.utils.MapUtils;
import com.sunyun.anygo.utils.PreconditionChecker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 路线编辑器：地图点选航点 → 保存/载入/GPX 导入导出 → 启动沿路线连续移动模拟。
 * 航点一律 GCJ-02 存储（与收藏/历史一致），启动模拟时转 WGS-84 交给 ServiceGo。
 */
public class RouteEditorActivity extends BaseActivity {
    private static final int REQ_IMPORT_GPX = 1001;

    private MapView mMapView;
    private AMap mAMap;
    private TextView mCountText;

    private final ArrayList<double[]> mPoints = new ArrayList<>();     // GCJ-02 {lng, lat}
    private final ArrayList<Marker> mWayMarkers = new ArrayList<>();
    private Polyline mPolyline;

    private SQLiteDatabase mRouteDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary, this.getTheme()));

        setContentView(R.layout.activity_route);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mCountText = findViewById(R.id.route_count);

        mMapView = findViewById(R.id.route_map);
        mMapView.onCreate(savedInstanceState);
        mAMap = mMapView.getMap();
        mAMap.setOnMapClickListener(this::addPoint);

        mRouteDB = new DataBaseRoute(getApplicationContext()).getWritableDatabase();

        findViewById(R.id.btn_route_undo).setOnClickListener(v -> undoPoint());
        findViewById(R.id.btn_route_clear).setOnClickListener(v -> clearPoints());
        findViewById(R.id.btn_route_load).setOnClickListener(v -> showLoadDialog());
        findViewById(R.id.btn_route_save).setOnClickListener(v -> saveRoute());
        findViewById(R.id.btn_route_import).setOnClickListener(v -> pickGpx());
        findViewById(R.id.btn_route_export).setOnClickListener(v -> exportGpx());
        findViewById(R.id.btn_route_start).setOnClickListener(v -> startRoute());
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mRouteDB.close();
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* ============================== 航点编辑 ============================== */

    private void addPoint(LatLng point) {
        mPoints.add(new double[]{point.longitude, point.latitude});
        redrawOverlays();
    }

    private void undoPoint() {
        if (!mPoints.isEmpty()) {
            mPoints.remove(mPoints.size() - 1);
            redrawOverlays();
        }
    }

    private void clearPoints() {
        mPoints.clear();
        redrawOverlays();
    }

    private void redrawOverlays() {
        for (Marker m : mWayMarkers) {
            m.remove();
        }
        mWayMarkers.clear();

        if (mPolyline != null) {
            mPolyline.remove();
            mPolyline = null;
        }

        if (!mPoints.isEmpty()) {
            PolylineOptions po = new PolylineOptions();
            for (double[] pt : mPoints) {
                LatLng ll = new LatLng(pt[1], pt[0]);
                mWayMarkers.add(mAMap.addMarker(new MarkerOptions().position(ll)
                        .icon(com.amap.api.maps.model.BitmapDescriptorFactory
                                .defaultMarker(com.amap.api.maps.model.BitmapDescriptorFactory.HUE_BLUE))));
                po.add(ll);
            }
            if (mPoints.size() >= 2) {
                mPolyline = mAMap.addPolyline(po.width(6f).color(0xFF2196F3));
            }
        }

        mCountText.setText(getResources().getString(R.string.route_points_fmt, mPoints.size()));
    }

    /* ============================== 保存 / 载入 ============================== */

    private void saveRoute() {
        if (mPoints.size() < 2) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_need_points));
            return;
        }

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.route_name_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.route_save)
                .setView(input)
                .setPositiveButton(R.string.route_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = String.format("路线-%tF %<tT", System.currentTimeMillis());
                    }

                    ContentValues cv = new ContentValues();
                    cv.put(DataBaseRoute.DB_COLUMN_NAME, name);
                    cv.put(DataBaseRoute.DB_COLUMN_POINTS, toJson());
                    cv.put(DataBaseRoute.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                    DataBaseRoute.saveRoute(mRouteDB, cv);
                    GoUtils.DisplayToast(this, getResources().getString(R.string.route_saved));
                })
                .setNegativeButton(R.string.precheck_close, null)
                .show();
    }

    private void showLoadDialog() {
        List<String> names = new ArrayList<>();
        List<double[][]> pointSets = new ArrayList<>();
        List<long[]> ids = new ArrayList<>();

        try (Cursor c = mRouteDB.query(DataBaseRoute.TABLE_NAME, null, null, null, null, null,
                DataBaseRoute.DB_COLUMN_TIMESTAMP + " DESC")) {
            while (c.moveToNext()) {
                String name = c.getString(1);
                double[][] pts = fromJson(c.getString(2));
                if (pts.length >= 2) {
                    names.add(name);
                    pointSets.add(pts);
                    ids.add(new long[]{c.getLong(0)});
                }
            }
        } catch (Exception e) {
            XLog.e("ROUTE: load list error");
        }

        if (names.isEmpty()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_none_saved));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.route_load)
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    mPoints.clear();
                    for (double[] pt : pointSets.get(w)) {
                        mPoints.add(pt);
                    }
                    redrawOverlays();
                    mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(mPoints.get(0)[1], mPoints.get(0)[0]), 14f));
                    GoUtils.DisplayToast(this, getResources().getString(R.string.route_loaded, names.get(w)));
                })
                .setNeutralButton(R.string.route_manage_delete, (d, w) -> showDeleteDialog(names, ids))
                .setNegativeButton(R.string.precheck_close, null)
                .show();
    }

    private void showDeleteDialog(List<String> names, List<long[]> ids) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.route_manage_delete)
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    DataBaseRoute.deleteRoute(mRouteDB, ids.get(w)[0]);
                    GoUtils.DisplayToast(this, getResources().getString(R.string.fav_deleted));
                })
                .setNegativeButton(R.string.precheck_close, null)
                .show();
    }

    /* ============================== GPX 导入导出 ============================== */

    private void pickGpx() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_GPX);
        } catch (Exception e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_import_fail));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_GPX && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importGpx(data.getData());
        }
    }

    private void importGpx(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            GpxUtils.GpxTrack track = GpxUtils.parse(in);
            if (track.points.isEmpty()) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.route_import_none));
                return;
            }

            mPoints.clear();
            for (double[] wgs : track.points) {
                double[] gcj = MapUtils.wgs2gcj02(wgs[0], wgs[1]);
                mPoints.add(new double[]{gcj[0], gcj[1]});
            }
            redrawOverlays();
            mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(mPoints.get(0)[1], mPoints.get(0)[0]), 14f));
        } catch (Exception e) {
            XLog.e("ROUTE: gpx import error " + e.getMessage());
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_import_fail));
        }
    }

    private void exportGpx() {
        if (mPoints.size() < 2) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_need_points));
            return;
        }

        try {
            List<double[]> wgsPoints = new ArrayList<>();
            for (double[] gcj : mPoints) {
                wgsPoints.add(MapUtils.gcj2wgs(gcj[0], gcj[1]));
            }

            String name = "AnyGo路线-" + System.currentTimeMillis();
            File dir = getExternalFilesDir("Export");
            if (dir == null) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.route_export_fail));
                return;
            }
            File file = new File(dir, name + ".gpx");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(GpxUtils.build(name, wgsPoints).getBytes(StandardCharsets.UTF_8));
            }

            com.sunyun.anygo.utils.ShareUtils.shareFile(this, file, name);
        } catch (Exception e) {
            XLog.e("ROUTE: gpx export error " + e.getMessage());
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_export_fail));
        }
    }

    /* ============================== 启动路线模拟 ============================== */

    private void startRoute() {
        if (mPoints.size() < 2) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_need_points));
            return;
        }

        List<PreconditionChecker.Item> unmet = PreconditionChecker.checkUnmet(this);
        if (!unmet.isEmpty()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.route_unmet, unmet.get(0).name));
            return;
        }

        // 速度模式（米/秒，与摇杆档位一致）
        RadioGroup rg = findViewById(R.id.route_speed_group);
        double speed;
        int checked = rg.getCheckedRadioButtonId();
        if (checked == R.id.route_run) {
            speed = 3.6;
        } else if (checked == R.id.route_bike) {
            speed = 10.0;
        } else {
            speed = 1.2;
        }
        RadioButton checkedBtn = findViewById(checked == View.NO_ID ? R.id.route_walk : checked);
        checkedBtn.setChecked(true);

        // GCJ-02 -> WGS-84，扁平化 {lng,lat,...}
        ArrayList<Double> flat = new ArrayList<>();
        for (double[] gcj : mPoints) {
            double[] wgs = MapUtils.gcj2wgs(gcj[0], gcj[1]);
            flat.add(wgs[0]);
            flat.add(wgs[1]);
        }
        double[] pts = new double[flat.size()];
        for (int i = 0; i < flat.size(); i++) {
            pts[i] = flat.get(i);
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.putExtra(ServiceGo.EXTRA_ROUTE, true);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_POINTS, pts);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_SPEED, speed);
        startForegroundService(intent);
        bindService(new Intent(this, ServiceGo.class), new ServiceGoConnection(), BIND_AUTO_CREATE);

        Toast.makeText(this, R.string.route_started, Toast.LENGTH_LONG).show();
        finish();
    }

    /** 仅用于把服务拉起后立刻解绑（路线模式无 Binder 交互，状态恢复走 MainActivity.onStart） */
    private class ServiceGoConnection implements android.content.ServiceConnection {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
        }
    }

    /* ============================== JSON 序列化 ============================== */

    private String toJson() {
        try {
            JSONArray points = new JSONArray();
            for (double[] pt : mPoints) {
                points.put(new JSONArray().put(pt[0]).put(pt[1]));
            }
            return new JSONObject().put("points", points).toString();
        } catch (Exception e) {
            return "{\"points\":[]}";
        }
    }

    private double[][] fromJson(String json) {
        ArrayList<double[]> list = new ArrayList<>();
        try {
            JSONArray points = new JSONObject(json).getJSONArray("points");
            for (int i = 0; i < points.length(); i++) {
                JSONArray pt = points.getJSONArray(i);
                list.add(new double[]{pt.getDouble(0), pt.getDouble(1)});
            }
        } catch (Exception e) {
            XLog.e("ROUTE: json parse error");
        }
        return list.toArray(new double[0][]);
    }
}
