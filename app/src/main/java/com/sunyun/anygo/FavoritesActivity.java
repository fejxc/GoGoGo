package com.sunyun.anygo;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.elvishew.xlog.XLog;
import com.sunyun.anygo.database.DataBaseFavorite;
import com.sunyun.anygo.utils.GoUtils;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                R.layout.history_item,
                new String[]{KEY_ID, KEY_NAME, KEY_TIME, KEY_COORD, KEY_ADDRESS},
                new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
        mListView.setAdapter(adapter);
    }

    private void deleteFavorite(long id, int position) {
        DataBaseFavorite.deleteFavorite(mFavDB, id);
        mRecords.remove(position);
        showList();
    }
}
