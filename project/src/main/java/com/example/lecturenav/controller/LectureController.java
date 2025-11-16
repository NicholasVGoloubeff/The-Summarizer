// src/main/java/com/example/lecturenav/controller/LectureController.java
package com.example.lecturenav.controller;

import java.util.stream.Collectors;
import com.example.lecturenav.model.Lecture;
import com.example.lecturenav.service.LectureService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // allow your frontend
public class LectureController {

    private final LectureService lectureService;

    public LectureController(LectureService lectureService) {
        this.lectureService = lectureService;
    }

    // POST /api/lectures (multipart form)
    @PostMapping(value = "/lectures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadLecture(@RequestPart("file") MultipartFile file, 
                                            @RequestPart("name") String name) throws IOException {
        Lecture lecture = lectureService.uploadLecture(name, file);
        return Map.of(
                "lectureId", lecture.getId(),
                "name", lecture.getName()
        );
    }

    // GET /api/lectures
    @GetMapping("/lectures")
    public List<Map<String, Object>> listLectures() {
        return lectureService.listLectures().stream()
                .map(l -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", l.getId());
                    m.put("name", l.getName());
                    return m;
                })
                .collect(Collectors.toList());
    }


    // POST /api/ask
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        String question = (String) body.get("question");
        String answer = lectureService.answerQuestion(lectureId, question);
        return Map.of("answer", answer);
    }

    // POST /api/summarize
    @PostMapping("/summarize")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        String summary = lectureService.summarizeLecture(lectureId);
        return Map.of("summary", summary);
    }

    // POST /api/generate-questions
    @PostMapping("/generate-questions")
    public Map<String, Object> generateQuestions(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        Integer num = body.get("numQuestions") != null
                ? ((Number) body.get("numQuestions")).intValue()
                : 5;
        String text = lectureService.generatePracticeQuestions(lectureId, num);
        return Map.of("questions", text);
    }
}
