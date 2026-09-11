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
        mGroupDeadline = System.currentTimeMillis() + 9000;
        AppLogger.i("A11Y", "Scheduled WhatsApp group send for '" + mTargetGroupName + "' (9s deadline)");
        schedulePollingChecks();
    }

    public void scheduleWhatsAppGroupOpen(String groupName) {
        mPendingWhatsAppGroupOpen = true;
        mTargetGroupName = groupName != null ? groupName.trim() : "";
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
        String targetViewId = isVideo ? ":id/video_call" : ":id/voice_call";
        String targetDesc = isVideo ? "video call" : "voice call";

        // Check if a confirmation dialog is already shown (e.g. "Start voice call? [Cancel] [CALL]")
        List<AccessibilityNodeInfo> callButtons = root.findAccessibilityNodeInfosByText("CALL");
        if (callButtons != null && !callButtons.isEmpty()) {
            for (AccessibilityNodeInfo btn : callButtons) {
                if (btn != null && btn.isVisibleToUser() && performClickOnNode(btn)) {
                    AppLogger.i("A11Y", "WhatsApp call: confirmed dialog CALL button");
                    mPendingWhatsAppCall = false;
                    return true;
                }
            }
        }
        List<AccessibilityNodeInfo> callButtonsLower = root.findAccessibilityNodeInfosByText("Call");
        if (callButtonsLower != null && !callButtonsLower.isEmpty()) {
            for (AccessibilityNodeInfo btn : callButtonsLower) {
                if (btn != null && btn.isVisibleToUser() && performClickOnNode(btn)) {
                    AppLogger.i("A11Y", "WhatsApp call: confirmed dialog Call button");
                    mPendingWhatsAppCall = false;
                    return true;
                }
            }
        }

        // Look for call icon in action bar
        AccessibilityNodeInfo callNode = findNodeEndingWithId(root, targetViewId);
        if (callNode != null && callNode.isVisibleToUser()) {
            if (performClickOnNode(callNode)) {
                AppLogger.i("A11Y", "WhatsApp call: clicked " + targetViewId + " icon");
                mMainHandler.postDelayed(this::dismissCallConfirmationDialogIfAny, 350);
                mPendingWhatsAppCall = false;
                return true;
            }
        }

        // Check by content description
        AccessibilityNodeInfo descNode = findNodeWithDescriptionContains(root, targetDesc);
        if (descNode != null && descNode.isVisibleToUser()) {
            if (performClickOnNode(descNode)) {
                AppLogger.i("A11Y", "WhatsApp call: clicked node with desc '" + targetDesc + "'");
                mMainHandler.postDelayed(this::dismissCallConfirmationDialogIfAny, 350);
                mPendingWhatsAppCall = false;
                return true;
            }
        }

        return false;
    }

    private void dismissCallConfirmationDialogIfAny() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("CALL");
        if (nodes != null) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && performClickOnNode(n)) {
                    AppLogger.i("A11Y", "WhatsApp call dialog auto-confirmed");
                    return;
                }
            }
        }
        List<AccessibilityNodeInfo> nodes2 = root.findAccessibilityNodeInfosByText("Call");
        if (nodes2 != null) {
            for (AccessibilityNodeInfo n : nodes2) {
                if (n != null && performClickOnNode(n)) {
                    AppLogger.i("A11Y", "WhatsApp call dialog auto-confirmed (Call)");
                    return;
                }
            }
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

        // 1. Try to click the group by text directly on the screen
        boolean clicked = clickByText(mTargetGroupName, false);
        if (clicked) {
            AppLogger.i("A11Y", "WhatsApp group send: clicked group text '" + mTargetGroupName + "'");
            mMainHandler.postDelayed(() -> {
                AccessibilityNodeInfo curRoot = getRootInActiveWindow();
                if (curRoot != null) {
                    AccessibilityNodeInfo fab = findNodeEndingWithId(curRoot, ":id/fab");
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

        // 2. If already selected, check if FAB is visible and click it
        AccessibilityNodeInfo fab = findNodeEndingWithId(root, ":id/fab");
        if (fab != null && fab.isVisibleToUser()) {
            if (performClickOnNode(fab)) {
                AppLogger.i("A11Y", "WhatsApp group send: clicked FAB directly");
                mMainHandler.postDelayed(this::performWhatsAppAutoSend, 450);
                mPendingWhatsAppGroupSend = false;
                mTargetGroupName = null;
                return true;
            }
        }

        // 3. If group not found in visible list, use search icon
        AccessibilityNodeInfo searchNode = findSearchNode(root);
        if (searchNode != null) {
            if (searchNode.isEditable()) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mTargetGroupName);
                searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            } else {
                if (performClickOnNode(searchNode)) {
                    mMainHandler.postDelayed(() -> {
                        typeText(mTargetGroupName);
                    }, 300);
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

        boolean clicked = clickByText(mTargetGroupName, false);
        if (clicked) {
            AppLogger.i("A11Y", "WhatsApp group open: opened group '" + mTargetGroupName + "'");
            mPendingWhatsAppGroupOpen = false;
            mTargetGroupName = null;
            return true;
        }

        AccessibilityNodeInfo searchNode = findSearchNode(root);
        if (searchNode != null) {
            if (searchNode.isEditable()) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mTargetGroupName);
                searchNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            } else {
                if (performClickOnNode(searchNode)) {
                    mMainHandler.postDelayed(() -> {
                        typeText(mTargetGroupName);
                    }, 300);
                }
            }
        }

        return false;
    }
}
