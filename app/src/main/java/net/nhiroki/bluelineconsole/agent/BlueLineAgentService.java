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
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Active event listening
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
            return false;
        }
        return true;
    }

    public boolean clickByText(String targetText, boolean exact) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!isSafeWindow(root)) return false;
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
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }

        AccessibilityNodeInfo editable = findFirstEditableNode(root);
        if (editable != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
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
}
