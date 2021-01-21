package com.spgrvl.packagetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;


public class DatabaseHelper extends SQLiteOpenHelper {

    public static final int VERSION = 3;

    public static final String DATABASE_NAME = "tracking.db";
    public static final String INDEX_TABLE = "Tracking_Index";
    public static final String TRACKING_COL = "Tracking";
    public static final String UPDATED_COL = "Updated";
    public static final String LAST_UPDATE_COL = "LastUpdate";
    public static final String CUSTOM_NAME_COL = "CustomName";
    public static final String UNREAD_COL = "Unread";
    public static final String STATUS_COL = "Status";
    public static final String PLACE_COL = "Place";
    public static final String DATETIME_COL = "Datetime";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME,null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE IF NOT EXISTS " + INDEX_TABLE + "(" + TRACKING_COL + " TEXT PRIMARY KEY, " + UPDATED_COL + " TEXT, " + LAST_UPDATE_COL + " TEXT, " + CUSTOM_NAME_COL + " TEXT, " + UNREAD_COL + " BOOLEAN NOT NULL CHECK ( " + UNREAD_COL + " IN (0,1)))";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // grab cursor for all data
        String queryString = "SELECT * FROM " + INDEX_TABLE;
        Cursor cursor = db.rawQuery(queryString, null);

        // check if CUSTOM_NAME_COL exists and if not add it
        int customNameColumnIndex = cursor.getColumnIndex(CUSTOM_NAME_COL);
        if (customNameColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + CUSTOM_NAME_COL + " TEXT");
        }

        // check if UNREAD_COL exists and if not add it
        int unreadColumnIndex = cursor.getColumnIndex(UNREAD_COL);
        if (unreadColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + UNREAD_COL + " BOOLEAN NOT NULL DEFAULT 0 CHECK ( " + UNREAD_COL + " IN (0,1))");
        }

        // close cursor when done.
        cursor.close();
    }

    public boolean addNewTracking(String Tracking, String CustomName) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Create table for details of new Tracking number
        try {
            String createTrackingTable = "CREATE TABLE IF NOT EXISTS '" + Tracking + "'(" + STATUS_COL + " TEXT, " + PLACE_COL + " TEXT, " + DATETIME_COL + " TEXT )";
            db.execSQL(createTrackingTable);

            // Adds one row to Tracking Index table
            ContentValues contentValues = new ContentValues();
            contentValues.put(TRACKING_COL, Tracking);
            contentValues.put(UPDATED_COL, "Never");
            contentValues.put(LAST_UPDATE_COL, "Status: None");
            contentValues.put(CUSTOM_NAME_COL, CustomName);
            contentValues.put(UNREAD_COL, false);
            long result = db.insert(INDEX_TABLE, null, contentValues);

            db.close();

            if(result == -1) {
                return false;
            }
            else {
                return true;
            }
        }
        catch(SQLException e) {
            Log.e("SQLException", e.toString());
            db.close();
            return false;
        }
    }

    public void updateTrackingDetails(String tracking, ArrayList<TrackingDetailsModel> detailsList) {
        // Delete Tracking table contents and insert the new list of entries

        SQLiteDatabase db = this.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + tracking);
            for (int i=0; i<detailsList.size(); i++) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(STATUS_COL, detailsList.get(i).getStatus());
                contentValues.put(PLACE_COL, detailsList.get(i).getPlace());
                contentValues.put(DATETIME_COL, detailsList.get(i).getDatetime());
                db.insert(tracking, null, contentValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        db.close();
    }

    public void updateTrackingIndex(String tracking, String updated, String lastUpdate) {
        // Update index table entry

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(UPDATED_COL, updated);
        if (lastUpdate != null) {
            contentValues.put(LAST_UPDATE_COL, lastUpdate);
            contentValues.put(UNREAD_COL, true);
        }

        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[] {tracking});

        db.close();
    }

    public List<TrackingIndexModel> getAllTracking() {
        List<TrackingIndexModel> returnList = new ArrayList<>();

        // get data from DB

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT * FROM " + INDEX_TABLE;

        Cursor cursor = db.rawQuery(queryString, null);

        if (cursor.moveToFirst()) {
            // loop through the cursor and create new tracking objects. Add them in the return list.
            do {
                String tracking = cursor.getString(0);
                String updated = cursor.getString(1);
                String lastUpdate = cursor.getString(2);
                String customName = cursor.getString(3);

                TrackingIndexModel newTracking = new TrackingIndexModel(tracking, updated, lastUpdate, customName);
                returnList.add(newTracking);

            } while (cursor.moveToNext());
        }
        else {
            // failure. do not add anything to the list.
        }

        // close cursor and db when done.
        cursor.close();
        db.close();
        return returnList;
    }

    public ArrayList<String> getTrackingNumbers() {
        ArrayList<String> tracking_numbers = new ArrayList<>();

        // get data from DB
        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT " + TRACKING_COL + " FROM " + INDEX_TABLE;

        Cursor cursor = db.rawQuery(queryString, null);

        if (cursor.moveToFirst()) {
            // loop through the cursor and create new tracking objects. Add them in the return list.
            do {
                String tracking = cursor.getString(0);
                tracking_numbers.add(tracking);
            } while (cursor.moveToNext());
        }
        else {
            // failure. do not add anything to the list.
        }

        // close cursor and db when done.
        cursor.close();
        db.close();
        return tracking_numbers;
    }

    public List<TrackingDetailsModel> getTrackingDetails(String tracking) {
        List<TrackingDetailsModel> returnDetailsList = new ArrayList<>();

        // get data from DB

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT * FROM '" + tracking + "'";

        Cursor cursor = db.rawQuery(queryString, null);

        if (cursor.moveToFirst()) {
            // loop through the cursor and create new tracking objects. Add them in the return list.
            do {
                String status = cursor.getString(0);
                String place = cursor.getString(1);
                String datetime = cursor.getString(2);

                TrackingDetailsModel newDetails = new TrackingDetailsModel(status, place, datetime);
                returnDetailsList.add(newDetails);

            } while (cursor.moveToNext());
        }

        // close cursor and db when done.
        cursor.close();
        db.close();
        return returnDetailsList;
    }

    public int getTrackingDetailsCount(String tracking) {
        // get data from DB

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT COUNT(*) FROM '" + tracking + "'";

        Cursor cursor = db.rawQuery(queryString, null);

        int count = -1;
        if (cursor.moveToFirst()) {
            count = parseInt(cursor.getString(0));
        }

        // close cursor and db when done.
        cursor.close();
        db.close();

        return count;
    }

    public boolean getUnreadStatus(String tracking) {
        // get data from DB

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT " + UNREAD_COL + " FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = '" + tracking + "'";

        Cursor cursor = db.rawQuery(queryString, null);

        int isUnread = -1;
        if (cursor.moveToFirst()) {
            isUnread = parseInt(cursor.getString(0));
        }

        // close cursor and db when done.
        cursor.close();
        db.close();

        if (isUnread == 1) {
            return true;
        } else {
            return false;
        }
    }

    public void setUnreadStatus(String tracking, Boolean isUnread) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(UNREAD_COL, isUnread);

        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[] {tracking});

        db.close();
    }

    public void deleteTracking(String tracking) {
        // Delete Tracking table and Index table entry. Return true if done, else false
        SQLiteDatabase db = this.getWritableDatabase();

        // Delete tracking table
        db.execSQL("DROP TABLE IF EXISTS '" + tracking + "'");

        db.execSQL("DELETE FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = '" + tracking + "'");

        // close db when done.
        db.close();
    }
}
