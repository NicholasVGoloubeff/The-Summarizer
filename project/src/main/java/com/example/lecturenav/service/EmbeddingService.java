package com.example.lecturenav.service;

import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    // Dimension of our "fake" embedding vectors
    private static final int DIM = 256;

    /**
     * Very simple local embedding:
     * - lowercase
     * - split on whitespace
     * - hash each token into a bucket
     * - count frequency in that bucket
     * - L2-normalize the vector
     *
     * This is NOT a neural embedding, but it is:
     * - deterministic
     * - cheap
     * - good enough for cosine similarity ranking in a hackathon demo
     */
    public double[] embedOne(String text) {
        double[] vec = new double[DIM];
        if (text == null || text.isEmpty()) {
            return vec;
        }

        String[] tokens = text.toLowerCase().split("\\s+");
        for (String token : tokens) {
            int h = token.hashCode();
            int idx = Math.floorMod(h, DIM);
            vec[idx] += 1.0;
        }

        // L2-normalize so cosine similarity behaves well
        double norm = 0.0;
        for (int i = 0; i < DIM; i++) {
            norm += vec[i] * vec[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vec[i] /= norm;
            }
        }

        return vec;
    }
}
