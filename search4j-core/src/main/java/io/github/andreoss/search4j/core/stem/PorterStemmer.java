/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Snowball stemming runtime and language registry.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.termgen.Stemmer;
import java.util.Locale;
import java.util.Map;

/**
 * Porter stemmer for English words.
 *
 * <p>Implements the original Porter algorithm: a word is reduced by the
 * suffix rules of steps 1a to 5b, each conditioned on the measure of its
 * stem.</p>
 *
 * @since 0.1.0
 */
final class PorterStemmer implements Stemmer {

    /**
     * Step 2 suffix replacements, ordered from the longest suffix.
     */
    private static final Map<String, String> STEP2 = Map.ofEntries(
        Map.entry("ational", "ate"),
        Map.entry("ation", "ate"),
        Map.entry("ization", "ize"),
        Map.entry("iveness", "ive"),
        Map.entry("ousness", "ous"),
        Map.entry("biliti", "ble"),
        Map.entry("tional", "tion"),
        Map.entry("fulness", "ful"),
        Map.entry("ousli", "ous"),
        Map.entry("aliti", "al"),
        Map.entry("iviti", "ive"),
        Map.entry("enci", "ence"),
        Map.entry("anci", "ance"),
        Map.entry("izer", "ize"),
        Map.entry("abli", "able"),
        Map.entry("alli", "al"),
        Map.entry("entli", "ent"),
        Map.entry("eli", "e"),
        Map.entry("ator", "ate"),
        Map.entry("alism", "al"),
        Map.entry("logi", "log")
    );

    /**
     * Step 3 suffix replacements, ordered from the longest suffix.
     */
    private static final Map<String, String> STEP3 = Map.ofEntries(
        Map.entry("icate", "ic"),
        Map.entry("alize", "al"),
        Map.entry("iciti", "ic"),
        Map.entry("ical", "ic"),
        Map.entry("ative", ""),
        Map.entry("ness", ""),
        Map.entry("ful", "")
    );

    /**
     * Step 4 suffixes, ordered from the longest suffix.
     */
    private static final String[] STEP4 = {
        "ement", "ance", "ence", "able", "ible", "ment", "ant", "ent",
        "ion", "ism", "ate", "iti", "ous", "ive", "ize", "al", "er", "ic",
        "ou",
    };

    @Override
    public String stem(final String word) {
        String result = word.toLowerCase(Locale.ROOT);
        if (result.length() > 2) {
            result = PorterStemmer.stepOneA(result);
            result = PorterStemmer.stepOneB(result);
            result = PorterStemmer.stepOneC(result);
            result = PorterStemmer.stepTwo(result);
            result = PorterStemmer.stepThree(result);
            result = PorterStemmer.stepFour(result);
            result = PorterStemmer.stepFiveA(result);
            result = PorterStemmer.stepFiveB(result);
        }
        return result;
    }

    @Override
    public String name() {
        return "porter";
    }

    private static String stepOneA(final String word) {
        final String result;
        if (word.endsWith("sses")) {
            result = PorterStemmer.replace(word, "sses", "ss");
        } else if (word.endsWith("ied")) {
            result = PorterStemmer.shortIe(word);
        } else if (word.endsWith("ies")) {
            result = PorterStemmer.replace(word, "ies", "i");
        } else if (word.endsWith("ss")) {
            result = word;
        } else if (word.endsWith("s")) {
            final String stem = PorterStemmer.replace(word, "s", "");
            if (PorterStemmer.hasVowel(stem)) {
                result = stem;
            } else {
                result = word;
            }
        } else {
            result = word;
        }
        return result;
    }

    private static String shortIe(final String word) {
        final String result;
        if (word.length() == 4) {
            result = PorterStemmer.replace(word, "ied", "ie");
        } else {
            result = PorterStemmer.replace(word, "ied", "i");
        }
        return result;
    }

    private static String stepOneB(final String word) {
        String result = word;
        if (word.endsWith("eed")) {
            final String stem = PorterStemmer.replace(word, "eed", "");
            if (PorterStemmer.measure(stem) > 0) {
                result = PorterStemmer.concat(stem, "ee");
            }
        } else if (word.endsWith("ed")
            && PorterStemmer.hasVowel(PorterStemmer.replace(word, "ed", ""))) {
            result = PorterStemmer.post(
                PorterStemmer.replace(word, "ed", "")
            );
        } else if (word.endsWith("ing")
            && PorterStemmer.hasVowel(
                PorterStemmer.replace(word, "ing", "")
            )) {
            result = PorterStemmer.post(
                PorterStemmer.replace(word, "ing", "")
            );
        }
        return result;
    }

    private static String post(final String word) {
        String result = word;
        if (word.endsWith("at") || word.endsWith("bl")
            || word.endsWith("iz")) {
            result = PorterStemmer.concat(word, "e");
        } else if (PorterStemmer.doubleConsonant(word)
            && !"lsz".contains(word.substring(word.length() - 1))) {
            result = word.substring(0, word.length() - 1);
        } else if (PorterStemmer.measure(word) == 1
            && PorterStemmer.cvc(word)) {
            result = PorterStemmer.concat(word, "e");
        }
        return result;
    }

