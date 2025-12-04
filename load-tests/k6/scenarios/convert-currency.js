// Currency conversion load test
// Run with: k6 run scenarios/convert-currency.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomAmount } from '../lib/helpers.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  
  // Use test credentials (pre-created user)
  const loginPayload = JSON.stringify({
    username: 'testuser',
    password: 'password123',
  });
  
  const loginResponse = http.post(
    `${baseUrl}/api/v1/auth/login`,
    loginPayload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  if (!check(loginResponse, { 'login successful': (r) => r.status === 200 })) {
    return;
  }
  
  const token = JSON.parse(loginResponse.body).token;
  
  // Convert currency
  const amount = randomAmount(10, 500);
  const convertPayload = JSON.stringify({
    fromCurrency: 'USD',
    toCurrency: 'NGN',
    amount: amount,
  });
  
  const convertResponse = http.post(
    `${baseUrl}/api/v1/accounts/convert`,
    convertPayload,
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }
  );
  
  check(convertResponse, {
    'conversion successful': (r) => r.status === 200,
    'has converted amount': (r) => {
      const body = JSON.parse(r.body);
      return body.convertedAmount > 0;
    },
  });
  
  sleep(2);
}
