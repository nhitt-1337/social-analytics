# Social Analytics Dashboard

Ứng dụng web giúp admin tổng hợp và theo dõi lượng tương tác (likes, shares, comments, followers)
của bài viết trên **Facebook** và **Twitter**.

Xây bằng **Spring Boot 4.1 / Java 21 / MySQL 8.4**.

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## 1. Tình trạng

Giai đoạn hiện tại: **Unit Test + Import/Export Excel**.

| Hạng mục | Trạng thái |
|---|---|
| Cấu trúc Controller → Service → Repository → DTO → Util | ✅ |
| JPA + MySQL, entity `User` / `Post` / `SocialMetric` | ✅ |
| API CRUD `/posts`, `/metrics` | ✅ |
| Swagger / OpenAPI | ✅ |
| `application.yaml` + cấu hình logging | ✅ |
| Social Login (FB, TW) | ⏳ bước sau |
| Import/Export Excel bằng Apache POI + Reflection | ✅ |
| Unit test Service & Controller, `@DataJpaTest` | ✅ |
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
| POST | `/import-posts?userId=` | Nhập bài viết từ file Excel (multipart, part tên `file`) |
| GET | `/export-report?platform=&from=&to=` | Xuất báo cáo tương tác ra file `.xlsx` |

**Định dạng response.** Danh sách phân trang trả `{ data, total, page, limit }`, danh sách thường
trả `{ data: [...] }`. Lỗi dùng chung một khuôn:

```json
{ "error": { "code": "VALIDATION", "message": "Dữ liệu không hợp lệ",
             "fields": { "likes": "likes không được nhỏ hơn 0" } } }
```

`fields` chỉ xuất hiện ở lỗi bean-validation. Status: 200/201/204 · 400 tham số sai ·
404 không tồn tại · 409 trùng dữ liệu · 422 lỗi validate.

## 4. Import / Export Excel

Dùng **Apache POI** cho phần đọc/ghi `.xlsx`, và **Reflection** cho phần ánh xạ cột ↔ field.

### Nhập bài viết

```bash
curl -X POST 'http://localhost:8080/api/v1/import-posts?userId=1' \
     -F 'file=@posts.xlsx'
```

File cần các cột sau ở **dòng 1** (tên cột khớp không phân biệt hoa thường, **thứ tự cột tuỳ ý**):

| Cột | Bắt buộc | Ghi chú |
|---|---|---|
| `platform` | ✅ | `facebook` hoặc `twitter` |
| `externalId` | ✅ | Id bài viết trên chính nền tảng đó |
| `content` | | |
| `url` | | |
| `postedAt` | | Ô kiểu ngày của Excel, hoặc `2026-01-31 08:30`, `31/01/2026` |

Import **chịu lỗi**: dòng nào sai định dạng hoặc trùng bài đã có thì bị bỏ qua và liệt kê trong
`errors`, các dòng còn lại vẫn được lưu — file do người dùng gõ tay, bắt cả file phải đúng tuyệt
đối mới cho nhập là quá khắt khe.

```json
{ "totalRows": 3, "imported": 1, "skipped": 2,
  "errors": [ { "rowNumber": 3, "message": "cột 'platform' có giá trị 'instagram' không hợp lệ..." } ] }
```

`rowNumber` đếm đúng như Excel hiển thị (dòng 1 là tiêu đề) để mở file lên là thấy ngay chỗ sai.
Thiếu hẳn cột bắt buộc là lỗi **cả file** → 400, khác với lỗi từng dòng.

### Xuất báo cáo

```bash
curl -OJ 'http://localhost:8080/api/v1/export-report?platform=facebook'
```

Mỗi dòng là một bài viết kèm số liệu của **lần đo gần nhất**; bài chưa crawl lần nào thì các cột
chỉ số để **trống** (không ghi `0`, tránh nhầm "chưa đo" với "đo được 0"). Lọc `from`/`to` áp lên
thời điểm **đăng bài**, nên bài chưa có `postedAt` sẽ không xuất hiện khi có bộ lọc thời gian.

### Ánh xạ bằng Reflection

`ExcelMapper` đọc/ghi được **bất kỳ class nào** có field gắn `@ExcelColumn`:

