// 두 모의 서버를 동시에 때려 p50·p95와 실패율을 본다. 정상 모드에서의 기준선을 잡는 스크립트이며,
// 고장을 걸고 보는 것은 tail-latency.js다.
//
//   k6 run k6/load.js
//
// 임계값(threshold)을 두지 않는 이유: 이 숫자는 모의 서버의 성능이지 자사 앱의 성능이 아니다.
// 지금 통과·실패 선을 그으면 아직 없는 코드의 성능을 넘겨짚는 것이 된다.

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { SUPPLIERS, queryParams, resetMode } from './control.js';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '5s', target: 0 },
  ],
};

// 서버별로 지표를 따로 둔다. 하나로 합치면 한쪽만 느린 상황이 평균에 묻힌다.
const durations = {
  a: new Trend('supplier_a_duration', true),
  b: new Trend('supplier_b_duration', true),
};

export function setup() {
  Object.values(SUPPLIERS).forEach(resetMode);
}

export default function () {
  Object.entries(SUPPLIERS).forEach(([key, supplier]) => {
    hit(durations[key], supplier, supplier.listPath);
    hit(durations[key], supplier, supplier.availabilityPath);
  });
}

function hit(duration, supplier, path) {
  const response = http.get(`${supplier.baseUrl}${path}`, queryParams());
  duration.add(response.timings.duration);
  check(response, { 'status is 200': (r) => r.status === 200 });
}
