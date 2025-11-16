package com.example.lecturenav.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfUtil {

    /**
     * Extract all text from the PDF input stream.
     * This now reads the entire document (no page limit).
     */
    public static String extractText(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // No page limits: take the whole document
            return stripper.getText(doc);
        }
    }

    /**
     * Simple overlapping character-based chunking.
     * Example: chunkText(text, 800, 200) → chunks of up to 800 chars, each overlapping
     * the previous by 200 chars to keep context continuity.
     */
    public static List<String> chunkText(String text, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int len = text.length();
        int start = 0;

        while (start < len) {
            int end = Math.min(start + maxChunkSize, len);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == len) {
                break;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }

        return chunks;
    }
}
