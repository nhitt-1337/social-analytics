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
        // KHÔNG tải lại trang. Ô "Cập nhật lần cuối" và biểu đồ đã tự đổi qua WebSocket
        // (/topic/crawl và /topic/chart) — tải lại là che mất đúng cái đang muốn cho thấy.
        output.textContent =
            'Xong sau ' + run.durationMs + ' ms — ' +
            run.succeededPosts + '/' + run.totalPosts + ' bài.\n' +
            'Ô "Cập nhật lần cuối" và biểu đồ phía trên vừa tự đổi, không tải lại trang.';
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}

// Nhập Excel. Gửi bằng fetch chứ không phải form submit để hiện kết quả ngay tại chỗ —
// import "chịu lỗi" nên phần errors mới là thứ đáng xem.
async function importExcel() {
    const input = document.getElementById('importFile');
    const output = document.getElementById('importResult');

    if (!input.files.length) {
        output.textContent = 'Chưa chọn file.';
        return;
    }

    const body = new FormData();
    body.append('file', input.files[0]);
    output.textContent = 'Đang nhập...';

    try {
        const response = await fetch('/api/v1/import-posts', {
            method: 'POST',
            // Không đặt Content-Type: trình duyệt tự thêm boundary cho multipart.
            headers: { 'X-XSRF-TOKEN': csrfToken(), 'Accept': 'application/json' },
            body: body
        });
        const data = await response.json();

        if (!response.ok) {
            output.textContent = 'Lỗi: ' + (data.error ? data.error.message : response.status);
            return;
        }

        let text = 'Đọc ' + data.totalRows + ' dòng — '
            + data.imported + ' bài đã lưu, ' + data.skipped + ' bỏ qua.';
        if (data.errors.length) {
            text += '\n\nCác dòng bị bỏ:\n'
                + data.errors.map(e => '  dòng ' + e.rowNumber + ': ' + e.message).join('\n');
        }
        output.textContent = text;
        input.value = '';
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}
