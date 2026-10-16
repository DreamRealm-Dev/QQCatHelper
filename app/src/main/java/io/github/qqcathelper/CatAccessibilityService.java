package io.github.qqcathelper;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Accessibility service that rewrites QQ input text.
 *
 * <p>Clean-room implementation. It listens only to QQ packages and modifies the
 * QQ input field through the public AccessibilityNodeInfo API.</p>
 */
public class CatAccessibilityService extends AccessibilityService {

    private static final String TAG = "QQCatHelper";

    private static final String PKG_QQ = "com.tencent.mobileqq";
    private static final String PKG_QQ_I = "com.tencent.mobileqqi";
    private static final String ID_INPUT = "com.tencent.mobileqq:id/input";
    private static final String ID_SEND = "com.tencent.mobileqq:id/send_btn";

    private static final int ACTION_SET_TEXT = 0x00200000;
    private static final int ACTION_SET_SELECTION = 0x00020000;
    private static final String EXTRA_SET_TEXT = "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    private static final String EXTRA_SELECTION_START = "ACTION_ARGUMENT_SELECTION_START_INT";
    private static final String EXTRA_SELECTION_END = "ACTION_ARGUMENT_SELECTION_END_INT";

    private static final long WRITE_ECHO_GUARD_MS = 600;

    private CatConfig cachedConfig;
    private String userOriginal = "";
    private String lastSet = "";
    private long lastWriteTime = 0;
    private String sentGuard = null;
    private boolean processing = false;
    private boolean pendingProcess = false;
    private boolean pendingSend = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        cachedConfig = CatConfig.load(this);
        Log.d(TAG, "service connected");
        KeepAliveService.start(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String pkg = event.getPackageName().toString();
        if (!PKG_QQ.equals(pkg) && !PKG_QQ_I.equals(pkg)) {
            return;
        }

        int type = event.getEventType();
        Log.d(TAG, "event type=" + type + " pkg=" + pkg);
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            resetState();
            cachedConfig = CatConfig.load(this);
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                String viewId = source.getViewIdResourceName();
                if (ID_SEND.equals(viewId)) {
                    Log.d(TAG, "send button clicked; final processing");
                    sentGuard = null;
                    doProcess(true);
                }
                source.recycle();
            }
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (cachedConfig == null) {
                cachedConfig = CatConfig.load(this);
            }
            String mode = cachedConfig.rewriteMode == null
                    ? CatConfig.MODE_PUNCTUATION
                    : cachedConfig.rewriteMode;
            Log.d(TAG, "text changed mode=" + mode + " source=" + event.getSource());

            AccessibilityNodeInfo source = event.getSource();
            AccessibilityNodeInfo editableSource = null;
            if (source != null) {
                if (source.isEditable()) {
                    editableSource = AccessibilityNodeInfo.obtain(source);
                }
                source.recycle();
            }

            if (editableSource != null) {
                if (CatConfig.MODE_REALTIME.equals(mode)) {
                    processInput(editableSource, false);
                    return;
                }

                CharSequence text = editableSource.getText();
                if (text != null && text.length() > 0) {
                    String current = text.toString().trim();
                    if (!current.isEmpty() && isPunctuationEnding(current)) {
                        Log.d(TAG, "punctuation triggered: " + current);
                        processInput(editableSource, false);
                        return;
                    }
                }
                editableSource.recycle();
                // Punctuation mode only processes when the text ends with a
                // punctuation mark. If the event source is available and does
                // not qualify, do not fall through to realtime-style handling.
                return;
            }

