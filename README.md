# LectureNav – AI Lecture Assistant

LectureNav is a small full-stack project that helps students work with lecture PDFs:

- Upload a **lecture PDF**
- Ask **questions** about the lecture content
- Generate a concise **summary**
- Create **practice questions** with answers
- See a **clickable history** of Q&A, summaries, and practice runs

The backend is a Spring Boot app (Java 11) with a local embedding engine and Cerebras LLM integration.  
The frontend is a single static HTML+JS page served by Spring Boot.

---
## How2Run

- ./run-dev.sh

# Then open

- http://localhost:8080/index.html

## Tech Stack

**Backend**

- Java 11
- Spring Boot 2.7.x
- Maven
- Apache PDFBox (PDF text extraction)
- Custom local “hashed” embeddings + cosine similarity
- Cerebras LLM API (`gpt-4.1` / similar) for:
  - Q&A over lecture chunks
  - Summaries
  - Practice questions

**Frontend**

- Static `index.html` served from `src/main/resources/static/`
- Modern CSS, no framework
- Vanilla JS talking to the backend via `fetch()`

---

## Project Structure

Rough layout (backend + frontend in one project):

```text
project/
├── pom.xml
├── run-dev.sh           # helper script to run the app in one step
├── .env                 # local-only, holds CEREBRAS_API_KEY (not committed)
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com/example/lecturenav
│   │   │       ├── LecturenavBackendApplication.java
│   │   │       ├── controller/
│   │   │       │   └── LectureController.java
│   │   │       ├── model/
│   │   │       │   ├── Lecture.java
│   │   │       │   └── Chunk.java
│   │   │       ├── service/
│   │   │       │   ├── LectureService.java
│   │   │       │   └── EmbeddingService.java
│   │   │       └── util/
│   │   │           ├── PdfUtil.java
│   │   │           └── VectorUtil.java
│   │   └── resources
│   │       ├── application.properties
│   │       └── static/
│   │           └── index.html      # frontend UI
│   └── test
│       └── ... (optional)
└── README.md
