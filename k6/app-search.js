// 자사 앱을 대상으로 하는 부하 스크립트. **F7~F9 이후에 실행한다.**
//
//   APP_BASE_URL=http://localhost:8080 APP_SEARCH_PATH='/api/...' k6 run k6/app-search.js
//
// 지금은 자리만 잡아 둔다. 경로와 응답 필드를 여기에 미리 적지 않는 이유는, 아직 없는 API의 모양을
// 넘겨짚는 것이 되기 때문이다. F7~F9에서 실제 계약이 정해지면 경로 기본값과 필드 단정을 채운다.
//
// 그때 볼 것: 공급사 하나가 느리거나 죽었을 때(k6/control.js의 setMode로 A에만 고장을 건다)
// 앱의 p95와 실패율이 어떻게 움직이는가. 모의 서버를 함께 띄운 상태에서 돌린다.

import http from 'k6/http';
import { check } from 'k6';

const APP_BASE_URL = __ENV.APP_BASE_URL;
const APP_SEARCH_PATH = __ENV.APP_SEARCH_PATH;

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  if (!APP_BASE_URL || !APP_SEARCH_PATH) {
    throw new Error('APP_BASE_URL과 APP_SEARCH_PATH를 지정해야 한다 (F7~F9에서 정해진다)');
  }
  const response = http.get(`${APP_BASE_URL}${APP_SEARCH_PATH}`);
  check(response, { 'status is 200': (r) => r.status === 200 });
}
