// src/main/java/com/example/lecturenav/model/Lecture.java
package com.example.lecturenav.model;

import java.util.List;

public class Lecture {
    private Long id;
    private String name;
    private List<Chunk> chunks;

    public Lecture(Long id, String name, List<Chunk> chunks) {
        this.id = id;
        this.name = name;
        this.chunks = chunks;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Chunk> getChunks() { return chunks; }
}
