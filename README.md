# Social Analytics Dashboard

Ứng dụng web giúp admin tổng hợp và theo dõi lượng tương tác (likes, shares, comments, followers)
của bài viết trên **Facebook** và **Twitter**.

Xây bằng **Spring Boot 4.1 / Java 21 / MySQL 8.4**.

- Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## 1. Tình trạng

**Đã hoàn thành toàn bộ các hạng mục của đề bài.**

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
| JMS: queue, listener, retry & DLQ (ActiveMQ) | ✅ |
| WebService SOAP (Spring-WS): tạo & tiêu thụ | ✅ |
| Reflection nâng cao: export model bất kỳ | ✅ |
| Biểu đồ Chart.js | ✅ |
| WebSocket realtime (STOMP + SockJS) | ✅ |

## 2. Chạy

MySQL chạy bằng Docker (không cần cài đặt lên máy):

```bash
docker compose up -d           # MySQL 8.4 (3306) + ActiveMQ (61616, quản trị 8161)
./mvnw spring-boot:run
```

Giao diện quản trị ActiveMQ: <http://localhost:8161> (admin/admin) — xem được số message đang
nằm trong từng hàng đợi, kể cả `ActiveMQ.DLQ`.

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
| GET | `/statistics` | Thống kê tổng hợp theo nền tảng |
| GET | `/statistics/dead-letters` | Message đã vào hàng đợi thư chết |
| GET | `/chart-data?platform=&days=` | Dữ liệu tổng hợp cho biểu đồ (days mặc định 7, tối đa 90) |
| GET | `/exchange-rate?from=&to=` | Tỷ giá, lấy qua WebService SOAP |
| GET | `/export/models` | Model nào xuất được, mỗi model có cột gì |
| GET | `/export/{model}` | Xuất model ra Excel — cột suy ra bằng Reflection |

SOAP (không dùng REST):

| Địa chỉ | Mô tả |
|---|---|
| `POST /soap` | `getPlatformSummary`, `getExchangeRate` |
| `GET /soap/socialAnalytics.wsdl` | WSDL sinh tự động từ XSD |

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
- **Quyền `email` phải bật riêng.** App Facebook mới chỉ có sẵn `public_profile`. Xin một quyền
  app chưa được cấp thì Facebook chặn ngay ở màn hình đăng nhập:
  `Invalid Scopes: email`. Chưa bật thì chạy với `FACEBOOK_SCOPES=public_profile` — app vẫn hoạt
  động, chỉ là `email` bằng `null` (tài khoản X/Twitter cũng vậy, code đã xử lý sẵn).

  Vì vậy scope là **cấu hình**, không cứng trong code:
  ```bash
  FACEBOOK_SCOPES=public_profile ./mvnw spring-boot:run
  ```

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

### Vì sao số liệu là giả lập — và tại sao không thể khác

`SocialApiClient` trả về dữ liệu giả, **không** phải số liệu Facebook thật. Đây là giới hạn của
nền tảng Facebook, không phải thiếu sót của project. Đã kiểm chứng bằng access token thật lấy
từ bảng `oauth2_authorized_clients` sau khi đăng nhập Facebook thành công:

| Gọi thử | Kết quả |
|---|---|
| `GET /me` | ✅ trả về đúng id và tên — token hoạt động |
| `GET /me/posts` | `{"data":[]}` — rỗng |
| `GET /me/feed` | `{"data":[]}` — rỗng |
| `GET /me/accounts` | không có Page nào |
| scope `user_posts` | `Invalid Scopes` |
| scope `pages_show_list`, `pages_read_engagement` | `Invalid Scopes` |

Hai kết luận:

- **Bài viết trên trang cá nhân: Facebook không cho đọc.** Cần quyền `user_posts`, mà quyền đó
  phải qua App Review kèm xác minh doanh nghiệp. Không có quyền thì Graph API trả mảng **rỗng**
  chứ không báo lỗi — dễ tưởng nhầm là code sai.
