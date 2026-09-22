# Social Analytics Dashboard

Ứng dụng web giúp admin tổng hợp và theo dõi lượng tương tác (likes, shares, comments, followers)
của bài viết trên **Facebook** và **Twitter**.

Xây bằng **Spring Boot 4.1 / Java 21 / MySQL 8.4**.

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## 1. Tình trạng

Giai đoạn hiện tại: **Background Job & Multithreading**.

| Hạng mục | Trạng thái |
|---|---|
| Cấu trúc Controller → Service → Repository → DTO → Util | ✅ |
| JPA + MySQL, entity `User` / `Post` / `SocialMetric` | ✅ |
| API CRUD `/posts`, `/metrics` | ✅ |
| Swagger / OpenAPI | ✅ |
| `application.yaml` + cấu hình logging | ✅ |
| Spring Security, CSRF, trang dashboard | ✅ |
| Social Login OAuth2 (Facebook, X/Twitter) | ✅ |
| Import/Export Excel bằng Apache POI + Reflection | ✅ |
| Unit test Service & Controller, `@DataJpaTest` | ✅ |
| Background job crawl định kỳ (`@Scheduled` + `@Async`) | ✅ |
| JMS | ⏳ |
| Biểu đồ Chart.js | ⏳ |
| WebSocket realtime | ⏳ |

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

Toàn bộ endpoint đều **yêu cầu đăng nhập**. Chưa đăng nhập thì:
API (nhận JSON) trả **401**, còn trang HTML bị chuyển hướng về `/login`.

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
| GET | `/crawl/last-run` | Lần cập nhật gần nhất — 204 khi job chưa chạy lần nào |
| GET | `/crawl/runs` | 10 lần chạy gần nhất |
| POST | `/crawl/run` | Chạy job ngay — 409 khi đang có lần chạy dở |

Trang HTML (Thymeleaf):

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/login` | Trang đăng nhập, công khai |
| GET | `/dashboard` | Dashboard sau khi đăng nhập |
| POST | `/dashboard/note` | Form demo CSRF |
| POST | `/logout` | Đăng xuất (phải là POST kèm token) |

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

## 5. Bảo mật & Social Login

### Cấu hình

```bash
export FACEBOOK_CLIENT_ID=...   FACEBOOK_CLIENT_SECRET=...
export X_CLIENT_ID=...          X_CLIENT_SECRET=...
./mvnw spring-boot:run
```

Chỉ nhà cung cấp nào có **đủ cả** client id và secret mới được đăng ký. Không cấu hình gì thì app
vẫn chạy bình thường, trang `/login` hiện hướng dẫn thay vì nút dẫn tới trang lỗi của Facebook.

Redirect URI phải khai **đúng y hệt** bên trang quản trị ứng dụng của nhà cung cấp — chú ý tiền
tố `/api/v1` do `server.servlet.context-path`:

```
http://localhost:8080/api/v1/login/oauth2/code/facebook
http://localhost:8080/api/v1/login/oauth2/code/x
```

### Tạo app Facebook để đăng nhập thật

1. Vào <https://developers.facebook.com/apps> → **Create app**.
2. Chọn use case **Authenticate and request data from users with Facebook Login**.
3. Sau khi tạo: **Facebook Login → Settings**, điền vào ô *Valid OAuth Redirect URIs*:
   ```
   http://localhost:8080/api/v1/login/oauth2/code/facebook
   ```
4. **App settings → Basic**: lấy *App ID* và *App Secret*.
5. Chạy app với hai giá trị đó:
   ```bash
   FACEBOOK_CLIENT_ID=<App ID> FACEBOOK_CLIENT_SECRET=<App Secret> ./mvnw spring-boot:run
   ```
6. Mở <http://localhost:8080/api/v1/login> → bấm **Tiếp tục với Facebook**.

**Ba chỗ hay vấp:**

- **App đang ở Development mode** thì chỉ tài khoản có vai trò trong app mới đăng nhập được
  (admin/developer/tester). Thêm người ở **App roles → Roles**. Muốn ai cũng dùng được thì phải
  qua App Review.
- **Enforce HTTPS.** Facebook mặc định bắt redirect URI dùng HTTPS; `http://localhost` thường được
  miễn trừ. Nếu vẫn bị từ chối thì tắt *Enforce HTTPS* trong Facebook Login → Settings, hoặc dùng
  một đường hầm HTTPS (ngrok) rồi khai URI của nó.
