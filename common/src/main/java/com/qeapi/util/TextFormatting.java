package com.qeapi.util;

import java.util.Arrays;
import java.util.stream.Collectors;

// Small display-text formatting helpers shared across the GUI, task, requirement, and reward code.
public final class TextFormatting {

    private TextFormatting() {}

    // used for effect/level display, e.g. "Level II"; falls back to the plain number outside 1-10
    public static String toRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    // e.g. "pillager_outpost" -> "Pillager Outpost"
    public static String titleCaseWords(String snakeCase) {
        return Arrays.stream(snakeCase.split("_"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