- **Bài viết của Page: về lý thuyết làm được**, cần `pages_show_list` + `pages_read_engagement`,
  nhưng phải thêm use case tương ứng trong console của app và nhiều khả năng vẫn qua App Review.

Vì vậy phần đọc số liệu được thiết kế để **thay nguồn không phải sửa gì khác**:
`SocialApiClient` là interface, `MockSocialApiClient` là một implementation. Nối API thật chỉ là
thêm một implementation đọc access token đã lưu — `SocialMetricsUpdateJob`, `SocialMetricsCollector`
và toàn bộ phần còn lại không đụng tới.

Công thức giả lập dùng **đường cong bão hoà** (`1 - e^(-giờ/36)`) chứ không tăng tuyến tính:
tương tác thật dồn vào một hai ngày đầu rồi chững lại. Tỷ lệ share ≈ 6% số like, bình luận ≈ 4% —
theo tỷ lệ thường gặp trên mạng xã hội, để biểu đồ demo nhìn hợp lý.

### Còn thiếu

- **Chạy nhiều instance sẽ crawl trùng.** Cờ chặn chạy chồng (`AtomicBoolean`) nằm trong bộ nhớ của
  một tiến trình. Nhiều instance cần khoá dùng chung (ShedLock hoặc khoá trên DB).
- **`SocialApiClient` hiện là dữ liệu giả.** Khi nối API thật chỉ cần thêm một implementation đọc
  access token từ `oauth2_authorized_clients` (đã lưu ở bước Social Login) — phần job không phải
  sửa gì.

## 7. JMS & Queue Handling

Import Excel xong thì việc tính lại thống kê tổng hợp **không** chạy trong request upload, mà đi
qua hàng đợi:

```
PostImportService  --(sự kiện Spring, trong transaction)-->  ImportCompletedProducer
                                                                    |  AFTER_COMMIT
                                                                    v
                                                          queue: import.completed
                                                                    |
                                                    ImportCompletedListener
                                                                    |
                                                          StatisticsService.refresh()
                                                                    |
                                                          bảng platform_summaries
```

Hỏng hết số lần thử lại thì broker đẩy sang `ActiveMQ.DLQ`, `DeadLetterListener` ghi xuống bảng
`dead_letters`.

### Gửi SAU KHI commit, không phải trong transaction

Đây là chỗ dễ sai nhất và chỉ thỉnh thoảng mới lộ ra. `importPosts()` chạy trong `@Transactional`;
gửi thẳng JMS từ trong đó sẽ hỏng theo hai kiểu:

1. Listener chạy luồng khác, nhận message **trước** khi dữ liệu commit → tính thống kê thiếu đúng
   những bài vừa import.
2. Transaction rollback **sau** khi đã gửi → message báo "import xong" cho một lần import không
   hề tồn tại.

Cách làm: service phát một **sự kiện Spring** trong transaction, `ImportCompletedProducer` nhận
bằng `@TransactionalEventListener(phase = AFTER_COMMIT)` rồi mới đẩy lên hàng đợi.

### Thử lại và DLQ

```
giao lần 1 -> lỗi -> chờ 0,5s -> giao lại (1) -> lỗi -> chờ 1s -> giao lại (2)
           -> lỗi -> chờ 2s   -> giao lại (3) -> lỗi -> ActiveMQ.DLQ
```

Hai điều kiện bắt buộc để chuỗi này xảy ra:

- **`sessionTransacted = true`** trên listener container factory. Để mặc định
  (`AUTO_ACKNOWLEDGE`) thì message coi như xử lý xong ngay lúc giao tới — lỗi là mất luôn, không
  thử lại và cũng không vào DLQ.
- **Listener KHÔNG bắt exception.** Ném ra mới rollback được. Bắt rồi ghi log là mất sạch cơ chế
  thử lại.

