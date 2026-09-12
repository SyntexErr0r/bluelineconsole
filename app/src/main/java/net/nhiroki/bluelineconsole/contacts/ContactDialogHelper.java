package net.nhiroki.bluelineconsole.contacts;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.theming.ThemedDialogHelper;
import net.nhiroki.bluelineconsole.wrapperForAndroid.ContactsReader;

public class ContactDialogHelper {

    private static Activity getActivityFromContext(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) {
                return (Activity) cur;
            }
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return null;
    }

    public static void showGlobalSettingsDialog(Context context, Runnable onDismiss) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_contact_global_settings, null);
        builder.setView(view);
        builder.setTitle("📇 GLOBAL CONTACT SETTINGS");

        final AlertDialog dialog = builder.create();
        ContactManager mgr = ContactManager.getInstance();

        RadioGroup rgCall = view.findViewById(R.id.dialogContactRadioGroupCall);
        String currentCall = mgr.getGlobalCallMethod(context);
        if (ContactManager.CALL_METHOD_WHATSAPP_VOICE.equals(currentCall)) {
            rgCall.check(R.id.dialogContactRadioCallWhatsAppVoice);
        } else if (ContactManager.CALL_METHOD_WHATSAPP_VIDEO.equals(currentCall)) {
            rgCall.check(R.id.dialogContactRadioCallWhatsAppVideo);
        } else if (ContactManager.CALL_METHOD_TELEGRAM.equals(currentCall)) {
            rgCall.check(R.id.dialogContactRadioCallTelegram);
        } else {
            rgCall.check(R.id.dialogContactRadioCallPhone);
        }

        RadioGroup rgMsg = view.findViewById(R.id.dialogContactRadioGroupMsg);
        String currentMsg = mgr.getGlobalMsgMethod(context);
        if (ContactManager.MSG_METHOD_TELEGRAM.equals(currentMsg)) {
            rgMsg.check(R.id.dialogContactRadioMsgTelegram);
        } else if (ContactManager.MSG_METHOD_SMS.equals(currentMsg)) {
            rgMsg.check(R.id.dialogContactRadioMsgSms);
        } else {
            rgMsg.check(R.id.dialogContactRadioMsgWhatsApp);
        }

        SwitchCompat swOnlyCmd = view.findViewById(R.id.dialogContactSwitchOnlyCommand);
        swOnlyCmd.setChecked(mgr.isOnlySearchOnCommand(context));

        view.findViewById(R.id.dialogContactBtnSaveGlobal).setOnClickListener(v -> {
            int selectedCallId = rgCall.getCheckedRadioButtonId();
            String chosenCall = ContactManager.CALL_METHOD_PHONE;
            if (selectedCallId == R.id.dialogContactRadioCallWhatsAppVoice) {
                chosenCall = ContactManager.CALL_METHOD_WHATSAPP_VOICE;
            } else if (selectedCallId == R.id.dialogContactRadioCallWhatsAppVideo) {
                chosenCall = ContactManager.CALL_METHOD_WHATSAPP_VIDEO;
            } else if (selectedCallId == R.id.dialogContactRadioCallTelegram) {
                chosenCall = ContactManager.CALL_METHOD_TELEGRAM;
            }
            mgr.setGlobalCallMethod(context, chosenCall);

            int selectedMsgId = rgMsg.getCheckedRadioButtonId();
            String chosenMsg = ContactManager.MSG_METHOD_WHATSAPP;
            if (selectedMsgId == R.id.dialogContactRadioMsgTelegram) {
                chosenMsg = ContactManager.MSG_METHOD_TELEGRAM;
            } else if (selectedMsgId == R.id.dialogContactRadioMsgSms) {
                chosenMsg = ContactManager.MSG_METHOD_SMS;
            }
            mgr.setGlobalMsgMethod(context, chosenMsg);

            mgr.setOnlySearchOnCommand(context, swOnlyCmd.isChecked());

            Toast.makeText(context, "Contact preferences saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });

        dialog.show();
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }

    public static void showContactEditDialog(Context context, ContactsReader.Contact contact, Runnable onSaved) {
        if (contact == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_contact_edit, null);
        builder.setView(view);
        builder.setTitle("⚡ EDIT CONTACT CONFIG");

        final AlertDialog dialog = builder.create();
        ContactManager mgr = ContactManager.getInstance();
        String key = ContactManager.getContactKey(contact);
        ContactManager.ContactCustomConfig existingCfg = mgr.getCustomConfig(context, key);
        boolean isPinned = mgr.isPinned(context, key);

        TextView tvName = view.findViewById(R.id.dialogEditContactName);
        TextView tvPhone = view.findViewById(R.id.dialogEditContactPhone);
        tvName.setText(contact.displayName);
        String phoneStr = ContactManager.getPrimaryPhoneNumber(contact);
        tvPhone.setText(phoneStr.isEmpty() ? "(No phone registered)" : phoneStr);

        SwitchCompat swPin = view.findViewById(R.id.dialogEditContactSwitchPin);
        swPin.setChecked(isPinned);

        RadioGroup rgCall = view.findViewById(R.id.dialogEditContactRadioGroupCall);
        String currentCall = existingCfg != null ? existingCfg.preferredCallMethod : ContactManager.CALL_METHOD_DEFAULT;
        if (ContactManager.CALL_METHOD_PHONE.equals(currentCall)) {
            rgCall.check(R.id.dialogEditContactCallPhone);
        } else if (ContactManager.CALL_METHOD_WHATSAPP_VOICE.equals(currentCall)) {
            rgCall.check(R.id.dialogEditContactCallWhatsAppVoice);
        } else if (ContactManager.CALL_METHOD_WHATSAPP_VIDEO.equals(currentCall)) {
            rgCall.check(R.id.dialogEditContactCallWhatsAppVideo);
        } else if (ContactManager.CALL_METHOD_TELEGRAM.equals(currentCall)) {
            rgCall.check(R.id.dialogEditContactCallTelegram);
        } else {
            rgCall.check(R.id.dialogEditContactCallDefault);
        }

        RadioGroup rgMsg = view.findViewById(R.id.dialogEditContactRadioGroupMsg);
        String currentMsg = existingCfg != null ? existingCfg.preferredMsgMethod : ContactManager.MSG_METHOD_DEFAULT;
        if (ContactManager.MSG_METHOD_WHATSAPP.equals(currentMsg)) {
            rgMsg.check(R.id.dialogEditContactMsgWhatsApp);
        } else if (ContactManager.MSG_METHOD_TELEGRAM.equals(currentMsg)) {
            rgMsg.check(R.id.dialogEditContactMsgTelegram);
        } else if (ContactManager.MSG_METHOD_SMS.equals(currentMsg)) {
            rgMsg.check(R.id.dialogEditContactMsgSms);
        } else {
            rgMsg.check(R.id.dialogEditContactMsgDefault);
        }

        EditText etTg = view.findViewById(R.id.dialogEditContactTgUsername);
        if (existingCfg != null && existingCfg.telegramUsername != null) {
            etTg.setText(existingCfg.telegramUsername);
        }

        view.findViewById(R.id.dialogEditContactBtnTestCall).setOnClickListener(v -> {
            int selectedCallId = rgCall.getCheckedRadioButtonId();
            String method = ContactManager.CALL_METHOD_DEFAULT;
            if (selectedCallId == R.id.dialogEditContactCallPhone) method = ContactManager.CALL_METHOD_PHONE;
            else if (selectedCallId == R.id.dialogEditContactCallWhatsAppVoice) method = ContactManager.CALL_METHOD_WHATSAPP_VOICE;
            else if (selectedCallId == R.id.dialogEditContactCallWhatsAppVideo) method = ContactManager.CALL_METHOD_WHATSAPP_VIDEO;
            else if (selectedCallId == R.id.dialogEditContactCallTelegram) method = ContactManager.CALL_METHOD_TELEGRAM;

            if (ContactManager.CALL_METHOD_DEFAULT.equals(method)) {
                mgr.executeCall(context, contact);
            } else {
                mgr.executeCallWithMethod(context, contact, method);
            }
        });

        view.findViewById(R.id.dialogEditContactBtnTestMsg).setOnClickListener(v -> {
            int selectedMsgId = rgMsg.getCheckedRadioButtonId();
            String method = ContactManager.MSG_METHOD_DEFAULT;
            if (selectedMsgId == R.id.dialogEditContactMsgWhatsApp) method = ContactManager.MSG_METHOD_WHATSAPP;
            else if (selectedMsgId == R.id.dialogEditContactMsgTelegram) method = ContactManager.MSG_METHOD_TELEGRAM;
            else if (selectedMsgId == R.id.dialogEditContactMsgSms) method = ContactManager.MSG_METHOD_SMS;

            if (ContactManager.MSG_METHOD_DEFAULT.equals(method)) {
                mgr.executeMessage(context, contact, "");
            } else {
                mgr.executeMessageWithMethod(context, contact, method, "");
            }
        });

        view.findViewById(R.id.dialogEditContactBtnCancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.dialogEditContactBtnSave).setOnClickListener(v -> {
            int selectedCallId = rgCall.getCheckedRadioButtonId();
            String callMethod = ContactManager.CALL_METHOD_DEFAULT;
            if (selectedCallId == R.id.dialogEditContactCallPhone) callMethod = ContactManager.CALL_METHOD_PHONE;
            else if (selectedCallId == R.id.dialogEditContactCallWhatsAppVoice) callMethod = ContactManager.CALL_METHOD_WHATSAPP_VOICE;
            else if (selectedCallId == R.id.dialogEditContactCallWhatsAppVideo) callMethod = ContactManager.CALL_METHOD_WHATSAPP_VIDEO;
            else if (selectedCallId == R.id.dialogEditContactCallTelegram) callMethod = ContactManager.CALL_METHOD_TELEGRAM;

            int selectedMsgId = rgMsg.getCheckedRadioButtonId();
            String msgMethod = ContactManager.MSG_METHOD_DEFAULT;
            if (selectedMsgId == R.id.dialogEditContactMsgWhatsApp) msgMethod = ContactManager.MSG_METHOD_WHATSAPP;
            else if (selectedMsgId == R.id.dialogEditContactMsgTelegram) msgMethod = ContactManager.MSG_METHOD_TELEGRAM;
            else if (selectedMsgId == R.id.dialogEditContactMsgSms) msgMethod = ContactManager.MSG_METHOD_SMS;

            String tgUsername = etTg.getText().toString().trim();
            boolean pinned = swPin.isChecked();

            ContactManager.ContactCustomConfig newCfg = new ContactManager.ContactCustomConfig(
                    key, contact.displayName, callMethod, msgMethod, tgUsername, pinned
            );
            mgr.setCustomConfig(context, key, newCfg);
            mgr.setPinned(context, key, pinned);

            Toast.makeText(context, "Saved config for " + contact.displayName, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onSaved != null) onSaved.run();
        });

        dialog.show();
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }
}
