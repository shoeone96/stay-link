# k6 스크립트

모의 공급사 서버(A 9091 · B 9092)에 부하를 걸고 고장 상황에서의 응답 시간을 관찰한다.

## 준비

두 서버를 각각 다른 터미널에서 띄운다.

```bash
./gradlew :mock-supplier-a:bootRun
./gradlew :mock-supplier-b:bootRun
```

## 스크립트

| 파일 | 하는 일 |
|---|---|
| `control.js` | 두 서버의 주소와 고장 제어 호출을 모아 둔 공용 모듈. 단독 실행하지 않는다 |
| `load.js` | 정상 모드에서 두 서버를 동시에 때려 기준선(p50·p95·실패율)을 잡는다 |
| `tail-latency.js` | A에만 꼬리 지연(열 번에 한 번 5초)을 걸고 B는 정상으로 둔다. 평균은 멀쩡한데 p95만 튀는 것을 본다 |
| `app-search.js` | 자사 앱 대상. **F7~F9 이후에** 경로와 필드를 채워 실행한다 |

```bash
k6 run k6/load.js
k6 run k6/tail-latency.js
```

주소나 키가 다르면 환경 변수로 덮는다 — `SUPPLIER_A_URL` · `SUPPLIER_B_URL` · `MOCK_API_KEY`.

## 임계값을 두지 않은 이유

여기서 나오는 숫자는 모의 서버의 성능이지 자사 앱의 성능이 아니다. 지금 통과·실패 선을 그으면
아직 없는 코드의 성능을 넘겨짚는 것이 된다. 임계값은 F7~F9에서 앱을 대상으로 잡는다.

## 보는 지표

- `supplier_duration` — 서버별 태그(`supplier`)가 붙어 있어 A와 B를 나란히 볼 수 있다.
- `http_req_duration` p50·p95, `http_req_failed`.

`tail-latency.js`는 끝날 때 두 서버의 모드를 정상으로 되돌린다. 중간에 끊었다면 직접 되돌린다.

```bash
curl -X POST 'http://localhost:9091/control/mode?value=normal'
curl -X POST 'http://localhost:9092/control/mode?value=normal'
```
