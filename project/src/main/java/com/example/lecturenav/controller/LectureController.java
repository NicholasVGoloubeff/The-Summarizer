package com.example.lecturenav.controller;

import com.example.lecturenav.model.Lecture;
import com.example.lecturenav.service.LectureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
    public ResponseEntity<?> uploadLecture(@RequestPart("file") MultipartFile file,
                                           @RequestPart("name") String name) {
        try {
            Lecture lecture = lectureService.uploadLecture(name, file);

            Map<String, Object> resp = new HashMap<>();
            resp.put("lectureId", lecture.getId());
            resp.put("name", lecture.getName());

            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            e.printStackTrace();
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Failed to read PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        } catch (Exception e) {
            // This will catch embedding errors, Cerebras HTTP errors, etc.
            e.printStackTrace();
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // GET /api/lectures
    @GetMapping("/lectures")
    public List<Map<String, Object>> listLectures() {
        List<Lecture> lectures = lectureService.listLectures();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Lecture l : lectures) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", l.getId());
            map.put("name", l.getName());
            result.add(map);
        }

        return result;
    }

    // POST /api/ask
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        String question = (String) body.get("question");
        String answer = lectureService.answerQuestion(lectureId, question);

        Map<String, Object> resp = new HashMap<>();
        resp.put("answer", answer);
        return resp;
    }

    // POST /api/summarize
    @PostMapping("/summarize")
    public Map<String, Object> summarize(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        String summary = lectureService.summarizeLecture(lectureId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("summary", summary);
        return resp;
    }

    // POST /api/generate-questions
    @PostMapping("/generate-questions")
    public Map<String, Object> generateQuestions(@RequestBody Map<String, Object> body) {
        Long lectureId = ((Number) body.get("lectureId")).longValue();
        Integer num = body.get("numQuestions") != null
                ? ((Number) body.get("numQuestions")).intValue()
                : 5;

        String text = lectureService.generatePracticeQuestions(lectureId, num);

        Map<String, Object> resp = new HashMap<>();
        resp.put("questions", text);
        return resp;
    }
}
