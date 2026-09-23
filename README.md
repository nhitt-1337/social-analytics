# Social Analytics Dashboard

Ứng dụng web tổng hợp và theo dõi lượng tương tác (likes, shares, comments, followers) của bài
viết trên Facebook và X (Twitter).

Spring Boot 4.1 · Java 21 · MySQL 8.4 · ActiveMQ

- Base URL: `http://localhost:8080/api/v1`
- Swagger: `/api/v1/swagger-ui.html`

## Chạy

```bash
cp .env.example .env      # điền credential vào đây; .env đã nằm trong .gitignore
docker compose up -d      # MySQL (3306) + ActiveMQ (61616, quản trị 8161 admin/admin)
./mvnw spring-boot:run
```

Docker Compose tự đọc `.env`; Spring Boot đọc qua `spring.config.import` khai trong
`application.yaml`. Không có `.env` thì app vẫn chạy bằng giá trị mặc định
(`optional:` nên không bắt buộc).

Đóng gói:

```bash
./mvnw clean package
java -jar target/social-analytics-0.0.1-SNAPSHOT.jar
```

Biến môi trường vẫn ghi đè được `.env`, tiện khi muốn thử nhanh:

```bash
CRAWL_INITIAL_DELAY=PT10S CRAWL_INTERVAL=PT60S ./mvnw spring-boot:run
```

## Chức năng

| Hạng mục | Công nghệ |
|---|---|
| CRUD bài viết & chỉ số | Spring Data JPA, MySQL |
| Import / export Excel | Apache POI + Reflection |
| Đăng nhập mạng xã hội | OAuth2 (Facebook, X), token lưu xuống DB |
| Bảo mật | Spring Security, CSRF |
| Job cập nhật định kỳ | `@Scheduled` + `@Async`, bể luồng riêng |
| Hàng đợi | JMS / ActiveMQ, retry + DLQ |
| Biểu đồ realtime | STOMP over WebSocket, SockJS, Chart.js |
| WebService | Spring-WS, contract-first (XSD) |

## API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/posts` | Danh sách bài viết, kèm số liệu mới nhất |
| GET/POST/PUT/DELETE | `/posts/{id}` | CRUD bài viết |
| GET | `/metrics?postId=` | Lịch sử đo của một bài |
| GET | `/metrics/series?postId=&from=&to=` | Chuỗi thời gian cho biểu đồ |
| POST | `/metrics` | Ghi nhận một lần đo |
| POST | `/import-posts` | Nhập bài viết từ Excel (multipart); bỏ trống `userId` thì lấy người đang đăng nhập |
| GET | `/export-report?platform=&from=&to=` | Xuất báo cáo `.xlsx` |
| GET | `/export/models` | Model nào xuất được, cột gì |
| GET | `/export/{model}` | Xuất model bất kỳ ra Excel |
| GET | `/chart-data?platform=&days=` | Dữ liệu biểu đồ |
| GET | `/statistics` | Thống kê tổng hợp theo nền tảng |
| GET | `/statistics/dead-letters` | Message vào hàng đợi thư chết |
| GET | `/crawl/last-run` · `/crawl/runs` | Trạng thái job |
| POST | `/crawl/run` | Chạy job ngay |
| GET | `/exchange-rate?from=&to=` | Tỷ giá, lấy qua SOAP |

SOAP: `POST /soap` (`getPlatformSummary`, `getExchangeRate`), WSDL tại
`/soap/socialAnalytics.wsdl`.

Trang HTML: `/login`, `/dashboard` (có form nhập Excel, biểu đồ realtime, nút chạy job).

File Excel mẫu để thử nhập: [`samples/mau-import-bai-viet.xlsx`](samples/mau-import-bai-viet.xlsx)
— 5 dòng, trong đó 2 dòng cố tình sai để thấy cơ chế import chịu lỗi. Hai cột bắt buộc là
`platform` và `externalId`; cột tìm theo tên nên thứ tự tuỳ ý.

Mọi endpoint đều yêu cầu đăng nhập, trừ `/login` và `/soap`. Chưa đăng nhập thì API trả 401,
trang HTML chuyển hướng về `/login`.

**Khuôn lỗi chung:**

```json
{ "error": { "code": "VALIDATION", "message": "Dữ liệu không hợp lệ",
             "fields": { "likes": "likes không được nhỏ hơn 0" } } }
```

200/201/204 · 400 tham số sai · 401 chưa đăng nhập · 403 thiếu token CSRF · 404 không tồn tại ·
409 trùng dữ liệu · 422 lỗi validate · 503 dịch vụ ngoài hỏng.

