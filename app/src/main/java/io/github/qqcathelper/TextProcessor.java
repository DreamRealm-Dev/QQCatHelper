package io.github.qqcathelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text rewriting logic: meow suffix, word replacements, random emoticons.
 *
 * <p>Clean-room implementation based on behavior, not original source.</p>
 */
public final class TextProcessor {

    private static final Random RANDOM = new Random();

    private static final Pattern SENTENCE_SPLIT_PATTERN =
            Pattern.compile("([，,。！!？?\\s]+)");

    private TextProcessor() {
    }

    public static String process(String input, CatConfig config) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String result = input.trim();

        if (config.replaceWo) {
            result = result.replace("我", "本喵");
        }

        if (config.replaceNi) {
            result = result.replace("你", "主人");
        }

        if (config.addMeowSuffix) {
            result = addMeow(result);
        }

        if (config.appendKaomoji) {
            String emoticon = getRandomKaomoji(config);
            if (emoticon != null && !emoticon.isEmpty()) {
                result = result + " " + emoticon;
            }
        }

        return result;
    }

    private static String addMeow(String input) {
        List<String> segments = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher matcher = SENTENCE_SPLIT_PATTERN.matcher(input);

        int cursor = 0;
        while (matcher.find()) {
            segments.add(input.substring(cursor, matcher.start()));
            separators.add(matcher.group(1));
            cursor = matcher.end();
        }

        if (cursor < input.length()) {
            segments.add(input.substring(cursor));
        } else if (!segments.isEmpty() && cursor == input.length()) {
            // Trailing punctuation: add an empty segment so the trailing
            // punctuation itself is not given a "喵".
            segments.add("");
        }

        if (segments.isEmpty()) {
            segments.add(input);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i).trim();
            if (!segment.isEmpty()) {
                sb.append(segment).append("喵");
            }
            if (i < separators.size()) {
                sb.append(separators.get(i));
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            result = input + "喵";
        }
        return result;
    }

    private static String getRandomKaomoji(CatConfig config) {
        String[] kaomoji = config.getActiveKaomoji();
        if (kaomoji == null || kaomoji.length == 0) {
            return "";
        }
        return kaomoji[RANDOM.nextInt(kaomoji.length)];
    }
}