    private static String stepOneC(final String word) {
        final String result;
        if (word.endsWith("y")
            && PorterStemmer.vowelIn(
                word.substring(0, word.length() - 1)
            )) {
            result = PorterStemmer.replace(word, "y", "i");
        } else {
            result = word;
        }
        return result;
    }

    private static String stepTwo(final String word) {
        return PorterStemmer.mapped(word, PorterStemmer.STEP2);
    }

    private static String stepThree(final String word) {
        return PorterStemmer.mapped(word, PorterStemmer.STEP3);
    }

    private static String mapped(
        final String word, final Map<String, String> rules
    ) {
        String result = word;
        final String key = PorterStemmer.longest(word, rules.keySet());
        if (key != null) {
            final String stem = PorterStemmer.replace(word, key, "");
            if (PorterStemmer.measure(stem) > 0) {
                result = PorterStemmer.concat(stem, rules.get(key));
            }
        }
        return result;
    }

    private static String stepFour(final String word) {
        String result = word;
        final String key = PorterStemmer.longest(word, PorterStemmer.STEP4);
        if (key != null) {
            final String stem = PorterStemmer.replace(word, key, "");
            final boolean kept = "ion".equals(key)
                && !stem.endsWith("s") && !stem.endsWith("t");
            if (PorterStemmer.measure(stem) > 1 && !kept) {
                result = stem;
            }
        }
        return result;
    }

    private static String stepFiveA(final String word) {
        String result = word;
        if (word.endsWith("e")) {
            final String stem = PorterStemmer.replace(word, "e", "");
            final int size = PorterStemmer.measure(stem);
            if (size > 1 || size == 1 && !PorterStemmer.cvc(stem)) {
                result = stem;
            }
        }
        return result;
    }

    private static String stepFiveB(final String word) {
        final String result;
        if (PorterStemmer.measure(word) > 1 && word.endsWith("ll")) {
            result = word.substring(0, word.length() - 1);
        } else {
            result = word;
        }
        return result;
    }

    private static String longest(final String word, final String... suffixes) {
        String found = null;
        for (final String suffix : suffixes) {
            if ((found == null || suffix.length() > found.length())
                && word.endsWith(suffix)) {
                found = suffix;
            }
        }
        return found;
    }

    private static String longest(
        final String word, final Iterable<String> suffixes
    ) {
        String found = null;
        for (final String suffix : suffixes) {
            if ((found == null || suffix.length() > found.length())
                && word.endsWith(suffix)) {
                found = suffix;
            }
        }
        return found;
    }

    private static String replace(
        final String word, final String suffix, final String repl
    ) {
        return PorterStemmer.concat(
            word.substring(0, word.length() - suffix.length()), repl
        );
    }

    private static String concat(final String left, final String right) {
        return String.format("%s%s", left, right);
    }

    private static int vowelIndex(final char chr) {
        return "aeiou".indexOf(chr);
    }

    private static boolean vowelIn(final String word) {
        boolean found = false;
        for (int idx = 0; idx < word.length() && !found; idx = idx + 1) {
            found = !PorterStemmer.consonant(word, idx);
        }
        return found;
    }

    private static boolean hasVowel(final String word) {
        boolean found = PorterStemmer.vowelIn(word);
        if (!found && word.length() > 1) {
            final String ending = word.substring(word.length() - 2);
            found = "ying".endsWith(ending) || "uing".endsWith(ending);
        }
        return found;
    }

    private static boolean consonant(final String word, final int pos) {
        final char chr = word.charAt(pos);
        final boolean result;
        if (PorterStemmer.vowelIndex(chr) >= 0) {
            result = false;
        } else if (chr == 'y') {
            result = pos == 0 || !PorterStemmer.consonant(word, pos - 1);
        } else {
            result = true;
        }
        return result;
    }

    private static boolean doubleConsonant(final String word) {
        final int len = word.length();
        return len > 1 && word.charAt(len - 1) == word.charAt(len - 2)
            && PorterStemmer.consonant(word, len - 1);
    }

    private static boolean cvc(final String word) {
        final int len = word.length();
        boolean found = false;
        if (len > 2) {
            found = PorterStemmer.consonant(word, len - 3)
                && !PorterStemmer.consonant(word, len - 2)
                && PorterStemmer.consonant(word, len - 1)
                && "wxy".indexOf(word.charAt(len - 1)) < 0;
        }
        return found;
    }

    private static int measure(final String word) {
        int count = 0;
        int pos = 0;
        final int len = word.length();
        while (pos < len && PorterStemmer.consonant(word, pos)) {
            pos = pos + 1;
        }
        while (pos < len) {
            while (pos < len && !PorterStemmer.consonant(word, pos)) {
                pos = pos + 1;
            }
            if (pos < len) {
                while (pos < len && PorterStemmer.consonant(word, pos)) {
                    pos = pos + 1;
                }
                count = count + 1;
            }
        }
        return count;
    }
}
