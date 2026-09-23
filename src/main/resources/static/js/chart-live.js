// Server đẩy xuống dữ liệu đã tính sẵn, không phải tín hiệu "có thay đổi".

const API_BASE = '/api/v1';
const COLORS = { likes: '#1877f2', shares: '#42b72a', comments: '#f7b928', followers: '#8b5cf6' };

let trendChart = null;
let platformChart = null;

function setLiveBadge(text, kind) {
    const badge = document.getElementById('liveBadge');
    if (badge) {
        badge.textContent = text;
        badge.className = 'badge ' + kind;
    }
}

function buildTrendChart(data) {
    const canvas = document.getElementById('trendChart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }
    const config = {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [
                dataset('Lượt thích', data.likes, COLORS.likes),
                dataset('Chia sẻ', data.shares, COLORS.shares),
                dataset('Bình luận', data.comments, COLORS.comments)
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            scales: { y: { beginAtZero: true } }
        }
    };
    if (trendChart) {
        trendChart.destroy();
    }
    trendChart = new Chart(canvas, config);
}

function dataset(label, values, color) {
    return {
        label: label,
        data: values,
        borderColor: color,
        backgroundColor: color + '22',
        fill: true,
        tension: 0.3,
        pointRadius: 3
    };
}

function buildPlatformChart(platforms) {
    const canvas = document.getElementById('platformChart');
    if (!canvas || typeof Chart === 'undefined' || !platforms) {
        return;
    }
    if (platformChart) {
        platformChart.destroy();
    }
    platformChart = new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels: platforms.map(p => p.platform),
            datasets: [{
                data: platforms.map(p => p.postCount),
                backgroundColor: [COLORS.likes, '#111']
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { title: { display: true, text: 'Số bài theo nền tảng' } }
        }
    });
}

function render(data) {
    buildTrendChart(data);
    buildPlatformChart(data.platforms);
}

async function loadChartData() {
    try {
        const response = await fetch(API_BASE + '/chart-data', {
            headers: { 'Accept': 'application/json' }
        });
        if (!response.ok) {
            return;
        }
        render(await response.json());
    } catch (error) {
        console.warn('Không tải được dữ liệu biểu đồ', error);
    }
}

function connectLiveUpdates() {
    if (typeof StompJs === 'undefined' || typeof SockJS === 'undefined') {
        setLiveBadge('không có realtime', 'warn');
        return;
    }
    const client = new StompJs.Client({
        // SockJS: chặn WebSocket thì tự lùi về HTTP long-polling
        webSocketFactory: () => new SockJS(API_BASE + '/ws'),
        reconnectDelay: 5000
    });

    client.onConnect = () => {
        setLiveBadge('realtime', 'ok');
        client.subscribe('/topic/chart', message => render(JSON.parse(message.body)));
        client.subscribe('/topic/crawl', message => {
            const run = JSON.parse(message.body);
            const output = document.getElementById('crawlResult');
            if (output) {
                output.textContent =
                    'Vừa cập nhật: ' + run.succeededPosts + '/' + run.totalPosts +
                    ' bài (' + run.status + ')';
            }
        });
    };
    client.onWebSocketClose = () => setLiveBadge('mất kết nối', 'warn');
    client.onStompError = frame => {
        console.warn('Lỗi STOMP', frame);
        setLiveBadge('lỗi kết nối', 'warn');
    };

    client.activate();
}

document.addEventListener('DOMContentLoaded', () => {
    loadChartData();
    connectLiveUpdates();
});