```java
public class PostImportRow {
    @ExcelColumn(header = "platform", order = 1, required = true)
    private Platform platform;
    ...
}
```

Thêm một loại báo cáo mới chỉ cần tạo thêm một DTO gắn annotation — không sửa `ExcelMapper`.
Việc quét annotation được **cache theo class** nên Reflection chỉ tốn ở lần gọi đầu.

Hai chiều có ràng buộc khác nhau:

- **Đọc** ghi giá trị vào field (`Field.set`) nên class đích phải là class thường có constructor
  rỗng — field của `record` là `final`, không set được. Vì vậy `PostImportRow` là class.
- **Ghi** chỉ đọc field (`Field.get`) nên dùng `record` được — `PostReportRow` là record.

Giới hạn: đọc tối đa `ExcelMapper.MAX_DATA_ROWS` = 5.000 dòng, xuất tối đa
`ReportExportService.MAX_EXPORT_ROWS` = 10.000 dòng; dung lượng upload theo `MAX_FILE_SIZE`
(mặc định 10MB).

## 5. Kiểm thử

```bash
./mvnw test
```

**136 test**, chạy trên H2 ở `MODE=MySQL` — không cần dựng MySQL thật.

| Tầng | Kiểu test | Lớp test |
|---|---|---|
| Engine Excel | JUnit5 thuần | `ExcelMapperTest` (16) |
| Service | JUnit5 + Mockito | `PostServiceTest` (16), `MetricServiceTest` (13), `PostImportServiceTest` (12), `ReportExportServiceTest` (11) |
| Repository | `@DataJpaTest` | `PostRepositoryTest` (11), `SocialMetricRepositoryTest` (9) |
| Controller (lát cắt web) | `@WebMvcTest` + `@MockitoBean` | `PostControllerTest` (17) |
| Đầu-cuối | `@SpringBootTest` + `MockMvc` | `PostControllerIntegrationTest` (8), `MetricControllerIntegrationTest` (7), `ExcelControllerIntegrationTest` (16) |

Quy ước đã áp dụng:

- **Mock ở biên, không mock logic.** Service test mock repository nhưng dùng **mapper thật** và
  **`ExcelMapper` thật** với file `.xlsx` dựng trong bộ nhớ — mock chính phần dễ sai nhất (đọc ô,
  ép kiểu) thì test không còn chứng minh được gì.
- **Kiểm tra nội dung file, không chỉ kiểm tra "đã gọi write()".** Test export đọc ngược file xuất
  ra rồi so từng ô.
- `@WebMvcTest` cho câu hỏi "tầng web ánh xạ request/response/lỗi đúng chưa" (nhanh, không nạp JPA);
  `@SpringBootTest` cho câu hỏi "cả chuỗi chạy được chưa".
- `@DataJpaTest` để xác minh các câu **JPQL viết tay** thật sự chạy và **unique constraint** có hiệu
  lực — mock repository không bao giờ phát hiện được hai thứ này.
- Helper dùng chung ở `src/test/java/.../support/`: `TestEntities` (dựng entity, gán `id` bằng
  `ReflectionTestUtils` vì entity không có setter cho `id`), `ExcelTestFiles` (dựng/đọc `.xlsx`).

## 6. Thiết kế

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
- Import không hỏi DB từng dòng: một truy vấn `findIdentitiesByExternalIdIn` lấy hết bài đã tồn tại
  rồi đối chiếu trong bộ nhớ. Tập đối chiếu cũng chứa các dòng đã gặp phía trên trong **cùng file**,
  nên file tự trùng với chính nó cũng bị chặn trước khi `saveAll` đụng unique constraint.
- Export cũng chỉ một truy vấn cho toàn bộ chỉ số (`findByPostIdInOrderByCollectedAtDesc`), đã sắp
  giảm dần nên bản ghi đầu tiên của mỗi bài chính là lần đo gần nhất.
- `ExcelMapper` không để lọt message tiếng Anh của POI ra API; mọi lỗi file được dịch thành thông
  báo tiếng Việt qua `ExcelParseException` (cả file) và `ExcelCellException` (một ô).
