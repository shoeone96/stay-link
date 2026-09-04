# 공급사 API 계약

> 연동 대상인 공급사 A·B의 API 규약을 정리한 문서다. 모의 공급사 서버(F2)가 구현할 대상이자,
> 클라이언트(F3)·어댑터(F4·F5)가 읽어야 할 계약의 단일 기준이다.
> 응답 구조 비교와 모델링 시사점은 `supplier-response-comparison.html`에 있다.

## 1. 두 공급사가 똑같이 지키는 규약

| 항목 | 규약 |
|---|---|
| 인증 | 요청 헤더 `X-Api-Key: <발급키>` |
| 날짜 형식 | `YYYY-MM-DD` |
| 날짜 경계 | **체크아웃일은 숙박일에 포함되지 않는다.** 09-10 체크인 / 09-13 체크아웃 = 3박 = 09-10·11·12 |
| 인원 | 요청은 `adults`·`children` 분리. `maxOccupancy`는 성인+아동 **합산** 기준 |
| 재고 | 날짜별 잔여 객실 수 `remainingRooms` (정수) |
| 통화 | ISO 4217 코드 (`KRW`, `USD` 등) |
| 금액 | 통화의 **최소 단위 정수** (KRW는 원 단위, 소수점 없음) |
| 조식 | `breakfastIncluded` (boolean). 같은 객실이라도 공급사마다 다를 수 있다 |

## 2. 조회는 두 단계다

공급사는 **지역으로 재고·요금을 검색해 주지 않는다.** 성격이 다른 두 API로 나뉜다.

| 단계 | API | 성격 |
|---|---|---|
| ① 숙소 목록 | `hotels` / `properties` | 공급사가 취급하는 숙소·객실 타입 전체 목록. 조건 파라미터 없음. 요금·재고 없음. 자주 바뀌지 않는 정적 콘텐츠 |
| ② 재고·요금 | `availability` / `search` | 숙소 코드 목록을 받아 한 번에(bulk) 조회. 호출할 때마다 값이 달라지는 동적 데이터 |

즉 **어떤 숙소를 조회할지는 우리가 정해서 알려줘야 한다.** ①로 숙소 목록을 미리 확보해 매핑으로 저장해 두고,
고객 검색이 들어오면 그 목록에서 대상 숙소를 골라 ②를 호출하는 구조다.

- ②는 **한 번에 최대 50개** 숙소 코드를 받는다. 초과하면 오류다.
- ①을 언제 호출할지(기동 시 한 번 / 주기적 / 검색할 때마다)는 우리 판단이다.

## 3. 상품 구조

두 공급사 모두 **숙소 > 객실 타입** 2단계로 상품을 표현한다.

- 개별 물리 객실(101호·102호)은 노출하지 않는다.
- **요금과 재고는 모두 객실 타입 단위다.** `remainingRooms: 3`은 "그 타입의 객실이 그날 3개 남았다"는 뜻이다.
- 공급사는 **요청 인원을 수용할 수 있는 객실 타입만 반환한다** (`adults + children <= maxOccupancy`).
  인원이 넘쳐 여러 객실을 묶어 파는 경우는 다루지 않는다.

## 4. 식별자의 유일성 범위

| 식별자 | 유일성 범위 |
|---|---|
| 숙소 (`hotelCode` / `propertyId`) | 공급사 안에서 유일 |
| 객실 타입 (`roomTypeCode` / `roomId`) | **해당 숙소 안에서만** 유일 — 다른 숙소에 같은 코드가 존재할 수 있다 |

- 따라서 객실 타입 하나를 유일하게 가리키려면 **(공급사, 숙소 코드, 객실 타입 코드) 세 값**이 필요하다.
- 값은 안정적이다 — 같은 상품은 언제 조회해도 같은 코드로 돌아온다.
- 두 공급사가 같은 숙소를 취급해도 **그 사실을 알려주는 공통 키는 없다.** 동일 숙소 여부는 숙소명·객실 구성으로 추정할 수밖에 없다.

---

## 5. Supplier A

특징: **날짜별 1박 단가 · 세금 별도(net) · HTTP 상태 코드로 실패 표현**

### ① 숙소 목록

```
GET /a/v1/hotels
X-Api-Key: {key}
```

조건 파라미터 없음. 전체 목록을 반환한다. 요금·재고·조식 정보는 없다.

```json
{
  "items": [
    {
      "hotelCode": "A-3201",
      "hotelName": "Haeundae Blue Hotel",
      "roomTypes": [
        { "roomTypeCode": "OCN-DBL", "roomTypeName": "Ocean Double", "maxOccupancy": 2 }
      ]
    }
  ]
}
```

### ② 재고·요금

```
GET /a/v1/availability?hotelCodes=A-3201,A-3305&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0
X-Api-Key: {key}
```

`hotelCodes`는 쉼표로 구분한 숙소 코드 목록(최대 50개). 응답은 **(숙소, 객실 타입) 조합당 항목 1개**인 평평한 배열이다.

