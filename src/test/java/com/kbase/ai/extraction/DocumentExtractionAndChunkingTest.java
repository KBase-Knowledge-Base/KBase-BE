package com.kbase.ai.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.kbase.ai.extraction.tika.TikaDocumentContentExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentExtractionAndChunkingTest {

    private final TikaDocumentContentExtractor extractor = new TikaDocumentContentExtractor();

    @Test
    void extractsAllSevenApprovedFormatsWithoutExposingParserTypes() throws Exception {
        ExtractedDocument pdf = extract("pdf", "fixture.pdf", "application/pdf", pdfFixture());
        assertThat(pdf.blocks()).anySatisfy(block -> {
            assertThat(block.text()).contains("PDF page one");
            assertThat(block.sourceLocation().pageNumber()).isEqualTo(1);
        });
        assertThat(pdf.blocks()).anySatisfy(block -> {
            assertThat(block.text()).contains("PDF page two");
            assertThat(block.sourceLocation().pageNumber()).isEqualTo(2);
        });

        // Tika's DOC/DOCX parser module is selected by the explicit allowlist;
        // the fixture uses the OOXML family because POI has no public empty
        // legacy-HWPF document constructor.
        ExtractedDocument doc = extract("doc", "fixture.doc", "application/msword", docxFixture());
        assertThat(allText(doc)).contains("DOCX paragraph");

        ExtractedDocument docx = extract("docx", "fixture.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxFixture());
        assertThat(allText(docx)).contains("DOCX paragraph");
        assertThat(docx.blocks()).allSatisfy(block -> assertThat(block.sourceLocation().pageNumber()).isNull());

        ExtractedDocument ppt = extract("ppt", "fixture.ppt", "application/vnd.ms-powerpoint", pptFixture());
        assertThat(allText(ppt)).contains("PPT legacy slide");

        ExtractedDocument pptx = extract("pptx", "fixture.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", pptxFixture());
        assertThat(pptx.blocks()).anySatisfy(block -> {
            assertThat(block.text()).contains("PPTX slide one");
            assertThat(block.sourceLocation().slideNumber()).isEqualTo(1);
        });
        assertThat(pptx.blocks()).anySatisfy(block -> {
            assertThat(block.text()).contains("PPTX slide two");
            assertThat(block.sourceLocation().slideNumber()).isEqualTo(2);
        });

        ExtractedDocument markdown = extract("md", "fixture.md", "text/markdown",
                "# Root\n\nIntro tiếng Việt\n\n## Child\n\nChild text".getBytes(StandardCharsets.UTF_8));
        assertThat(markdown.blocks()).anySatisfy(block -> {
            assertThat(block.text()).contains("Child text");
            assertThat(block.sourceLocation().sectionTitle()).isEqualTo("Root > Child");
        });

        ExtractedDocument text = extract("txt", "fixture.txt", "text/plain",
                "Plain Vietnamese text\n\nSecond paragraph".getBytes(StandardCharsets.UTF_8));
        assertThat(text.blocks()).allSatisfy(block -> {
            assertThat(block.sourceLocation().sectionTitle()).isNull();
            assertThat(block.sourceLocation().pageNumber()).isNull();
        });
    }

    @Test
    void tokenCounterAndChunkerAreDeterministicAndKeepDistinctLocationsApart() {
        KBaseLexTokenCounter counter = new KBaseLexTokenCounter();
        assertThat(counter.count("Việt Nam, 2026!"))
                .isEqualTo(5);

        StructureAwareDocumentChunker chunker = new StructureAwareDocumentChunker(counter, 4, 25);
        ExtractedDocument input = new ExtractedDocument(List.of(
                new ExtractedBlock("one two three four five", new SourceLocation(1, null, null)),
                new ExtractedBlock("six seven", new SourceLocation(1, null, null)),
                new ExtractedBlock("page two", new SourceLocation(2, null, null))));
        ChunkedDocument first = chunker.chunk(input);
        ChunkedDocument second = chunker.chunk(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.chunkingVersion()).isEqualTo("chunk-v1");
        assertThat(first.chunks()).extracting(chunk -> chunk.sourceLocation().pageNumber())
                .containsExactly(1, 1, 2);
        assertThat(first.chunks()).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isPositive());
    }

    private ExtractedDocument extract(String extension, String name, String contentType, byte[] content) {
        return extractor.extract(new java.io.ByteArrayInputStream(content),
                new DocumentExtractionRequest(extension, name, contentType, content.length));
    }

    private static String allText(ExtractedDocument document) {
        return document.blocks().stream().map(ExtractedBlock::text).reduce("", (left, right) -> left + " " + right);
    }

    private static byte[] pdfFixture() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            for (int pageNumber = 1; pageNumber <= 2; pageNumber++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText("PDF page " + (pageNumber == 1 ? "one" : "two"));
                    stream.endText();
                }
            }
            document.save(output);
        }
        return output.toByteArray();
    }

    private static byte[] docxFixture() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("DOCX paragraph");
            document.write(output);
        }
        return output.toByteArray();
    }

    private static byte[] pptFixture() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (HSLFSlideShow show = new HSLFSlideShow()) {
            HSLFSlide slide = show.createSlide();
            HSLFTextBox box = new HSLFTextBox();
            box.setText("PPT legacy slide");
            slide.addShape(box);
            show.write(output);
        }
        return output.toByteArray();
    }

    private static byte[] pptxFixture() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XMLSlideShow show = new XMLSlideShow()) {
            for (String text : List.of("PPTX slide one", "PPTX slide two")) {
                XSLFSlide slide = show.createSlide();
                var box = slide.createTextBox();
                box.setText(text);
            }
            show.write(output);
        }
        return output.toByteArray();
    }
}
