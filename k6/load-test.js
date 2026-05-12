import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        sustained_load: {
            executor: 'constant-arrival-rate',
            rate: 10000,
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 500,
            maxVUs: 1000,
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<10'],
        http_req_failed: ['rate<0.001'],
    },
};

export default function () {
    const res = http.get('http://localhost:8080/hello');
    check(res, {
        'status is 200': (r) => r.status === 200,
        'body is correct': (r) => r.body === 'Hello, World!',
    });
}