## Cấu hình

Đặt trong `.env` (xem `.env.example`) hoặc truyền qua biến môi trường. Đều có mặc định cho dev:

| Nhóm | Biến |
|---|---|
| Database | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Social Login | `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`, `FACEBOOK_SCOPES`, `X_CLIENT_ID`, `X_CLIENT_SECRET` |
| Job crawl | `CRAWL_ENABLED`, `CRAWL_INTERVAL`, `CRAWL_INITIAL_DELAY`, `CRAWL_POOL_SIZE` |
| ActiveMQ | `MQ_URL`, `MQ_USER`, `MQ_PASSWORD` |
| SOAP | `EXCHANGE_RATE_ENDPOINT` |

Không cấu hình Social Login thì app vẫn chạy, trang `/login` hiện hướng dẫn thay vì nút đăng nhập.

**Tạo app Facebook:** <https://developers.facebook.com/apps> → Create app → use case
*Authenticate and request data from users with Facebook Login* → App settings → Basic để lấy
App ID / App Secret. Redirect URI localhost được Facebook tự cho phép ở Development mode.

App mới chỉ có sẵn quyền `public_profile`; xin thêm `email` mà chưa bật trong console thì Facebook
chặn ở màn hình đăng nhập (`Invalid Scopes: email`) — khi đó chạy với
`FACEBOOK_SCOPES=public_profile`.

## Kiến trúc

```
Trình duyệt ──HTML/REST/WebSocket──> Controller ──> Service ──> Repository ──> MySQL
                                                      │
                                    ┌─────────────────┼──────────────────┐
                                    ▼                 ▼                  ▼
                              ExcelMapper /     SocialApiClient      Spring-WS
                              ModelExporter      (job crawl)        (SOAP /soap)
                                    │
                              ActiveMQ ──> listener ──> thống kê tổng hợp
```

Một luồng dữ liệu đi qua gần hết hệ thống:

1. Đăng nhập Facebook → token lưu vào `oauth2_authorized_clients`
2. Upload Excel → đọc bằng Reflection → lưu `posts`
3. Import xong (sau khi commit) → message `IMPORT_COMPLETED` lên ActiveMQ
4. Listener nhận → tính lại `platform_summaries`
5. Job `@Scheduled` → `@Async` crawl song song theo tài khoản → ghi `social_metrics`
6. Crawl xong → đẩy `/topic/chart` → trình duyệt vẽ lại Chart.js, không tải lại trang

`EndToEndIntegrationTest` chạy đúng chuỗi này.

**Nguyên tắc xuyên suốt:** việc chậm đẩy ra khỏi request; chỉ gửi tin sau khi commit; lỗi của
việc phụ không làm hỏng việc chính; tính lại từ đầu thay vì cộng dồn (JMS chỉ bảo đảm "ít nhất
một lần").

## Kiểm thử

```bash
./mvnw test                                   # 337 test
./mvnw verify                                 # kèm báo cáo độ phủ JaCoCo
```

Chạy trên H2 (`MODE=MySQL`) và broker ActiveMQ nhúng (`vm://`) nên không cần Docker.

Độ phủ: **90% lệnh, 75% nhánh** — `target/site/jacoco/index.html`. Thấp nhất là gói `exception`
(59%): một số nhánh dịch kiểu lỗi chưa có test riêng.

| Tầng | Kiểu |
|---|---|
| Service, engine Excel, Reflection | JUnit 5 + Mockito |
| Repository, thống kê | `@DataJpaTest` |
| Controller | `@WebMvcTest` + `@MockitoBean` |
| Bảo mật, JMS, WebSocket, SOAP, đầu-cuối | `@SpringBootTest` |

## Giới hạn đã biết

- **Số liệu là giả lập.** Facebook không cho đọc bài viết trên trang cá nhân (cần `user_posts` +
  App Review); đọc bài của Page cần `pages_read_engagement`. Đã kiểm chứng bằng token thật:
  `/me/posts` và `/me/feed` trả mảng rỗng. `SocialApiClient` là interface nên nối API thật chỉ cần
  thêm một implementation, job không phải sửa.
- **Chạy nhiều instance sẽ crawl trùng** — cờ chặn nằm trong bộ nhớ, cần ShedLock.
- **WebSocket broker in-memory** không chia sẻ giữa các instance.
- **`ddl-auto: update`** — nên chuyển sang Flyway.
- **`/soap` để công khai** — production cần WS-Security hoặc chặn ở tầng mạng.

Ghi chú chi tiết về từng quyết định thiết kế: [`docs/CHI-TIET.md`](docs/CHI-TIET.md)