Giãn cách tăng dần vì lỗi tạm thời (DB bận, mạng chập chờn) thường tự hết sau một lúc; thử lại
dồn dập chỉ làm tình hình tệ thêm. Chỉnh bằng `social.messaging.*`.

### Ba chỗ phải mò mới ra

- **Hàng đợi gốc** không phải property JMS thường — nằm trong trường riêng
  `ActiveMQMessage.getOriginalDestination()`. Đây là chỗ duy nhất trong ứng dụng phụ thuộc vào
  thư viện broker cụ thể.
- **Không lưu "số lần giao lại"** thành một con số riêng. `JMSXDeliveryCount` trên message trong
  DLQ đếm số lần giao của chính message **trong DLQ** (luôn là 1), còn
  `ActiveMQMessage.getRedeliveryCounter()` bị **reset về 0** khi chuyển sang DLQ — lưu cái nào
  cũng ra con số trông hợp lý nhưng vô nghĩa. Thay vào đó lưu property
  `dlqDeliveryFailureCause` do chính broker ghi:
  `"Delivery[4] exceeds redelivery policy limit:RedeliveryPolicy {...}"`.
- **Không bật `spring.activemq.pool.enabled`.** Nó cần thêm thư viện `pooled-jms`; thiếu thư viện
  thì **không có** `ConnectionFactory` nào được tạo và app chết lúc khởi động. Mặc định Spring
  Boot đã bọc trong `CachingConnectionFactory` nên mỗi lần gửi không phải mở kết nối mới.

### Xử lý message trùng

JMS chỉ bảo đảm **"ít nhất một lần"**: broker giao lại khi listener đang xử lý dở mà mất kết nối,
dù lần trước có thể đã chạy xong. Nên `StatisticsService.refresh()` **tính lại từ đầu** chứ không
cộng dồn — chạy một lần hay ba lần đều ra cùng kết quả.

## 8. WebSocket & Biểu đồ

Dashboard vẽ biểu đồ bằng Chart.js và tự cập nhật khi có dữ liệu mới — không cần tải lại trang.

```
job crawl xong  ─┐
                 ├─> DashboardBroadcaster ─> /topic/chart  ─> trình duyệt vẽ lại Chart.js
listener import ─┘                           /topic/crawl
                                             /topic/statistics
```

### Vì sao STOMP chứ không phải WebSocket trần

WebSocket trần chỉ cho gửi/nhận chuỗi byte, không có khái niệm "chủ đề" hay "đăng ký". STOMP thêm
đúng phần đó: một kết nối phục vụ được nhiều loại cập nhật, và trình duyệt chỉ nhận thứ nó đăng ký.
Có test riêng cho điều này (`WebSocketBroadcastTest#chiNhanDuocChuDeDaDangKy`).

**SockJS** làm lớp dự phòng: trình duyệt hoặc proxy nào chặn WebSocket thì tự lùi về HTTP
long-polling.

### Gửi kèm dữ liệu, không gửi tín hiệu suông

Server đẩy xuống **dữ liệu biểu đồ đã tính sẵn**, không phải thông báo "có thay đổi". Nếu chỉ báo
suông thì mọi trình duyệt đang mở sẽ cùng lúc gọi lại `/chart-data`, dồn tải vào đúng thời điểm
vừa crawl xong.

### `/chart-data`: lấy lần đo cuối mỗi ngày, không cộng dồn

Một bài có thể được crawl nhiều lần trong ngày. Cộng hết mọi dòng lại thì số liệu **phồng lên theo
tần suất crawl** chứ không phản ánh tương tác thật — đổi lịch crawl từ 1 giờ xuống 30 phút là biểu
đồ tự nhiên gấp đôi.

Câu truy vấn JOIN với một bảng con chọn ra lần đo cuối của mỗi bài trong mỗi ngày rồi mới cộng:

