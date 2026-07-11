import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9091';

const hit429 = new Counter('hit_429');
const hit5xx = new Counter('hit_5xx');
const requestOk = new Rate('request_ok');

export const options = {
  scenarios: {
    loginBurst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 10 },
        { duration: '30s', target: 40 },
        { duration: '45s', target: 80 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    request_ok: ['rate>0.95'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const payload = JSON.stringify({
    email: `attacker_${__VU}_${__ITER}@example.com`,
    password: 'WrongPass123!',
    rememberMe: false,
  });

  const res = http.post(`${BASE_URL}/api/portal/auth/login`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Captcha-Token': 'invalid-captcha-token',
    },
  });

  if (res.status === 429) {
    hit429.add(1);
  }

  if (res.status >= 500) {
    hit5xx.add(1);
  }

  const ok = check(res, {
    '登录接口未返回5xx': (r) => r.status < 500,
    '响应时间<1500ms': (r) => r.timings.duration < 1500,
  });
  requestOk.add(ok);

  sleep(0.2);
}
