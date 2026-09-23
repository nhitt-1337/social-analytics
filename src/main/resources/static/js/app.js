// Cookie XSRF-TOKEN không đặt HttpOnly nên JavaScript đọc được
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
                // Thiếu header này là 403
                'X-XSRF-TOKEN': csrfToken()
            },
            body: '{}'
        });
        const text = await response.text();
        // 422 nghĩa là đã qua CSRF, chỉ còn vướng validate
        output.textContent = 'HTTP ' + response.status + '\n' + text;
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}

// POST nên vẫn phải kèm token CSRF
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
        // Không tải lại trang: ô trạng thái và biểu đồ đã tự đổi qua WebSocket
        output.textContent =
            'Xong sau ' + run.durationMs + ' ms — ' +
            run.succeededPosts + '/' + run.totalPosts + ' bài.\n' +
            'Ô "Cập nhật lần cuối" và biểu đồ phía trên vừa tự đổi, không tải lại trang.';
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}

// Dùng fetch thay form submit để hiện kết quả ngay tại chỗ
async function importExcel() {
    const input = document.getElementById('importFile');
    const output = document.getElementById('importResult');

    if (!input.files.length) {
        output.className = 'alert warn';
        output.textContent = 'Chưa chọn file.';
        return;
    }

    const body = new FormData();
    body.append('file', input.files[0]);
    output.className = 'result';
    output.textContent = 'Đang nhập...';

    try {
        const response = await fetch('/api/v1/import-posts', {
            method: 'POST',
            // Không đặt Content-Type: trình duyệt tự thêm boundary
            headers: { 'X-XSRF-TOKEN': csrfToken(), 'Accept': 'application/json' },
            body: body
        });
        const data = await response.json();

        if (!response.ok) {
            output.className = 'alert error';
            output.textContent = 'Lỗi: ' + (data.error ? data.error.message : response.status);
            return;
        }

        // Bỏ qua vài dòng không phải thất bại — hiển thị phải nói rõ
        output.className = data.imported > 0 ? 'alert ok' : 'alert warn';
        let text = data.imported > 0
            ? '✓ Đã nhập ' + data.imported + '/' + data.totalRows + ' bài viết.'
            : 'Không nhập được bài nào (' + data.totalRows + ' dòng).';

        if (data.errors.length) {
            text += '\n\n' + data.errors.length + ' dòng được bỏ qua — các dòng còn lại '
                + 'vẫn đã lưu bình thường:\n'
                + data.errors.map(e => '  · dòng ' + e.rowNumber + ': ' + e.message).join('\n');
        }
        output.textContent = text;
        input.value = '';
    } catch (error) {
        output.textContent = 'Lỗi: ' + error;
    }
}