```sql
JOIN (SELECT post_id, CAST(collected_at AS DATE) AS d, MAX(id) AS last_id
      FROM social_metrics WHERE collected_at >= :from
      GROUP BY post_id, CAST(collected_at AS DATE)) latest ON m.id = latest.last_id
```

Là native query vì JPQL không viết được bảng con trong `FROM`. Hai chi tiết để chạy được trên cả
MySQL lẫn H2: dùng `CAST(... AS DATE)` thay cho `DATE(...)`, và alias là `metric_day` chứ không
phải `day` — `day` là **từ khoá dành riêng** của H2.

### Bảo mật cho WebSocket

Endpoint `/ws/**` được **miễn CSRF**: SockJS khi lùi về HTTP dùng POST cho các khung truyền mà
không gắn được token, có CSRF thì kết nối không bao giờ mở được. Bù lại bằng hai lớp khác:

- Bắt buộc đăng nhập (`anyRequest().authenticated()`).
- Chỉ nhận kết nối từ **cùng nguồn gốc** (`setAllowedOriginPatterns`). Để `"*"` thì mọi trang web
  khác đều mở được kết nối tới đây bằng phiên đăng nhập của người dùng.

### Lỗi phát tin không được làm hỏng nghiệp vụ

Phát tin realtime là việc phụ. Cả `SocialMetricsUpdateJob` lẫn `ImportCompletedListener` đều bọc
try/catch quanh **cả khối** phát tin, không chỉ dựa vào việc `DashboardBroadcaster` tự nuốt lỗi —
vì phần tính dữ liệu biểu đồ nằm **ngoài** broadcaster. Bỏ sót chỗ này thì:

- Ở job crawl: chỉ số đã ghi xong nhưng lần crawl bị báo là thất bại.
- Ở listener JMS: thống kê đã cập nhật đúng nhưng message bị giao lại rồi vào DLQ — DLQ đầy những
  message thực ra đã xử lý xong, che mất message hỏng thật.

### Giới hạn

`enableSimpleBroker` giữ danh sách người đăng ký **trong bộ nhớ**, đủ cho một instance. Chạy nhiều
instance thì trình duyệt nối vào instance nào chỉ nhận được cập nhật do instance đó phát — khi ấy
cần broker ngoài qua `enableStompBrokerRelay`.

Chart.js, SockJS và stomp.js tải từ CDN: không có mạng thì biểu đồ không hiện, phần còn lại của
trang vẫn chạy.

## 9. SOAP & Reflection nâng cao

### Contract-first: XSD là nguồn sự thật

`src/main/resources/xsd/social-analytics.xsd` là **hợp đồng**; các lớp Java được sinh ra từ nó lúc
build (`jaxb2-maven-plugin`), không phải chiều ngược lại. Suy XSD ra từ lớp Java thì đổi tên một
trường trong code là hợp đồng đổi theo mà không ai hay, và mọi client đang chạy sẽ hỏng.

`src/main/resources/xjb/bindings.xjb` bảo XJC sinh `java.time.LocalDateTime` thay vì
`XMLGregorianCalendar`, để code sinh tự động dùng chung kiểu thời gian với phần còn lại.

WSDL sinh tự động từ chính XSD đó tại `/soap/socialAnalytics.wsdl` — tài liệu không bao giờ lệch
với thực tế.

### Hai đầu SOAP đều nằm trong ứng dụng

```
ExchangeRateClient ──HTTP──> POST /soap ──> SocialAnalyticsEndpoint ──> ExchangeRateService
   (tiêu thụ)                                    (tạo)                    (nhà cung cấp giả lập)
```

Dịch vụ tỷ giá là **giả lập**, đóng vai nhà cung cấp SOAP bên ngoài, nhờ vậy bản demo end-to-end
chạy được khi không có mạng. Trỏ sang nhà cung cấp thật bằng `EXCHANGE_RATE_ENDPOINT`, không phải
sửa code.

### Ba quyết định đáng nói