```json
{
  "items": [
    {
      "hotelCode": "A-3201",
      "hotelName": "Haeundae Blue Hotel",
      "roomTypeCode": "OCN-DBL",
      "roomTypeName": "Ocean Double",
      "maxOccupancy": 2,
      "breakfastIncluded": false,
      "currency": "KRW",
      "dailyRates": [
        { "date": "2026-09-10", "remainingRooms": 3, "nightlyRate": 110000, "taxAmount": 11000 },
        { "date": "2026-09-11", "remainingRooms": 1, "nightlyRate": 143000, "taxAmount": 14300 },
        { "date": "2026-09-12", "remainingRooms": 1, "nightlyRate": 143000, "taxAmount": 14300 }
      ]
    }
  ]
}
```

**요금 규약**

- `nightlyRate`는 **세금 별도(net)** 금액이다.
- 해당 날짜의 고객 결제 금액 = `nightlyRate + taxAmount`.
- 숙박 전체 금액 = 각 날짜의 `(nightlyRate + taxAmount)` 합산.

**필드 사전**

| 필드 | 타입 | 의미 |
|---|---|---|
| `hotelCode` | string | 숙소 식별자. A 안에서만 유일 |
| `hotelName` | string | 숙소명 |
| `roomTypes[]` | array | 숙소가 가진 객실 타입 목록 (①에만 있음) |
| `roomTypeCode` | string | 객실 타입 식별자. 해당 숙소 안에서만 유일 |
| `roomTypeName` | string | 객실 타입명 |
| `maxOccupancy` | int | 객실 1실의 최대 수용 인원 (성인+아동 합산) |
| `breakfastIncluded` | boolean | 요금에 조식이 포함되는지 (②에만 있음) |
| `currency` | string | `nightlyRate`·`taxAmount`의 통화 |
| `dailyRates[].date` | date | 숙박일 (체크인일부터 체크아웃 전날까지) |
| `dailyRates[].remainingRooms` | int | 그날 예약 가능한 해당 타입 객실 수 |
| `dailyRates[].nightlyRate` | int | 그날 1박 요금 — 세금 별도(net) |
| `dailyRates[].taxAmount` | int | 그날 1박에 붙는 세금 |

### 실패 응답 — HTTP 상태 코드

```
HTTP/1.1 503 Service Unavailable

{ "error": "SERVICE_UNAVAILABLE", "message": "temporarily unavailable" }
```

| 상태 | error | 의미 |
|---|---|---|
| 400 | `INVALID_DATE_RANGE` / `INVALID_PARAMETER` | 잘못된 요청 |
| 400 | `TOO_MANY_HOTEL_CODES` | `hotelCodes`가 50개 초과 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 429 | `RATE_LIMIT_EXCEEDED` | 호출 한도 초과 |
| 500 | `INTERNAL_ERROR` | 공급사 내부 오류 |
| 503 | `SERVICE_UNAVAILABLE` | 일시적 장애 |

---

## 6. Supplier B

특징: **숙박 전체 총액 · 세금 포함(gross) · 항상 HTTP 200 + 본문 `resultCode`로 실패 표현**

### ① 숙소 목록

```
GET /b/api/properties
X-Api-Key: {key}
```

```json
{
  "resultCode": "0000",
  "resultMessage": "SUCCESS",
  "data": {
    "items": [
      {
        "propertyId": "P-88410",
        "propertyName": "Haeundae Blue Hotel",
        "rooms": [
          { "roomId": "R-201", "roomName": "Ocean Double Room", "maxOccupancy": 2 }
        ]
      }
    ]
  }
}
```

### ② 재고·요금

```
GET /b/api/search?propertyIds=P-88410&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0
X-Api-Key: {key}
```

`propertyIds`는 쉼표로 구분한 숙소 코드 목록(최대 50개).

```json
{
  "resultCode": "0000",
  "resultMessage": "SUCCESS",
  "data": {
    "items": [
      {
        "propertyId": "P-88410",
        "propertyName": "Haeundae Blue Hotel",
        "roomId": "R-201",
        "roomName": "Ocean Double Room",
        "maxOccupancy": 2,
        "breakfastIncluded": true,
        "currency": "KRW",
        "totalPrice": 453600,
        "taxIncluded": true,
        "inventory": [
          { "date": "2026-09-10", "remainingRooms": 2 },
          { "date": "2026-09-11", "remainingRooms": 1 },
          { "date": "2026-09-12", "remainingRooms": 1 }
        ]
      }
    ]
  }
}
```

**요금 규약**

- `totalPrice`는 **요청한 숙박 기간 전체의 총액**이며 **세금이 포함(gross)**되어 있다.
- 날짜별 요금은 제공하지 않는다.
- 세금 금액도 별도로 제공하지 않는다. `taxIncluded: true`만 알려준다.

**필드 사전**

