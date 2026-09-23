package com.kbase.ai.extraction.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.kbase.ai.extraction.DocumentContentExtractor;
import com.kbase.ai.extraction.DocumentExtractionException;
import com.kbase.ai.extraction.DocumentExtractionRequest;
import com.kbase.ai.extraction.DocumentSourceReadException;
import com.kbase.ai.extraction.ExtractedBlock;
import com.kbase.ai.extraction.ExtractedDocument;
import com.kbase.ai.extraction.MarkdownStructureParser;
import com.kbase.ai.extraction.SourceLocation;
import com.kbase.ai.service.AiDocumentSupportPolicy;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Apache Tika adapter for the deliberately small M5 allowlist. Tika/SAX types
 * are confined to this adapter package.
 */
@Component
public final class TikaDocumentContentExtractor implements DocumentContentExtractor {

    private final AiDocumentSupportPolicy supportPolicy;
    private final MarkdownStructureParser markdownStructureParser;

    public TikaDocumentContentExtractor() {
        this(new AiDocumentSupportPolicy());
    }

    @Autowired
    public TikaDocumentContentExtractor(AiDocumentSupportPolicy supportPolicy) {
        this.supportPolicy = supportPolicy;
        this.markdownStructureParser = new MarkdownStructureParser();
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentExtractionRequest request) {
        if (input == null || request == null || !supportPolicy.supports(request.extension())) {
            throw new DocumentExtractionException();
        }
        try {
            Metadata metadata = metadata(request);
            AutoDetectParser parser = new AutoDetectParser();
            if (request.extension().equals("md") || request.extension().equals("txt")) {
                BodyContentHandler handler = new BodyContentHandler(-1);
                parser.parse(input, handler, metadata, new ParseContext());
                String text = handler.toString();
                return request.extension().equals("md")
                        ? markdownStructureParser.parseMarkdown(text)
                        : markdownStructureParser.parsePlainText(text);
            }

            StructuredSaxHandler handler = new StructuredSaxHandler();
            parser.parse(input, handler, metadata, new ParseContext());
            return handler.toDocument();
        } catch (IOException | SAXException | TikaException | RuntimeException exception) {
            // The adapter deliberately collapses parser messages and bodies to
            // a safe category at the application boundary.
            if (exception instanceof DocumentExtractionException extractionException) {
                throw extractionException;
            }
            if (exception instanceof DocumentSourceReadException sourceReadException) {
                throw sourceReadException;
            }
            throw new DocumentExtractionException(exception);
        }
    }

    private static Metadata metadata(DocumentExtractionRequest request) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, request.resourceName());
        metadata.set(Metadata.CONTENT_TYPE, request.contentType());
        return metadata;
    }

    /** SAX collector for Tika's PDF page and PowerPoint slide XHTML markers. */
    private static final class StructuredSaxHandler extends DefaultHandler {

        private final List<ExtractedBlock> blocks = new ArrayList<>();
        private final Deque<ElementFrame> frames = new ArrayDeque<>();
        private final StringBuilder fallback = new StringBuilder();

        private String currentBlockName;
        private int currentBlockDepth = -1;
        private boolean currentBlockHeading;
        private StringBuilder currentBlock;
        private int pageCount;
        private int slideCount;
        private Integer pageNumber;
        private Integer slideNumber;
        private String sectionTitle;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = elementName(localName, qName);
            String classes = attribute(attributes, "class");
            boolean pageStart = "div".equals(name) && hasClass(classes, "page");
            boolean slideStart = "div".equals(name) && hasClass(classes, "slide-content");
            flushFallbackIfNeeded();
            if (pageStart) {
                flushBlock();
                pageNumber = ++pageCount;
            }
            if (slideStart) {
                flushBlock();
                slideNumber = ++slideCount;
            }

            int depth = frames.size();
            frames.push(new ElementFrame(name, depth, pageStart, slideStart));
            if (isBlockElement(name) && currentBlock == null) {
                flushFallbackIfNeeded();
                currentBlockName = name;
                currentBlockDepth = depth;
                currentBlockHeading = isHeading(name, classes, attribute(attributes, "style"));
                currentBlock = new StringBuilder();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = elementName(localName, qName);
            ElementFrame frame = frames.isEmpty() ? null : frames.pop();
            if (currentBlock != null && frame != null
                    && frame.depth() == currentBlockDepth
                    && name.equals(currentBlockName)) {
                flushBlock();
            }
            if (frame != null && frame.pageStart()) {
                flushBlock();
                flushFallbackIfNeeded();
                pageNumber = null;
            }
            if (frame != null && frame.slideStart()) {
                flushBlock();
                flushFallbackIfNeeded();
                slideNumber = null;
            }
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            if (currentBlock != null) {
                currentBlock.append(characters, start, length);
            } else {
                fallback.append(characters, start, length);
            }
        }

        ExtractedDocument toDocument() {
            flushBlock();
            flushFallbackIfNeeded();
            return new ExtractedDocument(blocks);
        }

        private void flushBlock() {
            if (currentBlock == null) {
                return;
            }
            String text = normalize(currentBlock.toString());
            if (!text.isEmpty()) {
                String locationTitle = currentBlockHeading ? text : sectionTitle;
                blocks.add(new ExtractedBlock(text,
                        new SourceLocation(pageNumber, slideNumber, locationTitle)));
                if (currentBlockHeading) {
                    sectionTitle = text;
                }
            }
            currentBlock = null;
            currentBlockName = null;
            currentBlockDepth = -1;
            currentBlockHeading = false;
        }

        private void flushFallbackIfNeeded() {
            if (fallback.length() == 0 || currentBlock != null) {
                return;
            }
            String text = normalize(fallback.toString());
            fallback.setLength(0);
            if (!text.isEmpty()) {
                blocks.add(new ExtractedBlock(text,
                        new SourceLocation(pageNumber, slideNumber, sectionTitle)));
            }
        }

        private static boolean isBlockElement(String name) {
            return name.equals("p") || name.equals("li") || name.equals("pre")
                    || name.equals("blockquote") || name.matches("h[1-6]");
        }

        private static boolean isHeading(String name, String classes, String style) {
            return name.matches("h[1-6]")
                    || containsIgnoreCase(classes, "heading")
                    || containsIgnoreCase(classes, "title")
                    || containsIgnoreCase(style, "heading");
        }

        private static String elementName(String localName, String qName) {
            String value = localName == null || localName.isBlank() ? qName : localName;
            int separator = value == null ? -1 : value.indexOf(':');
            return (separator < 0 ? value : value.substring(separator + 1))
                    .toLowerCase(Locale.ROOT);
        }

        private static String attribute(Attributes attributes, String name) {
            if (attributes == null) {
                return null;
            }
            String value = attributes.getValue(name);
            if (value != null) {
                return value;
            }
            for (int index = 0; index < attributes.getLength(); index++) {
                if (name.equalsIgnoreCase(attributes.getLocalName(index))
                        || name.equalsIgnoreCase(attributes.getQName(index))) {
                    return attributes.getValue(index);
                }
            }
            return null;
        }

        private static boolean hasClass(String classes, String expected) {
            if (classes == null) {
                return false;
            }
            for (String value : classes.split("\\s+")) {
                if (value.equalsIgnoreCase(expected)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsIgnoreCase(String value, String expected) {
            return value != null && value.toLowerCase(Locale.ROOT)
                    .contains(expected.toLowerCase(Locale.ROOT));
        }

        private static String normalize(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").trim();
        }

        private record ElementFrame(String name, int depth, boolean pageStart, boolean slideStart) {
        }
    }
}