- **Đặt ở `/soap`, không phải `/ws`.** `/ws` đã là endpoint WebSocket của dashboard;
  `MessageDispatcherServlet` mà ánh xạ vào `/ws/*` sẽ nuốt luôn request WebSocket và phần realtime
  chết.
- **Mã lỗi SOAP đúng ngữ nghĩa.** Mặc định Spring-WS trả `Server` fault cho mọi ngoại lệ. Sai với
  lỗi do người gọi gây ra (tiền tệ không hỗ trợ): client SOAP đọc mã `Server` là "lỗi tạm thời,
  cứ thử lại" nên sẽ retry mãi một request không bao giờ đúng được.
  `SoapFaultMappingExceptionResolver` ánh xạ các lỗi tham số sang `Client` fault.
- **`/soap/**` để công khai — đánh đổi có chủ ý.** Client SOAP là máy gọi máy, không có phiên
  đăng nhập trình duyệt; để sau lớp form login thì mọi client (kể cả `ExchangeRateClient` của
  chính ứng dụng này) chỉ nhận về trang đăng nhập. Chấp nhận được vì bề mặt SOAP chỉ lộ **số liệu
  gộp** và tỷ giá — không có dữ liệu cá nhân, không có nội dung bài viết, không có token.
  **Triển khai thật thì phải bọc bằng WS-Security hoặc chặn ở tầng mạng.**

### Reflection nâng cao: xuất model bất kỳ

Dự án có **hai** đường xuất Excel, dùng cho hai mục đích khác nhau:

| | `ExcelMapper` | `ModelExporter` |
|---|---|---|
| Cần gì ở model | `@ExcelColumn` trên từng field | Không cần gì |
| Kiểm soát cột | Tường minh: tên, thứ tự, bắt buộc | Suy ra tự động |
| Đọc/ghi | Cả hai chiều | Chỉ ghi |
| Dùng khi | Mình làm chủ lớp, cần kiểm soát chính xác | Lớp không sửa được (thư viện ngoài, sinh từ XSD), hoặc chỉ cần "xuất bảng này ra Excel" |

`ModelExporter` ưu tiên `ExcelMapper` khi model có `@ExcelColumn` — **khai báo tường minh luôn
thắng suy đoán**.

`ModelIntrospector` suy ra cột bằng Reflection:

- **Ưu tiên gọi method (getter) thay vì đọc thẳng field** — getter mới là phần công khai của lớp,
  và nhiều lớp tính giá trị trong getter chứ không lưu sẵn. Không có getter thì mới đọc field.
- **`boolean` dùng `isXxx()`** chứ không phải `getXxx()`.
- **Thứ tự cột**: `record` theo thứ tự khai báo component; lớp thường theo thứ tự **field**, vì
  `getMethods()` do Reflection trả về **không có thứ tự ổn định giữa các lần chạy JVM** — xếp theo
  field thì file xuất ra lần nào cũng như nhau.
- `camelCase` → `"Camel case"`, khỏi khai tay tiêu đề từng cột.
- Getter tự ném lỗi (lazy loading ngoài transaction) thì báo rõ **thuộc tính nào** hỏng.

Thêm một model xuất được = thêm **một dòng** vào bảng đăng ký trong `ModelExportService`. Không
phải viết thêm endpoint, không phải tạo thêm DTO.

## 10. Đóng gói & chạy demo

```bash
./mvnw clean package                       # chạy toàn bộ test rồi đóng gói
docker compose up -d                       # MySQL + ActiveMQ
java -jar target/social-analytics-0.0.1-SNAPSHOT.jar
```

Kèm Social Login thật:

```bash
FACEBOOK_CLIENT_ID=... FACEBOOK_CLIENT_SECRET=... \
  java -jar target/social-analytics-0.0.1-SNAPSHOT.jar
```

Muốn xem job crawl chạy ngay thay vì đợi 1 giờ:

