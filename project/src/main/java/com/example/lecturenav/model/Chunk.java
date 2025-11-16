// src/main/java/com/example/lecturenav/model/Chunk.java
package com.example.lecturenav.model;

public class Chunk {
    private int index;
    private String text;
    private double[] embedding;

    public Chunk(int index, String text, double[] embedding) {
        this.index = index;
        this.text = text;
        this.embedding = embedding;
    }

    public int getIndex() { return index; }
    public String getText() { return text; }
    public double[] getEmbedding() { return embedding; }
}
