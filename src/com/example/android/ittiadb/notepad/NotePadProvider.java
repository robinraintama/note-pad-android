/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2011 ITTIA L.L.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.ittiadb.notepad;

import com.example.android.ittiadb.notepad.NotePad.Notes;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.ittiadb.IttiaDbDatabase;
import android.database.ittiadb.IttiaDbOpenHelper;
import android.database.ittiadb.IttiaDbQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified data.
 */
public class NotePadProvider extends ContentProvider {
    private static final String TAG = "NotePadProvider";

    private static final String DATABASE_NAME = "note_pad.db";
    private static final int DATABASE_VERSION = 2;
    private static final String NOTES_TABLE_NAME = "notes";

    private static HashMap<String, String> sNotesProjectionMap;

    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends IttiaDbOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(IttiaDbDatabase db) {
            db.execSQL("CREATE TABLE " + NOTES_TABLE_NAME + " ("
                    + Notes._ID + " INTEGER PRIMARY KEY,"
                    + Notes.TITLE + " NVARCHAR(100),"
                    + Notes.NOTE + " NVARCHAR(100),"
                    + Notes.CREATED_DATE + " BIGINT,"
                    + Notes.MODIFIED_DATE + " BIGINT"
                    + ");");
            db.execSQL("CREATE SEQUENCE " + NOTES_TABLE_NAME + " START WITH 1");
        }

        @Override
        public void onUpgrade(IttiaDbDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            try {
            	db.execSQL("DROP TABLE " + NOTES_TABLE_NAME);
            	db.execSQL("DROP SEQUENCE " + NOTES_TABLE_NAME);
            } catch (SQLException ex)            
            {
            }
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        IttiaDbQueryBuilder qb = new IttiaDbQueryBuilder();

        switch (sUriMatcher.match(uri)) {
        case NOTES:
            qb.setTables(NOTES_TABLE_NAME);
            qb.setProjectionMap(sNotesProjectionMap);
            break;

        case NOTE_ID:
            qb.setTables(NOTES_TABLE_NAME);
            qb.setProjectionMap(sNotesProjectionMap);
            qb.appendWhere(Notes._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        IttiaDbDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case NOTES:
            return Notes.CONTENT_TYPE;

        case NOTE_ID:
            return Notes.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        // Make sure that the fields are all set
        if (values.containsKey(NotePad.Notes.CREATED_DATE) == false) {
            values.put(NotePad.Notes.CREATED_DATE, now);
        }

        if (values.containsKey(NotePad.Notes.MODIFIED_DATE) == false) {
            values.put(NotePad.Notes.MODIFIED_DATE, now);
        }

        if (values.containsKey(NotePad.Notes.TITLE) == false) {
            Resources r = Resources.getSystem();
            values.put(NotePad.Notes.TITLE, r.getString(android.R.string.untitled));
        }

        if (values.containsKey(NotePad.Notes.NOTE) == false) {
            values.put(NotePad.Notes.NOTE, "");
        }

        IttiaDbDatabase db = mOpenHelper.getWritableDatabase();
        
        long rowId;
        if (values.containsKey(NotePad.Notes._ID) == false) {
	        rowId = db.sequenceNext(NOTES_TABLE_NAME);
	        values.put(NotePad.Notes._ID, rowId);
        }
        else {
        	rowId = values.getAsLong(NotePad.Notes._ID);
        }
        
        if (db.insert(NOTES_TABLE_NAME, Notes.NOTE, values) >= 0)
        {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }
        else {
        	throw new SQLException("Failed to insert row into " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        IttiaDbDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case NOTES:
            count = db.delete(NOTES_TABLE_NAME, where, whereArgs);
            break;

        case NOTE_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.delete(NOTES_TABLE_NAME, Notes._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        IttiaDbDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case NOTES:
            count = db.update(NOTES_TABLE_NAME, values, where, whereArgs);
            break;

        case NOTE_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.update(NOTES_TABLE_NAME, values, Notes._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        sNotesProjectionMap = new HashMap<String, String>();
        sNotesProjectionMap.put(Notes._ID, Notes._ID);
        sNotesProjectionMap.put(Notes.TITLE, Notes.TITLE);
        sNotesProjectionMap.put(Notes.NOTE, Notes.NOTE);
        sNotesProjectionMap.put(Notes.CREATED_DATE, Notes.CREATED_DATE);
        sNotesProjectionMap.put(Notes.MODIFIED_DATE, Notes.MODIFIED_DATE);
        
		// Load ITTIA DB-SQL native JNI library
		System.loadLibrary("ittiajni");
	}
}