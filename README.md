# Social Analytics Dashboard

Ứng dụng web giúp admin tổng hợp và theo dõi lượng tương tác (likes, shares, comments, followers)
của bài viết trên **Facebook** và **Twitter**.

Xây bằng **Spring Boot 4.1 / Java 21 / MySQL 8.4**.

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## 1. Tình trạng

Giai đoạn hiện tại: **khởi tạo project & cấu trúc nền**.

| Hạng mục | Trạng thái |
|---|---|
| Cấu trúc Controller → Service → Repository → DTO → Util | ✅ |
| JPA + MySQL, entity `User` / `Post` / `SocialMetric` | ✅ |
| API CRUD `/posts`, `/metrics` | ✅ |
| Swagger / OpenAPI | ✅ |
| `application.yaml` + cấu hình logging | ✅ |
| Social Login (FB, TW) | ⏳ bước sau |
| Import/Export Excel (Reflection) | ⏳ |
| Background job crawl (Multithreading, JMS) | ⏳ |
| Biểu đồ Chart.js | ⏳ |
| WebSocket realtime | ⏳ |
| Bảo mật CSRF | ⏳ |

## 2. Chạy

MySQL chạy bằng Docker (không cần cài đặt lên máy):

```bash
docker compose up -d           # MySQL 8.4 ở cổng 3306
./mvnw spring-boot:run
```

Cổng 3306 đang bận thì đổi: `DB_PORT=33306 docker compose up -d`, rồi chạy app với
`DB_URL='jdbc:mysql://localhost:33306/social_analytics?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh'`.

Biến môi trường (đều có mặc định cho dev): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`,
`LOG_LEVEL_ROOT`, `LOG_LEVEL_APP`, `LOG_LEVEL_SQL`, `LOG_FILE`, `MAX_FILE_SIZE`.

Schema do Hibernate `ddl-auto: update` tự tạo lúc khởi động.

## 3. API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/posts` | Danh sách bài viết (`?platform=facebook&page=1&limit=20`), kèm số liệu mới nhất |
| GET | `/posts/{id}` | Chi tiết bài viết |
| POST | `/posts` | Tạo bài viết → 201 |
| PUT | `/posts/{id}` | Cập nhật |
| DELETE | `/posts/{id}` | Xoá (kèm toàn bộ lịch sử chỉ số) → 204 |
| GET | `/metrics?postId=` | Lịch sử đo của một bài, mới nhất trước |
| GET | `/metrics/series?postId=&from=&to=` | Chuỗi thời gian tăng dần — dữ liệu cho Chart.js |
| GET | `/metrics/{id}` | Chi tiết một lần đo |
| POST | `/metrics` | Ghi nhận một lần đo → 201 |
| DELETE | `/metrics/{id}` | Xoá một lần đo → 204 |

**Định dạng response.** Danh sách phân trang trả `{ data, total, page, limit }`, danh sách thường
trả `{ data: [...] }`. Lỗi dùng chung một khuôn:

```json
{ "error": { "code": "VALIDATION", "message": "Dữ liệu không hợp lệ",
             "fields": { "likes": "likes không được nhỏ hơn 0" } } }
```

`fields` chỉ xuất hiện ở lỗi bean-validation. Status: 200/201/204 · 400 tham số sai ·
404 không tồn tại · 409 trùng dữ liệu · 422 lỗi validate.

## 4. Kiểm thử

```bash
./mvnw test
```

15 integration test (MockMvc) chạy trên H2 ở `MODE=MySQL`, không cần dựng MySQL thật.

## 5. Thiết kế

**Phân lớp:** `Controller` (mỏng, chỉ `@Valid` + delegate, không truy vấn DB) → `Service`
(nghiệp vụ, `@Transactional`) → `Repository` (Spring Data JPA) → `DTO` (record + Bean Validation)
→ `Util` (hàm thuần, vd `PlatformParser`).

**Quan hệ dữ liệu:**

```
User 1─* Post 1─* SocialMetric
```

**Điểm đáng chú ý:**

- `SocialMetric` lưu theo **chuỗi thời gian** (mỗi lần crawl một dòng) thay vì ghi đè, nên vẽ được
  biểu đồ tăng trưởng. Số liệu hiện tại lấy bằng bản ghi mới nhất.
- Cặp `(platform, external_id)` của `Post` là **unique** — import Excel nhiều lần không tạo bản ghi
  trùng. Cùng một id trên hai nền tảng khác nhau vẫn hợp lệ.
- Chống N+1: `@EntityGraph("user")` khi liệt kê bài viết; `latestMetric` do service truy vấn riêng
  thay vì duyệt collection LAZY của entity.
- Phân trang **ở DB** qua `Pageable`, không tải hết rồi cắt trong bộ nhớ.
- DTO tách hẳn khỏi entity nên không lộ trường nhạy cảm (`provider`, `providerId` của `User`).
- Lỗi tập trung ở `GlobalExceptionHandler` (`@RestControllerAdvice`); service chỉ ném exception có
  tên nên không phụ thuộc tầng web.