- **Quyền `email`.** Trong Development mode thì dùng được ngay với các tài khoản có vai trò trong
  app. Người dùng vẫn có quyền từ chối chia sẻ email — khi đó `email` là `null` và app vẫn đăng
  nhập bình thường.

### Hai thứ đã chỉnh so với mặc định của Spring Security

- **Không bám vào một phiên bản Graph API.** Provider `facebook` dựng sẵn của Spring Security trỏ
  vào **v2.8** — bản từ 2016. Facebook hiện vẫn định tuyến được v2.8, nhưng bám vào một bản đã bỏ
  9 năm là chuyện sớm muộn sẽ hỏng. `SocialLoginClientRegistrations` chuyển sang endpoint **không
  ghi phiên bản** (`/dialog/oauth`, `/oauth/access_token`): Facebook tự định tuyến sang bản được
  hỗ trợ, không có con số nào để cũ đi.
- **Ảnh đại diện xin thẳng trong user-info** (`fields=...,picture.type(large)`) rồi đọc
  `picture.data.url`. Tự ghép URL `graph.facebook.com/{id}/picture` mà không kèm access token thì
  Facebook trả về **ảnh silhouette xám** cho mọi người. Tài khoản chưa đặt ảnh thì payload có
  `is_silhouette: true` → coi như không có ảnh để giao diện hiện phần dự phòng.

### CSRF

Bật cho toàn ứng dụng. Token để trong cookie `XSRF-TOKEN` **không đặt HttpOnly** để JavaScript
đọc được — nhờ vậy form thường và lời gọi `fetch` dùng chung một cơ chế:

- **Form Thymeleaf**: dùng `th:action` + `method="post"` là Thymeleaf tự chèn
  `<input type="hidden" name="_csrf">`.
- **JavaScript**: tự đọc cookie rồi gắn vào header `X-XSRF-TOKEN` (xem `static/js/app.js`).

Thứ tự filter quyết định mã lỗi trả về — thử bằng `curl` sẽ thấy rõ:

```bash
# Thiếu token -> 403, CsrfFilter chặn TRƯỚC cả bước kiểm tra đăng nhập
curl -X POST localhost:8080/api/v1/posts -H 'Accept: application/json' -d '{}'      # 403

# Có token nhưng chưa đăng nhập -> 401, tức là đã qua được CSRF
curl -X POST localhost:8080/api/v1/posts -H "X-XSRF-TOKEN: $TOKEN" -b cookies.txt   # 401
```

Từ Spring Security 6, token CSRF được nạp **lười**: không chỗ nào đọc tới thì cookie không bao giờ
được gửi về trình duyệt. `SecurityConfig.CsrfCookieFilter` chạm vào token ở mọi request để cookie
luôn có mặt ngay từ lần tải trang đầu tiên.

### Hai chỗ dễ vấp

- **X (Twitter) bắt buộc PKCE.** Spring Security chỉ tự bật PKCE cho client *không có* client
  secret; X thì có secret nhưng vẫn đòi PKCE. Vì vậy `SecurityConfig` bật tay bằng
  `OAuth2AuthorizationRequestCustomizers.withPkce()`. Thiếu dòng này thì X báo lỗi ngay ở bước
  đầu tiên và rất khó lần ra nguyên nhân. Có test riêng cho nó (`OAuth2LoginTest#batPkceChoX`).
- **`/2/users/me` của X trả JSON lồng trong `data`**, trong khi `DefaultOAuth2UserService` tìm
  thuộc tính ở tầng ngoài cùng nên không thấy `username`. `SocialUserAttributes` gỡ lớp bọc này.

### Lưu token & thông tin user

- **Thông tin user** → bảng `users`. `SocialLoginUserService` tạo mới ở lần đăng nhập đầu, các lần
  sau chỉ cập nhật hồ sơ. Đã có tài khoản `LOCAL` trùng email thì gắn danh tính mạng xã hội vào
  chính tài khoản đó; **không** gắn đè lên tài khoản đã liên kết nhà cung cấp khác (mở đường
  chiếm tài khoản).