```bash
CRAWL_INITIAL_DELAY=PT10S CRAWL_INTERVAL=PT60S \
  java -jar target/social-analytics-0.0.1-SNAPSHOT.jar
```

Kiểm nhanh vài thứ không cần đăng nhập:

```bash
curl http://localhost:8080/api/v1/soap/socialAnalytics.wsdl        # hợp đồng SOAP

curl -X POST -H 'Content-Type: text/xml' --data '<?xml version="1.0"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
<getExchangeRateRequest xmlns="http://demo/socialanalytics/ws">
<fromCurrency>USD</fromCurrency><toCurrency>VND</toCurrency>
</getExchangeRateRequest></soap:Body></soap:Envelope>' \
  http://localhost:8080/api/v1/soap
```

Các màn hình: `/api/v1/login` → `/api/v1/dashboard` (biểu đồ realtime, nút chạy job, form CSRF);
Swagger ở `/api/v1/swagger-ui.html`; quản trị ActiveMQ ở `localhost:8161` (admin/admin).

## 11. Kiểm thử

```bash
./mvnw test
```

**337 test**, chạy trên H2 và broker ActiveMQ nhúng (`vm://`) ở `MODE=MySQL` — không cần dựng MySQL thật.

| Tầng | Kiểu test | Lớp test |
|---|---|---|
| Engine Excel | JUnit5 thuần | `ExcelMapperTest` (17) |
| Service | JUnit5 + Mockito | `PostServiceTest` (16), `MetricServiceTest` (13), `PostImportServiceTest` (15), `ReportExportServiceTest` (11) |
| Repository | `@DataJpaTest` | `PostRepositoryTest` (11), `SocialMetricRepositoryTest` (9) |
| Controller (lát cắt web) | `@WebMvcTest` + `@MockitoBean` | `PostControllerTest` (17) |
| Đầu-cuối | `@SpringBootTest` + `MockMvc` | `PostControllerIntegrationTest` (8), `MetricControllerIntegrationTest` (7), `ExcelControllerIntegrationTest` (16) |
| Bảo mật | `@SpringBootTest` + `MockMvc` | `SecurityRulesTest` (20), `OAuth2LoginTest` (11), `CsrfCookieTest` (1) |
| Cấu hình Social Login | `ApplicationContextRunner` | `SocialLoginClientRegistrationsTest` (8) |
| JMS | `@SpringBootTest` + broker nhúng | `ImportMessagingIntegrationTest` (4), `RetryAndDeadLetterTest` (5), `StatisticsControllerIntegrationTest` (5) |
| Thống kê | `@DataJpaTest` | `StatisticsServiceTest` (6) |
| Biểu đồ | `@DataJpaTest` / `@SpringBootTest` | `ChartDataServiceTest` (11), `ChartControllerIntegrationTest` (6) |
| WebSocket | `@SpringBootTest(RANDOM_PORT)` + STOMP client | `WebSocketBroadcastTest` (4) |
| SOAP | `MockWebServiceClient` / `MockWebServiceServer` | `SocialAnalyticsEndpointTest` (5), `ExchangeRateClientTest` (4) |
| SOAP qua HTTP thật | `@SpringBootTest(RANDOM_PORT)` | `SoapOverHttpTest` (4) |
| Reflection nâng cao | JUnit5 thuần | `ModelExporterTest` (15) |
| Export generic | `@SpringBootTest` | `ModelExportControllerIntegrationTest` (6) |
| **Đầu-cuối toàn hệ thống** | `@SpringBootTest` + `MockMvc` | `EndToEndIntegrationTest` (9) |
| Social Login | Mockito / `@DataJpaTest` | `SocialUserAttributesTest` (12), `SocialLoginUserServiceTest` (8), `JpaOAuth2AuthorizedClientServiceTest` (8) |
| Job & đa luồng | Mockito | `SocialMetricsCollectorTest` (6), `SocialMetricsUpdateJobTest` (12), `MockSocialApiClientTest` (7) |
| Job & đa luồng | `@SpringBootTest` | `AsyncConfigTest` (6), `SocialMetricsJobIntegrationTest` (6), `CrawlControllerIntegrationTest` (8) |

