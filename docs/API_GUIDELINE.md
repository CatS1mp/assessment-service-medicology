# Assessment Service API Guideline

## 1. Mục đích tài liệu

Tài liệu này mô tả endpoint trong `assessment-service-medicology`, gồm:

- Công dụng của endpoint
- Input request
- Output response
- Màn hình hoặc flow nên sử dụng
- Lưu ý triển khai FE/BE

## 2. Base URL và tài liệu kỹ thuật

- Local: `http://localhost:8083`
- Staging: `Chưa cấu hình riêng trong repo`
- Production: `https://assessment-service-medicology-0dba1b186cc6.herokuapp.com`

Swagger / OpenAPI:

- `GET /swagger-ui.html`
- `GET /api-docs`
- `GET /` redirect sang Swagger

## 3. Authentication và quy ước chung

### 3.1 Authentication

- Public: `/`, Swagger, `/actuator/health`
- Mọi endpoint business còn lại yêu cầu JWT Bearer
- Yêu cầu `ROLE_ADMIN`: CRUD assessment, CRUD question, `GET /api/v1/admin/attempts`
- Learner flow dùng JWT thường: discovery assessment, start attempt, save answer, submit, xem kết quả, xem lịch sử của tôi

Header chuẩn:

```http
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

### 3.2 Kiểu lỗi

Format lỗi hiện tại:

```json
{
  "status": 400,
  "code": 1400,
  "message": "Mô tả lỗi",
  "path": "/api/v1/assessments",
  "timestamp": "2026-04-13T10:30:00Z"
}
```

Mapping chính:

- `ApiException` giữ nguyên HTTP status và business code
- Validation và `IllegalArgumentException` trả `400`, code nội bộ `1400`
- `AccessDeniedException` trả `403`, code nội bộ `1403`
- Lỗi chưa bắt riêng trả `500`, code nội bộ `1500`

### 3.3 Quy ước response

- Service dùng wrapper `ApiResponse<T>` theo format:

```json
{
  "code": 0,
  "message": "Assessment retrieved successfully.",
  "data": {}
}
```

- Success code hiện cố định là `0`
- Các endpoint delete trả `data: null`
- Nhóm admin thường trả `AssessmentSummaryResponse`, `AssessmentDetailResponse`, `QuestionResponse`
- Nhóm learner/attempt trả `StudentAssessmentDetailResponse`, `AttemptStartResponse`, `AttemptResultResponse`, `AttemptSummaryResponse`

## 4. Tóm tắt mapping theo màn hình

| Màn hình / flow | Endpoint chính |
| --- | --- |
| Mở section để kiểm tra có quiz hay không | `GET /api/v1/sections/{sectionId}/assessment` |
| Bắt đầu làm bài | `POST /api/v1/assessments/{assessmentId}/attempts` |
| Chọn đáp án từng câu | `POST /api/v1/attempts/{attemptId}/answers` |
| Nộp bài | `POST /api/v1/attempts/{attemptId}/submit` |
| Xem kết quả | `GET /api/v1/attempts/{attemptId}/result` |
| Lịch sử bài làm của tôi | `GET /api/v1/users/me/attempts` |
| CMS danh sách assessment | `GET /api/v1/assessments` |
| CMS tạo/sửa/xóa assessment | `POST/PUT/DELETE /api/v1/assessments...` |
| CMS quản lý câu hỏi | `GET/POST /api/v1/assessments/{assessmentId}/questions`, `PUT/DELETE /api/v1/questions/{questionId}` |
| Admin xem toàn bộ attempts | `GET /api/v1/admin/attempts` |

## 5. Nhóm API — Assessment discovery và attempt

### 5.1 Flow cho learner

- `GET /api/v1/sections/{sectionId}/assessment`
  - **Mục đích:** Tìm assessment active của section để learner bắt đầu làm bài
  - **Query / Path:** `sectionId=<UUID>`, `lessonId=<UUID>` là optional
  - **Response:** `200 OK`, `ApiResponse<StudentAssessmentDetailResponse>`
  - **Ghi chú:** Dùng user hiện tại từ JWT principal
- `POST /api/v1/assessments/{assessmentId}/attempts`
  - **Mục đích:** Tạo attempt mới cho learner
  - **Query / Path:** `assessmentId=<UUID>`
  - **Response:** `201 Created`, `ApiResponse<AttemptStartResponse>`
  - **Ghi chú:** Response đã bao gồm danh sách câu hỏi để render bài làm
- `POST /api/v1/attempts/{attemptId}/answers`
  - **Mục đích:** Lưu đáp án từng câu
  - **Body:** `AttemptAnswerRequest` với `questionId`, `selectedOptionId`
  - **Response:** `200 OK`, `ApiResponse<AttemptAnswerResponse>`
- `POST /api/v1/attempts/{attemptId}/submit`
  - **Mục đích:** Nộp bài và chấm điểm
  - **Response:** `200 OK`, `ApiResponse<AttemptResultResponse>`
- `GET /api/v1/attempts/{attemptId}/result`
  - **Mục đích:** Xem lại kết quả attempt
  - **Response:** `200 OK`, `ApiResponse<AttemptResultResponse>`
- `GET /api/v1/users/me/attempts`
  - **Mục đích:** Lấy lịch sử bài làm của user hiện tại
  - **Response:** `200 OK`, `ApiResponse<List<AttemptSummaryResponse>>`

## 6. Nhóm API — Quản trị assessment

### 6.1 CRUD bài kiểm tra

- `GET /api/v1/assessments`
  - **Mục đích:** Liệt kê toàn bộ assessment cho CMS/admin
  - **Response:** `200 OK`, `ApiResponse<List<AssessmentSummaryResponse>>`
- `POST /api/v1/assessments`
  - **Mục đích:** Tạo assessment mới
  - **Body:** `AssessmentRequest`
  - **Response:** `201 Created`, `ApiResponse<AssessmentDetailResponse>`
  - **Ghi chú:** Field chính của request gồm `title`, `description`, `courseId`, `sectionId`, `lessonId`, `passScore`, `timeLimitMinutes`, `status`, `active`
- `GET /api/v1/assessments/{assessmentId}`
  - **Mục đích:** Lấy chi tiết một assessment cho admin
  - **Response:** `200 OK`, `ApiResponse<AssessmentDetailResponse>`
- `PUT /api/v1/assessments/{assessmentId}`
  - **Mục đích:** Cập nhật assessment
  - **Body:** `AssessmentRequest`
  - **Response:** `200 OK`, `ApiResponse<AssessmentDetailResponse>`
- `DELETE /api/v1/assessments/{assessmentId}`
  - **Mục đích:** Xóa assessment
  - **Response:** `200 OK`, `ApiResponse<Void>`

## 7. Nhóm API — Quản trị câu hỏi

### 7.1 CRUD question trong assessment

- `GET /api/v1/assessments/{assessmentId}/questions`
  - **Mục đích:** Lấy danh sách câu hỏi của assessment
  - **Response:** `200 OK`, `ApiResponse<List<QuestionResponse>>`
- `POST /api/v1/assessments/{assessmentId}/questions`
  - **Mục đích:** Tạo câu hỏi mới
  - **Body:** `QuestionRequest`
  - **Response:** `201 Created`, `ApiResponse<QuestionResponse>`
  - **Ghi chú:** Request gồm `content`, `explanation`, `type`, `displayOrder`, `points`, `active`, `options`
- `PUT /api/v1/questions/{questionId}`
  - **Mục đích:** Cập nhật câu hỏi
  - **Body:** `QuestionRequest`
  - **Response:** `200 OK`, `ApiResponse<QuestionResponse>`
- `DELETE /api/v1/questions/{questionId}`
  - **Mục đích:** Xóa câu hỏi
  - **Response:** `200 OK`, `ApiResponse<Void>`

## 8. Nhóm API — Theo dõi attempt cho admin

### 8.1 Monitoring và support

- `GET /api/v1/admin/attempts`
  - **Mục đích:** Xem tất cả bài làm trong hệ thống
  - **Response:** `200 OK`, `ApiResponse<List<AttemptSummaryResponse>>`
  - **Ghi chú:** Yêu cầu `ROLE_ADMIN`

## 9. Webhook / callback (nếu có)

- Không áp dụng

## 10. Hợp đồng với service khác

- `courseId`, `sectionId`, `lessonId` trong assessment tham chiếu sang domain của learning service
- Repo có cấu hình contract với learning service qua `assessment.learning-sync-enabled` và `assessment.learning-contract-path`
- Với luồng integration nội bộ, assessment service dự kiến dùng learning service để:
  - kiểm tra learner có quyền truy cập assessment theo section/lesson
  - gửi kết quả assessment về learning service sau khi submit

---

*Cập nhật lần cuối: 2026-04-13 — Backend team*