- **Access token / refresh token** → bảng `oauth2_authorized_clients`, qua
  `JpaOAuth2AuthorizedClientService`. Bản mặc định của Spring Security chỉ giữ token trong bộ nhớ:
  khởi động lại là mất, và tiến trình nền không đọc được. Job crawl ở bước sau chạy ngoài phiên
  đăng nhập nhưng vẫn cần token để gọi API Facebook/X.

> **Lưu ý khi nâng cấp từ bản trước.** Cột `users.email` chuyển sang cho phép `NULL` vì X không trả
> về email. `ddl-auto: update` của Hibernate **không** nới lỏng ràng buộc `NOT NULL` sẵn có, nên
> trên database cũ phải chạy tay một lần:
>
> ```sql
> ALTER TABLE users MODIFY email VARCHAR(255) NULL;
> ```
>
> Hoặc dựng lại từ đầu: `docker compose down -v && docker compose up -d`.
> Bỏ qua bước này thì đăng nhập bằng X sẽ lỗi `Field 'email' doesn't have a default value`.

## 6. Background Job & Multithreading

Job `updateSocialMetricsJob` chạy **mỗi 1 giờ**, lấy chỉ số mới cho toàn bộ bài viết.

```
@Scheduled (scheduler-*)          @Async (crawl-*)
      |                                 |
SocialMetricsUpdateJob  --giao việc-->  SocialMetricsCollector   (1 tác vụ = 1 TÀI KHOẢN)
      |                                 |
      |                          SocialApiClient  (hiện là dữ liệu giả)
      |                                 |
      |                          MetricWriter     (1 transaction / 1 bài)
      |
   CrawlRun  ->  "Last updated time" trên dashboard
```

### Đơn vị chạy song song là tài khoản, không phải bài viết

Giới hạn tần suất của Facebook/X tính theo **token**, nên gọi dồn nhiều bài của cùng một tài khoản
cùng lúc là cách nhanh nhất để bị chặn. Vì vậy: **nhiều tài khoản song song, trong một tài khoản
thì tuần tự**.

Đo thực tế với 3 tài khoản × 4 bài, độ trễ giả lập 300ms mỗi lần gọi:

```
tuần tự hoàn toàn : 12 × 300ms          = 3600ms
thiết kế hiện tại : 4 × 300ms (song song) = 1200ms
đo được           : ~1280ms
```

### Cấu hình

```yaml
social.crawl:
  enabled: true            # đặt false để tắt job (test dùng cách này)
  interval: PT1H           # tính từ lúc lần trước KẾT THÚC
  initial-delay: PT1M
  pool-size: 8
  queue-capacity: 100
  timeout-seconds: 300
  mock:
    latency-ms: 40         # giả lập độ trễ mạng
    failure-rate: 0.1      # giả lập 10% lời gọi hỏng
```

Tất cả đều có biến môi trường tương ứng (`CRAWL_INTERVAL`, `CRAWL_POOL_SIZE`...). Muốn quan sát
nhanh thì đặt `CRAWL_INTERVAL=PT30S CRAWL_INITIAL_DELAY=PT10S`.

### Những chỗ quyết định có chủ ý

- **`fixedDelay` chứ không `fixedRate`.** `fixedRate` đếm từ lúc BẮT ĐẦU, nên một lần chạy lâu hơn
  1 giờ sẽ khiến các lần sau dồn cục lên nhau.
- **Hai bể luồng riêng.** Không khai báo thì `@Scheduled` chạy trên **một** luồng duy nhất (một job
  chậm chặn mọi job khác) và `@Async` dùng `SimpleAsyncTaskExecutor` — tạo luồng mới cho **từng**
  tác vụ, không giới hạn. Cả hai đều không dùng được ở production.
- **`CallerRunsPolicy`.** Hàng đợi đầy thì tác vụ chạy ngay trên luồng gọi: job chậm lại chứ không
  mất bài nào. Mặc định của JDK là `AbortPolicy` — ném lỗi và bỏ luôn tác vụ đó.
