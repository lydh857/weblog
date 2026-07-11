import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ===== 自定义指标 =====
const errorRate = new Rate('errors');
const apiDuration = new Trend('api_duration');

// ===== 配置 =====
const BASE_URL = __ENV.BASE_URL || 'http://localhost:9091';

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // 预热
    { duration: '90s', target: 80 },   // 加压
    { duration: '120s', target: 120 }, // 峰值
    { duration: '30s', target: 0 },    // 降压
  ],
  thresholds: {
    http_req_duration: ['p(95)<600', 'p(99)<1200'],
    errors: ['rate<0.02'],
    api_duration: ['p(95)<500'],
  },
};

function markResult(res, successRule) {
  const ok = successRule(res);
  errorRate.add(!ok);
  apiDuration.add(res.timings.duration);
}

function isBusinessOk(res) {
  if (res.status !== 200) return false;
  try {
    const json = res.json();
    return json && json.code === 200;
  } catch (e) {
    return false;
  }
}

// ===== 测试场景 =====
export default function () {
  let sampleSlug = 'welcome';

  group('首页文章列表', () => {
    const res = http.get(`${BASE_URL}/api/portal/post?pageNum=1&pageSize=10&sortBy=latest`);
    check(res, {
      '文章列表状态码200': (r) => r.status === 200,
      '文章列表业务成功': (r) => isBusinessOk(r),
      '文章列表响应<800ms': (r) => r.timings.duration < 800,
    });

    try {
      const records = res.json('data.records');
      if (records && records.length > 0 && records[0].slug) {
        sampleSlug = records[0].slug;
      }
    } catch (e) {}

    markResult(res, isBusinessOk);
  });

  sleep(0.5);

  group('文章详情', () => {
    const res = http.get(`${BASE_URL}/api/portal/post/${sampleSlug}`);
    check(res, {
      '文章详情状态码200': (r) => r.status === 200,
      '文章详情业务成功': (r) => isBusinessOk(r),
      '文章详情响应<800ms': (r) => r.timings.duration < 800,
    });
    markResult(res, isBusinessOk);
  });

  sleep(0.5);

  group('搜索接口', () => {
    const res = http.get(`${BASE_URL}/api/search?keyword=test&pageNum=1&pageSize=10`);
    check(res, {
      '搜索状态码200': (r) => r.status === 200,
      '搜索业务成功': (r) => isBusinessOk(r),
      '搜索响应<1200ms': (r) => r.timings.duration < 1200,
    });
    markResult(res, isBusinessOk);
  });

  sleep(0.5);

  group('分类列表', () => {
    const res = http.get(`${BASE_URL}/api/portal/category/tree`);
    check(res, {
      '分类树状态码200': (r) => r.status === 200,
      '分类树业务成功': (r) => isBusinessOk(r),
    });
    markResult(res, isBusinessOk);
  });

  sleep(0.5);

  group('标签列表', () => {
    const res = http.get(`${BASE_URL}/api/portal/tag/cloud`);
    check(res, {
      '标签云状态码200': (r) => r.status === 200,
      '标签云业务成功': (r) => isBusinessOk(r),
    });
    markResult(res, isBusinessOk);
  });

  sleep(0.5);
}
