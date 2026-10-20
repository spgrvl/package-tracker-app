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

    public static final int VERSION = 8;

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
    // Language the package's details were saved in, "el" or "en"; null if saved before it was recorded
    public static final String LANGUAGE_COL = "Language";
    public static final String STATUS_COL = "Status";
    public static final String PLACE_COL = "Place";
    public static final String DATETIME_COL = "Datetime";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE IF NOT EXISTS " + INDEX_TABLE + "(" + TRACKING_COL + " TEXT PRIMARY KEY, " + UPDATED_COL + " TEXT, " + LAST_UPDATE_COL + " TEXT, " + CUSTOM_NAME_COL + " TEXT, " + UNREAD_COL + " BOOLEAN NOT NULL, " + CARRIER_COL + " TEXT, " + COMPLETED_COL + " BOOLEAN NOT NULL, " + CREATED_COL + " TEXT, " + LANGUAGE_COL + " TEXT, CHECK ( " + UNREAD_COL + " IN (0,1) AND " + COMPLETED_COL + " IN (0,1)))";
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

        // check if LANGUAGE_COL exists and if not add it
        int languageColumnIndex = cursor.getColumnIndex(LANGUAGE_COL);
        if (languageColumnIndex < 0) {
            db.execSQL("ALTER TABLE " + INDEX_TABLE + " ADD COLUMN " + LANGUAGE_COL + " TEXT");
        }

        // close cursor when done.
        cursor.close();

        // Table names are case-insensitive, so packages differing only in case shared one details
        // table and deleting one dropped it for the other: recreate any missing details tables
        Cursor trackingCursor = db.rawQuery("SELECT " + TRACKING_COL + " FROM " + INDEX_TABLE, null);
        while (trackingCursor.moveToNext()) {
            db.execSQL(createDetailsTable(trackingCursor.getString(0)));
        }
        trackingCursor.close();
    }

    /**
     * The tracking number as stored, if a package with it exists in any letter case, else null.
     * Details tables are named after tracking numbers and SQLite table names ignore case, so
     * numbers differing only in case must not be stored twice.
     */
    public String findTracking(String tracking) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + TRACKING_COL + " FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ? COLLATE NOCASE",
                new String[]{tracking});
        String stored = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        db.close();
        return stored;
    }

    /**
     * Quoted name of a package's details table. Tracking numbers come from user input, barcodes
     * and backups, so quotes inside them are escaped instead of breaking the SQL.
     */
    static String detailsTable(String tracking) {
        return "\"" + tracking.replace("\"", "\"\"") + "\"";
    }

    static String createDetailsTable(String tracking) {
        return "CREATE TABLE IF NOT EXISTS " + detailsTable(tracking) + "(" + STATUS_COL + " TEXT, " + PLACE_COL + " TEXT, " + DATETIME_COL + " TEXT )";
    }

    /**
     * Whether a package differing only in case shares this package's details table (stored
     * before duplicates were checked regardless of case), so it must not be dropped.
     */
    private static boolean isDetailsTableShared(SQLiteDatabase db, String tracking) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ? COLLATE NOCASE AND " + TRACKING_COL + " != ?",
                new String[]{tracking, tracking});
        boolean shared = cursor.moveToFirst();
        cursor.close();
        return shared;
    }

    public boolean addNewTracking(String Tracking, String CustomName, Boolean Completed, String Created) {
        if (findTracking(Tracking) != null) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();

        // Create table for details of new Tracking number
        try {
            db.execSQL(createDetailsTable(Tracking));

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

    public void updateTrackingDetails(String tracking, ArrayList<TrackingDetailsModel> detailsList, String language) {
        // Delete Tracking table contents and insert the new list of entries, in the given language

        SQLiteDatabase db = this.getWritableDatabase();

        db.beginTransaction();
        try {
            setDetailsLanguage(db, tracking, language);
            db.execSQL("DELETE FROM " + detailsTable(tracking));
            for (int i = 0; i < detailsList.size(); i++) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(STATUS_COL, detailsList.get(i).getStatus());
                contentValues.put(PLACE_COL, detailsList.get(i).getPlace());
                contentValues.put(DATETIME_COL, detailsList.get(i).getDatetime());
                db.insert(detailsTable(tracking), null, contentValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        db.close();
    }

    /** The language a package's details were saved in, or null if it wasn't recorded. */
    public String getDetailsLanguage(String tracking) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + LANGUAGE_COL + " FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ?",
                new String[]{tracking});
        String language = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        db.close();
        return language;
    }

    public void setDetailsLanguage(String tracking, String language) {
        SQLiteDatabase db = this.getWritableDatabase();
        setDetailsLanguage(db, tracking, language);
        db.close();
    }

    private static void setDetailsLanguage(SQLiteDatabase db, String tracking, String language) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(LANGUAGE_COL, language);
        db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});
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
        return getAllTracking(null);
    }

    public List<TrackingIndexModel> getAllTracking(Boolean completed) {
        List<TrackingIndexModel> returnList = new ArrayList<>();

        // get data from DB
        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT * FROM " + INDEX_TABLE;
        
        if (completed != null) {
            queryString += " WHERE " + COMPLETED_COL + " = " + (completed ? "1" : "0");
        }

        // Newest packages first (Created stores millis as text; empty/legacy rows sort last)
        queryString += " ORDER BY CAST(" + CREATED_COL + " AS INTEGER) DESC, " + TRACKING_COL + " COLLATE NOCASE ASC";

        Cursor cursor = db.rawQuery(queryString, null);

        if (cursor.moveToFirst()) {
            // loop through the cursor and create new tracking objects. Add them in the return list.
            do {
                String tracking = cursor.getString(0);
                String updated = cursor.getString(1);
                String lastUpdate = cursor.getString(2);
                String customName = cursor.getString(3);
                String carrier = cursor.getString(5);
                boolean isCompleted = cursor.getString(6).equals("1");
                String created = cursor.getString(7);

                TrackingIndexModel newTracking = new TrackingIndexModel(tracking, created, updated, lastUpdate, carrier, customName, isCompleted);
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
        String queryString = "SELECT rowid, * FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ?";

        Cursor cursor = db.rawQuery(queryString, new String[]{tracking});
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
        // A change of letter case only keeps the package as it is, since table names ignore case
        boolean numberChanged = !tracking.equalsIgnoreCase(newTracking);
        if (numberChanged && findTracking(newTracking) != null) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();

        db.beginTransaction();
        try {
            ContentValues contentValues = new ContentValues();
            if (numberChanged) {
                // The old number's history, carrier and status belong to another parcel: start
                // the new number empty, as if just added
                if (!isDetailsTableShared(db, tracking)) {
                    db.execSQL("DROP TABLE IF EXISTS " + detailsTable(tracking));
                }
                db.execSQL("DROP TABLE IF EXISTS " + detailsTable(newTracking));
                db.execSQL(createDetailsTable(newTracking));
                contentValues.put(UPDATED_COL, "Never");
                contentValues.put(LAST_UPDATE_COL, "Status: None");
                contentValues.putNull(CARRIER_COL);
                contentValues.putNull(LANGUAGE_COL);
                contentValues.put(UNREAD_COL, false);
            }
            contentValues.put(TRACKING_COL, newTracking);
            contentValues.put(CUSTOM_NAME_COL, customName);
            db.update(INDEX_TABLE, contentValues, TRACKING_COL + " = ?", new String[]{tracking});
            db.setTransactionSuccessful();
            return true;
        } catch (SQLException e) {
            Log.e("SQLException", e.toString());
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public List<TrackingDetailsModel> getTrackingDetails(String tracking) {
        List<TrackingDetailsModel> returnDetailsList = new ArrayList<>();

        // get data from DB

        SQLiteDatabase db = this.getReadableDatabase();
        String queryString = "SELECT * FROM " + detailsTable(tracking);

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
        String queryString = "SELECT COUNT(*) FROM " + detailsTable(tracking);

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
        String queryString = "SELECT " + UNREAD_COL + " FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ?";

        Cursor cursor = db.rawQuery(queryString, new String[]{tracking});

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

        // Delete tracking table, unless another package still uses it
        if (!isDetailsTableShared(db, tracking)) {
            db.execSQL("DROP TABLE IF EXISTS " + detailsTable(tracking));
        }

        db.execSQL("DELETE FROM " + INDEX_TABLE + " WHERE " + TRACKING_COL + " = ?", new Object[]{tracking});

        // close db when done.
        db.close();
    }
}
