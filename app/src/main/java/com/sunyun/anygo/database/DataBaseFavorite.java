package com.sunyun.anygo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

/**
 * 位置收藏：GCJ-02 坐标按名称唯一存储
 */
public class DataBaseFavorite extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "Favorites";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_NAME = "DB_COLUMN_NAME";
    public static final String DB_COLUMN_ADDRESS = "DB_COLUMN_ADDRESS";
    public static final String DB_COLUMN_LONGITUDE = "DB_COLUMN_LONGITUDE";
    public static final String DB_COLUMN_LATITUDE = "DB_COLUMN_LATITUDE";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";

    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "Favorites.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_NAME TEXT NOT NULL, " +
            "DB_COLUMN_ADDRESS TEXT, DB_COLUMN_LONGITUDE TEXT NOT NULL, DB_COLUMN_LATITUDE TEXT NOT NULL, " +
            "DB_COLUMN_TIMESTAMP BIGINT NOT NULL)";

    public DataBaseFavorite(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }

    /** 同名收藏覆盖保存 */
    public static void saveFavorite(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            String name = contentValues.getAsString(DB_COLUMN_NAME);
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_NAME + " = ?", new String[]{name});
            sqLiteDatabase.insert(TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            XLog.e("DATABASE: favorite insert error");
        }
    }

    public static void deleteFavorite(SQLiteDatabase sqLiteDatabase, long id) {
        try {
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            XLog.e("DATABASE: favorite delete error");
        }
    }
}
