// src/main/java/com/example/lecturenav/util/PdfUtil.java
package com.example.lecturenav.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfUtil {

    public static String extractText(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public static List<String> chunkText(String text, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(len, start + maxChars);
            String chunk = text.substring(start, end);
            chunks.add(chunk);
            // step forward but keep overlap
            start = end - overlap;
            if (start < 0) start = 0;
        }
        return chunks;
    }
}
