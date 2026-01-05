// Smoke test - Basic functionality check
// Run with: k6 run scenarios/smoke-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '1m',
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  
  // Health check
  const healthResponse = http.get(`${baseUrl}/health`);
  check(healthResponse, {
    'health check status is 200': (r) => r.status === 200,
  });
  
  sleep(1);
}
