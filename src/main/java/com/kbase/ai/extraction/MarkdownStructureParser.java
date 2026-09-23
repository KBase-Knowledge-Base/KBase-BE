package com.kbase.ai.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic Markdown/plain-text parser used after Tika text extraction. */
public final class MarkdownStructureParser {

    private static final Pattern ATX_HEADING = Pattern.compile(
            "^[ \\t]{0,3}(#{1,6})[ \\t]+(.+?)\\s*#*[ \\t]*$");
    private static final Pattern SETEXT_HEADING = Pattern.compile("^[ \\t]{0,3}(=+|-+)[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^[ \\t]{0,3}(```+|~~~+).*$", Pattern.CASE_INSENSITIVE);

    public ExtractedDocument parseMarkdown(String text) {
        return parse(text, true);
    }

    public ExtractedDocument parsePlainText(String text) {
        return parse(text, false);
    }

    private ExtractedDocument parse(String input, boolean markdown) {
        if (input == null || input.isBlank()) {
            return new ExtractedDocument(List.of());
        }
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<ExtractedBlock> blocks = new ArrayList<>();
        String[] headings = new String[6];
        StringBuilder paragraph = new StringBuilder();
        String paragraphSection = null;
        String fenceMarker = null;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();

            if (markdown && fenceMarker != null) {
                appendLine(paragraph, line);
                if (isFenceEnd(trimmed, fenceMarker)) {
                    fenceMarker = null;
                }
                continue;
            }
            if (markdown && !trimmed.isEmpty()) {
                Matcher fence = FENCE.matcher(line);
                if (fence.matches()) {
                    if (fenceMarker == null) {
                        fenceMarker = fence.group(1).substring(0, 3);
                    }
                    if (paragraphSection == null) {
                        paragraphSection = sectionPath(headings);
                    }
                    appendLine(paragraph, line);
                    continue;
                }
            }

            if (markdown && !trimmed.isEmpty() && index + 1 < lines.length
                    && SETEXT_HEADING.matcher(lines[index + 1]).matches()) {
                flushParagraph(blocks, paragraph, paragraphSection);
                paragraphSection = null;
                int level = lines[index + 1].trim().startsWith("=") ? 1 : 2;
                String title = normalizeHeading(trimmed);
                applyHeading(headings, level, title);
                blocks.add(new ExtractedBlock(title,
                        new SourceLocation(null, null, sectionPath(headings))));
                index++;
                continue;
            }

            if (markdown) {
                Matcher atx = ATX_HEADING.matcher(line);
                if (atx.matches()) {
                    flushParagraph(blocks, paragraph, paragraphSection);
                    paragraphSection = null;
                    int level = atx.group(1).length();
                    String title = normalizeHeading(atx.group(2));
                    applyHeading(headings, level, title);
                    blocks.add(new ExtractedBlock(title,
                            new SourceLocation(null, null, sectionPath(headings))));
                    continue;
                }
            }

            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph, paragraphSection);
                paragraphSection = null;
                continue;
            }
            if (paragraphSection == null) {
                paragraphSection = sectionPath(headings);
            }
            appendLine(paragraph, line);
        }
        flushParagraph(blocks, paragraph, paragraphSection);
        return new ExtractedDocument(blocks);
    }

    private static void flushParagraph(List<ExtractedBlock> blocks, StringBuilder paragraph,
            String section) {
        String content = normalizeText(paragraph.toString());
        if (!content.isEmpty()) {
            blocks.add(new ExtractedBlock(content, new SourceLocation(null, null, section)));
        }
        paragraph.setLength(0);
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private static void applyHeading(String[] headings, int level, String title) {
        headings[level - 1] = title;
        for (int index = level; index < headings.length; index++) {
            headings[index] = null;
        }
    }

    private static String sectionPath(String[] headings) {
        List<String> path = new ArrayList<>();
        for (String heading : headings) {
            if (heading != null && !heading.isBlank()) {
                path.add(heading);
            }
        }
        return path.isEmpty() ? null : String.join(" > ", path);
    }

    private static String normalizeHeading(String value) {
        return normalizeText(value).replaceFirst("[ \\t]+#+$", "").trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static boolean isFenceEnd(String line, String marker) {
        return line.toLowerCase(Locale.ROOT).startsWith(marker.toLowerCase(Locale.ROOT));
    }
}