### Độ phủ

`./mvnw verify` rồi mở `target/site/jacoco/index.html`.

```
độ phủ lệnh : 90%
độ phủ nhánh: 75%
```

| Gói | Phủ | Ghi chú |
|---|---|---|
| `dto.*`, `mapper`, `util` | 100% | |
| `config` | 97% | |
| `soap` | 96% | |
| `service` | 95% | |
| `controller` | 94% | |
| `entity` | 91% | |
| `excel` | 86% | |
| `messaging` | 82% | |
| `security` | 78% | Nhánh chưa phủ: luồng OAuth2 cần credential thật |
| `exception` | 59% | **Thấp nhất** — nhiều nhánh dịch kiểu lỗi chưa được chạm tới |

Lớp JAXB sinh tự động từ XSD bị loại khỏi phép đo: đó là code máy sinh, đo độ phủ của chúng chỉ
làm loãng con số thật.

**Chỗ còn mỏng, nói thẳng:** `GlobalExceptionHandler` mới 59% — các nhánh `describeType()` cho
kiểu ngày/boolean và vài handler ít gặp chưa có test riêng. Phần đăng nhập Facebook cũng chỉ được
kiểm tới bước chuyển hướng; đoạn callback → đổi token → lưu user chưa chạy với app Facebook thật.

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
- Test JMS chạy trên broker **nhúng** (`vm://localhost?broker.persistent=false`) nên `./mvnw test`
  không cần Docker. Listener chạy luồng khác nên dùng `Awaitility` (`await().untilAsserted`) thay
  vì khẳng định ngay — hoặc `Thread.sleep`, thứ cho ra test lúc xanh lúc đỏ tuỳ máy.
- Test WebSocket chạy trên cổng thật (`RANDOM_PORT`) với `WebSocketStompClient` — `MockMvc` không
  có WebSocket. Endpoint `/ws` yêu cầu đăng nhập nên test mở riêng bằng một `SecurityFilterChain`
  đặt trước, **chỉ trong test**, để kiểm đúng việc truyền tin; việc endpoint được bảo vệ ở cấu
  hình thật thì `SecurityRulesTest` kiểm.
- **Mock ở đúng tầng cần mock.** `ExchangeRateClientTest` và `EndToEndIntegrationTest` đều dùng
  `MockWebServiceServer`, chặn ngay **dưới** tầng HTTP nên không đi qua Spring Security — cả hai
  vẫn xanh trong khi endpoint `/soap` thật bị chặn ở lớp đăng nhập và mọi client SOAP chỉ nhận về
  trang login. `SoapOverHttpTest` chạy trên cổng thật để bịt đúng khoảng trống đó.
- **Cảnh giác khi profile test đặt giá trị khác production.** `spring.activemq.pool.enabled=false`
  trong profile test đã che mất lỗi chỉ xảy ra ở production (thiếu `ConnectionFactory`), và app
  chỉ chết khi chạy thật. Cấu hình nào lệch giữa hai bên thì phải có lý do rõ ràng.

## 12. Tổng kết kiến trúc

### Toàn cảnh

```
                    Trình duyệt
                        |
     ┌──────────────────┼──────────────────┐
     | HTML (Thymeleaf) | REST JSON        | STOMP/WebSocket
     v                  v                  v
  /login /dashboard   /posts /metrics    /topic/chart
                      /chart-data        /topic/crawl
                      /export/{model}
                        |
     ┌──────────────────┴──────────────────────────────┐
     |            Spring Security (CSRF, OAuth2)        |
     └──────────────────┬──────────────────────────────┘
                        v
                    Service
          ┌─────────────┼──────────────┬──────────────┐
          v             v              v              v
     Repository   ExcelMapper /    SocialApiClient   SOAP
      (JPA)       ModelExporter     (crawl)       (Spring-WS)
          |                              ^              ^
          v                              |              |
       MySQL                       @Scheduled +     /soap
                                     @Async        (tạo & tiêu thụ)
          ^
          |  listener
     ActiveMQ (import.completed, ActiveMQ.DLQ)
```

