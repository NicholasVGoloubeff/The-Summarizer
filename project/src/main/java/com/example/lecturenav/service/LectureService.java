package com.example.lecturenav.service;

import com.example.lecturenav.model.Chunk;
import com.example.lecturenav.model.Lecture;
import com.example.lecturenav.util.PdfUtil;
import com.example.lecturenav.util.VectorUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LectureService {

    // ===== SAFETY LIMITS FOR HACKATHON =====
    // Only use the first N characters of the PDF text
    private static final int MAX_TEXT_CHARS = 20_000;

    // Only keep embeddings for the first N chunks
    private static final int MAX_CHUNKS = 40;

    // Chunking parameters
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 200;

    // Cerebras chat completions endpoint (OpenAI-compatible)
    private static final String CHAT_URL = "https://api.cerebras.ai/v1/chat/completions";
    private static final String CHAT_MODEL = "llama3.1-8b";

    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;
    private final String cerebrasApiKey;

    private final Map<Long, Lecture> lectures = new HashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public LectureService(EmbeddingService embeddingService, RestTemplate restTemplate) {
        this.embeddingService = embeddingService;
        this.restTemplate = restTemplate;

        String key = System.getenv("CEREBRAS_API_KEY");
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "CEREBRAS_API_KEY environment variable is not set. " +
                    "Set it before running the application."
            );
        }
        this.cerebrasApiKey = key;
    }

    // ====== UPLOAD + CHUNK + EMBED ======
    public Lecture uploadLecture(String name, MultipartFile file) throws IOException {
        String text = PdfUtil.extractText(file.getInputStream());
        if (text == null) {
            text = "";
        }

        // 1) Hard cap total text length
        if (text.length() > MAX_TEXT_CHARS) {
            System.out.println("PDF text length = " + text.length()
                    + ", truncating to " + MAX_TEXT_CHARS + " characters for memory safety.");
            text = text.substring(0, MAX_TEXT_CHARS);
        } else {
            System.out.println("PDF text length = " + text.length() + " characters.");
        }

        // 2) Chunk the truncated text
        List<String> chunksText = PdfUtil.chunkText(text, CHUNK_SIZE, CHUNK_OVERLAP);
        System.out.println("Initial number of chunks = " + chunksText.size());

        // 3) Limit number of chunks to embed
        if (chunksText.size() > MAX_CHUNKS) {
            chunksText = chunksText.subList(0, MAX_CHUNKS);
            System.out.println("Limiting chunks to first " + MAX_CHUNKS + " for embeddings.");
        }

        List<Chunk> chunks = new ArrayList<>();
        int idx = 0;
        for (String chunkText : chunksText) {
            double[] emb = embeddingService.embedOne(chunkText);
            chunks.add(new Chunk(idx++, chunkText, emb));
        }

        Long id = idGen.getAndIncrement();
        Lecture lecture = new Lecture(id, name, chunks);
        lectures.put(id, lecture);

        System.out.println("Lecture " + id + " stored with " + chunks.size() + " embedded chunks.");
        return lecture;
    }

    public List<Lecture> listLectures() {
        return new ArrayList<>(lectures.values());
    }

    public Lecture getLecture(Long id) {
        return lectures.get(id);
    }

    // ====== RAG: RETRIEVE TOP CHUNKS ======
    private List<Chunk> getTopChunks(Lecture lecture, String question, int k) {
        double[] qEmb = embeddingService.embedOne(question);
        List<Chunk> chunks = lecture.getChunks();

        List<Map.Entry<Chunk, Double>> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            double sim = VectorUtil.cosineSimilarity(qEmb, c.getEmbedding());
            scored.add(new AbstractMap.SimpleEntry<Chunk, Double>(c, sim));
        }

        scored.sort(new Comparator<Map.Entry<Chunk, Double>>() {
            @Override
            public int compare(Map.Entry<Chunk, Double> a, Map.Entry<Chunk, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });

        List<Chunk> top = new ArrayList<Chunk>();
        int limit = Math.min(k, scored.size());
        for (int i = 0; i < limit; i++) {
            top.add(scored.get(i).getKey());
        }
        return top;
    }

    // ====== LOCAL callChat USING CEREBRAS ======
    private String callChat(String systemPrompt, String userContent) {
        // Build request body
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("model", CHAT_MODEL);

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();

        Map<String, String> sysMsg = new HashMap<String, String>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        Map<String, String> userMsg = new HashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        messages.add(sysMsg);
        messages.add(userMsg);

        body.put("messages", messages);
        body.put("temperature", 0.2);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cerebrasApiKey);
        headers.add("User-Agent", "lecturenav-backend/1.0");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(body, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(CHAT_URL, entity, Map.class);

            Map respBody = response.getBody();
            if (respBody == null) {
                throw new IllegalStateException("No response body from Cerebras chat endpoint.");
            }

            Object choicesObj = respBody.get("choices");
            if (!(choicesObj instanceof List)) {
                throw new IllegalStateException(
                        "Unexpected chat response: missing 'choices' array. Body: " + respBody
                );
            }

            List choices = (List) choicesObj;
            if (choices.isEmpty()) {
                throw new IllegalStateException("Chat response 'choices' array is empty.");
            }

            Object first = choices.get(0);
            if (!(first instanceof Map)) {
                throw new IllegalStateException("Unexpected chat 'choices[0]' format.");
            }

            Map firstMap = (Map) first;
            Object msgObj = firstMap.get("message");
            if (!(msgObj instanceof Map)) {
                throw new IllegalStateException("Chat 'message' field missing in choices[0].");
            }

            Map msgMap = (Map) msgObj;
            Object contentObj = msgMap.get("content");
            if (!(contentObj instanceof String)) {
                throw new IllegalStateException("Chat 'message.content' is missing or not a string.");
            }

            return (String) contentObj;

        } catch (HttpStatusCodeException e) {
            System.err.println("Cerebras chat HTTP error: " + e.getStatusCode());
            System.err.println("Response body: " + e.getResponseBodyAsString());
            throw new RuntimeException(
                    "Cerebras chat HTTP error: " + e.getStatusCode() +
                            " - " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            System.err.println("Cerebras chat error: " + e.getMessage());
            throw new RuntimeException("Cerebras chat error: " + e.getMessage(), e);
        }
    }

    // ====== PUBLIC METHODS THAT USE callChat ======

    public String answerQuestion(Long lectureId, String question) {
        Lecture lecture = getLecture(lectureId);
        if (lecture == null) {
            throw new IllegalArgumentException("Lecture not found");
        }

        List<Chunk> topChunks = getTopChunks(lecture, question, 4);
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : topChunks) {
            ctx.append("[Chunk ").append(c.getIndex()).append("]\n")
               .append(c.getText()).append("\n\n");
        }

        String systemPrompt =
                "You are a helpful college tutor. " +
                "Use ONLY the provided lecture excerpts to answer the question. " +
                "If the answer is not clearly supported, say you are not sure. " +
                "Explain at a 2nd-year undergraduate level, concisely but clearly.";

        String userContent = "CONTEXT:\n" + ctx + "\nQUESTION:\n" + question;

        return callChat(systemPrompt, userContent);
    }

    public String summarizeLecture(Long lectureId) {
        Lecture lecture = getLecture(lectureId);
        if (lecture == null) throw new IllegalArgumentException("Lecture not found");

        List<Chunk> chunks = lecture.getChunks()
                .subList(0, Math.min(5, lecture.getChunks().size()));

        StringBuilder ctx = new StringBuilder();
        for (Chunk c : chunks) {
            ctx.append(c.getText()).append("\n\n");
        }

        String systemPrompt =
                "You are summarizing lecture notes for a college student. " +
                "Write a clear, concise summary (4-8 sentences) of the content.";

        return callChat(systemPrompt, ctx.toString());
    }

    public String generatePracticeQuestions(Long lectureId, int numQuestions) {
        Lecture lecture = getLecture(lectureId);
        if (lecture == null) throw new IllegalArgumentException("Lecture not found");

        List<Chunk> chunks = lecture.getChunks()
                .subList(0, Math.min(5, lecture.getChunks().size()));

        StringBuilder ctx = new StringBuilder();
        for (Chunk c : chunks) {
            ctx.append(c.getText()).append("\n\n");
        }

        String systemPrompt =
                "You are a tutor generating practice questions for a college student. " +
                "Using only the provided lecture content, create numbered questions with answers. " +
                "Mix conceptual and computational reasoning if appropriate. " +
                "Format as:\n" +
                "Q1: ...\n" +
                "A1: ...";

        String userContent = "CONTENT:\n" + ctx + "\nNumber of questions: " + numQuestions;

        return callChat(systemPrompt, userContent);
    }
}
