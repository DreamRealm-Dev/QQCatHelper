package io.github.qqcathelper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model backed by SharedPreferences.
 *
 * <p>This is a clean-room implementation based on observed behavior only.
 * Preference keys are intentionally namespaced to this project.</p>
 */
public final class CatConfig {

    private static final String PREF_NAME = "io.github.qqcathelper.settings";
    private static final String KEY_ADD_MEOW_SUFFIX = "add_meow_suffix";
    private static final String KEY_REPLACE_WO = "replace_wo";
    private static final String KEY_REPLACE_NI = "replace_ni";
    private static final String KEY_APPEND_KAOMOJI = "append_kaomoji";
    private static final String KEY_REWRITE_MODE = "rewrite_mode";
    private static final String KEY_CUSTOM_KAOMOJI = "custom_kaomoji";
    private static final String KEY_KEEP_ALIVE_MODE = "keep_alive_mode";

    private static final String LEGACY_PREF_NAME = "qq_cat_helper_config";
    private static final String LEGACY_KEY_ENABLE_MEOW = "enable_meow";
    private static final String LEGACY_KEY_ENABLE_WO = "enable_wo";
    private static final String LEGACY_KEY_ENABLE_NI = "enable_ni";
    private static final String LEGACY_KEY_ENABLE_EMOTICON = "enable_emoticon";
    private static final String LEGACY_KEY_PROCESSING_MODE = "processing_mode";
    private static final String LEGACY_KEY_CUSTOM_EMOTICONS = "custom_emoticons";

    public static final String MODE_PUNCTUATION = "punctuation";
    public static final String MODE_REALTIME = "realtime";

    public static final String KEEP_ALIVE_OFF = "off";
    public static final String KEEP_ALIVE_GENERAL = "general";
    public static final String KEEP_ALIVE_OVERLAY = "overlay";
    public static final String KEEP_ALIVE_SHIZUKU = "shizuku";

    private static final String[] BUILTIN_KAOMOJI = {
            "(=^･ω･^=)",
            "(=^‥^=)",
            "(=^･ｪ･^=)",
            "(=①ω①=)",
            "ヾ(=^･ω･^=)ノ",
            "(=；ω；=)",
            "ฅ(^•ﻌ•^)ฅ",
            "(=｀ω´=)",
            "(=^ω^=)",
            "~(˘▾˘~)",
            "(=｀ェ´=)",
            "ヽ(=^･ω･^=)丿",
            "₍˄·͈༝·͈˄₎ฅ˒˒",
            "(=;ω;=)",
            "(=￣ω￣=)"
    };

    public boolean addMeowSuffix = true;
    public boolean replaceWo = true;
    public boolean replaceNi = false;
    public boolean appendKaomoji = true;
    public String rewriteMode = MODE_PUNCTUATION;
    public String[] customKaomoji = new String[0];
    public String keepAliveMode = KEEP_ALIVE_GENERAL;

