package com.docqa.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns an uploaded file into plain text, keeping page numbers for PDFs. */
@Component
public class TextExtractor {

    public record PageText(Integer pageNumber, String text) {}

    public List<PageText> extract(String filename, byte[] bytes) throws IOException {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (name.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        if (isPlainText(name)) {
            return List.of(new PageText(null, new String(bytes, StandardCharsets.UTF_8)));
        }
        throw new IllegalArgumentException("Unsupported file type. Use PDF, DOCX, TXT, MD, CSV, JSON or HTML.");
    }

    public static boolean isSupported(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".docx") || isPlainText(name);
    }

    private static boolean isPlainText(String name) {
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".html") || name.endsWith(".htm")
                || name.endsWith(".xml") || name.endsWith(".log");
    }

    private List<PageText> extractPdf(byte[] bytes) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int total = pdf.getNumberOfPages();
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(pdf);
                if (!text.isBlank()) {
                    pages.add(new PageText(p, text));
                }
            }
        }
        return pages;
    }

    private List<PageText> extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return List.of(new PageText(null, extractor.getText()));
        }
    }
}
