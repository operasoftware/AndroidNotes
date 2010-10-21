/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.opera.link.android.notes;


import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.opera.link.apilib.android.items.Note;

/**
 * Simple notes database access helper class. Defines operations
 * for the notes and their synchronisation with Opera Link server.
 */
public class NotesDbAdapter {

    private static final String DATABASE_NAME = "opera_link";
    private static final String DATABASE_TABLE = "notes";
    private static final int DATABASE_VERSION = 2;
    
    private static final String TAG = "NotesDbAdapter";
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    
	private static final String IS_NULL = " is null";
	private static final String EMPTY = "";
	private static final String EQUALS_EMPTY = "=''";
	
	public static final String NOTES_SELECTION = AppNote.CONTENT_FIELD + "!=''";
    public static final String CHANGED_NOTES_SELECTION = AppNote.SYNCED_FIELD + "=0";
	public static final String NEW_NOTES_SELECTION = AppNote.OPERA_ID + IS_NULL;
	public static final String TO_DELETE_SELECTION = AppNote.CONTENT_FIELD + EQUALS_EMPTY;

	private static final int idColumn = 0;
	private static final int contentColumn = 1;
	private static final int createdColumn = 2;
	private static final int opera_idColumn = 3;
	private static final int syncedColumn = 4;
    
    /**
     * Database creation sql statement
     */
    private static final String DATABASE_CREATE =
    	"CREATE TABLE " + DATABASE_TABLE + " ("
	        + AppNote._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
	        + AppNote.CONTENT_FIELD + " TEXT NOT NULL,"
	        + AppNote.CREATED_FIELD + " TEXT,"
	        + AppNote.OPERA_ID + " TEXT,"
	        + AppNote.SYNCED_FIELD + " INTEGER"
        + ");";
	
    	

    private final Context mCtx;

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
            onCreate(db);
        }
    }

    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     * 
     * @param ctx the Context within which to work
     */
    public NotesDbAdapter(Context ctx) {
        this.mCtx = ctx;
    }

    /**
     * Open the notes database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     * 
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public NotesDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }
    
    public void close() {
        mDbHelper.close();
    }


    /**
     * Save new note object into the database. If the note is
     * successfully created return the new rowId for that note, otherwise return
     * a -1 to indicate failure.
     * 
     * @param Note element to be saved
     * @return rowId or -1 if failed
     */
    public long createNote(Note note) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(AppNote.CONTENT_FIELD, note.content);
        initialValues.put(AppNote.CREATED_FIELD, note.created.toLocaleString());

        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }
    
    /**
     * Save a note got from the Opera Link server. 
     * @param note element to be saved
     * @return AppNote object created from the note received
     */
    public AppNote importAndAddNote(Note note) {
    	ContentValues initialValues = new ContentValues();
    	initialValues.put(AppNote.CONTENT_FIELD, note.content);
    	
    	String createdString = new Date().toLocaleString();
    	if (note.created != null) {
    		createdString = note.created.toLocaleString();
    	}
    	
		initialValues.put(AppNote.CREATED_FIELD, createdString);
    	initialValues.put(AppNote.OPERA_ID, note.getId());
    	initialValues.put(AppNote.SYNCED_FIELD, 1);
    	
    	long rowId = mDb.insert(DATABASE_TABLE, null, initialValues);
    
    	return new AppNote(rowId, note.content, createdString, note.getId(), true);
    }

    /**
     * Mark element to be deleted. Those elements are not displayed in the notes list.
     * @param rowId
     * @return
     */
    public boolean markToDelete(long rowId) {

        ContentValues args = new ContentValues();
        args.put(AppNote.CONTENT_FIELD, EMPTY);
    	return mDb.update(DATABASE_TABLE, args, AppNote._ID + "=" + rowId, null) > 0;
    }
    
    /**
     * Delete the note with the given rowId
     * 
     * @param rowId id of note to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteNote(long rowId) {
        return mDb.delete(DATABASE_TABLE, AppNote._ID + "=" + rowId, null) > 0;
    }

    /**
     * @return notes from the database which are not marked to be deleted
     */
	public ArrayList<AppNote> fetchAllNotes() {
		return fetchNotes(NOTES_SELECTION);
	}
	
	/**
	 * @param selection WHERE expression of SELECT query
	 * @return AppNotes created from rows which satisfy selection
	 */
	public ArrayList<AppNote> fetchNotes(String selection) {
		Cursor cursor = mDb.query(DATABASE_TABLE, null,
        		selection, null, null, null, null);
		
		ArrayList<AppNote> result = new ArrayList<AppNote>(cursor.getCount());
		
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			result.add(noteFromCursor(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return result;
	}
	
	/**
	 * Creates and returns object from the row pointed by cursor
	 */
    private AppNote noteFromCursor(Cursor cursor) {
    	if (cursor.isAfterLast()) {
    		return null;
    	}
		long _id = cursor.getLong(idColumn);
		String content = cursor.getString(contentColumn);
		String created = cursor.getString(createdColumn);
		String opera_id = cursor.getString(opera_idColumn);
		boolean synced = cursor.getInt(syncedColumn) == 1;
		return new AppNote(_id, content, created, opera_id, synced);
	}

	/**
     * @param rowId id of note to retrieve
     * @return Cursor positioned to matching note, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public AppNote fetchNote(long rowId) throws SQLException {
    	if (rowId == -1) {
    		return null;
    	}
        Cursor cursor = mDb.query(true, DATABASE_TABLE, null,
                		AppNote._ID + "=" + rowId, null,
                        null, null, null, null);
        AppNote result = null;
        
        cursor.moveToFirst();
        result = noteFromCursor(cursor);
        cursor.close();
        
        return result;
    }

    /**
     * Returns AppNote object from row with opera_id = opera_id
     * @param opera_id Opera Link Note identifier
     * @return AppNote object with given opera_id or null if no such element found 
     */
    public AppNote fetchOperaNote(String opera_id) {
        Cursor cursor = mDb.query(true, DATABASE_TABLE, null,
        		AppNote.OPERA_ID + "='" + opera_id +"'", null,
                null, null, null, null);
        AppNote result = null;
        
        cursor.moveToFirst();
        result = noteFromCursor(cursor);
        cursor.close();
        
        return result;
    }

    /**
     * Update the note using the details provided. The note to be updated is
     * specified using the rowId, and it is altered to use the content
     * values passed in note
     * 
     * @param rowId id of note to update
     * @param note contains new content for note
     * @param synced specifies whether set the element as synchronised with the server
     * @return true if the note was successfully updated, false otherwise
     */
    public boolean updateNote(long rowId, Note note, boolean synced) {
        ContentValues args = new ContentValues();
        args.put(AppNote.CONTENT_FIELD, note.content);
        args.put(AppNote.OPERA_ID, note.getId());
        args.put(AppNote.SYNCED_FIELD, synced);

        return mDb.update(DATABASE_TABLE, args, AppNote._ID + "=" + rowId, null) > 0;
    }
}