- **Giao hết việc rồi mới chờ.** Gọi `.get()` ngay trong vòng lặp thì hoá ra chạy tuần tự, mất trắng
  tác dụng của đa luồng. Có test riêng giữ điều này
  (`SocialMetricsUpdateJobTest#giaoHetViecRoiMoiChoChuKhongChoTungCai`).
- **Một transaction cho một bài** (`REQUIRES_NEW` trong `MetricWriter`). Bài thứ 5 lỗi thì 4 bài
  trước vẫn giữ kết quả. Collector không mở transaction nào: giữ kết nối DB trong lúc ngồi chờ
  mạng là cách chắc chắn để cạn connection pool.
- **`PARTIAL` tách khỏi `SUCCESS`/`FAILED`.** Crawl vài chục tài khoản thì một hai tài khoản lỗi là
  bình thường; gọi cả lần chạy đó là "thất bại" sẽ che mất việc phần lớn đã chạy xong.

### Xử lý lỗi trong luồng nền

Lỗi ở luồng nền không ai nhìn thấy ngoài log, nên có ba lớp:

1. **Từng bài** — `SocialMetricsCollector` bắt cả `Exception` (không riêng `SocialApiException`, vì
   lỗi ghi DB cũng không được làm hỏng phần còn lại), ghi `WARN` kèm stack trace, tính là thất bại
   rồi đi tiếp.
2. **Từng lần chạy** — job bắt `TimeoutException` / `InterruptedException` / `ExecutionException`,
   huỷ phần còn lại và vẫn ghi lại `CrawlRun` thay vì treo mãi. Cờ `running` được thả trong
   `finally`, thiếu chỗ này thì job không bao giờ chạy lại sau lần đầu gặp lỗi.
3. **Lưới an toàn** — `AsyncUncaughtExceptionHandler` trong `AsyncConfig`. Ngoại lệ ném ra từ một
   method `@Async` trả về `void` **không quay lại được** luồng gọi; không có handler thì nó biến
   mất không dấu vết.

`InterruptedException` luôn được khôi phục cờ ngắt (`Thread.currentThread().interrupt()`) — nuốt nó
là cách chắc chắn khiến ứng dụng không tắt được.

### Màn hình "Last updated time"

Mỗi lần chạy ghi một dòng vào bảng `crawl_runs`, dashboard đọc dòng gần nhất. Lưu xuống DB chứ
không giữ trong bộ nhớ vì mốc thời gian này phải đúng cả sau khi restart — hiện số liệu cũ mà nói
là vừa cập nhật thì tệ hơn là nói thẳng "chưa chạy lần nào". Dashboard có thêm nút **Chạy cập nhật
ngay** để khỏi đợi hết 1 giờ.

### Còn thiếu

- **Chạy nhiều instance sẽ crawl trùng.** Cờ chặn chạy chồng (`AtomicBoolean`) nằm trong bộ nhớ của
  một tiến trình. Nhiều instance cần khoá dùng chung (ShedLock hoặc khoá trên DB).
- **`SocialApiClient` hiện là dữ liệu giả.** Khi nối API thật chỉ cần thêm một implementation đọc
  access token từ `oauth2_authorized_clients` (đã lưu ở bước Social Login) — phần job không phải
  sửa gì.

## 7. Kiểm thử

```bash
./mvnw test
```

**243 test**, chạy trên H2 ở `MODE=MySQL` — không cần dựng MySQL thật.

