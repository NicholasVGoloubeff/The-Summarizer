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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LectureService {

    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;

    private final Map<Long, Lecture> lectures = new HashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    // Cerebras settings
    private static final String CEREBRAS_URL = "https://api.cerebras.ai/v1/chat/completions";
    private static final String CEREBRAS_MODEL = "llama3.1-8b";

    private final String cerebrasApiKey;

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

    public Lecture uploadLecture(String name, MultipartFile file) throws IOException {
        String text = PdfUtil.extractText(file.getInputStream());
        List<String> chunksText = PdfUtil.chunkText(text, 800, 200);

        List<Chunk> chunks = new ArrayList<>();
        int idx = 0;
        for (String chunkText : chunksText) {
            double[] emb = embeddingService.embedOne(chunkText);
            chunks.add(new Chunk(idx++, chunkText, emb));
        }

        Long id = idGen.getAndIncrement();
        Lecture lecture = new Lecture(id, name, chunks);
        lectures.put(id, lecture);
        return lecture;
    }

    public List<Lecture> listLectures() {
        return new ArrayList<>(lectures.values());
    }

    public Lecture getLecture(Long id) {
        return lectures.get(id);
    }

    // Retrieve top-k relevant chunks for a question
    private List<Chunk> getTopChunks(Lecture lecture, String question, int k) {
        double[] qEmb = embeddingService.embedOne(question);
        List<Chunk> chunks = lecture.getChunks();

        List<Map.Entry<Chunk, Double>> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            double sim = VectorUtil.cosineSimilarity(qEmb, c.getEmbedding());
            scored.add(new AbstractMap.SimpleEntry<>(c, sim));
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Chunk> top = new ArrayList<>();
        int limit = Math.min(k, scored.size());
        for (int i = 0; i < limit; i++) {
            top.add(scored.get(i).getKey());
        }
        return top;
    }

    /**
     * Call Cerebras chat completions API with a system prompt + user content.
     */
    private String callChat(String systemPrompt, String userContent) {
        // Build request body
        Map<String, Object> body = new HashMap<>();
        body.put("model", CEREBRAS_MODEL);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        body.put("messages", messages);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cerebrasApiKey);
        headers.add("User-Agent", "lecturenav-backend/1.0");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(CEREBRAS_URL, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            return "No response from Cerebras.";
        }

        Object choicesObj = responseBody.get("choices");
        if (!(choicesObj instanceof List)) {
            return "Unexpected response format from Cerebras (no choices array).";
        }

        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) {
            return "Cerebras returned no choices.";
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) {
            return "Unexpected choice format from Cerebras.";
        }

        Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map)) {
            return "Unexpected message format from Cerebras.";
        }

        Map<?, ?> messageMap = (Map<?, ?>) messageObj;
        Object contentObj = messageMap.get("content");
        if (contentObj == null) {
            return "Cerebras reply had no content.";
        }

        return contentObj.toString();
    }

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
                "You are a helpful college tutor. "
              + "Use ONLY the provided lecture excerpts to answer the question. "
              + "If the answer is not clearly supported, say you are not sure. "
              + "Explain at a 2nd-year undergraduate level, concisely but clearly.";

        String userContent = "CONTEXT:\n" + ctx + "\nQUESTION:\n" + question;

        return callChat(systemPrompt, userContent);
    }

    public String summarizeLecture(Long lectureId) {
        Lecture lecture = getLecture(lectureId);
        if (lecture == null) {
            throw new IllegalArgumentException("Lecture not found");
        }

        List<Chunk> chunks = lecture.getChunks().subList(
                0, Math.min(5, lecture.getChunks().size())
        );
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : chunks) {
            ctx.append(c.getText()).append("\n\n");
        }

        String systemPrompt =
                "You are summarizing lecture notes for a college student. "
              + "Write a clear, concise summary (4-8 sentences) of the content.";

        return callChat(systemPrompt, ctx.toString());
    }

    public String generatePracticeQuestions(Long lectureId, int numQuestions) {
        Lecture lecture = getLecture(lectureId);
        if (lecture == null) {
            throw new IllegalArgumentException("Lecture not found");
        }

        List<Chunk> chunks = lecture.getChunks().subList(
                0, Math.min(5, lecture.getChunks().size())
        );
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : chunks) {
            ctx.append(c.getText()).append("\n\n");
        }

        String systemPrompt =
                "You are a tutor generating practice questions for a college student. "
              + "Using only the provided lecture content, create numbered questions with answers. "
              + "Mix conceptual and computational reasoning if appropriate. "
              + "Format as:\n"
              + "Q1: ...\n"
              + "A1: ...";

        String userContent = "CONTENT:\n" + ctx + "\nNumber of questions: " + numQuestions;

        return callChat(systemPrompt, userContent);
    }
}
