package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.ContactManagerActivity;
import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.commandSearchers.lib.StringMatchStrategy;
import net.nhiroki.bluelineconsole.contacts.ContactManager;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;
import net.nhiroki.bluelineconsole.wrapperForAndroid.ContactsReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactSearchCommandSearcher implements CommandSearcher {
    public static final String PREF_CONTACT_SEARCH_ENABLED_KEY = "pref_contact_search_enabled";

    private List<ContactsReader.Contact> contactList = null;
    private boolean preparationCompleted = false;
    private final List<Thread> waitingThreads = new ArrayList<>();

    private Thread loader;

    @Override
    public void refresh(final Context context) {
        this.cancelAnyRefreshJob();

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_CONTACT_SEARCH_ENABLED_KEY, false)
                || ContactsReader.appHasReadContactsPermission(context);

        if (!enabled) {
            this.contactList = new ArrayList<>();
            this.setPreparationCompleted();
            return;
        }

        this.preparationCompleted = false;

        loader = new Thread() {
            @Override
            public void run() {
                refreshDatabase(context);
            }
        };
        loader.start();
    }

    private void refreshDatabase(Context context) {
        try {
            this.contactList = ContactsReader.fetchAllContacts(context);
        } catch (ContactsReader.ContactReadPermissionDenied e) {
            this.contactList = new ArrayList<>();

            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
            prefEdit.putBoolean(ContactSearchCommandSearcher.PREF_CONTACT_SEARCH_ENABLED_KEY, false);
            prefEdit.apply();
        }

        this.setPreparationCompleted();
    }

    private synchronized void cancelAnyRefreshJob() {
        for (Thread th: waitingThreads) {
            th.interrupt();
        }
        this.waitingThreads.clear();

        if (loader != null) {
            loader.interrupt();
            loader = null;
        }
    }

    private synchronized void setPreparationCompleted() {
        this.preparationCompleted = true;

        for (Thread th: waitingThreads) {
            th.interrupt();
        }
        this.waitingThreads.clear();
    }

    private synchronized void registerWaitingThread(Thread thread) {
        if (this.preparationCompleted) {
            thread.interrupt();
            return;
        }

        waitingThreads.add(thread);
    }

    @Override
    public void close() {
        this.cancelAnyRefreshJob();
        this.contactList = null;
    }

    @Override
    public boolean isPrepared() {
        return preparationCompleted;
    }

    @Override
    public void waitUntilPrepared() {
        Thread th = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        th.start();
        registerWaitingThread(th);
        try {
            th.join();
        } catch (InterruptedException e) {
            // waitingThreads are completed by interrupt, so this is expected behavior
        }
    }

    @NonNull
    @Override
    public List<CandidateEntry> searchCandidateEntries(String query, Context context) {
        if (query == null) return new ArrayList<>();

        String rawTrimmed = query.trim();
        String low = rawTrimmed.toLowerCase();

        boolean isExplicitCommand = false;
        String contactQuery = rawTrimmed;

        if (low.startsWith("/con ") || low.startsWith("/contact ") || low.startsWith("/contacts ")) {
            isExplicitCommand = true;
            contactQuery = rawTrimmed.substring(rawTrimmed.indexOf(' ') + 1).trim();
        } else if (low.equals("/con") || low.equals("/contact") || low.equals("/contacts")) {
            isExplicitCommand = true;
            contactQuery = "";
        } else if (low.startsWith("con ") || low.startsWith("contact ") || low.startsWith("contacts ")) {
            isExplicitCommand = true;
            contactQuery = rawTrimmed.substring(rawTrimmed.indexOf(' ') + 1).trim();
        } else if (low.equals("con") || low.equals("contact") || low.equals("contacts")) {
            isExplicitCommand = true;
            contactQuery = "";
        }

        ContactManager mgr = ContactManager.getInstance();

        // If not explicit command and only command search is enabled, suppress contacts
        if (!isExplicitCommand && mgr.isOnlySearchOnCommand(context)) {
            return new ArrayList<>();
        }

        // If contacts permission not granted
        if (!ContactsReader.appHasReadContactsPermission(context)) {
            if (isExplicitCommand) {
                List<CandidateEntry> ret = new ArrayList<>();
                ret.add(new ContactPermissionPromptCandidateEntry());
                return ret;
            }
            return new ArrayList<>();
        }

        if (contactList == null) {
            waitUntilPrepared();
        }
        if (contactList == null || contactList.isEmpty()) {
            try {
                contactList = ContactsReader.fetchAllContacts(context);
            } catch (Exception ignored) {}
        }
        if (contactList == null) {
            contactList = new ArrayList<>();
        }

        List<CandidateEntry> ret = new ArrayList<>();

        // If explicit command without subquery: show Hub launcher + pinned contacts
        if (isExplicitCommand && contactQuery.isEmpty()) {
            ret.add(new ContactHubLauncherCandidateEntry());
            for (ContactsReader.Contact contact : contactList) {
                if (mgr.isPinned(context, ContactManager.getContactKey(contact))) {
                    ret.add(new ContactCandidateEntry(contact, context, true));
                    ret.add(new ContactMessageCandidateEntry(contact, context));
                }
            }
            return ret;
        }

        List<Pair<Integer, ContactsReader.Contact>> resultList = new ArrayList<>();
        for (ContactsReader.Contact contact : contactList) {
            int match = judgeQueryForContact(context, contactQuery, contact);
            if (match >= 0) {
                resultList.add(new Pair<>(match, contact));
            }
        }

        Collections.sort(resultList, (o1, o2) -> {
            boolean p1 = mgr.isPinned(context, ContactManager.getContactKey(o1.second));
            boolean p2 = mgr.isPinned(context, ContactManager.getContactKey(o2.second));
            if (p1 != p2) return p1 ? -1 : 1;
            return o1.first.compareTo(o2.first);
        });

        for (Pair<Integer, ContactsReader.Contact> contactPair : resultList) {
            ContactsReader.Contact contact = contactPair.second;
            boolean isPinned = mgr.isPinned(context, ContactManager.getContactKey(contact));
            ret.add(new ContactCandidateEntry(contact, context, isPinned));
            ret.add(new ContactMessageCandidateEntry(contact, context));

            // If contact has multiple phone numbers, add sub-items for alternate numbers
            if (contact.phoneNumbers.size() > 1) {
                for (int i = 1; i < contact.phoneNumbers.size(); i++) {
                    ret.add(new PhoneNumberCandidateEntry(contact.phoneNumbers.get(i), context));
                }
            }

            for (String emailAddress : contact.emailAddresses) {
                ret.add(new EmailCandidateEntry(emailAddress, context));
            }
        }

        return ret;
    }

    private static int judgeQueryForContact(Context context, String query, ContactsReader.Contact contact) {
        int displayNameMatch = StringMatchStrategy.match(context, query, contact.displayName, false);
        if (displayNameMatch >= 0) {
            return displayNameMatch;
        }

        int phoneticNameMatch = StringMatchStrategy.match(context, query, contact.phoneticName, false);
        if (phoneticNameMatch >= 0) {
            return phoneticNameMatch;
        }

        for (String emailAddress: contact.emailAddresses) {
            int match = StringMatchStrategy.match(context, query, emailAddress, false);
            if (match >= 0) {
                return match + 1000000;
            }
        }

        final String queryToUseForPhone = query.replace("(", "").replace(")", "").replace("-", "");
        if (queryToUseForPhone.isEmpty()) {
            return -1;
        }

        for (String phoneNumber: contact.phoneNumbers) {
            int match = StringMatchStrategy.match(context, queryToUseForPhone, phoneNumber.replace("(", "").replace(")", "").replace("-", ""), false);
            if (match >= 0) {
                return match + 2000000;
            }
        }

        return -1;
    }

    private static class ContactCandidateEntry implements CandidateEntry {
        private final ContactsReader.Contact contact;
        private final Context context;
        private final boolean isPinned;
        private final String title;

        private ContactCandidateEntry(ContactsReader.Contact contact, Context context, boolean isPinned) {
            this.contact = contact;
            this.context = context;
            this.isPinned = isPinned;

            ContactManager mgr = ContactManager.getInstance();
            String callMethod = mgr.getEffectiveCallMethod(context, contact);
            String callLabel = "Phone";
            if (ContactManager.CALL_METHOD_WHATSAPP_VOICE.equals(callMethod)) callLabel = "WhatsApp";
            else if (ContactManager.CALL_METHOD_WHATSAPP_VIDEO.equals(callMethod)) callLabel = "WA Video";
            else if (ContactManager.CALL_METHOD_TELEGRAM.equals(callMethod)) callLabel = "Telegram";

            String name = (isPinned ? "📌 " : "") + contact.displayName;
            String phone = ContactManager.getPrimaryPhoneNumber(contact);
            if (!phone.isEmpty()) {
                this.title = name + " (" + phone + ")  ➔  Call via " + callLabel;
            } else {
                this.title = name + "  ➔  Call via " + callLabel;
            }
        }

        @NonNull
        @Override
        public String getTitle() {
            return this.title;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> ContactManager.getInstance().executeCall(context, contact);
        }

        @Override
        public Drawable getIcon(Context context) {
            return ContextCompat.getDrawable(context, R.drawable.ic_call_cyber);
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return false;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }

    private static class ContactMessageCandidateEntry implements CandidateEntry {
        private final ContactsReader.Contact contact;
        private final Context context;
        private final String title;

        private ContactMessageCandidateEntry(ContactsReader.Contact contact, Context context) {
            this.contact = contact;
            this.context = context;

            ContactManager mgr = ContactManager.getInstance();
            String msgMethod = mgr.getEffectiveMsgMethod(context, contact);
            String msgLabel = "WhatsApp";
            if (ContactManager.MSG_METHOD_TELEGRAM.equals(msgMethod)) msgLabel = "Telegram";
            else if (ContactManager.MSG_METHOD_SMS.equals(msgMethod)) msgLabel = "SMS";

            this.title = "💬 Message " + contact.displayName + " via " + msgLabel;
        }

        @NonNull
        @Override
        public String getTitle() {
            return this.title;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> ContactManager.getInstance().executeMessage(context, contact, "");
        }

        @Override
        public Drawable getIcon(Context context) {
            return ContextCompat.getDrawable(context, R.drawable.ic_message_cyber);
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return true;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }

    private static class ContactHubLauncherCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📇 Open Contact Hub (Manage, Pin & Default Apps)";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> activity.startActivity(new Intent(activity, ContactManagerActivity.class));
        }

        @Override
        public Drawable getIcon(Context context) {
            return ContextCompat.getDrawable(context, R.drawable.ic_settings_cyber);
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return false;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }

    private static class ContactPermissionPromptCandidateEntry implements CandidateEntry {
        @NonNull
        @Override
        public String getTitle() {
            return "📇 Grant Contacts Permission to Use Contact Hub";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return false;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> activity.startActivity(new Intent(activity, ContactManagerActivity.class));
        }

        @Override
        public Drawable getIcon(Context context) {
            return ContextCompat.getDrawable(context, R.drawable.ic_contact_cyber);
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return false;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }

    private static class PhoneNumberCandidateEntry implements CandidateEntry {
        private final String phoneNumber;
        private final String title;

        private PhoneNumberCandidateEntry(String phoneNumber, Context context) {
            this.phoneNumber = phoneNumber;
            this.title = String.format(context.getString(R.string.contacts_action_dial_phone_number), this.phoneNumber);
        }

        @NonNull
        @Override
        public String getTitle() {
            return this.title;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(PhoneNumberCandidateEntry.this.phoneNumber)));
                activity.startActivity(intent);
            };
        }

        @Override
        public Drawable getIcon(Context context) {
            return null;
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return true;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }

    private static class EmailCandidateEntry implements CandidateEntry {
        private final String emailAddresses;
        private final String title;

        private EmailCandidateEntry(String emailAddress, Context context) {
            this.emailAddresses = emailAddress;
            this.title = String.format(context.getString(R.string.contacts_action_email), this.emailAddresses);
        }

        @NonNull
        @Override
        public String getTitle() {
            return this.title;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            return null;
        }

        @Override
        public boolean hasLongView() {
            return true;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(EmailCandidateEntry.this.emailAddresses)));
                activity.startActivity(intent);
            };
        }

        @Override
        public Drawable getIcon(Context context) {
            return null;
        }

        @Override
        public boolean hasEvent() {
            return true;
        }

        @Override
        public boolean isSubItem() {
            return true;
        }

        @Override
        public boolean viewIsRecyclable() {
            return true;
        }
    }
}