| 필드 | 타입 | 의미 |
|---|---|---|
| `resultCode` | string | 처리 결과 코드. `0000`이 성공 |
| `resultMessage` | string | 결과 메시지 |
| `data` | object | 성공 시에만 값이 있고, **실패 시 `null`** |
| `propertyId` | string | 숙소 식별자. B 안에서만 유일 (A의 `hotelCode`에 대응) |
| `propertyName` | string | 숙소명 |
| `rooms[]` | array | 숙소가 가진 객실 타입 목록 (①에만 있음) |
| `roomId` | string | 객실 타입 식별자 (A의 `roomTypeCode`에 대응). **이름과 달리 개별 물리 객실이 아니다** |
| `roomName` | string | 객실 타입명 |
| `maxOccupancy` | int | 객실 1실의 최대 수용 인원 (성인+아동 합산) |
| `breakfastIncluded` | boolean | 요금에 조식이 포함되는지 (②에만 있음) |
| `currency` | string | `totalPrice`의 통화 |
| `totalPrice` | int | 요청 기간 전체의 총액 — 세금 포함(gross) |
| `taxIncluded` | boolean | 항상 `true` |
| `inventory[].date` | date | 숙박일 |
| `inventory[].remainingRooms` | int | 그날 예약 가능한 해당 타입 객실 수 |

### 실패 응답 — HTTP는 항상 200

```
HTTP/1.1 200 OK

{ "resultCode": "E503", "resultMessage": "TEMPORARILY_UNAVAILABLE", "data": null }
```

| `resultCode` | 의미 |
|---|---|
| `0000` | 성공 |
| `E400` | 잘못된 요청 (`propertyIds` 50개 초과 포함) |
| `E401` | 인증 실패 |
| `E429` | 호출 한도 초과 |
| `E500` | 공급사 내부 오류 |
| `E503` | 일시적 장애 |

> **B는 실패해도 HTTP 상태 코드가 200이다.** 응답 본문의 `resultCode`를 확인하지 않으면
> 장애를 정상 응답으로 처리하게 된다. 어댑터가 `resultCode != "0000"`을 A의 4xx/5xx와
> **같은 내부 실패 유형**으로 번역해야 한다.

---

## 7. 실패 코드 대응표

같은 의미의 실패가 두 공급사에서 전혀 다른 모양으로 나온다. 어댑터가 이 둘을 하나의 내부 실패 유형으로 번역한다.

| 의미 | A (HTTP + error) | B (200 + resultCode) |
|---|---|---|
| 잘못된 요청 | 400 `INVALID_DATE_RANGE` / `INVALID_PARAMETER` | `E400` |
| 코드 50개 초과 | 400 `TOO_MANY_HOTEL_CODES` | `E400` |
| 인증 실패 | 401 `UNAUTHORIZED` | `E401` |
| 호출 한도 초과 | 429 `RATE_LIMIT_EXCEEDED` | `E429` |
| 공급사 내부 오류 | 500 `INTERNAL_ERROR` | `E500` |
| 일시적 장애 | 503 `SERVICE_UNAVAILABLE` | `E503` |

---

## 8. 제한 사항과 주의점

| # | 제한 | 영향 |
|---|---|---|
| 1 | 재고·요금 조회는 **한 번에 최대 50개** 숙소 코드 | 보유 숙소가 50개를 넘으면 묶음으로 잘라 여러 번 호출해야 한다. 묶음 일부만 실패하는 경우가 생긴다 |
| 2 | 지역·키워드로 검색할 수 없다 | 조회 대상 숙소 코드를 우리가 먼저 알고 있어야 하고, 그 출처가 숙소 목록 API다 |
| 3 | 두 공급사를 잇는 공통 키가 없다 | 같은 숙소라도 각각 다른 내부 식별자를 갖는 것이 기본 동작이다 |
| 4 | B는 총액만 준다 | 변환은 한 방향만 가능하다 — A(날짜별 net) → 총액 gross는 계산되지만 그 역은 불가능하다 |
| 5 | 같은 객실이라도 조건이 다를 수 있다 | 조식 포함 여부가 공급사마다 달라 단순 가격 비교가 성립하지 않는다 |
| 6 | 실제 공급사 서버는 없다 | 모의 서버(F2)로 대체한다. 정상·장애·무응답을 재현할 수 있어야 견고성 구현을 보일 수 있다 |
| 7 | **모의 서버는 공급사마다 별도 프로세스·별도 포트에 둔다** | 자사 앱과 포트가 같으면 앱이 자기 자신을 호출해 스레드가 묶인다. 또 A·B가 한 프로세스면 서버를 내렸을 때 둘이 함께 죽어 "한쪽만 연결이 안 되는" 상황을 만들 수 없다 |

## 관련 문서

- `supplier-response-comparison.html` — A·B 응답 구조 비교, 필드 대응표, 모델링 시사점
- `list-api-integration-design.html` — 목록 연동 설계 (D1~D5)
- `availability-api-integration-design.html` — 재고·요금 연동 설계 (D6~D12)
- `features/mock-supplier-server/01-design.md` — 이 계약을 구현하는 모의 서버 설계
