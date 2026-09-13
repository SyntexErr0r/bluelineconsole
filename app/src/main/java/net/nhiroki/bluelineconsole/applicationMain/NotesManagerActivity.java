package net.nhiroki.bluelineconsole.applicationMain;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.theming.ThemedDialogHelper;
import net.nhiroki.bluelineconsole.notes.Note;
import net.nhiroki.bluelineconsole.notes.NoteDialogHelper;
import net.nhiroki.bluelineconsole.notes.NotesManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesManagerActivity extends BaseWindowActivity {

    private TextView mTvStatusSummary;
    private EditText mSearchEdit;
    private ListView mListView;
    private View mEmptyState;
    private NotesAdapter mAdapter;

    private final List<Note> mNotes = new ArrayList<>();

    public NotesManagerActivity() {
        super(R.layout.notes_manager_activity, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHeaderFooterTexts("Notes", null);
        this.setWindowBoundarySize(ROOT_WINDOW_FULL_WIDTH_IN_MOBILE, 3);

        this.changeBaseWindowElementSizeForAnimation(false);
        this.enableBaseWindowAnimation();

        initViews();
        refreshNotesList();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.changeBaseWindowElementSizeForAnimation(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotesList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.finish();
    }

    private void initViews() {
        mTvStatusSummary = findViewById(R.id.notesTopStatusSummary);
        mSearchEdit = findViewById(R.id.notesSearchEdit);
        mListView = findViewById(R.id.notesListView);
        mEmptyState = findViewById(R.id.notesEmptyState);

        View btnAdd = findViewById(R.id.notesBtnAdd);
        btnAdd.setOnClickListener(v -> NoteDialogHelper.showNoteDialog(this, null, note -> refreshNotesList()));

        mAdapter = new NotesAdapter();
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < mNotes.size()) {
                Note note = mNotes.get(position);
                NoteDialogHelper.showNoteDialog(this, note, updated -> refreshNotesList());
            }
        });

        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                filterNotes(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void refreshNotesList() {
        String q = (mSearchEdit != null && mSearchEdit.getText() != null)
                ? mSearchEdit.getText().toString().trim()
                : "";
        filterNotes(q);
    }

    private void filterNotes(String query) {
        mNotes.clear();
        mNotes.addAll(NotesManager.getInstance().searchNotes(this, query));
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }

        int totalCount = NotesManager.getInstance().getAllNotes(this).size();
        if (mTvStatusSummary != null) {
            mTvStatusSummary.setText("Notes: " + totalCount + (query.isEmpty() ? "" : " (filtered: " + mNotes.size() + ")"));
        }

        if (mEmptyState != null) {
            mEmptyState.setVisibility(mNotes.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void copyNoteToClipboard(Note note) {
        if (note == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                String text = (note.title != null && !note.title.isEmpty() ? note.title + "\n\n" : "") +
                              (note.content != null ? note.content : "");
                cm.setPrimaryClip(ClipData.newPlainText("Cyber Note", text));
                Toast.makeText(this, "Note copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to copy", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteNote(Note note) {
        if (note == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CyberGlassAlertDialogTheme);
        builder.setTitle("DELETE NOTE");
        builder.setMessage("Delete '" + (note.title.isEmpty() ? "this note" : note.title) + "' permanently?");
        builder.setPositiveButton("DELETE", (dialog, which) -> {
            NotesManager.getInstance().deleteNote(this, note.id);
            refreshNotesList();
            Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("CANCEL", null);

        AlertDialog dialog = builder.create();
        dialog.show();
        ThemedDialogHelper.styleDialog(dialog, this);
    }

    private class NotesAdapter extends BaseAdapter {
        private final SimpleDateFormat mDateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

        @Override
        public int getCount() {
            return mNotes.size();
        }

        @Override
        public Object getItem(int position) {
            return mNotes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(NotesManagerActivity.this).inflate(R.layout.note_list_item, parent, false);
            }

            Note note = mNotes.get(position);

            TextView tvTitle = view.findViewById(R.id.noteItemTitle);
            TextView tvDate = view.findViewById(R.id.noteItemDate);
            TextView tvSnippet = view.findViewById(R.id.noteItemSnippet);
            View btnCopy = view.findViewById(R.id.noteBtnCopy);
            View btnEdit = view.findViewById(R.id.noteBtnEdit);
            View btnDelete = view.findViewById(R.id.noteBtnDelete);

            tvTitle.setText(note.title != null && !note.title.isEmpty() ? note.title : "Untitled Note");
            tvDate.setText(mDateFormat.format(new Date(note.updatedAt)));
            tvSnippet.setText(note.content != null && !note.content.isEmpty() ? note.content : "(Empty note)");

            btnCopy.setOnClickListener(v -> copyNoteToClipboard(note));
            btnEdit.setOnClickListener(v -> NoteDialogHelper.showNoteDialog(NotesManagerActivity.this, note, updated -> refreshNotesList()));
            btnDelete.setOnClickListener(v -> confirmDeleteNote(note));

            return view;
        }
    }
}
