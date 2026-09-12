package net.nhiroki.bluelineconsole.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

import java.util.ArrayList;
import java.util.List;

public class BlueLineAgentService extends AccessibilityService {
    private static BlueLineAgentService sInstance = null;

    public static BlueLineAgentService getInstance() {
        return sInstance;
    }

    public static boolean isServiceConnected() {
        return sInstance != null;
    }

    public static boolean isAccessibilityEnabled(Context context) {
        if (sInstance != null) return true;
        int accessibilityEnabled = 0;
        final String service = context.getPackageName() + "/" + BlueLineAgentService.class.getName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {}

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString(settingValue);
                while (colonSplitter.hasNext()) {
                    String accessibilityService = colonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service) || accessibilityService.contains("BlueLineAgentService")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        AppLogger.i("A11Y", "Accessibility service connected");
    }

    private volatile boolean mPendingWhatsAppAutoSend = false;
    private volatile long mAutoSendDeadline = 0;
    private volatile boolean mPendingWhatsAppCall = false;
    private volatile boolean mPendingWhatsAppCallIsVideo = false;
    private volatile long mCallDeadline = 0;
    private volatile boolean mPendingWhatsAppGroupSend = false;
    private volatile boolean mPendingWhatsAppGroupOpen = false;
    private volatile String mTargetGroupName = null;
    private volatile long mGroupDeadline = 0;
    private volatile long mGroupStartTime = 0;
    private volatile long mLastSearchClickTime = 0;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        AppLogger.i("A11Y", "Accessibility service disconnected/destroyed");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null) {
            int eventType = event.getEventType();
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                CharSequence pkgChar = event.getPackageName();
                if (pkgChar != null) {
                    net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance().onWindowStateChanged(this, pkgChar.toString());
                }
            }
        }

        if (mPendingWhatsAppAutoSend) {
            performWhatsAppAutoSend();
        }
        if (mPendingWhatsAppCall) {
            performWhatsAppCallClick();
        }
        if (mPendingWhatsAppGroupSend) {
            performWhatsAppGroupSend();
        }
        if (mPendingWhatsAppGroupOpen) {
            performWhatsAppGroupOpen();
        }
    }

    @Override
    public void onInterrupt() {
    }

    public static boolean isSafeWindow(AccessibilityNodeInfo root) {
        if (root == null) return false;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String p = pkg.toString().toLowerCase();
        if (p.equals("com.android.systemui") ||
            p.equals("android") ||
            p.contains("launcher") ||
            p.contains("recents") ||
            p.contains("quickstep") ||
            p.contains("systemui") ||
            p.equals("net.nhiroki.bluelineconsole") ||
            p.equals("net.nhiroki.bluelineconsole.beta")) {
            AppLogger.w("A11Y", "Blocked interaction with unsafe system/launcher window: " + p);
            return false;
        }
        return true;
    }

    public boolean clickByText(String targetText, boolean exact) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        AppLogger.i("A11Y", "clickByText: looking for '" + targetText + "' (exact=" + exact + ")");
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(targetText);
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node == null) continue;
                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                boolean match = false;
                if (exact) {
                    match = (text != null && text.toString().equalsIgnoreCase(targetText)) ||
                            (desc != null && desc.toString().equalsIgnoreCase(targetText));
                } else {
                    match = (text != null && text.toString().toLowerCase().contains(targetText.toLowerCase())) ||
                            (desc != null && desc.toString().toLowerCase().contains(targetText.toLowerCase()));
                }
                if (match) {
                    if (performClickOnNode(node)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean clickById(String viewId) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClickOnNode(node)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean typeText(String textToType) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        AppLogger.i("A11Y", "typeText: typing '" + textToType + "'");
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            boolean done = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            AppLogger.i("A11Y", "typeText on focused view: " + (done ? "SUCCESS" : "FAILED"));
            return done;
        }

        AccessibilityNodeInfo editable = findFirstEditableNode(root);
        if (editable != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            boolean done = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            AppLogger.i("A11Y", "typeText on first editable node: " + (done ? "SUCCESS" : "FAILED"));
            return done;
        }
        AppLogger.w("A11Y", "typeText: no editable node found on screen");
        return false;
    }

    public boolean performInAppSearch(final String query) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;

        // 1. If an editable input is already visible or focused, type into it directly
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
            boolean typed = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (typed) {
                return true;
            }
        }

        // 2. Look for search icon, search button, or search view
        AccessibilityNodeInfo searchNode = findSearchNode(root);
        if (searchNode != null) {
            if (searchNode.isEditable()) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
                return searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            } else {
                boolean clicked = performClickOnNode(searchNode);
                if (clicked) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        typeText(query);
                    }, 400);
                    return true;
                }
            }
        }

        // 3. Fallback to any editable node
        AccessibilityNodeInfo editable = findFirstEditableNode(root);
        if (editable != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
            return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
        return false;
    }

    private AccessibilityNodeInfo findSearchNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        String viewId = node.getViewIdResourceName();

        if (desc != null && desc.toString().toLowerCase().contains("search")) {
            return node;
        }
        if (text != null && text.toString().toLowerCase().contains("search")) {
            return node;
        }
        if (viewId != null && viewId.toLowerCase().contains("search")) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findSearchNode(child);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findFirstEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo res = findFirstEditableNode(child);
            if (res != null) return res;
        }
        return null;
    }

    public boolean performClickOnNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            current = current.getParent();
        }
        return false;
    }

    public boolean tapAt(float x, float y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        if (Build.VERSION.SDK_INT >= 24) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            return dispatchGesture(builder.build(), null, null);
        }
        return false;
    }

    public boolean scroll(boolean forward) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        AccessibilityNodeInfo scrollable = findFirstScrollableNode(root);
        if (scrollable != null) {
            return scrollable.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        return false;
    }

    private AccessibilityNodeInfo findFirstScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo res = findFirstScrollableNode(child);
            if (res != null) return res;
        }
        return null;
    }

    public boolean pressBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean pressHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public boolean pressRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public void scheduleWhatsAppAutoSend() {
        mPendingWhatsAppAutoSend = true;
        mAutoSendDeadline = System.currentTimeMillis() + 8000;
        AppLogger.i("A11Y", "Scheduled WhatsApp auto-send (8s deadline)");
        schedulePollingChecks();
    }

    public void scheduleWhatsAppCallClick(boolean isVideo) {
        mPendingWhatsAppCall = true;
        mPendingWhatsAppCallIsVideo = isVideo;
        mCallDeadline = System.currentTimeMillis() + 8000;
        AppLogger.i("A11Y", "Scheduled WhatsApp " + (isVideo ? "video" : "voice") + " call click (8s deadline)");
        schedulePollingChecks();
    }

    private void schedulePollingChecks() {
        int[] delays = {350, 700, 1100, 1600, 2300, 3200, 4500};
        for (int delay : delays) {
            mMainHandler.postDelayed(() -> {
                if (mPendingWhatsAppAutoSend) {
                    performWhatsAppAutoSend();
                }
                if (mPendingWhatsAppCall) {
                    performWhatsAppCallClick();
                }
                if (mPendingWhatsAppGroupSend) {
                    performWhatsAppGroupSend();
                }
                if (mPendingWhatsAppGroupOpen) {
                    performWhatsAppGroupOpen();
                }
            }, delay);
        }
    }

    public void scheduleWhatsAppGroupSend(String groupName) {
        mPendingWhatsAppGroupSend = true;
        mTargetGroupName = groupName != null ? groupName.trim() : "";
        mGroupStartTime = System.currentTimeMillis();
        mLastSearchClickTime = 0;
        mGroupDeadline = System.currentTimeMillis() + 9000;
        AppLogger.i("A11Y", "Scheduled WhatsApp group send for '" + mTargetGroupName + "' (9s deadline)");
        schedulePollingChecks();
    }

    public void scheduleWhatsAppGroupOpen(String groupName) {
        mPendingWhatsAppGroupOpen = true;
        mTargetGroupName = groupName != null ? groupName.trim() : "";
        mGroupStartTime = System.currentTimeMillis();
        mLastSearchClickTime = 0;
        mGroupDeadline = System.currentTimeMillis() + 9000;
        AppLogger.i("A11Y", "Scheduled WhatsApp group open for '" + mTargetGroupName + "' (9s deadline)");
        schedulePollingChecks();
    }

    public synchronized boolean performWhatsAppAutoSend() {
        if (!mPendingWhatsAppAutoSend) return false;
        if (System.currentTimeMillis() > mAutoSendDeadline) {
            AppLogger.w("A11Y", "WhatsApp auto-send timed out");
            mPendingWhatsAppAutoSend = false;
            return false;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String pkgStr = pkg.toString().toLowerCase();
        if (!pkgStr.equals("com.whatsapp") && !pkgStr.equals("com.whatsapp.w4b")) {
            return false;
        }

        // 1. Search for send button by ID
        String[] sendIds = {
                "com.whatsapp:id/send",
                "com.whatsapp.w4b:id/send"
        };
        for (String id : sendIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && node.isVisibleToUser()) {
                        if (performClickOnNode(node)) {
                            AppLogger.i("A11Y", "WhatsApp auto-send: clicked send button by id '" + id + "'");
                            mPendingWhatsAppAutoSend = false;
                            return true;
                        }
                    }
                }
            }
        }

        // 2. Search for send button by content description "Send"
        List<AccessibilityNodeInfo> descNodes = root.findAccessibilityNodeInfosByText("Send");
        if (descNodes != null && !descNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : descNodes) {
                if (node != null && node.isVisibleToUser()) {
                    CharSequence cd = node.getContentDescription();
                    if (cd != null && cd.toString().equalsIgnoreCase("Send")) {
                        if (performClickOnNode(node)) {
                            AppLogger.i("A11Y", "WhatsApp auto-send: clicked send button by contentDescription 'Send'");
                            mPendingWhatsAppAutoSend = false;
                            return true;
                        }
                    }
                }
            }
        }

        // 3. Search any node ending with ":id/send"
        AccessibilityNodeInfo sendNode = findNodeEndingWithId(root, ":id/send");
        if (sendNode != null && sendNode.isVisibleToUser()) {
            if (performClickOnNode(sendNode)) {
                AppLogger.i("A11Y", "WhatsApp auto-send: clicked send button by suffix ':id/send'");
                mPendingWhatsAppAutoSend = false;
                return true;
            }
        }

        return false;
    }

    private boolean isVideoNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        String resId = node.getViewIdResourceName();
        if (resId != null && resId.toLowerCase().contains("video")) return true;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains("video")) return true;
        CharSequence text = node.getText();
        if (text != null && text.toString().toLowerCase().contains("video")) return true;
        return false;
    }

    private AccessibilityNodeInfo findCallNodeByDesc(AccessibilityNodeInfo node, boolean isVideo) {
        if (node == null) return null;
        if (isVideo) {
            if (isVideoNode(node) && node.isClickable() && node.isVisibleToUser()) {
                return node;
            }
        } else {
            if (!isVideoNode(node) && node.isVisibleToUser()) {
                CharSequence desc = node.getContentDescription();
                if (desc != null) {
                    String d = desc.toString().trim().toLowerCase();
                    if (d.equals("voice call") || d.equals("call") || d.equals("audio call") || d.contains("voice call")) {
                        return node;
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findCallNodeByDesc(child, isVideo);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findWhatsAppCallButton(AccessibilityNodeInfo root, boolean isVideo) {
        if (root == null) return null;
        if (isVideo) {
            AccessibilityNodeInfo node = findNodeEndingWithId(root, ":id/menuitem_video_call");
            if (node != null && node.isVisibleToUser()) return node;
            node = findNodeEndingWithId(root, ":id/video_call");
            if (node != null && node.isVisibleToUser()) return node;
            return findCallNodeByDesc(root, true);
        } else {
            // Voice call: explicitly exclude any video element
            AccessibilityNodeInfo node = findNodeEndingWithId(root, ":id/menuitem_call");
            if (node != null && node.isVisibleToUser() && !isVideoNode(node)) return node;
            node = findNodeEndingWithId(root, ":id/voice_call");
            if (node != null && node.isVisibleToUser() && !isVideoNode(node)) return node;
            node = findNodeEndingWithId(root, ":id/audio_call");
            if (node != null && node.isVisibleToUser() && !isVideoNode(node)) return node;
            node = findNodeEndingWithId(root, ":id/call");
            if (node != null && node.isVisibleToUser() && !isVideoNode(node)) return node;
            return findCallNodeByDesc(root, false);
        }
    }

    private AccessibilityNodeInfo findDialogCallButton(AccessibilityNodeInfo root, boolean isVideo) {
        if (root == null) return null;
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        List<AccessibilityNodeInfo> callNodes = root.findAccessibilityNodeInfosByText("CALL");
        if (callNodes != null) candidates.addAll(callNodes);
        List<AccessibilityNodeInfo> callNodes2 = root.findAccessibilityNodeInfosByText("Call");
        if (callNodes2 != null) candidates.addAll(callNodes2);

        for (AccessibilityNodeInfo btn : candidates) {
            if (btn == null || !btn.isVisibleToUser()) continue;
            CharSequence cls = btn.getClassName();
            String id = btn.getViewIdResourceName();
            if (id != null && (id.contains("menuitem") || id.contains("action_bar") || id.contains("toolbar"))) {
                continue; // Skip action bar items
            }
            if (cls != null && (cls.toString().contains("ImageView") || cls.toString().contains("ImageButton"))) {
                continue; // Skip image buttons
            }
            CharSequence txt = btn.getText();
            if (txt != null) {
                String tStr = txt.toString().trim();
                if (tStr.equalsIgnoreCase("CALL") || tStr.equalsIgnoreCase("Call") || tStr.equalsIgnoreCase("Voice Call") || tStr.equalsIgnoreCase("Video Call")) {
                    if (!isVideo && tStr.toLowerCase().contains("video")) {
                        continue;
                    }
                    return btn;
                }
            }
        }
        return null;
    }

    public synchronized boolean performWhatsAppCallClick() {
        if (!mPendingWhatsAppCall) return false;
        if (System.currentTimeMillis() > mCallDeadline) {
            AppLogger.w("A11Y", "WhatsApp call click timed out");
            mPendingWhatsAppCall = false;
            return false;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String pkgStr = pkg.toString().toLowerCase();
        if (!pkgStr.equals("com.whatsapp") && !pkgStr.equals("com.whatsapp.w4b")) {
            return false;
        }

        boolean isVideo = mPendingWhatsAppCallIsVideo;

        // 1. Check if a confirmation dialog button is already showing
        AccessibilityNodeInfo dialogBtn = findDialogCallButton(root, isVideo);
        if (dialogBtn != null && performClickOnNode(dialogBtn)) {
            AppLogger.i("A11Y", "WhatsApp call: confirmed dialog button");
            mPendingWhatsAppCall = false;
            return true;
        }

        // 2. Look for call icon in action bar (voice or video)
        AccessibilityNodeInfo callNode = findWhatsAppCallButton(root, isVideo);
        if (callNode != null && callNode.isVisibleToUser()) {
            if (performClickOnNode(callNode)) {
                AppLogger.i("A11Y", "WhatsApp call: clicked " + (isVideo ? "video" : "voice") + " call button");
                mMainHandler.postDelayed(() -> dismissCallConfirmationDialogIfAny(isVideo), 350);
                mPendingWhatsAppCall = false;
                return true;
            }
        }

        return false;
    }

    private void dismissCallConfirmationDialogIfAny(boolean isVideo) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo dialogBtn = findDialogCallButton(root, isVideo);
        if (dialogBtn != null && performClickOnNode(dialogBtn)) {
            AppLogger.i("A11Y", "WhatsApp call dialog auto-confirmed");
        }
    }

    private AccessibilityNodeInfo findNodeEndingWithId(AccessibilityNodeInfo node, String idSuffix) {
        if (node == null) return null;
        String resId = node.getViewIdResourceName();
        if (resId != null && resId.endsWith(idSuffix)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findNodeEndingWithId(child, idSuffix);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeWithDescriptionContains(AccessibilityNodeInfo node, String descSub) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(descSub.toLowerCase())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findNodeWithDescriptionContains(child, descSub);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isAvatarOrPhotoNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence cls = node.getClassName();
        if (cls != null) {
            String cStr = cls.toString();
            if (cStr.contains("ImageView") || cStr.contains("ImageButton")) {
                return true;
            }
        }
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String vLow = viewId.toLowerCase();
            if (vLow.contains("photo") || vLow.contains("avatar") || vLow.contains("picture") || vLow.contains("profile_pic") || vLow.contains("icon")) {
                return true;
            }
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String dLow = desc.toString().toLowerCase();
            if (dLow.contains("profile photo") || dLow.contains("profile picture") || dLow.contains("view photo") || dLow.contains("avatar")) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findClickableRowAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (isAvatarOrPhotoNode(current)) {
                return null;
            }
            if (current.isClickable()) {
                CharSequence cls = current.getClassName();
                if (cls != null && (cls.toString().contains("ImageView") || cls.toString().contains("ImageButton"))) {
                    current = current.getParent();
                    continue;
                }
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public boolean clickChatRowByText(String targetText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
        if (targetText == null || targetText.trim().isEmpty()) return false;
        String q = targetText.trim().toLowerCase();
        AppLogger.i("A11Y", "clickChatRowByText: looking for '" + q + "'");

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(targetText);
        if (nodes == null || nodes.isEmpty()) return false;

        // Pass 1: exact match on TextView / text
        for (AccessibilityNodeInfo node : nodes) {
            if (node == null) continue;
            if (isAvatarOrPhotoNode(node)) continue;
            if (node.isEditable()) continue;
            CharSequence cls = node.getClassName();
            if (cls != null && cls.toString().contains("EditText")) continue;

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String tStr = text != null ? text.toString().trim() : "";
            String dStr = desc != null ? desc.toString().trim() : "";

            if (tStr.equalsIgnoreCase(targetText) || dStr.equalsIgnoreCase(targetText)) {
                AccessibilityNodeInfo row = findClickableRowAncestor(node);
                if (row != null && row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLogger.i("A11Y", "clickChatRowByText (exact): clicked row ancestor for '" + targetText + "'");
                    return true;
                }
                if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLogger.i("A11Y", "clickChatRowByText (exact): clicked node directly for '" + targetText + "'");
                    return true;
                }
            }
        }

        // Pass 2: case-insensitive contains match
        for (AccessibilityNodeInfo node : nodes) {
            if (node == null) continue;
            if (isAvatarOrPhotoNode(node)) continue;
            if (node.isEditable()) continue;
            CharSequence cls = node.getClassName();
            if (cls != null && cls.toString().contains("EditText")) continue;

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String tStr = text != null ? text.toString().trim().toLowerCase() : "";
            String dStr = desc != null ? desc.toString().trim().toLowerCase() : "";

            if (tStr.contains(q) || dStr.contains(q)) {
                AccessibilityNodeInfo row = findClickableRowAncestor(node);
                if (row != null && row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLogger.i("A11Y", "clickChatRowByText (contains): clicked row ancestor for '" + targetText + "'");
                    return true;
                }
                if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AppLogger.i("A11Y", "clickChatRowByText (contains): clicked node directly for '" + targetText + "'");
                    return true;
                }
            }
        }

        return false;
    }

    private AccessibilityNodeInfo findWhatsAppSearchInput(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo byId = findNodeEndingWithId(root, ":id/search_src_text");
        if (byId != null && byId.isEditable()) return byId;
        byId = findNodeEndingWithId(root, ":id/search_input");
        if (byId != null && byId.isEditable()) return byId;
        return findFirstEditableNode(root);
    }

    private AccessibilityNodeInfo findWhatsAppSearchButton(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo byId = findNodeEndingWithId(root, ":id/menuitem_search");
        if (byId != null && byId.isVisibleToUser()) return byId;
        return findSearchNode(root);
    }

    public synchronized boolean performWhatsAppGroupSend() {
        if (!mPendingWhatsAppGroupSend || mTargetGroupName == null || mTargetGroupName.isEmpty()) return false;
        if (System.currentTimeMillis() > mGroupDeadline) {
            AppLogger.w("A11Y", "WhatsApp group send timed out for: " + mTargetGroupName);
            mPendingWhatsAppGroupSend = false;
            mTargetGroupName = null;
            return false;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String pkgStr = pkg.toString().toLowerCase();
        if (!pkgStr.equals("com.whatsapp") && !pkgStr.equals("com.whatsapp.w4b")) {
            return false;
        }

        // 1. Try to click matching group row in the picker list
        boolean clicked = clickChatRowByText(mTargetGroupName);
        if (clicked) {
            AppLogger.i("A11Y", "WhatsApp group send: selected group row for '" + mTargetGroupName + "'");
            mMainHandler.postDelayed(() -> {
                AccessibilityNodeInfo curRoot = getRootInActiveWindow();
                if (curRoot != null) {
                    AccessibilityNodeInfo fab = findNodeEndingWithId(curRoot, ":id/fab");
                    if (fab == null) {
                        fab = findNodeEndingWithId(curRoot, ":id/send");
                    }
                    if (fab != null && fab.isVisibleToUser() && performClickOnNode(fab)) {
                        AppLogger.i("A11Y", "WhatsApp group send: clicked FAB");
                        mMainHandler.postDelayed(this::performWhatsAppAutoSend, 450);
                    } else {
                        performWhatsAppAutoSend();
                    }
                }
            }, 350);
            mPendingWhatsAppGroupSend = false;
            mTargetGroupName = null;
            return true;
        }

        // 2. If group was already selected and FAB is visible, click FAB
        AccessibilityNodeInfo fab = findNodeEndingWithId(root, ":id/fab");
        if (fab == null) {
            fab = findNodeEndingWithId(root, ":id/send");
        }
        if (fab != null && fab.isVisibleToUser()) {
            if (performClickOnNode(fab)) {
                AppLogger.i("A11Y", "WhatsApp group send: clicked FAB directly");
                mMainHandler.postDelayed(this::performWhatsAppAutoSend, 450);
                mPendingWhatsAppGroupSend = false;
                mTargetGroupName = null;
                return true;
            }
        }

        // 3. If group not found in visible list, use search after 1000ms
        long elapsed = System.currentTimeMillis() - mGroupStartTime;
        if (elapsed >= 1000) {
            AccessibilityNodeInfo searchInput = findWhatsAppSearchInput(root);
            if (searchInput != null) {
                CharSequence currentText = searchInput.getText();
                String currentStr = currentText != null ? currentText.toString() : "";
                if (!currentStr.equalsIgnoreCase(mTargetGroupName)) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mTargetGroupName);
                    boolean set = searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    AppLogger.i("A11Y", "WhatsApp group send: typed '" + mTargetGroupName + "' into search bar: " + (set ? "SUCCESS" : "FAILED"));
                }
            } else {
                if (System.currentTimeMillis() - mLastSearchClickTime > 2000) {
                    AccessibilityNodeInfo searchBtn = findWhatsAppSearchButton(root);
                    if (searchBtn != null && performClickOnNode(searchBtn)) {
                        mLastSearchClickTime = System.currentTimeMillis();
                        AppLogger.i("A11Y", "WhatsApp group send: clicked search button");
                    }
                }
            }
        }

        return false;
    }

    public synchronized boolean performWhatsAppGroupOpen() {
        if (!mPendingWhatsAppGroupOpen || mTargetGroupName == null || mTargetGroupName.isEmpty()) return false;
        if (System.currentTimeMillis() > mGroupDeadline) {
            AppLogger.w("A11Y", "WhatsApp group open timed out for: " + mTargetGroupName);
            mPendingWhatsAppGroupOpen = false;
            mTargetGroupName = null;
            return false;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        CharSequence pkg = root.getPackageName();
        if (pkg == null) return false;
        String pkgStr = pkg.toString().toLowerCase();
        if (!pkgStr.equals("com.whatsapp") && !pkgStr.equals("com.whatsapp.w4b")) {
            return false;
        }

        // 1. Try to click matching chat row directly in visible list / search results
        boolean clicked = clickChatRowByText(mTargetGroupName);
        if (clicked) {
            AppLogger.i("A11Y", "WhatsApp group open: opened group '" + mTargetGroupName + "'");
            mPendingWhatsAppGroupOpen = false;
            mTargetGroupName = null;
            return true;
        }

        // 2. Only attempt search after waiting 1000ms for initial chat list to load
        long elapsed = System.currentTimeMillis() - mGroupStartTime;
        if (elapsed >= 1000) {
            AccessibilityNodeInfo searchInput = findWhatsAppSearchInput(root);
            if (searchInput != null) {
                CharSequence currentText = searchInput.getText();
                String currentStr = currentText != null ? currentText.toString() : "";
                if (!currentStr.equalsIgnoreCase(mTargetGroupName)) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mTargetGroupName);
                    boolean set = searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    AppLogger.i("A11Y", "Typed '" + mTargetGroupName + "' into WhatsApp search bar: " + (set ? "SUCCESS" : "FAILED"));
                }
            } else {
                // Click search button if not clicked recently (wait at least 2000ms between attempts)
                if (System.currentTimeMillis() - mLastSearchClickTime > 2000) {
                    AccessibilityNodeInfo searchBtn = findWhatsAppSearchButton(root);
                    if (searchBtn != null && performClickOnNode(searchBtn)) {
                        mLastSearchClickTime = System.currentTimeMillis();
                        AppLogger.i("A11Y", "Clicked WhatsApp search button");
                    }
                }
            }
        }

        return false;
    }
}
