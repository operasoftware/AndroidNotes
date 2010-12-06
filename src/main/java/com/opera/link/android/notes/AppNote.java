package com.opera.link.android.notes;

import java.util.Date;

import com.opera.link.apilib.items.Note;

/**
 * Contains Opera Link data and informations about its status in the application
 */
public class AppNote {

	private static final String REPLACED_ENDING = "...";
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	private static final String WHITESPACE = " ";
	
	
	private Note note;
	
    public final static String _ID = "_id";
    public final static String CONTENT_FIELD = "content";
    public final static String CREATED_FIELD = "created";
    public final static String OPERA_ID = "opera_id";
    public final static String SYNCED_FIELD = "synced";

    public Note getNote() {
		return note;
	}

	public long getId() {
		return id;
	}

	public String getOpera_id() {
		return note.getId();
	}

	public boolean isSynced() {
		return synced;
	}
	

	private long id;
    private boolean synced;
	
	public final static String[] properties = new String[] {
		_ID, CONTENT_FIELD, CREATED_FIELD, OPERA_ID, SYNCED_FIELD
	};
	
	public AppNote(long id, String content, String createdString, String opera_id, boolean synced) {
		this(id, new Note(content));
		this.note.created = new Date(createdString);
		this.note.setId(opera_id);
		this.synced = synced;
	}

	public AppNote(long id, Note note) {
		this.id = id;
		this.note = note;
		this.synced = false;
	}

	/**
	 * Shortens note content for its preview
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(this.note.content.trim());
		
		if (sb.length() > OperaNotes.NOTE_PREVIEW_MAX_LENGTH) {
			sb.delete(OperaNotes.NOTE_PREVIEW_MAX_LENGTH - REPLACED_ENDING.length(), sb.length());
			sb.append(REPLACED_ENDING);
		}
		return sb.toString().replaceAll(LINE_SEPARATOR, WHITESPACE);
	}

}