            // Fallback to searching the active window only when the event did
            // not provide an editable source.
            doProcess(false);
        }
    }

    @Override
    public void onInterrupt() {
        processing = false;
        pendingProcess = false;
        pendingSend = false;
    }

    private void resetState() {
        userOriginal = "";
        lastSet = "";
        lastWriteTime = 0;
    }

    private void doProcess(boolean isSending) {
        if (processing) {
            pendingProcess = true;
            pendingSend = pendingSend || isSending;
            return;
        }
        processing = true;

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "doProcess root is null");
                return;
            }

            AccessibilityNodeInfo input = findNodeById(root, ID_INPUT);
            if (input == null) {
                input = findEditable(root);
            }
            if (input == null) {
                Log.w(TAG, "doProcess input is null");
            }
            root.recycle();

            processInput(input, isSending);
        } finally {
            processing = false;
            if (pendingProcess) {
                pendingProcess = false;
                boolean send = pendingSend;
                pendingSend = false;
                doProcess(send);
            }
        }
    }

    private void processInput(AccessibilityNodeInfo input, boolean isSending) {
        if (input == null) {
            return;
        }
        try {
            CharSequence text = input.getText();
            if (text == null || text.length() == 0) {
                Log.d(TAG, "input text empty");
                if (!lastSet.isEmpty()) {
                    sentGuard = lastSet;
                }
                resetState();
                return;
            }

            String current = text.toString().trim();
            if (current.isEmpty()) {
                Log.d(TAG, "input text blank");
                if (!lastSet.isEmpty()) {
                    sentGuard = lastSet;
                }
                resetState();
                return;
            }

            if (sentGuard != null) {
                // After a send, ignore the exact restored message. As soon as
                // the user types something different, resume normal processing.
                if (current.equals(sentGuard)) {
                    Log.d(TAG, "ignoring restored message: " + current);
                    return;
                }
                sentGuard = null;
            }

            if (cachedConfig == null) {
                cachedConfig = CatConfig.load(this);
            }

            // While the user is deleting, avoid re-processing and re-adding
            // decorations on every keystroke. This prevents the input from
            // piling up more kaomoji while the text is temporarily incomplete.
            if (!lastSet.isEmpty() && current.length() < lastSet.length()) {
                String cleaned = stripAll(current, cachedConfig);
                Log.d(TAG, "deletion detected; cleaned=" + cleaned + " current=" + current);
                if (!cleaned.isEmpty() && !cleaned.equals(current)) {
                    // Immediately remove the stale suffix from the input field so
                    // old emoticons do not linger or get partially merged later.
                    if (setText(input, cleaned)) {
                        userOriginal = cleaned;
                        lastSet = cleaned;
                        lastWriteTime = SystemClock.uptimeMillis();
                    } else {
                        userOriginal = cleaned;
                        lastSet = current;
                    }
                } else if (!cleaned.isEmpty()) {
                    userOriginal = cleaned;
                    lastSet = current;
                } else {
                    resetState();
                }
                return;
            }

            long now = SystemClock.uptimeMillis();
            boolean echo = lastWriteTime > 0
                    && (now - lastWriteTime) < WRITE_ECHO_GUARD_MS
                    && current.equals(lastSet);
            if (echo) {
                lastWriteTime = 0;
                return;
            }

            updateUserOriginal(current);

            if (userOriginal.isEmpty()) {
                Log.d(TAG, "original text empty; skip");
                return;
            }

            CatConfig effectiveConfig = cachedConfig;
            String target = TextProcessor.process(userOriginal, effectiveConfig);
            if (target.equals(current)) {
                lastSet = target;
                return;
            }

            Log.d(TAG, "write: raw=" + current + " original=" + userOriginal + " target=" + target);
            if (setText(input, target)) {
                lastSet = target;
                lastWriteTime = SystemClock.uptimeMillis();
            }
        } finally {
            input.recycle();
        }
    }

    private void updateUserOriginal(String current) {
        boolean realtime = CatConfig.MODE_REALTIME.equals(cachedConfig.rewriteMode);

        if (realtime && lastSet.isEmpty()) {
            userOriginal = stripAll(current, cachedConfig);
            Log.d(TAG, "realtime first strip: " + userOriginal);
            return;
        }

        if (lastSet.isEmpty()) {
            userOriginal = stripAll(current, cachedConfig);
            Log.d(TAG, "first strip: " + userOriginal);
            return;
        }

        if (current.startsWith(lastSet)) {
            String appended = current.substring(lastSet.length());
            userOriginal = userOriginal + appended;
            Log.d(TAG, "incremental append: +" + appended + " original=" + userOriginal);
        } else {
            userOriginal = stripAll(current, cachedConfig);
            Log.d(TAG, "not matching; strip: " + userOriginal);
        }
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return false;
        }
        try {
            Bundle setTextArgs = new Bundle();
            setTextArgs.putCharSequence(EXTRA_SET_TEXT, text);
            boolean ok = node.performAction(ACTION_SET_TEXT, setTextArgs);
            if (ok) {
                Bundle selectionArgs = new Bundle();
                selectionArgs.putInt(EXTRA_SELECTION_START, text.length());
                selectionArgs.putInt(EXTRA_SELECTION_END, text.length());
                node.performAction(ACTION_SET_SELECTION, selectionArgs);
            }
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "setText failed", e);
            return false;
        }
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo node, String id) {
        if (node == null || id == null) {
            return null;
        }
        if (id.equals(node.getViewIdResourceName())) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeById(child, id);
                child.recycle();
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findEditable(child);
                child.recycle();
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private boolean isPunctuationEnding(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        char c = text.charAt(text.length() - 1);
        return c == '\u3002' || c == '\uff01' || c == '!' || c == '\uff1f' || c == '?' || c == ' ';
    }

    private String removeFragment(String text, String fragment) {
        if (text == null || fragment == null || fragment.isEmpty()) {
            return text;
        }
        String result = text;
        int index;
        while ((index = result.indexOf(fragment)) >= 0) {
            int start = index;
            while (start > 0 && Character.isWhitespace(result.charAt(start - 1))) {
                start--;
            }
            result = result.substring(0, start)
                    + result.substring(index + fragment.length());
        }
        return result;
    }

    /**
     * Removes previously added "喵" and emoticons so we can recover the
     * user's original text while typing.
     */
    private String stripAll(String text, CatConfig config) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;
        String[] emoticons = config.getAllKnownKaomoji();
        if (emoticons == null || emoticons.length == 0) {
            emoticons = new String[0];
        }

        String[] sorted = emoticons.clone();
        Arrays.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(
                        b == null ? 0 : b.length(),
                        a == null ? 0 : a.length());
            }
        });

        for (String emoticon : sorted) {
            if (emoticon == null || emoticon.isEmpty()) {
                continue;
            }
            int index;
            while ((index = result.indexOf(emoticon)) >= 0) {
                int start = index;
                while (start > 0 && Character.isWhitespace(result.charAt(start - 1))) {
                    start--;
                }
                result = result.substring(0, start)
                        + result.substring(index + emoticon.length());
            }
        }

        // Remove fragments of known kaomoji anywhere in the text. This handles
        // deleting inside an already-appended emoticon and then typing again,
        // where a partial suffix may be embedded in the middle of the input.
        for (String emoticon : sorted) {
            if (emoticon == null || emoticon.length() < 3) {
                continue;
            }
            for (int len = emoticon.length(); len >= 3; len--) {
                String prefix = emoticon.substring(0, len);
                String suffix = emoticon.substring(emoticon.length() - len);
                result = removeFragment(result, prefix);
                result = removeFragment(result, suffix);
            }
        }

        // Clean up tiny trailing residues such as a single leading character of
        // a kaomoji (for example "ヾ") left after the rest was deleted. Only do
        // this when the residue is separated by whitespace or is the only text.
        for (String emoticon : sorted) {
            if (emoticon == null || emoticon.isEmpty()) {
                continue;
            }
            for (int len = Math.min(2, emoticon.length()); len >= 1; len--) {
                String prefix = emoticon.substring(0, len);
                if (result.endsWith(prefix)) {
                    int start = result.length() - len;
                    boolean isolated = start == 0
                            || Character.isWhitespace(result.charAt(start - 1));
                    if (isolated) {
                        result = result.substring(0, start).trim();
                        break;
                    }
                }
                String suffix = emoticon.substring(emoticon.length() - len);
                if (result.endsWith(suffix)) {
                    int start = result.length() - len;
                    boolean isolated = start == 0
                            || Character.isWhitespace(result.charAt(start - 1));
                    if (isolated) {
                        result = result.substring(0, start).trim();
                        break;
                    }
                }
            }
        }

        // Remove long symbol runs which may be leftovers from previous random
        // emoticons, then remove "喵" while protecting "本喵".
        result = result.replaceAll("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*", " ");
        String placeholder = "\uE000BM\uE001";
        result = result.replace("本喵", placeholder)
                .replace("喵", "")
                .replace(placeholder, "本喵")
                .trim();
        return result;
    }
}
