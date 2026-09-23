package com.kbase.ai.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Deterministic provider-independent token estimator owned by KBase.
 * Unicode letter/mark/number runs are one token; every other non-whitespace
 * code point is one token.
 */
@Component
public final class KBaseLexTokenCounter {

    public static final String VERSION = "kbase-lex-v1";

    public int count(String text) {
        return tokenize(text).size();
    }

    public List<Lexeme> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        List<Lexeme> lexemes = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                offset += width;
                continue;
            }
            int start = offset;
            if (isWordCodePoint(codePoint)) {
                offset += width;
                while (offset < text.length()) {
                    int next = text.codePointAt(offset);
                    if (!isWordCodePoint(next)) {
                        break;
                    }
                    offset += Character.charCount(next);
                }
            } else {
                offset += width;
            }
            lexemes.add(new Lexeme(text.substring(start, offset), start, offset));
        }
        return List.copyOf(lexemes);
    }

    private static boolean isWordCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetter(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    public record Lexeme(String text, int start, int end) {
    }
}
