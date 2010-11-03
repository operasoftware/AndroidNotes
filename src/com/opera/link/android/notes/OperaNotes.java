/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")savedInstanceState;
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

package com.opera.link.android.notes;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.opera.link.apilib.android.LinkClient;
import com.opera.link.apilib.android.exceptions.LibOperaLinkException;
import com.opera.link.apilib.android.exceptions.LinkAccessDeniedException;
import com.opera.link.apilib.android.exceptions.LinkItemNotFound;
import com.opera.link.apilib.android.items.Note;
import com.opera.link.apilib.android.items.NoteFolder;
import com.opera.link.apilib.android.items.NoteFolderEntry;

public class OperaNotes extends ListActivity {

	// Options menu items
	// Start synchronisation option
	private static final int SYNC_MENU_ITEM = Menu.FIRST;
	// Make a new note option
	private static final int INSERT_MENU_ITEM = Menu.FIRST + 1;
	// Synchronisation preferences option
	private static final int SYNC_OPTIONS_MENU_ITEM = Menu.FIRST + 2;

	// Delete note context menu option
	private static final int DELETE_MENU_ITEM = Menu.FIRST + 2;

	// Dialogs
	private static final int PREFERENCES_DIALOG = 0;

	// Activities
	private static final int REDIRECTION_ACTIVITY = 0;
	private static final int EDITOR_ACTIVITY = 1;

	private NotesDbAdapter mDbHelper;

	// Stores notes to be displayed
	private ArrayAdapter<AppNote> listAdapter;

	private SharedPreferences pref;

	// fields for sync with Opera Link
	private boolean isSynchronized = false;
	private boolean isConnected = false;
	private LinkClient link = null;
	private static final String consumerKey = "KYp31lrfLFJ0UvEOWvihNL6zfchSrWff";
	private static final String consumerSecret = "jl0UWFWNOXEujIiGB3SWnSgqjRzdUEDg";
	private String requestToken = null;
	private String accessToken = null;
	private String tokenSecret = null;

	// true if synchronisation should be invoked automatically when some changes
	// made
	private boolean automaticSync;

	private final static String REQUEST_TOKEN_PREF_KEY = "requestToken";
	private final static String ACCESS_TOKEN_PREF_KEY = "accessToken";
	private final static String TOKEN_SECRET_PREF_KEY = "tokenSecret";
	private final static String CALLBACK_URL = "opera://notes";

	private static final String SYNC_TAG = "SYNC";

