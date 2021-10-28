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

    public static final int VERSION = 6;

    public static final String DATABASE_NAME = "tracking.db";
    public static final String INDEX_TABLE = "Tracking_Index";
    public static final String TRACKING_COL = "Tracking";
    public static final String CREATED_COL = "Created";
    public static final String UPDATED_COL = "Updated";
    public static final String LAST_UPDATE_COL = "LastUpdate";
    public static final String CUSTOM_NAME_COL = "CustomName";
    public static final String UNREAD_COL = "Unread";
    public static final String CARRIER_COL = "Carrier";
    public static final String COMPLETED_COL = "Completed";
    public static final String STATUS_COL = "Status";
    public static final String PLACE_COL = "Place";
    public static final String DATETIME_COL = "Datetime";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE IF NOT EXISTS " + INDEX_TABLE + "(" + TRACKING_COL + " TEXT PRIMARY KEY, " + UPDATED_COL + " TEXT, " + LAST_UPDATE_COL + " TEXT, " + CUSTOM_NAME_COL + " TEXT, " + UNREAD_COL + " BOOLEAN NOT NULL, " + CARRIER_COL + " TEXT, " + COMPLETED_COL + " BOOLEAN NOT NULL, " + CREATED_COL + " TEXT, CHECK ( " + UNREAD_COL + " IN (0,1) AND " + COMPLETED_COL + " IN (0,1)))";
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

        // check if CARRIER_COL exists and if not add it
        int carrierColumnIndex = cursor.getColumnIndex(CARRIER_COL);
        if (carrierColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + CARRIER_COL + " TEXT");
        }

        // check if COMPLETED_COL exists and if not add it
        int completedColumnIndex = cursor.getColumnIndex(COMPLETED_COL);
        if (completedColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + COMPLETED_COL + " BOOLEAN NOT NULL DEFAULT 0 CHECK ( " + COMPLETED_COL + " IN (0,1))");
        }

        // check if CREATED_COL exists and if not add it
        int createdColumnIndex = cursor.getColumnIndex(CREATED_COL);
        if (createdColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + CREATED_COL + " TEXT DEFAULT ''");
        }

        // close cursor when done.
        cursor.close();
    }

    public boolean addNewTracking(String Tracking, String CustomName, Boolean Completed, String Created) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Create table for details of new Tracking number
        try {
            String createTrackingTable = "CREATE TABLE IF NOT EXISTS '" + Tracking + "'(" + STATUS_COL + " TEXT, " + PLACE_COL + " TEXT, " + DATETIME_COL + " TEXT )";
            db.execSQL(createTrackingTable);

            if (Created == null) {
                Created = String.valueOf(System.currentTimeMillis());
            }

            // Adds one row to Tracking Index table
            ContentValues contentValues = new ContentValues();
            contentValues.put(TRACKING_COL, Tracking);
            contentValues.put(CREATED_COL, Created);
            contentValues.put(UPDATED_COL, "Never");
            contentValues.put(LAST_UPDATE_COL, "Status: None");
            contentValues.put(CUSTOM_NAME_COL, CustomName);
            contentValues.put(UNREAD_COL, false);
            contentValues.put(COMPLETED_COL, Completed);
            long result = db.insert(INDEX_TABLE, null, contentValues);

            db.close();

            return result != -1;
        } catch (SQLException e) {
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
            db.execSQL("DELETE FROM '" + tracking + "'");
            for (int i = 0; i < detailsList.size(); i++) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(STATUS_COL, detailsList.get(i).getStatus());
                contentValues.put(PLACE_COL, detailsList.get(i).getPlace());
                contentValues.put(DATETIME_COL, detailsList.get(i).getDatetime());
                db.insert("'" + tracking + "'", null, contentValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        db.close();
    }

    public void updateTrackingIndex(String tracking, String updated, String lastUpdate, Boolean isUnread, String carrier) {
        // Update index table entry

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(UPDATED_COL, updated);
        contentValues.put(CARRIER_COL, carrier);
        if (lastUpdate != null) {
            contentValues.put(LAST_UPDATE_COL, lastUpdate);
        }
        if (isUnread) {
            contentValues.put(UNREAD_COL, true);
        }

        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});

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
                boolean completed = cursor.getString(6).equals("1");
                String created = cursor.getString(7);

                TrackingIndexModel newTracking = new TrackingIndexModel(tracking, created, updated, lastUpdate, customName, completed);
                returnList.add(newTracking);

            } while (cursor.moveToNext());
        }

        // close cursor and db when done.
        cursor.close();
        db.close();
        return returnList;
    }

    public ArrayList<String> getTrackingNumbers(Boolean getCompleted) {
        ArrayList<String> tracking_numbers = new ArrayList<>();

        // get data from DB
        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT * FROM " + INDEX_TABLE;

        Cursor cursor = db.rawQuery(queryString, null);

        if (cursor.moveToFirst()) {
            // loop through the cursor and create new tracking objects. Add them in the return list.
            do {
                boolean isCompleted = cursor.getString(6).equals("1");
                if (getCompleted) {
                    if (isCompleted) {
                        String tracking = cursor.getString(0);
                        tracking_numbers.add(tracking);
                    }
                } else {
                    if (!isCompleted) {
                        String tracking = cursor.getString(0);
                        tracking_numbers.add(tracking);
                    }
                }
            } while (cursor.moveToNext());
        }

        // close cursor and db when done.
        cursor.close();
        db.close();
        return tracking_numbers;
    }

    public ArrayList<String> getIndexEntry(String tracking) {
        // get a single entry including RowID from index table

        ArrayList<String> indexEntry = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT rowid, * FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = '" + tracking + "'";

        Cursor cursor = db.rawQuery(queryString, null);
        cursor.moveToFirst();
        indexEntry.add(cursor.getString(0)); // RowID
        indexEntry.add(cursor.getString(1)); // TrackingNumber
        indexEntry.add(cursor.getString(2)); // Updated
        indexEntry.add(cursor.getString(3)); // LastUpdate
        indexEntry.add(cursor.getString(4)); // CustomName
        indexEntry.add(cursor.getString(5)); // Unread
        indexEntry.add(cursor.getString(6)); // Carrier
        indexEntry.add(cursor.getString(7)); // Completed

        // close cursor and db when done.
        cursor.close();
        db.close();

        return indexEntry;
    }

    public boolean editTracking(String tracking, String newTracking, String customName) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            ContentValues contentValues = new ContentValues();
            if (!tracking.equals(newTracking)) {
                db.execSQL("ALTER TABLE '" + tracking + "' RENAME TO '" + newTracking + "'");
                contentValues.put(TRACKING_COL, newTracking);
            }
            contentValues.put(CUSTOM_NAME_COL, customName);
            db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});
            return true;
        } catch (SQLException e) {
            Log.e("SQLException", e.toString());
            db.close();
            return false;
        }
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

        return isUnread == 1;
    }

    public void setUnreadStatus(String tracking, Boolean isUnread) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(UNREAD_COL, isUnread);

        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});

        db.close();
    }

    public void setCompleted(String tracking, Boolean isCompleted) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues contentValues = new ContentValues();
        contentValues.put(COMPLETED_COL, isCompleted);

        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});

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
