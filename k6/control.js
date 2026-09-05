// 모의 공급사 서버 두 대의 주소와 고장 제어를 한곳에 모은다.
// A(9091)와 B(9092)는 별도 프로세스라 모드도 각 서버에 따로 건다.

import http from 'k6/http';

export const SUPPLIERS = {
  a: {
    name: 'mock-supplier-a',
    baseUrl: __ENV.SUPPLIER_A_URL || 'http://localhost:9091',
    listPath: '/a/v1/hotels',
    availabilityPath:
      '/a/v1/availability?hotelCodes=A-3201,A-3305&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0',
  },
  b: {
    name: 'mock-supplier-b',
    baseUrl: __ENV.SUPPLIER_B_URL || 'http://localhost:9092',
    listPath: '/b/api/properties',
    availabilityPath:
      '/b/api/search?propertyIds=P-88410&checkIn=2026-09-10&checkOut=2026-09-13&adults=2&children=0',
  },
};

export const API_KEY = __ENV.MOCK_API_KEY || 'test-key';

export function queryParams() {
  return { headers: { 'X-Api-Key': API_KEY } };
}

// params는 3.5.8의 네 축을 그대로 받는다: value·rate·errorCode·delayMillis·durationSeconds·endpoint
export function setMode(supplier, params) {
  const query = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&');
  return http.post(`${supplier.baseUrl}/control/mode?${query}`);
}

export function resetMode(supplier) {
  return setMode(supplier, { value: 'normal' });
}

export function readState(supplier) {
  return http.get(`${supplier.baseUrl}/control/state`);
}