	public static final int NOTE_PREVIEW_MAX_LENGTH = 80;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notes_list);

		// Initialise database connection and read notes
		mDbHelper = new NotesDbAdapter(this);
		mDbHelper.open();
		fillData();

		registerForContextMenu(getListView());

		// Read preferences
		pref = getPreferences(MODE_PRIVATE);
		requestToken = pref.getString(REQUEST_TOKEN_PREF_KEY, null);
		accessToken = pref.getString(ACCESS_TOKEN_PREF_KEY, null);
		tokenSecret = pref.getString(TOKEN_SECRET_PREF_KEY, null);
		automaticSync = pref.getBoolean(getString(R.string.sync_option), true);

		// Create object for connection with Opera Link
		if (accessToken != null && tokenSecret != null) {
			isConnected = true;
			link = LinkClient.createFromAccessToken(consumerKey,
					consumerSecret, accessToken, tokenSecret);
			if (automaticSync) {
				syncItems();
			}
		} else if (requestToken != null) {
			link = LinkClient.createFromRequestToken(consumerKey,
					consumerSecret, requestToken, tokenSecret);
		} else {
			link = new LinkClient(consumerKey, consumerSecret);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// if user has resumed the application after logging in to Opera
		// authorisation
		// site and giving it access OAuth access tokens can be obtained
		if (! isConnected) {
			grantAccess();
		}
	}

	/**
	 * Starts activity with url where user can grant permission to the user's
	 * Opera Link data
	 * 
	 * @return true if redirection succeeded
	 */
	private boolean redirectToBrowser() {

		try {
			String authorizeUrl = link.getAuthorizationURL(CALLBACK_URL);

			Editor prefEditor = pref.edit();
			prefEditor.putString(REQUEST_TOKEN_PREF_KEY, link.getRequestToken());
			prefEditor.putString(TOKEN_SECRET_PREF_KEY, link.getTokenSecret());
			prefEditor.commit();

			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(authorizeUrl));
			startActivityForResult(i, REDIRECTION_ACTIVITY);
		} catch (LibOperaLinkException e) {
			e.printStackTrace();
			showToast("Could not connect to the server");
			return false;
		}
		return true;
	}

	/**
	 * Obtains OAuth access tokens which can be used during entire communication
	 * process with Opera Link server and invokes first synchronisation
	 */
	public void grantAccess() {

		Uri uri = this.getIntent().getData();
		if (uri == null) {
			return;
		}

		String verifier = uri.getQueryParameter(LinkClient.OAUTH_VERIFIER);
		if (verifier == null) {
			return;
		}
		try {
			link.grantAccess(verifier);
			Log.i(SYNC_TAG, "Access granted");

			accessToken = link.getAccessToken();
			tokenSecret = link.getTokenSecret();
			Editor prefEditor = pref.edit();
			prefEditor.putString(ACCESS_TOKEN_PREF_KEY, accessToken);
			prefEditor.putString(TOKEN_SECRET_PREF_KEY, tokenSecret);

			prefEditor.commit();
			isConnected = true;

			syncItems();
		} catch (LinkAccessDeniedException e) {
			showToast("Access forbidden for access token " + accessToken);
			e.printStackTrace();
		} catch (LibOperaLinkException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set the list view to display notes from the database
	 */
	private void fillData() {
		mDbHelper.fetchAllNotes();

		ArrayList<AppNote> notesList = mDbHelper.fetchAllNotes();
		listAdapter = new ArrayAdapter<AppNote>(this, R.layout.notes_row,
				notesList);
		setListAdapter(listAdapter);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(1, INSERT_MENU_ITEM, 0, R.string.menu_insert).setIcon(
				android.R.drawable.ic_menu_add);
		menu.add(1, SYNC_OPTIONS_MENU_ITEM, 1, R.string.menu_sync_options)
				.setIcon(android.R.drawable.ic_menu_preferences);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		// if notes already synchronised remove option to enforce
		// synchronisation
		menu.removeItem(SYNC_MENU_ITEM);
		if (!isSynchronized) {
			menu.add(0, SYNC_MENU_ITEM, 1, R.string.menu_sync).setIcon(
					android.R.drawable.ic_popup_sync);
		}
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case SYNC_MENU_ITEM:
			if (!isConnected) {
				redirectToBrowser();
				return true;
			}
			syncItems();
			return true;
		case INSERT_MENU_ITEM:
			createNote();
			return true;
		case SYNC_OPTIONS_MENU_ITEM:
			showDialog(PREFERENCES_DIALOG);
			return true;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PREFERENCES_DIALOG:
			// dialog to enable or disable automatic synchronisation
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.menu_sync_options));
			boolean[] syncStatus = new boolean[] { true };
			if (!automaticSync) {
				syncStatus[0] = false;
			}

			builder.setMultiChoiceItems(
					new String[] { getString(R.string.sync_option) },
					syncStatus, new OnMultiChoiceClickListener() {
						// register handler which changes automatic
						// synchronisation preferences
						public void onClick(DialogInterface dialog, int which,
								boolean isChecked) {
							if (isChecked != automaticSync) {
								Editor editor = pref.edit();
								editor.putBoolean(
										getString(R.string.sync_option),
										isChecked);
								editor.commit();
								automaticSync = isChecked;
								if (automaticSync) {
									syncItems();
								}
							}
						}
					});
			return builder.create();
		}
		return super.onCreateDialog(id);
	}

	/**
	 * Perform Opera Link synchronisation
	 */
	private void syncItems() {
		ProgressDialog dialog = ProgressDialog.show(this, "",
				"Synchronizing notes with server. Please wait...", false);

		boolean synced;
		synced = uploadNewNotes();
		synced = synced && updateNotes();
		synced = synced && deleteNotes();
		synced = synced && getServerNotes();
		dialog.cancel();

		fillData();

		if (!isConnected) {
			showToast("Authorization expired, you need to authorize the application again");
			Editor prefEditor = pref.edit();
			prefEditor.putString(REQUEST_TOKEN_PREF_KEY, null);
			prefEditor.putString(ACCESS_TOKEN_PREF_KEY, null);
			prefEditor.putString(TOKEN_SECRET_PREF_KEY, null);
			prefEditor.commit();
		}
		if (!synced) {
			showToast("There were some problems, not all changes submitted. Try again later");
		}
		isSynchronized = synced;
	}

	/**
	 * Send to Opera Link server changes made to the notes
	 */
	private void sendChanges() {
		if (notesDeleted) {
			if (deleteNotes())
				notesDeleted = false;
		}
		if (notesAdded) {
			if (uploadNewNotes())
				notesAdded = false;
		}
		if (notesChanged) {
			if (updateNotes())
				notesChanged = false;
		}

		isSynchronized = !notesDeleted && !notesAdded && !notesChanged;
		if (!isSynchronized) {
			showToast("There were some problems, not all changes submitted. Try again later");
		}
	}

	/**
	 * Select from a tree structure all of the notes
	 * 
	 * @param notes
	 *            Notes tree structure
	 * @param flattenedNotes
	 *            Result of flattening a tree structure
	 */
	private void flattenNoteFolders(ArrayList<NoteFolderEntry> notes,
			ArrayList<Note> flattenedNotes) {
		for (NoteFolderEntry noteFolderEntry : notes) {
			if (noteFolderEntry.isNote()) {
				flattenedNotes.add((Note) noteFolderEntry);
			} else if (noteFolderEntry.isFolder()) {
				NoteFolder nf = (NoteFolder) noteFolderEntry;
				if (! nf.isTrash()) {
					flattenNoteFolders(nf.getChildren(), flattenedNotes);
				}
			}
		}
	}

	/**
	 * Get new notes and updates from Opera Link server and insert them into
	 * database
	 * 
	 * @return true if operation succeeded
	 */
	private boolean getServerNotes() {
		ArrayList<NoteFolderEntry> notes = null;
		try {
			notes = link.getRootNotes(true);
		} catch (LinkAccessDeniedException e) {
			e.printStackTrace();
			isConnected = false;
			return false;
		} catch (LibOperaLinkException e) {
			e.printStackTrace();
			return false;
		}
		int newNotes = 0;
		ArrayList<Note> allNotes = new ArrayList<Note>();
		flattenNoteFolders(notes, allNotes);

		for (Note note : allNotes) {
			// try to get note from the database
			AppNote appNote = mDbHelper.fetchOperaNote(note.getId());
			if (appNote == null) { // no such note in database, add a new one
				++newNotes;
				mDbHelper.importAndAddNote(note);
			} else if (!appNote.getNote().content.equals(note.content)) {
				// update already added note
				if (appNote.isSynced()) {
					mDbHelper.updateNote(appNote.getId(), note, true);
				} else { // note content conflicted, add new note leaving the
					// old one
					mDbHelper.createNote(note);
					// set that the old one is synchronised
					mDbHelper.updateNote(appNote.getId(), appNote.getNote(),
							true);
				}
			}
		}

		showToast(String.valueOf(newNotes) + " "
				+ getString(R.string.got_notes));
		return true;
	}

	/**
	 * Delete notes which were marked as those to be deleted, delete them also
	 * from the Opera Link server
	 * 
	 * @return true if deleting notes at the server succeeded
	 */
	private boolean deleteNotes() {
		ArrayList<AppNote> toDelete = mDbHelper
				.fetchNotes(NotesDbAdapter.TO_DELETE_SELECTION);
		boolean allDeleted = true;
		for (AppNote appNote : toDelete) {
			try {
				if (appNote.getOpera_id() != null) {
					link.delete(appNote.getNote());
				}
				mDbHelper.deleteNote(appNote.getId());
			} catch (LinkItemNotFound e) {
				e.printStackTrace();
				mDbHelper.deleteNote(appNote.getId());
			} catch (LinkAccessDeniedException e) {
				e.printStackTrace();
				isConnected = false;
				allDeleted = false;
			} catch (LibOperaLinkException e) {
				e.printStackTrace();
				allDeleted = false;
			}
		}

		return allDeleted;
	}

	/**
	 * Change notes at the server
	 * 
	 * @return true if succeeded
	 */
	private boolean uploadNewNotes() {
		ArrayList<AppNote> newNotes = mDbHelper
				.fetchNotes(NotesDbAdapter.NEW_NOTES_SELECTION);
		boolean allUpdated = true;
		try {
			for (AppNote appNote : newNotes) {
				link.add(appNote.getNote());
				mDbHelper.updateNote(appNote.getId(), appNote.getNote(), true);
			}
		} catch (LinkAccessDeniedException e) {
			e.printStackTrace();
			allUpdated = false;
			isConnected = false;
		} catch (LibOperaLinkException e) {
			e.printStackTrace();
			allUpdated = false;
		}
		return allUpdated;
	}

	private boolean updateNotes() {
		ArrayList<AppNote> changed = mDbHelper
				.fetchNotes(NotesDbAdapter.CHANGED_NOTES_SELECTION);
		boolean allUpdated = true;

		for (AppNote appNote : changed) {
			try {
				link.update(appNote.getNote());
				mDbHelper.updateNote(appNote.getId(), appNote.getNote(), true);
			} catch (LinkItemNotFound e) {
				e.printStackTrace();
				try {
					link.add(appNote.getNote());
					mDbHelper.updateNote(appNote.getId(), appNote.getNote(),
							true);
				} catch (LibOperaLinkException e1) {
					e1.printStackTrace();
					allUpdated = false;
				}
			} catch (LinkAccessDeniedException e) {
				e.printStackTrace();
				isConnected = false;
				allUpdated = false;
			} catch (LibOperaLinkException e) {
				e.printStackTrace();
				allUpdated = false;
			}
		}

		return allUpdated;
	}

	private void showToast(String message) {
		Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		// Context menu for deleting a note
		AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		AppNote note = listAdapter.getItem(info.position);
		menu.setHeaderTitle(note.toString());
		menu.add(0, DELETE_MENU_ITEM, 0, R.string.menu_delete).setIcon(
				android.R.drawable.ic_menu_delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case DELETE_MENU_ITEM:
			// Mark the selected note to be deleted and send changes to the
			// server
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
					.getMenuInfo();
			AppNote note = listAdapter.getItem((int) info.id);
			mDbHelper.markToDelete(note.getId());
			if (note.getOpera_id() != null) {
				notesDeleted = true;
				if (automaticSync) {
					sendChanges();
				}
			}
			fillData();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private void createNote() {
		Intent i = new Intent(this, NoteEdit.class);
		startActivityForResult(i, EDITOR_ACTIVITY);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent i = new Intent(this, NoteEdit.class);
		AppNote note = listAdapter.getItem((int) id);
		i.putExtra(AppNote._ID, note.getId());
		startActivityForResult(i, EDITOR_ACTIVITY);
	}

	private boolean notesChanged = false;
	private boolean notesAdded = false;
	private boolean notesDeleted = false;

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {

		super.onActivityResult(requestCode, resultCode, intent);
		if (resultCode == RESULT_CANCELED) {
			return;
		}

		if (requestCode == REDIRECTION_ACTIVITY) {
			// if back from the browser try to obtain access to Opera Link data
			// and then synchronise
			grantAccess();
			syncItems();
			return;
		}

		// back from note edit activity
		int mode = intent
				.getIntExtra(NoteEdit.NOTEPAD_MODE, NoteEdit.EDIT_NOTE);

		if (mode == NoteEdit.DELETE_NOTE) {
			notesDeleted = true;
		}
		if (mode == NoteEdit.INSERT_NOTE) {
			notesAdded = true;
		} else if (mode == NoteEdit.EDIT_NOTE) {
			notesChanged = true;
		}
		isSynchronized = isSynchronized && !notesDeleted && !notesAdded
				&& !notesChanged;

		if (automaticSync) {
			sendChanges();
		}
		fillData();
	}
}
