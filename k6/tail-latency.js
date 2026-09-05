// 꼬리 지연을 관찰한다 — 열 번에 한 번만 5초 걸리는 상태에서 평균은 멀쩡한데 p95만 튀는 것이 목표다.
//
//   k6 run k6/tail-latency.js
//
// A에만 고장을 걸고 B는 정상으로 둔다. supplier_a_duration과 supplier_b_duration을 나란히 놓으면
// 지연이 한쪽에만 걸린다는 것이 드러난다. 끝나면 teardown이 모드를 되돌린다.

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { SUPPLIERS, queryParams, setMode, resetMode } from './control.js';

export const options = {
  vus: 10,
  duration: '60s',
};

const durations = {
  a: new Trend('supplier_a_duration', true),
  b: new Trend('supplier_b_duration', true),
};

export function setup() {
  resetMode(SUPPLIERS.b);
  setMode(SUPPLIERS.a, { value: 'delay', rate: 0.1, delayMillis: 5000 });
}

export default function () {
  Object.entries(SUPPLIERS).forEach(([key, supplier]) => {
    const response = http.get(`${supplier.baseUrl}${supplier.availabilityPath}`, queryParams());
    durations[key].add(response.timings.duration);
    check(response, { 'status is 200': (r) => r.status === 200 });
  });
}

export function teardown() {
  Object.values(SUPPLIERS).forEach(resetMode);
}
