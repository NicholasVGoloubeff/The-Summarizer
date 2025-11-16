// src/main/java/com/example/lecturenav/service/EmbeddingService.java
package com.example.lecturenav.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final String cerebrasApiKey;

    // Cerebras uses OpenAI-compatible endpoints.
    // Embeddings endpoint:
    //   POST https://api.cerebras.ai/v1/embeddings
    private static final String EMBEDDINGS_URL = "https://api.cerebras.ai/v1/embeddings";

    // Model:
    // Use one of Cerebras’ supported models; they’re OpenAI-compatible.
    // For hackathon purposes, using the same model ID as chat is fine.
    // If Cerebras publishes a specific embedding model later, just swap the name here.
    private static final String EMBEDDINGS_MODEL = "llama3.1-8b";

    public EmbeddingService(RestTemplate restTemplate) {
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

    public double[] embedOne(String text) {
        if (text == null) {
            text = "";
        }

        // Request body (OpenAI-style embeddings API)
        Map<String, Object> body = new HashMap<>();
        body.put("model", EMBEDDINGS_MODEL);
        body.put("input", text);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cerebrasApiKey);
        headers.add("User-Agent", "lecturenav-backend/1.0");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(EMBEDDINGS_URL, entity, Map.class);

        Map<String, Object> respBody = response.getBody();
        if (respBody == null) {
            throw new IllegalStateException("No response body from Cerebras embeddings endpoint.");
        }

        Object dataObj = respBody.get("data");
        if (!(dataObj instanceof List)) {
            throw new IllegalStateException("Unexpected embeddings response: missing 'data' array.");
        }

        List<?> dataList = (List<?>) dataObj;
        if (dataList.isEmpty()) {
            throw new IllegalStateException("Embeddings response 'data' array is empty.");
        }

        Object first = dataList.get(0);
        if (!(first instanceof Map)) {
            throw new IllegalStateException("Unexpected embeddings 'data[0]' format.");
        }

        Map<?, ?> firstMap = (Map<?, ?>) first;
        Object embObj = firstMap.get("embedding");
        if (!(embObj instanceof List)) {
            throw new IllegalStateException("Embeddings 'embedding' field missing or not a list.");
        }

        List<?> embList = (List<?>) embObj;
        double[] vec = new double[embList.size()];
        for (int i = 0; i < embList.size(); i++) {
            Object val = embList.get(i);
            if (!(val instanceof Number)) {
                throw new IllegalStateException(
                        "Embedding value at index " + i + " is not numeric: " + val
                );
            }
            vec[i] = ((Number) val).doubleValue();
        }
        return vec;
    }
}
