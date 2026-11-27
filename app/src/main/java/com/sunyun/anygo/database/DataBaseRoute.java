package com.sunyun.anygo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

/**
 * 路线存储：一条路线一行，航点序列以 JSON 文本存储（GCJ-02 坐标）。
 * JSON 结构：{"name":"...", "points":[[lng,lat],[lng,lat],...]}
 */
public class DataBaseRoute extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "Routes";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_NAME = "DB_COLUMN_NAME";
    public static final String DB_COLUMN_POINTS = "DB_COLUMN_POINTS";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";

    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "Routes.db";
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_NAME TEXT NOT NULL, " +
            "DB_COLUMN_POINTS TEXT NOT NULL, DB_COLUMN_TIMESTAMP BIGINT NOT NULL)";

    public DataBaseRoute(Context context) {
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

    /** 同名路线覆盖保存 */
    public static void saveRoute(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            String name = contentValues.getAsString(DB_COLUMN_NAME);
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_NAME + " = ?", new String[]{name});
            sqLiteDatabase.insert(TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            XLog.e("DATABASE: route insert error");
        }
    }

    public static void deleteRoute(SQLiteDatabase sqLiteDatabase, long id) {
        try {
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            XLog.e("DATABASE: route delete error");
        }
    }
}