    public static CatConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_REWRITE_MODE)
                && !prefs.contains(KEY_ADD_MEOW_SUFFIX)
                && !prefs.contains(KEY_REPLACE_WO)
                && !prefs.contains(KEY_REPLACE_NI)
                && !prefs.contains(KEY_APPEND_KAOMOJI)) {
            migrateLegacy(context, prefs);
        }

        CatConfig config = new CatConfig();
        config.addMeowSuffix = prefs.getBoolean(KEY_ADD_MEOW_SUFFIX, true);
        config.replaceWo = prefs.getBoolean(KEY_REPLACE_WO, true);
        config.replaceNi = prefs.getBoolean(KEY_REPLACE_NI, false);
        config.appendKaomoji = prefs.getBoolean(KEY_APPEND_KAOMOJI, true);
        config.rewriteMode = prefs.getString(KEY_REWRITE_MODE, MODE_PUNCTUATION);

        config.customKaomoji = parseKaomojiLines(prefs.getString(KEY_CUSTOM_KAOMOJI, ""));
        config.keepAliveMode = prefs.getString(KEY_KEEP_ALIVE_MODE, KEEP_ALIVE_GENERAL);
        return config;
    }

    private static void migrateLegacy(Context context, SharedPreferences prefs) {
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
        if (!legacy.contains(LEGACY_KEY_ENABLE_MEOW)
                && !legacy.contains(LEGACY_KEY_ENABLE_EMOTICON)
                && !legacy.contains(LEGACY_KEY_PROCESSING_MODE)) {
            return;
        }

        CatConfig legacyConfig = new CatConfig();
        legacyConfig.addMeowSuffix = legacy.getBoolean(LEGACY_KEY_ENABLE_MEOW, true);
        legacyConfig.replaceWo = legacy.getBoolean(LEGACY_KEY_ENABLE_WO, true);
        legacyConfig.replaceNi = legacy.getBoolean(LEGACY_KEY_ENABLE_NI, false);
        legacyConfig.appendKaomoji = legacy.getBoolean(LEGACY_KEY_ENABLE_EMOTICON, true);
        legacyConfig.rewriteMode = legacy.getString(LEGACY_KEY_PROCESSING_MODE, MODE_PUNCTUATION);

        legacyConfig.customKaomoji = parseKaomojiLines(
                legacy.getString(LEGACY_KEY_CUSTOM_EMOTICONS, ""));

        prefs.edit()
                .putBoolean(KEY_ADD_MEOW_SUFFIX, legacyConfig.addMeowSuffix)
                .putBoolean(KEY_REPLACE_WO, legacyConfig.replaceWo)
                .putBoolean(KEY_REPLACE_NI, legacyConfig.replaceNi)
                .putBoolean(KEY_APPEND_KAOMOJI, legacyConfig.appendKaomoji)
                .putString(KEY_REWRITE_MODE, legacyConfig.rewriteMode)
                .putString(KEY_CUSTOM_KAOMOJI, joinLines(legacyConfig.customKaomoji))
                .putString(KEY_KEEP_ALIVE_MODE, KEEP_ALIVE_GENERAL)
                .apply();
    }

    public void save(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ADD_MEOW_SUFFIX, addMeowSuffix)
                .putBoolean(KEY_REPLACE_WO, replaceWo)
                .putBoolean(KEY_REPLACE_NI, replaceNi)
                .putBoolean(KEY_APPEND_KAOMOJI, appendKaomoji)
                .putString(KEY_REWRITE_MODE, rewriteMode == null ? MODE_PUNCTUATION : rewriteMode)
                .putString(KEY_CUSTOM_KAOMOJI, joinLines(customKaomoji));
        editor.putString(KEY_KEEP_ALIVE_MODE, keepAliveMode == null ? KEEP_ALIVE_GENERAL : keepAliveMode);
        editor.apply();
    }

    public String[] getActiveKaomoji() {
        if (customKaomoji != null && customKaomoji.length > 0) {
            return customKaomoji;
        }
        return BUILTIN_KAOMOJI;
    }

    /**
     * Returns both custom and built-in kaomoji for stripping previously added
     * decorations, even when a custom list is currently active.
     */
    public String[] getAllKnownKaomoji() {
        if (customKaomoji == null || customKaomoji.length == 0) {
            return BUILTIN_KAOMOJI.clone();
        }
        String[] all = new String[customKaomoji.length + BUILTIN_KAOMOJI.length];
        System.arraycopy(customKaomoji, 0, all, 0, customKaomoji.length);
        System.arraycopy(BUILTIN_KAOMOJI, 0, all, customKaomoji.length, BUILTIN_KAOMOJI.length);
        return all;
    }

    private static String[] parseKaomojiLines(String raw) {
        if (raw == null) {
            return new String[0];
        }
        List<String> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("例如") || trimmed.startsWith("比如")
                    || trimmed.startsWith("示例")) {
                continue;
            }
            list.add(trimmed);
        }
        return list.toArray(new String[0]);
    }

    private static String joinLines(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
