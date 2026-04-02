# Assessment Service Medicology

`assessment-service-medicology` là service chuyên trách domain assessment của Medicology. Repo này được dựng từ 4 plan implementation để tách scoring ra khỏi `learning-service`, nhưng vẫn giữ contract tích hợp rõ ràng cho progress/streak.

## Mục tiêu

- Tạo foundation Spring Boot đồng bộ với các service hiện có trong workspace.
- Chốt ownership domain assessment: `assessment`, `question`, `attempt`, `answer`, `result`.
- Cung cấp API admin + user cho vòng đời làm bài và chấm điểm.
- Giữ integration boundary rõ ràng để rollout sang `learning-service` an toàn.

## Cấu trúc chính

```text
src/main/java/com/medicology/assessment/
  config/
  controller/
  dto/
  entity/
  exception/
  repository/
  security/jwt/
  service/
  utils/
  wrapper/
docs/
  assessment-learning-boundary.md
```

## Endpoint chính

- `GET /api/v1/assessments`
- `POST /api/v1/assessments`
- `GET /api/v1/assessments/{assessmentId}`
- `PUT /api/v1/assessments/{assessmentId}`
- `DELETE /api/v1/assessments/{assessmentId}`
- `GET /api/v1/assessments/{assessmentId}/questions`
- `POST /api/v1/assessments/{assessmentId}/questions`
- `PUT /api/v1/questions/{questionId}`
- `DELETE /api/v1/questions/{questionId}`
- `POST /api/v1/assessments/{assessmentId}/attempts`
- `POST /api/v1/attempts/{attemptId}/answers`
- `POST /api/v1/attempts/{attemptId}/submit`
- `GET /api/v1/attempts/{attemptId}/result`
- `GET /api/v1/users/me/attempts`
- `GET /api/v1/admin/attempts`

## Local setup

Biến môi trường tối thiểu:

- `ASSESSMENT_DB_URL`
- `ASSESSMENT_DB_USERNAME`
- `ASSESSMENT_DB_PASSWORD`
- `JWT_SECRET`

Chạy local:

```bash
./mvnw spring-boot:run
```

Swagger:

- `http://localhost:8082/swagger-ui.html`

## Boundary với Learning

Xem [docs/assessment-learning-boundary.md](docs/assessment-learning-boundary.md).

Repo mới không copy secrets từ các service cũ. Toàn bộ cấu hình nhạy cảm đi qua environment variables.
