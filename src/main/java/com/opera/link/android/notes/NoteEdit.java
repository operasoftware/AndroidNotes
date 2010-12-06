package com.opera.link.android.notes;

import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.opera.link.apilib.items.Note;

public class NoteEdit extends Activity {

    public static final int DELETE_NOTE = 0;
    public static final int EDIT_NOTE = 1;
    public static final int INSERT_NOTE = 2;
    public static final int REVERT_NOTE = 3;
    public static final String NOTEPAD_MODE = "NOTEPAD_MODE";
    private int mode = EDIT_NOTE;
    
	private LinedEditText mBodyText;
    private TextView mCreationDate;
    private long mRowId = -1;
    private NotesDbAdapter mDbHelper;
    
    private Note note;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDbHelper = new NotesDbAdapter(this);
        mDbHelper.open();
        setContentView(R.layout.note_edit);
        
        mBodyText = (LinedEditText) findViewById(R.id.editor);
        mCreationDate = (TextView) findViewById(R.id.creation_date);
        
        mRowId = savedInstanceState != null ? savedInstanceState.getLong(AppNote._ID) 
                							: -1;
		if (mRowId == -1) {
			Bundle extras = getIntent().getExtras();            
			mRowId = extras != null ? extras.getLong(AppNote._ID) 
									: -1;
		}
		if (mRowId == -1) {
			mode = INSERT_NOTE;
		}
		
		populateFields();
		
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);

    	menu.add(0, NoteEdit.REVERT_NOTE, 0, R.string.menu_revert)
    		.setIcon(android.R.drawable.ic_menu_revert);
    	menu.add(0, NoteEdit.DELETE_NOTE, 1, R.string.menu_delete)
    		.setIcon(android.R.drawable.ic_menu_delete);

    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	
    	switch(item.getItemId()) {
    	case DELETE_NOTE:
    		mode = DELETE_NOTE;
    		mDbHelper.markToDelete(mRowId);
    		Intent resultIntent = new Intent();
        	resultIntent.putExtra(NOTEPAD_MODE, mode);
		    setResult(RESULT_OK, resultIntent);
    		finish();
    		break;
    	case REVERT_NOTE:
    		mBodyText.setText(note.content);
    	}
    	
    	return true;
    }

    
    @Override
    protected void onDestroy() {
    	mDbHelper.close();
    	super.onDestroy();
    }
    
    private void populateFields() {
    	if (mode == INSERT_NOTE) {
    		note = new Note("");
        }
        else {
    		note = mDbHelper.fetchNote(mRowId).getNote();
        }
    	mBodyText.setText(note.content);
    	Date created = note.created;
    	if (created != null) {
    		mCreationDate.setText(created.toLocaleString());
    	}
        
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(AppNote._ID, mRowId);
    }
    
    @Override
    public void onBackPressed() {
        if (saveState()) {
        	Intent resultIntent = new Intent();
        	resultIntent.putExtra(NOTEPAD_MODE, mode);
		    setResult(RESULT_OK, resultIntent);
		} else {
			setResult(RESULT_CANCELED);
		}
    	super.onBackPressed();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        populateFields();
    }
    
    /**
     * Saves current note in a database
     * @return true if there were some changes to save
     */
    private boolean saveState() {
    	
        String body = mBodyText.getText().toString();

        if (body.equals(note.content)) {
        	return false;
        }
        
    	note.content = body;
        if (mode == INSERT_NOTE) {
            long id = mDbHelper.createNote(note);
            if (id > 0) {
                mRowId = id;
            }
        } else if (mode == EDIT_NOTE) {
            mDbHelper.updateNote(mRowId, note, false);

        }
        return true;
    }
 

    /**
     * A custom EditText that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;
        private int minLines = 0;

        // we need this constructor for LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
            
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
            
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
        	if (minLines == 0) {
                minLines = getHeight() / getLineHeight();
                setMinLines(minLines);	
        	}
        	int count = Math.max(getLineCount(), minLines);     		
 
            Rect r = mRect;
            Paint paint = mPaint;
            
            int baseline = getLineBounds(0, r);
            for (int i = 0; i < count; i++) {
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
                baseline += getLineHeight();
            }

            super.onDraw(canvas);
        }
    }

}
