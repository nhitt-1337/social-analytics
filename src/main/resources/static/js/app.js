// Đọc token CSRF từ cookie XSRF-TOKEN.
// Cookie này không đặt HttpOnly (xem SecurityConfig) nên JavaScript đọc được — đó là điều kiện
// để kiểu gọi API bằng fetch dùng chung được cơ chế CSRF với form thường.
function csrfToken() {
    const entry = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
    return entry ? decodeURIComponent(entry.split('=')[1]) : '';
}

async function demoApiCall() {
    const output = document.getElementById('apiResult');
    try {
        const response = await fetch('/api/v1/posts', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // Thiếu header này thì CsrfFilter trả 403 trước khi request tới controller.
                'X-XSRF-TOKEN': csrfToken()
            },
            body: '{}'
        });
        const text = await response.text();
        // 422 nghĩa là đã QUA được CSRF và chỉ còn vướng validate — đúng điều muốn minh hoạ.
        output.textContent = 'HTTP ' + response.status + '\n' + text;
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}

// Bấm "Chạy cập nhật ngay": POST nên vẫn phải kèm token CSRF như mọi request đổi dữ liệu khác.
async function runCrawlNow() {
    const output = document.getElementById('crawlResult');
    output.textContent = 'Đang chạy...';
    try {
        const response = await fetch('/api/v1/crawl/run', {
            method: 'POST',
            headers: { 'X-XSRF-TOKEN': csrfToken(), 'Accept': 'application/json' }
        });
        if (response.status === 409) {
            output.textContent = 'Đang có một lần chạy khác chưa xong.';
            return;
        }
        const run = await response.json();
        output.textContent =
            'Trạng thái: ' + run.status +
            '\nTài khoản: ' + run.totalAccounts +
            '\nThành công: ' + run.succeededPosts + '/' + run.totalPosts +
            '\nLỗi: ' + run.failedPosts +
            '\nMất: ' + run.durationMs + ' ms';
        // Tải lại trang để ô "Cập nhật lần cuối" hiện số liệu mới.
        setTimeout(() => window.location.reload(), 1200);
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}
