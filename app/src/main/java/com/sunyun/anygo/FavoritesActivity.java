package com.sunyun.anygo;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.database.sqlite.SQLiteDatabase;
import android.view.Menu;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.elvishew.xlog.XLog;
import com.sunyun.anygo.database.DataBaseFavorite;
import com.sunyun.anygo.utils.GoUtils;
import com.sunyun.anygo.utils.MapUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 位置收藏列表：点击选中位置（传回主界面地图），长按删除
 */
public class FavoritesActivity extends BaseActivity {
    /* SimpleAdapter 展示键 */
    public static final String KEY_ID = "fav_id";
    public static final String KEY_NAME = "fav_name";
    public static final String KEY_ADDRESS = "fav_address";
    public static final String KEY_TIME = "fav_time";
    public static final String KEY_COORD = "fav_coord";
    /* 原始坐标（不参与展示，点击时取用） */
    private static final String KEY_LNG_RAW = "fav_lng_raw";
    private static final String KEY_LAT_RAW = "fav_lat_raw";

    private static final int REQ_IMPORT_FAV = 2001;

    private SQLiteDatabase mFavDB;
    private ListView mListView;
    private TextView mNoRecordText;
    private final List<Map<String, Object>> mRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary, this.getTheme()));

        setContentView(R.layout.activity_history);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mNoRecordText = findViewById(R.id.record_no_textview);
        mNoRecordText.setText(R.string.fav_none);
        mListView = findViewById(R.id.record_list_view);

        initDataBase();
        initListView();

        fetchAll();
        showList();
    }

    @Override
    protected void onResume() {
        super.onResume();

        fetchAll();
        showList();
    }

    @Override
    protected void onDestroy() {
        mFavDB.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_favorites, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            exportFavorites();
            return true;
        } else if (item.getItemId() == R.id.action_import) {
            pickFavoritesFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* ============================== 备份：导出 / 导入 ============================== */

    private void exportFavorites() {
        if (mRecords.isEmpty()) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.fav_none));
            return;
        }

        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Map<String, Object> rec : mRecords) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", String.valueOf(rec.get(KEY_NAME)));
                obj.put("address", String.valueOf(rec.get(KEY_ADDRESS)));
                obj.put("lng", rec.get(KEY_LNG_RAW));
                obj.put("lat", rec.get(KEY_LAT_RAW));
                arr.put(obj);
            }

            File dir = getExternalFilesDir("Export");
            if (dir == null) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.fav_export_fail));
                return;
            }
            File file = new File(dir, "AnyGo收藏-" + System.currentTimeMillis() + ".json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            com.sunyun.anygo.utils.ShareUtils.shareFile(this, file, "AnyGo收藏备份");
        } catch (Exception e) {
            XLog.e("FAVORITES: export error " + e.getMessage());
            GoUtils.DisplayToast(this, getResources().getString(R.string.fav_export_fail));
        }
    }

    private void pickFavoritesFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_FAV);
        } catch (Exception e) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.fav_import_fail));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_FAV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importFavorites(data.getData());
        }
    }

    private void importFavorites(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                sb.append(new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            }

            org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
            int imported = 0;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put(DataBaseFavorite.DB_COLUMN_NAME, obj.getString("name"));
                cv.put(DataBaseFavorite.DB_COLUMN_ADDRESS, obj.optString("address", ""));
                cv.put(DataBaseFavorite.DB_COLUMN_LONGITUDE, obj.getString("lng"));
                cv.put(DataBaseFavorite.DB_COLUMN_LATITUDE, obj.getString("lat"));
                cv.put(DataBaseFavorite.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                DataBaseFavorite.saveFavorite(mFavDB, cv);
                imported++;
            }

            fetchAll();
            showList();
            GoUtils.DisplayToast(this, getResources().getString(R.string.fav_imported, imported));
        } catch (Exception e) {
            XLog.e("FAVORITES: import error " + e.getMessage());
            GoUtils.DisplayToast(this, getResources().getString(R.string.fav_import_fail));
        }
    }

    private void initDataBase() {
        DataBaseFavorite dbHelper = new DataBaseFavorite(getApplicationContext());
        mFavDB = dbHelper.getWritableDatabase();
    }

    private void fetchAll() {
        mRecords.clear();

        try {
            Cursor cursor = mFavDB.query(DataBaseFavorite.TABLE_NAME, null,
                    DataBaseFavorite.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null, DataBaseFavorite.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String address = cursor.getString(2);
                double lng = Double.parseDouble(cursor.getString(3));
                double lat = Double.parseDouble(cursor.getString(4));
                long timestamp = cursor.getLong(5);

                item.put(KEY_ID, String.valueOf(id));
                item.put(KEY_NAME, name);
                item.put(KEY_ADDRESS, address == null ? "" : address);
                item.put(KEY_TIME, GoUtils.timeStamp2Date(Long.toString(timestamp)));
                item.put(KEY_COORD, String.format("[%.6f, %.6f]", lng, lat));
                item.put(KEY_LNG_RAW, cursor.getString(3));
                item.put(KEY_LAT_RAW, cursor.getString(4));
                mRecords.add(item);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("FAVORITES: ERROR - fetchAll");
        }
    }

    private void showList() {
        if (mRecords.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mNoRecordText.setVisibility(View.VISIBLE);
            return;
        }

        mNoRecordText.setVisibility(View.GONE);
        mListView.setVisibility(View.VISIBLE);

        SimpleAdapter adapter = new SimpleAdapter(
                this,
                mRecords,
                R.layout.favorite_item,
                new String[]{KEY_ID, KEY_NAME, KEY_ADDRESS, KEY_TIME, KEY_COORD},
                new int[]{R.id.LocationID, R.id.LocationText, R.id.BDLatLngText, R.id.TimeText, R.id.WGSLatLngText});
        mListView.setAdapter(adapter);
    }

    private void initListView() {
        // 点击：回显到主界面地图
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, Object> item = mRecords.get(position);
            String name = String.valueOf(item.get(KEY_NAME));
            String lng = String.valueOf(item.get(KEY_LNG_RAW));
            String lat = String.valueOf(item.get(KEY_LAT_RAW));
            MainActivity.showLocation(name, lng, lat);
            finish();
        });

        // 长按：删除确认
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Map<String, Object> item = mRecords.get(position);
            long favId = Long.parseLong(String.valueOf(item.get(KEY_ID)));
            String name = String.valueOf(item.get(KEY_NAME));

            new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage(R.string.fav_delete_confirm)
                    .setPositiveButton("确定", (d, w) -> {
                        DataBaseFavorite.deleteFavorite(mFavDB, favId);
                        fetchAll();
                        showList();
                        GoUtils.DisplayToast(this, getResources().getString(R.string.fav_deleted));
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }
}
