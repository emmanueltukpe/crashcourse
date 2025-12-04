# Load Testing Guide with k6

A practical guide to load testing our payment system, from basics to simulating 1 million users.

## Table of Contents

1. [What is Load Testing?](#what-is-load-testing)
2. [k6 Basics](#k6-basics)
3. [Test Scenarios](#test-scenarios)
4. [Metrics and Interpretation](#metrics-and-interpretation)
5. [Simulating 1M Users](#simulating-1m-users)
6. [Performance Tuning](#performance-tuning)

---

## What is Load Testing?

**Load testing** validates that your system can handle expected (and unexpected) user traffic.

### Types of Tests

**1. Smoke Test** (Sanity check)

- **Users**: 1-10
- **Duration**: 1-2 minutes
- **Goal**: Verify basic functionality
- **Run**: Every deployment

**2. Load Test** (Normal conditions)

- **Users**: Expected peak (e.g., 1000)
- **Duration**: 5-10 minutes
- **Goal**: System performs well under normal load
- **Run**: Weekly

**3. Stress Test** (Breaking point)

- **Users**: Beyond peak (e.g., 5000)
- **Duration**: 10-20 minutes
- **Goal**: Find system limits
- **Run**: Monthly

**4. Spike Test** (Sudden traffic)

- **Users**: 100 → 5000 → 100
- **Duration**: Quick bursts
- **Goal**: Handle flash sales, viral posts
- **Run**: Before major events

**5. Soak Test** (Endurance)

- **Users**: Normal load
- **Duration**: Hours/days
- **Goal**: Find memory leaks, degradation
- **Run**: Quarterly

---

## k6 Basics

### Installation

```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows (with Chocolatey)
choco install k6

# Or download from https://k6.io/docs/getting-started/installation/
```

### Simple Test

```javascript
// simple-test.js
import http from "k6/http";
import { check, sleep } from "k6";

export default function () {
  // Make HTTP request
  const response = http.get("http://localhost:8080/health");

  // Verify response
  check(response, {
    "status is 200": (r) => r.status === 200,
    "response time < 200ms": (r) => r.timings.duration < 200,
  });

  // Think time (simulate user reading page)
  sleep(1);
}
```

**Run:**

```bash
k6 run simple-test.js
```

### Test Configuration

```javascript
export const options = {
  // Virtual Users (simulated users)
  vus: 10,

  // Test duration
  duration: "30s",

  // Thresholds (pass/fail criteria)
  thresholds: {
    http_req_duration: ["p(95)<500"], // 95% of requests < 500ms
    http_req_failed: ["rate<0.01"], // Error rate < 1%
  },
};
```

### Stages (Ramping)

```javascript
export const options = {
  stages: [
    { duration: "30s", target: 20 }, // Ramp up to 20 users
    { duration: "1m", target: 20 }, // Stay at 20 users
    { duration: "30s", target: 100 }, // Ramp up to 100 users
    { duration: "1m", target: 100 }, // Stay at 100 users
    { duration: "30s", target: 0 }, // Ramp down
  ],
};
```

---

## Test Scenarios

### Scenario 1: User Registration

```javascript
// scenarios/register-users.js
import http from "k6/http";
import { check, sleep } from "k6";
import { randomString } from "../lib/helpers.js";

export const options = {
  vus: 10,
  duration: "1m",
  thresholds: {
    http_req_duration: ["p(95)<1000"],
    http_req_failed: ["rate<0.05"],
  },
};

export default function () {
  const baseUrl = "http://localhost:8080";

  // Generate random user data
  const username = `user_${randomString(8)}`;
  const email = `${username}@test.com`;

  const payload = JSON.stringify({
    username: username,
    email: email,
    password: "Test123!",
  });

  const params = {
    headers: {
      "Content-Type": "application/json",
    },
  };

  // Register user
  const response = http.post(
    `${baseUrl}/api/v1/auth/register`,
    payload,
    params
  );

  check(response, {
    "registration successful": (r) => r.status === 201,
    "has userId": (r) => JSON.parse(r.body).userId !== undefined,
  });

  sleep(1);
}
```

### Scenario 2: Currency Conversion

```javascript
// scenarios/convert-currency.js
import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  stages: [
    { duration: "30s", target: 50 },
    { duration: "2m", target: 50 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<2000"], // Conversion can be slower
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const baseUrl = "http://localhost:8080";

  // Login (use pre-created test user)
  const loginPayload = JSON.stringify({
    username: "testuser",
    password: "Test123!",
  });

  const loginResponse = http.post(
    `${baseUrl}/api/v1/auth/login`,
    loginPayload,
    { headers: { "Content-Type": "application/json" } }
  );

  const token = JSON.parse(loginResponse.body).token;

  // Convert currency
  const convertPayload = JSON.stringify({
    fromCurrency: "USD",
    toCurrency: "NGN",
    amount: 100.0,
  });

  const convertResponse = http.post(
    `${baseUrl}/api/v1/accounts/convert`,
    convertPayload,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    }
  );

  check(convertResponse, {
    "conversion successful": (r) => r.status === 200,
    "has converted amount": (r) => JSON.parse(r.body).convertedAmount > 0,
  });

  sleep(2); // User think time
}
```

### Scenario 3: Complete User Journey

```javascript
// scenarios/user-journey.js
import http from "k6/http";
import { check, sleep, group } from "k6";
import { randomString } from "../lib/helpers.js";

export const options = {
  vus: 20,
  duration: "3m",
};

export default function () {
  const baseUrl = "http://localhost:8080";
  const username = `user_${randomString(8)}`;
  let token;

  group("Registration", function () {
    const payload = JSON.stringify({
      username: username,
      email: `${username}@test.com`,
      password: "Test123!",
    });

    const response = http.post(`${baseUrl}/api/v1/auth/register`, payload, {
      headers: { "Content-Type": "application/json" },
    });

    check(response, { registered: (r) => r.status === 201 });
    sleep(1);
  });

  group("Login", function () {
    const payload = JSON.stringify({
      username: username,
      password: "Test123!",
    });

    const response = http.post(`${baseUrl}/api/v1/auth/login`, payload, {
      headers: { "Content-Type": "application/json" },
    });

    check(response, { "logged in": (r) => r.status === 200 });
    token = JSON.parse(response.body).token;
    sleep(1);
  });

  group("Fund Account", function () {
    const payload = JSON.stringify({
      currency: "USD",
      amount: 1000.0,
    });

    const response = http.post(`${baseUrl}/api/v1/accounts/fund`, payload, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    check(response, { funded: (r) => r.status === 200 });
    sleep(2);
  });

  group("Convert Currency", function () {
    const payload = JSON.stringify({
      fromCurrency: "USD",
      toCurrency: "NGN",
      amount: 100.0,
    });

    const response = http.post(`${baseUrl}/api/v1/accounts/convert`, payload, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    check(response, { converted: (r) => r.status === 200 });
    sleep(2);
  });

  group("Check Balance", function () {
    const response = http.get(`${baseUrl}/api/v1/accounts/balance`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    check(response, { "got balance": (r) => r.status === 200 });
    sleep(1);
  });
}
```

### Helper Functions

```javascript
// lib/helpers.js
export function randomString(length) {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  let result = "";
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function randomAmount(min, max) {
  return (Math.random() * (max - min) + min).toFixed(2);
}
```

---

## Metrics and Interpretation

### Key Metrics

**1. Response Time (http_req_duration)**

```
p(50) = 100ms  ← 50% of requests faster than 100ms (median)
p(95) = 500ms  ← 95% of requests faster than 500ms
p(99) = 1000ms ← 99% of requests faster than 1000ms
```

**Good targets:**

- p(50) < 100ms
- p(95) < 500ms
- p(99) < 1000ms

**2. Request Rate (http_reqs)**

```
http_reqs: 5000  (100/s)
```

- Total requests: 5000
- Requests per second: 100

**3. Error Rate (http_req_failed)**

```
http_req_failed: 0.5%
```

- Target: < 1%
- Production: < 0.1%

**4. Virtual Users (vus)**

```
vus: 100
vus_max: 100
```

- How many concurrent users

### Sample Output

```
          /\      |‾‾| /‾‾/   /‾‾/
     /\  /  \     |  |/  /   /  /
    /  \/    \    |     (   /   ‾‾\
   /          \   |  |\  \ |  (‾)  |
  / __________ \  |__| \__\ \_____/ .io

  execution: local
     script: scenarios/convert-currency.js
     output: -

  scenarios: (100.00%) 1 scenario, 50 max VUs, 3m30s max duration

     ✓ conversion successful
     ✓ has converted amount

     checks.........................: 100.00% ✓ 3000    ✗ 0
     data_received..................: 1.2 MB  4.0 kB/s
     data_sent......................: 720 kB  2.4 kB/s
     http_req_blocked...............: avg=0.01ms  min=0s       med=0.01ms  max=1.2ms
     http_req_connecting............: avg=0ms     min=0s       med=0ms     max=0.8ms
   ✓ http_req_duration..............: avg=150ms   min=50ms     med=140ms   max=800ms
     http_req_failed................: 0.00%   ✓ 0       ✗ 3000
     http_req_receiving.............: avg=0.5ms   min=0.1ms    med=0.4ms   max=5ms
     http_req_sending...............: avg=0.2ms   min=0.05ms   med=0.15ms  max=2ms
     http_req_tls_handshaking.......: avg=0ms     min=0s       med=0ms     max=0ms
     http_req_waiting...............: avg=149.3ms min=49.5ms   med=139.4ms max=795ms
     http_reqs......................: 3000    10/s
     iteration_duration.............: avg=5.15s   min=5.05s    med=5.14s   max=5.8s
     iterations.....................: 1500    5/s
     vus............................: 50      min=50    max=50
     vus_max........................: 50      min=50    max=50


running (5m00.0s), 00/50 VUs, 1500 complete and 0 interrupted iterations
default ✓ [======================================] 50 VUs  3m0s
```

**Interpretation:**
✅ 100% checks passed  
✅ 0% error rate  
✅ p(95) = 140ms (good!)  
✅ 10 requests/second throughput

---

## Simulating 1M Users

### Understanding Virtual Users (VUs)

**1 VU ≠ 1 Real User**

```
1 VU can simulate multiple real users:
- Real user: Opens app, waits 10 seconds, clicks, waits 30 seconds
- VU: Makes request, sleeps 40 seconds, repeat

1 VU = ~10 real users (with typical think time)
```

### Ramping Strategy

**Don't go 0 → 1M instantly!**

```javascript
export const options = {
  stages: [
    // Warm-up
    { duration: "5m", target: 1000 }, // 0-1K users
    { duration: "10m", target: 1000 }, // Sustain 1K

    // Ramp to 10K
    { duration: "10m", target: 10000 }, // 1K-10K users
    { duration: "30m", target: 10000 }, // Sustain 10K

    // Ramp to 100K
    { duration: "20m", target: 100000 }, // 10K-100K users
    { duration: "1h", target: 100000 }, // Sustain 100K

    // Ramp to 1M (if you dare!)
    { duration: "30m", target: 1000000 }, // 100K-1M users
    { duration: "2h", target: 1000000 }, // Sustain 1M

    // Cool down
    { duration: "10m", target: 0 }, // Graceful shutdown
  ],
};
```

### Distributed Load Generation

**Single machine can't generate 1M VUs!**

**Use k6 Cloud or run multiple machines:**

```bash
# Machine 1 (generates 100K VUs)
k6 run --vus 100000 --duration 1h test.js

# Machine 2 (generates 100K VUs)
k6 run --vus 100000 --duration 1h test.js

# ... 10 machines total = 1M VUs
```

**k6 Cloud (recommended):**

```bash
k6 cloud test.js --vus 1000000 --duration 1h
```

### Data Seeding

**Pre-create test users** (don't register 1M users during test!)

```bash
# Seed script
for i in {1..1000000}; do
  curl -X POST http://localhost:8080/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"user$i\",\"email\":\"user$i@test.com\",\"password\":\"Test123!\"}"
done
```

Then test with existing users:

```javascript
const userId = __VU; // VU number as user ID
const username = `user${userId}`;
```

---

## Performance Tuning

### Finding Bottlenecks

**1. Application Server**

```
Symptom: High CPU on app servers
Solution: Add more app servers (horizontal scaling)
```

**2. Database**

```
Symptom: Slow queries, high DB CPU
Solution:
- Add indexes
- Add read replicas
- Optimize queries
- Add caching
```

**3. Network**

```
Symptom: High latency, timeouts
Solution:
- CDN for static assets
- Reduce payload sizes
- Compress responses
```

### Optimization Checklist

Before scaling horizontally:

- [ ] Add database indexes
- [ ] Enable response compression
- [ ] Add Redis caching
- [ ] Optimize N+1 queries
- [ ] Increase connection pools
- [ ] Enable HTTP/2
- [ ] Add CDN for static files

After optimization, scale:

- [ ] Add more app servers
- [ ] Add database read replicas
- [ ] Shard database if needed

---

## Summary

**Key Takeaways:**

✅ **Start Small**: Smoke test → Load test → Stress test  
✅ **Ramp Gradually**: Don't jump to target instantly  
✅ **Watch Metrics**: Response time, error rate, throughput  
✅ **Test Realistic Flows**: Complete user journeys  
✅ **Distributed Testing**: Use k6 Cloud for high load  
✅ **Optimize First**: Before adding more servers

**Load Test Checklist:**

- [ ] Write smoke test (10 VUs)
- [ ] Write load test (expected peak)
- [ ] Write stress test (2x peak)
- [ ] Set thresholds (p95 < 500ms)
- [ ] Run before deployments
- [ ] Monitor production metrics
- [ ] Compare test vs production

**Next Steps:**

- Run our test scenarios in `load-tests/k6/``
- Compare results before/after optimizations
- Read [scaling-strategy.md](scaling-strategy.md) for scaling guidance
- Set up continuous load testing in CI/CD

Load testing prevents surprises in production! 📊