| Tầng | Kiểu test | Lớp test |
|---|---|---|
| Engine Excel | JUnit5 thuần | `ExcelMapperTest` (17) |
| Service | JUnit5 + Mockito | `PostServiceTest` (16), `MetricServiceTest` (13), `PostImportServiceTest` (13), `ReportExportServiceTest` (11) |
| Repository | `@DataJpaTest` | `PostRepositoryTest` (11), `SocialMetricRepositoryTest` (9) |
| Controller (lát cắt web) | `@WebMvcTest` + `@MockitoBean` | `PostControllerTest` (17) |
| Đầu-cuối | `@SpringBootTest` + `MockMvc` | `PostControllerIntegrationTest` (8), `MetricControllerIntegrationTest` (7), `ExcelControllerIntegrationTest` (16) |
| Bảo mật | `@SpringBootTest` + `MockMvc` | `SecurityRulesTest` (17), `OAuth2LoginTest` (11), `CsrfCookieTest` (1) |
| Cấu hình Social Login | `ApplicationContextRunner` | `SocialLoginClientRegistrationsTest` (6) |
| Social Login | Mockito / `@DataJpaTest` | `SocialUserAttributesTest` (12), `SocialLoginUserServiceTest` (8), `JpaOAuth2AuthorizedClientServiceTest` (8) |
| Job & đa luồng | Mockito | `SocialMetricsCollectorTest` (6), `SocialMetricsUpdateJobTest` (9), `MockSocialApiClientTest` (7) |
| Job & đa luồng | `@SpringBootTest` | `AsyncConfigTest` (6), `SocialMetricsJobIntegrationTest` (6), `CrawlControllerIntegrationTest` (8) |

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
- Test bảo mật dùng `@WithMockUser` + `.with(csrf())` thay vì đi qua luồng OAuth2 thật.
  `PostControllerTest` có `@Import(SecurityConfig.class)`: không import thì `@WebMvcTest` chạy với
  chain **mặc định** của Spring Security, test sẽ xanh với một cấu hình không hề tồn tại ở production.
- `CsrfCookieTest` tách riêng và có `@DirtiesContext`: `SecurityMockMvcRequestPostProcessors.csrf()`
  thay `CsrfTokenRepository` ngay trên instance `CsrfFilter` dùng chung của context, nên sau khi một
  test gọi `.with(csrf())` thì các request sau không còn ghi cookie thật — không tách ra thì kết quả
  phụ thuộc thứ tự chạy.
- Dùng `spring-boot-starter-security-test` chứ không phải `spring-security-test` trần: starter mới
  kéo theo phần tự động gắn security filter vào `MockMvc`; thiếu nó thì `@WithMockUser` vô tác dụng
  và mọi request trong test đều bị 401.
- **Test đa luồng dùng `CountDownLatch`, không dùng `Thread.sleep`.** Tác vụ chỉ thoát ra khi cả
  nhóm cùng vào được tới điểm hẹn — chạy tuần tự thì test treo và fail, chứ không phải "may thì
  xanh". `sleep` cho ra test lúc xanh lúc đỏ tuỳ máy.
- Job bị tắt trong profile test (`social.crawl.enabled=false`); test nào cần thì bật lại bằng
  `@SpringBootTest(properties = ...)` và tự gọi `runOnce()`. Để lịch tự chạy thì các test sẽ giẫm
  lên dữ liệu của nhau.
- Client giả lập trong test đặt `latency-ms=0`, `failure-rate=0` để kết quả xác định; riêng test
  cần kiểm tra song song thì thay hẳn `SocialApiClient` bằng bản ghi lại tên luồng.

## 8. Thiết kế

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
- `SecurityConfig` nhận `ClientRegistrationRepository` qua `ObjectProvider`: chưa cấu hình Social
  Login thì app vẫn khởi động, thay vì chết ngay lúc start.
- Danh sách nhà cung cấp dựng **từ code** (`SocialLoginClientRegistrations`) chứ không khai trong
  `spring.security.oauth2.client.registration.*`. Khai trong yaml thì mọi provider viết ở đó luôn
  được đăng ký kể cả khi chưa có client id — và đặt mặc định `${FACEBOOK_CLIENT_ID:#{null}}` cũng
  không cứu được, vì `@ConfigurationProperties` **không** đánh giá SpEL nên `#{null}` bị dùng làm
  client id nguyên văn.
- Entry point tự dựng bằng `DelegatingAuthenticationEntryPoint`, và bộ so khớp JSON **loại bỏ
  `*/*`**. Trình duyệt luôn gửi kèm `*/*;q=0.8` ở cuối header `Accept`, mà `*/*` thì "tương thích"
  với `application/json` — không loại ra thì mở trang bằng Chrome cũng bị coi là gọi API và nhận
  401 thay vì được chuyển tới trang đăng nhập.
- Đăng xuất bắt buộc là `POST` kèm token. Để `GET` thì chỉ cần dụ người dùng bấm vào một đường link
  là đăng xuất được họ — đúng kiểu tấn công CSRF.
