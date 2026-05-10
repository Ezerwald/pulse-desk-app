# PulseDesk — Comment-to-Ticket Triage

A Spring Boot backend that automatically triages user comments using the
Hugging Face AI API. Comments that describe real issues are converted into
structured support tickets with a title, category, priority, and summary.

**Developed for the IBM Application Developer Internship technical assignment.**

---

## Features

* **Automated Triage**: AI-driven classification using `meta-llama/Llama-3.1-8B-Instruct`.
* **Structured Data**: Automatically extracts Title, Category, Priority, and Summary from raw text.
* **RESTful Architecture**: Clean API design with standardized HTTP status codes and global exception handling.
* **Self-Documenting**: Full OpenAPI/Swagger integration for rapid testing.
* **Containerized**: Production-ready Docker configuration with multi-stage builds and resource limits.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| **Language** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.3+ |
| **Database** | H2 (In-Memory) + Spring Data JPA |
| **AI Engine** | Hugging Face API — Llama 3.1 8B (Novita) |
| **API Docs** | Springdoc OpenAPI (Swagger UI) |
| **Deployment** | Docker & Docker Compose |

---

## Architecture

```text
[Client]
   │
   │  POST /comments
   ▼
[CommentController]
   │
   ▼
[CommentService] ──────────────────────────────► [HuggingFaceService]
   │                                                      │
   │  save comment                                        │  POST chat/completions
   ▼                                                      ▼
[CommentRepository]                             [HF Inference API]
   │                                            (Llama 3.1 8B via Novita)
   │                                                      │
   │                                                      │  JSON response
   │                                                      ▼
   │                                             parse isTicket, title,
   │                                             category, priority, summary
   │                                                      │
   │                ┌─────────────────────────────────────┘
   │                │  if isTicket = true
   │                ▼
   │          [TicketRepository]
   │                │
   │                │  save ticket
   │                ▼
   │            [H2 Database]
   │                │
   └───────────────►┘
   │
   │  201 Created
   ▼
[Client]  ◄── Comment + linked Ticket (or null)
```

A new comment flows through:
1. `POST /comments` is received by `CommentController`
2. `CommentService` saves the comment and calls `HuggingFaceService`
3. HuggingFaceService sends a structured prompt to Llama 3.1 8B and parses the JSON response
4. If the AI flags the comment as an issue, a `Ticket` is created and linked
5. The full result is returned in the API response

---
## Project Structure
```
src/main/java/com/pulsedesk/
├── config/ # Spring beans, OpenAPI config
├── controller/ # REST endpoints
├── service/ # Business logic + AI integration
├── repository/ # Spring Data JPA interfaces
├── model/ # JPA entities (Comment, Ticket)
├── dto/ # Request/Response objects
└── exception/ # Custom exceptions + global handler

src/main/resources/
├── application.properties
└── static/ # Frontend dashboard (HTML/CSS/JS)
```

---

## Getting Started

### Prerequisites

* **Java 21+** and **Maven 3.9+**.
* **Hugging Face Account**: A free account with an [API Token](https://huggingface.co/settings/tokens).
* **Model Access**: Ensure you have accepted the license for [Meta-Llama-3.1-8B-Instruct](https://huggingface.co/meta-llama/Meta-Llama-3.1-8B-Instruct) on the Hugging Face website to allow API access.

### Run Locally

**1. Clone the repository**
```bash
git clone https://github.com/Ezerwald/pulse-desk-app.git
cd pulsedesk
```

**2. Export your token to your shell environment**

On macOS/Linux:
```bash
export HF_TOKEN=hf_your_token_here
```

On Windows (PowerShell):
```powershell
$env:HF_TOKEN="hf_your_token_here"
```

**3. Run the application**
```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.

### Run with Docker

**1. Clone the repository**
```bash
git clone https://github.com/Ezerwald/pul se-desk-app.git
cd pulsedesk
```

**2. Set your Hugging Face token in `.env`**

PulseDesk follows **12-factor app** principles for configuration. 

Copy `.env.example` to `.env` and set your token:

```env
HF_TOKEN=hf_your_token_here
```

**3. Build and start**
```bash
docker-compose up --build
```

The application starts on `http://localhost:8080`.

---

## API Reference

| Method | Endpoint           | Description                          |
|--------|--------------------|--------------------------------------|
| POST   | `/comments`        | Submit a comment for AI triage       |
| GET    | `/comments`        | Get all comments with linked tickets |
| GET    | `/tickets`         | Get all generated tickets            |
| GET    | `/tickets/{id}`    | Get a single ticket by ID            |

### Example: Submit a Comment

**Request**
```http
POST /comments
Content-Type: application/json

{
  "author": "Anna",
  "text": "The login button crashes the app every time on Android.",
  "channel": "app_review"
}
```

**Response** `201 Created`
```json
{
  "id": 1,
  "author": "Anna",
  "text": "The login button crashes the app every time on Android.",
  "channel": "app_review",
  "createdAt": "2026-05-10T17:00:00",
  "hasTicket": true,
  "ticket": {
    "id": 1,
    "commentId": 1,
    "title": "Login button crashes on Android",
    "category": "BUG",
    "priority": "HIGH",
    "summary": "User reports login button consistently crashes the app on Android.",
    "createdAt": "2026-05-10T17:00:00"
  }
}
```

### Error Responses

All errors follow a consistent format:

```json
{
  "timestamp": "2026-05-10T17:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Ticket not found with id: 99"
}
```

Validation errors include a field-level `errors` map:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": {
    "author": "Author name is required",
    "text": "Comment must be between 5 and 2000 characters"
  }
}
```

---

## Useful URLs (while running)

| URL                                   | Description              |
|---------------------------------------|--------------------------|
| `http://localhost:8080`               | Frontend dashboard       |
| `http://localhost:8080/swagger-ui.html` | Interactive API docs   |
| `http://localhost:8080/h2-console`    | Database browser (dev)   |

---

## Running Tests

```bash
./mvnw test
```

Tests include:
- Unit tests for `HuggingFaceService` (Mockito mocks)
- Integration tests for `CommentController` (MockMvc + Spring context)