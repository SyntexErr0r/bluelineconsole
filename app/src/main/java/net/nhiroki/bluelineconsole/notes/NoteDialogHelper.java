package net.nhiroki.bluelineconsole.notes;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.theming.ThemedDialogHelper;

public class NoteDialogHelper {

    public interface OnNoteSavedListener {
        void onNoteSaved(Note note);
    }

    private static Activity getActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void showNoteDialog(Context context, Note existingNote, OnNoteSavedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_note_editor, null);
        builder.setView(view);

        final AlertDialog dialog = builder.create();

        EditText titleEdit = view.findViewById(R.id.dialogNoteTitleEdit);
        EditText contentEdit = view.findViewById(R.id.dialogNoteContentEdit);
        TextView btnCancel = view.findViewById(R.id.dialogNoteBtnCancel);
        TextView btnSave = view.findViewById(R.id.dialogNoteBtnSave);

        if (existingNote != null) {
            titleEdit.setText(existingNote.title);
            contentEdit.setText(existingNote.content);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String title = titleEdit.getText().toString().trim();
            String content = contentEdit.getText().toString().trim();

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(context, "Note cannot be completely empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (title.isEmpty()) {
                // Use first line of content as title
                String[] lines = content.split("\\r?\\n");
                title = lines[0].length() > 30 ? lines[0].substring(0, 30) + "..." : lines[0];
            }

            Note noteToSave = (existingNote != null) ? existingNote : new Note();
            noteToSave.title = title;
            noteToSave.content = content;

            NotesManager.getInstance().saveNote(context, noteToSave);
            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();

            if (listener != null) {
                listener.onNoteSaved(noteToSave);
            }
            dialog.dismiss();
        });

        dialog.show();
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }
}
