// 자사 검색 API 를 대상으로 하는 부하·실측 스크립트.
//
//   APP_BASE_URL=http://localhost:8080 k6 run k6/app-search.js
//
// 모의 공급사 서버 A·B 를 함께 띄운 상태에서 돌린다. 이 스크립트가 존재하는 이유는 부하 측정만이
// 아니다 — **프록시를 목으로 대체하는 자동 테스트가 원리적으로 타지 않는 갈래**를 여기서 잡는다.
//
//   1) 날짜 직렬화 — 앱이 공급사로 보내는 checkIn/checkOut 이 계약 §1 의 YYYY-MM-DD 로 나가는가.
//      F5 구현 중 JVM 로케일 표기(`checkIn=26. 9. 10.`)로 나가 모의 서버가 400 으로 거절한 전례가
//      있고, HTTP Interface 프록시가 URL 을 만드는 경로는 단위 테스트 어디에서도 실행되지 않는다.
//      여기서는 검색이 200 과 results 를 돌려주는 것으로 그 경로가 살아 있음을 확인한다.
//   2) 예산 — 공급사 하나가 느리거나 죽어도 응답이 supplier.fan-out.budget 안에 나오는가.
//   3) 부분 실패 — 한쪽만 죽였을 때 200 + suppliers 에 FAILED 가 남는가.
//
// 고장 주입은 k6/control.js 의 setMode 로 한다. A 만 내리고 이 스크립트를 돌리면 위 3)이 보인다.
import http from 'k6/http';
import { check } from 'k6';

const APP_BASE_URL = __ENV.APP_BASE_URL || 'http://localhost:8080';
const SEARCH_PATH = __ENV.APP_SEARCH_PATH || '/api/v1/stays/search';

// 모의 서버 시드 기준 검산이 되는 구간이다 — 09-11·09-12 가 주말이라 할증이 걸리고,
// A OCN-DBL 435,600 / B R-201 453,600 · 양쪽 bookableRooms 1 이 나온다 (01-design.md §1).
const CHECK_IN = __ENV.CHECK_IN || '2026-09-10';
const CHECK_OUT = __ENV.CHECK_OUT || '2026-09-13';
const ADULTS = __ENV.ADULTS || '2';
const CHILDREN = __ENV.CHILDREN || '0';

// budget 5s + 여유. 이 값을 넘으면 조합기의 예산이 지켜지지 않은 것이다.
const BUDGET_MS = Number(__ENV.BUDGET_MS || 6000);

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    // 전원 실패(502)가 아닌 한 응답은 나가야 한다. 부분 실패도 200 이다.
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${BUDGET_MS}`],
  },
};

export default function () {
  const url =
    `${APP_BASE_URL}${SEARCH_PATH}` +
    `?checkIn=${CHECK_IN}&checkOut=${CHECK_OUT}&adults=${ADULTS}&children=${CHILDREN}`;
  const response = http.get(url);

  check(response, {
    'status is 200': (r) => r.status === 200,
    'budget 안에 응답': (r) => r.timings.duration < BUDGET_MS,
    // 날짜가 로케일 표기로 나갔다면 모의 서버가 400 을 주고 공급사 결과가 비어 실패로 잡힌다.
    'results 가 비어 있지 않다': (r) => {
      if (r.status !== 200) return false;
      const data = r.json('data');
      return data !== null && Array.isArray(data.results) && data.results.length > 0;
    },
    'suppliers 블록이 있다': (r) => {
      if (r.status !== 200) return false;
      const suppliers = r.json('data.suppliers');
      return Array.isArray(suppliers) && suppliers.length > 0;
    },
    // 응답 계약: 필수 정보 7종이 첫 항목에 모두 있다 (01-design.md §3.5).
    'results 항목에 필수 필드가 있다': (r) => {
      if (r.status !== 200) return false;
      const first = r.json('data.results.0');
      if (!first) return false;
      return (
        first.propertyId != null &&
        first.propertyName != null &&
        first.roomId != null &&
        first.roomName != null &&
        first.maxOccupancy != null &&
        first.bookableRooms != null &&
        first.totalAmount != null &&
        first.currency != null &&
        first.supplier != null
      );
    },
  });
}