### Một luồng dữ liệu đi qua gần hết hệ thống

```
1. Người dùng đăng nhập Facebook          -> OAuth2, token lưu vào oauth2_authorized_clients
2. Upload Excel                            -> ExcelMapper đọc bằng Reflection, lưu posts
3. Import xong (SAU khi commit)            -> message IMPORT_COMPLETED lên ActiveMQ
4. Listener nhận                           -> tính lại platform_summaries
5. Job @Scheduled mỗi 1 giờ                -> @Async crawl song song theo tài khoản, ghi social_metrics
6. Crawl/import xong                       -> đẩy /topic/chart qua WebSocket
7. Trình duyệt nhận                        -> Chart.js vẽ lại, không tải lại trang
8. Xuất báo cáo                            -> ExcelMapper (@ExcelColumn) hoặc ModelExporter (Reflection)
9. Hệ thống khác hỏi số liệu               -> SOAP /soap, hợp đồng XSD
```

`EndToEndIntegrationTest` chạy đúng chuỗi này trong 9 bước.

### Nguyên tắc lặp lại xuyên suốt

- **Việc chậm thì đẩy ra khỏi request.** Crawl vào thread pool riêng, tính thống kê vào hàng đợi.
  Người dùng nhận phản hồi ngay khi dữ liệu đã an toàn.
- **Chỉ gửi tin sau khi commit.** Cả JMS lẫn WebSocket. Gửi trong transaction thì bên nhận đọc
  được trạng thái chưa tồn tại.
- **Lỗi của việc phụ không được làm hỏng việc chính.** Phát tin realtime hỏng thì job crawl vẫn
  thành công; một bài crawl lỗi thì tài khoản đó vẫn chạy tiếp; một dòng Excel sai thì các dòng
  còn lại vẫn nhập được.
- **Tính lại từ đầu thay vì cộng dồn.** `platform_summaries` dựng lại từ `posts`, nên xử lý một
  message hai lần vẫn ra đúng một kết quả — điều bắt buộc vì JMS chỉ bảo đảm "ít nhất một lần".
- **Khai báo tường minh thắng suy đoán.** Có `@ExcelColumn` thì dùng nó; không có mới để
  Reflection tự suy.
- **Nói đúng bản chất lỗi.** 401 khác 403 khác 503; SOAP `Client` fault khác `Server` fault. Sai
  mã lỗi là bên gọi xử lý sai — retry vô ích hoặc bỏ cuộc sớm.

### Những chỗ còn thiếu, liệt kê thẳng

| Hạng mục | Trạng thái |
|---|---|
| Đăng nhập Facebook thật | Xác minh tới bước chuyển hướng; callback → đổi token chưa chạy với app thật |
| Biểu đồ trên trình duyệt | Server phát đúng, client STOMP trong test nhận đúng; chưa xem tận mắt Chart.js vẽ |
| Chạy nhiều instance | Job crawl sẽ crawl trùng (cờ chặn nằm trong bộ nhớ); WebSocket broker in-memory không chia sẻ giữa các instance |
| Quản lý schema | `ddl-auto: update` — đã phải sửa tay một lần (`users.email`); nên chuyển sang Flyway |
| Bảo mật `/soap` | Đang công khai có chủ ý; production cần WS-Security hoặc chặn ở tầng mạng |
| `SocialApiClient` | Dữ liệu giả — **Facebook không cấp quyền đọc bài viết**, xem mục 6 |
| Độ phủ `exception` | 59%, thấp nhất trong các gói |

### Phân lớp

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
