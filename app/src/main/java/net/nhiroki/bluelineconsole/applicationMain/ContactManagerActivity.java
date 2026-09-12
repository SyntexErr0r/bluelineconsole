package net.nhiroki.bluelineconsole.applicationMain;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.contacts.ContactDialogHelper;
import net.nhiroki.bluelineconsole.contacts.ContactManager;
import net.nhiroki.bluelineconsole.wrapperForAndroid.ContactsReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactManagerActivity extends BaseWindowActivity {

    private static final int REQ_READ_CONTACTS = 101;

    private TextView mTvTopStatusSummary;
    private EditText mSearchEdit;
    private ListView mContactListView;
    private ContactListAdapter mAdapter;
    private View mFastScrollContainer;
    private View mFastScrollThumb;
    private View mLoadingContainer;
    private boolean mIsDraggingFastScroll = false;

    private final List<ContactsReader.Contact> mAllContacts = new ArrayList<>();
    private final List<ContactsReader.Contact> mFilteredContacts = new ArrayList<>();

    public ContactManagerActivity() {
        super(R.layout.contact_manager_activity, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHeaderFooterTexts("📇 CONTACT HUB", null);
        this.setWindowBoundarySize(ROOT_WINDOW_FULL_WIDTH_IN_MOBILE, 3);

        this.changeBaseWindowElementSizeForAnimation(false);
        this.enableBaseWindowAnimation();

        initViews();
        loadContactsAsync();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.changeBaseWindowElementSizeForAnimation(true);
        if (hasFocus && mContactListView != null) {
            mContactListView.post(this::refreshFastScrollState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTopSummary();
        if (mContactListView != null) {
            mContactListView.post(this::refreshFastScrollState);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.finish();
    }

    private void initViews() {
        View btnOpenSettings = findViewById(R.id.contactBtnOpenSettings);
        btnOpenSettings.setOnClickListener(v -> ContactDialogHelper.showGlobalSettingsDialog(this, () -> {
            refreshTopSummary();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        }));

        mTvTopStatusSummary = findViewById(R.id.contactTopStatusSummary);

        mLoadingContainer = findViewById(R.id.contactLoadingContainer);
        ProgressBar pb = findViewById(R.id.contactLoadingProgress);
        if (pb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pb.setIndeterminateTintList(ColorStateList.valueOf(0xff00f0ff));
            pb.setIndeterminateTintMode(PorterDuff.Mode.SRC_IN);
        }

        mSearchEdit = findViewById(R.id.contactSearchEdit);
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mContactListView = findViewById(R.id.contactListView);
        mAdapter = new ContactListAdapter(this, mFilteredContacts);
        mContactListView.setAdapter(mAdapter);

        mContactListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < mFilteredContacts.size()) {
                ContactsReader.Contact contact = mFilteredContacts.get(position);
                ContactDialogHelper.showContactEditDialog(this, contact, () -> {
                    sortAndRefreshContacts();
                    refreshTopSummary();
                });
            }
        });

        mFastScrollContainer = findViewById(R.id.contactFastScrollContainer);
        mFastScrollThumb = findViewById(R.id.contactFastScrollThumb);

        if (mFastScrollContainer != null && mFastScrollThumb != null) {
            mContactListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {}

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (!mIsDraggingFastScroll) {
                        updateFastScrollThumbPosition(firstVisibleItem, visibleItemCount, totalItemCount);
                    }
                }
            });

            mFastScrollContainer.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        mIsDraggingFastScroll = true;
                        float y = event.getY();
                        int containerHeight = mFastScrollContainer.getHeight();
                        int thumbHeight = mFastScrollThumb.getHeight();
                        int trackHeight = containerHeight - thumbHeight;
                        if (trackHeight > 0) {
                            float clampedY = Math.max(0, Math.min(y - thumbHeight / 2.0f, trackHeight));
                            mFastScrollThumb.setTranslationY(clampedY);
                            float ratio = clampedY / (float) trackHeight;
                            int targetPos = Math.round(ratio * (mAdapter.getCount() - 1));
                            mContactListView.setSelection(Math.max(0, Math.min(targetPos, mAdapter.getCount() - 1)));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mIsDraggingFastScroll = false;
                        refreshFastScrollState();
                        return true;
                }
                return false;
            });
        }
    }

    private void updateFastScrollThumbPosition(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mFastScrollContainer == null || mFastScrollThumb == null) return;
        if (totalItemCount <= visibleItemCount || totalItemCount == 0) {
            mFastScrollContainer.setVisibility(View.GONE);
            return;
        }
        mFastScrollContainer.setVisibility(View.VISIBLE);
        int containerHeight = mFastScrollContainer.getHeight();
        int thumbHeight = mFastScrollThumb.getHeight();
        int trackHeight = containerHeight - thumbHeight;
        if (trackHeight > 0) {
            float ratio = (float) firstVisibleItem / (float) (totalItemCount - visibleItemCount);
            mFastScrollThumb.setTranslationY(ratio * trackHeight);
        }
    }

    private void refreshFastScrollState() {
        if (mContactListView != null) {
            int total = mContactListView.getCount();
            int visible = mContactListView.getLastVisiblePosition() - mContactListView.getFirstVisiblePosition() + 1;
            updateFastScrollThumbPosition(mContactListView.getFirstVisiblePosition(), visible, total);
        }
    }

    private void refreshTopSummary() {
        if (mTvTopStatusSummary == null) return;
        ContactManager mgr = ContactManager.getInstance();
        int total = mAllContacts.size();
        int pinned = mgr.getPinnedKeys(this).size();
        String callMethod = mgr.getGlobalCallMethod(this);
        String callDisplay = "Phone";
        if (ContactManager.CALL_METHOD_WHATSAPP_VOICE.equals(callMethod)) callDisplay = "WhatsApp";
        else if (ContactManager.CALL_METHOD_WHATSAPP_VIDEO.equals(callMethod)) callDisplay = "WA Video";
        else if (ContactManager.CALL_METHOD_TELEGRAM.equals(callMethod)) callDisplay = "Telegram";

        mTvTopStatusSummary.setText(String.format("Contacts: %d | 📌 %d | Call: %s", total, pinned, callDisplay));
    }

    private void loadContactsAsync() {
        List<String> perms = new ArrayList<>();
        if (!ContactsReader.appHasReadContactsPermission(this)) {
            perms.add(Manifest.permission.READ_CONTACTS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CALL_PHONE);
        }
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), REQ_READ_CONTACTS);
            return;
        }

        new Thread(() -> {
            List<ContactsReader.Contact> list = new ArrayList<>();
            try {
                list = ContactsReader.fetchAllContacts(this);
            } catch (Exception e) {
                list = new ArrayList<>();
            }

            final List<ContactsReader.Contact> loadedList = list;
            runOnUiThread(() -> {
                mAllContacts.clear();
                mAllContacts.addAll(loadedList);
                sortAndRefreshContacts();
                if (mLoadingContainer != null) mLoadingContainer.setVisibility(View.GONE);
                if (mContactListView != null) mContactListView.setVisibility(View.VISIBLE);
                refreshTopSummary();
                refreshFastScrollState();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_CONTACTS) {
            if (ContactsReader.appHasReadContactsPermission(this)) {
                loadContactsAsync();
            } else {
                Toast.makeText(this, "Contacts permission required to manage contacts", Toast.LENGTH_LONG).show();
                if (mLoadingContainer != null) mLoadingContainer.setVisibility(View.GONE);
            }
        }
    }

    private void sortAndRefreshContacts() {
        ContactManager mgr = ContactManager.getInstance();
        Collections.sort(mAllContacts, (a, b) -> {
            boolean aPinned = mgr.isPinned(this, ContactManager.getContactKey(a));
            boolean bPinned = mgr.isPinned(this, ContactManager.getContactKey(b));
            if (aPinned != bPinned) {
                return aPinned ? -1 : 1;
            }
            return a.displayName.compareToIgnoreCase(b.displayName);
        });
        filterContacts(mSearchEdit != null ? mSearchEdit.getText().toString() : "");
    }

    private void filterContacts(String query) {
        mFilteredContacts.clear();
        if (query == null || query.trim().isEmpty()) {
            mFilteredContacts.addAll(mAllContacts);
        } else {
            String lower = query.trim().toLowerCase();
            String cleanPhoneQuery = ContactManager.cleanPhoneNumber(lower);
            for (ContactsReader.Contact contact : mAllContacts) {
                boolean match = contact.displayName.toLowerCase().contains(lower) ||
                                contact.phoneticName.toLowerCase().contains(lower);
                if (!match && !cleanPhoneQuery.isEmpty()) {
                    for (String p : contact.phoneNumbers) {
                        if (ContactManager.cleanPhoneNumber(p).contains(cleanPhoneQuery)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match) {
                    for (String email : contact.emailAddresses) {
                        if (email.toLowerCase().contains(lower)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) {
                    mFilteredContacts.add(contact);
                }
            }
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        refreshFastScrollState();
    }

    private class ContactListAdapter extends BaseAdapter {
        private final Context mContext;
        private final List<ContactsReader.Contact> mContacts;

        public ContactListAdapter(Context context, List<ContactsReader.Contact> contacts) {
            this.mContext = context;
            this.mContacts = contacts;
        }

        @Override
        public int getCount() {
            return mContacts.size();
        }

        @Override
        public ContactsReader.Contact getItem(int position) {
            return mContacts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.contact_manager_item, parent, false);
            }

            ContactsReader.Contact contact = getItem(position);
            TextView avatarText = convertView.findViewById(R.id.contactItemAvatarText);
            TextView titleView = convertView.findViewById(R.id.contactItemTitle);
            TextView subtitleView = convertView.findViewById(R.id.contactItemSubtitle);
            ImageView btnCall = convertView.findViewById(R.id.contactItemBtnCall);
            ImageView btnMsg = convertView.findViewById(R.id.contactItemBtnMessage);
            ImageView btnPin = convertView.findViewById(R.id.contactItemBtnPin);
            ImageView btnEdit = convertView.findViewById(R.id.contactItemBtnEdit);

            String name = contact.displayName != null && !contact.displayName.isEmpty() ? contact.displayName : "Unknown";
            String initial = name.substring(0, 1).toUpperCase();
            avatarText.setText(initial);

            ContactManager mgr = ContactManager.getInstance();
            String key = ContactManager.getContactKey(contact);
            boolean isPinned = mgr.isPinned(mContext, key);

            titleView.setText(isPinned ? "📌 " + name : name);

            String phone = ContactManager.getPrimaryPhoneNumber(contact);
            String callMethod = mgr.getEffectiveCallMethod(mContext, contact);
            String msgMethod = mgr.getEffectiveMsgMethod(mContext, contact);

            String callLabel = "Phone";
            if (ContactManager.CALL_METHOD_WHATSAPP_VOICE.equals(callMethod)) callLabel = "WhatsApp";
            else if (ContactManager.CALL_METHOD_WHATSAPP_VIDEO.equals(callMethod)) callLabel = "WA Video";
            else if (ContactManager.CALL_METHOD_TELEGRAM.equals(callMethod)) callLabel = "Telegram";

            String msgLabel = "WA";
            if (ContactManager.MSG_METHOD_SMS.equals(msgMethod)) msgLabel = "SMS";
            else if (ContactManager.MSG_METHOD_TELEGRAM.equals(msgMethod)) msgLabel = "TG";

            StringBuilder sub = new StringBuilder();
            if (!phone.isEmpty()) sub.append(phone).append(" • ");
            sub.append("📞 ").append(callLabel).append(" | 💬 ").append(msgLabel);
            subtitleView.setText(sub.toString());

            btnCall.setOnClickListener(v -> mgr.executeCall(mContext, contact));
            btnMsg.setOnClickListener(v -> mgr.executeMessage(mContext, contact, ""));

            btnPin.setColorFilter(isPinned ? 0xff00f0ff : 0x6600f0ff);
            btnPin.setOnClickListener(v -> {
                boolean newPinned = !mgr.isPinned(mContext, key);
                mgr.setPinned(mContext, key, newPinned);
                Toast.makeText(mContext, (newPinned ? "Pinned " : "Unpinned ") + name, Toast.LENGTH_SHORT).show();
                sortAndRefreshContacts();
                refreshTopSummary();
            });

            btnEdit.setOnClickListener(v -> ContactDialogHelper.showContactEditDialog(mContext, contact, () -> {
                sortAndRefreshContacts();
                refreshTopSummary();
            }));

            return convertView;
        }
    }
}
