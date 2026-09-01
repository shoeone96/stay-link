# JOURNAL

프로젝트 진행 과정 기록입니다. 완성된 결과가 아니라 의사결정·시행착오의 여정을 남깁니다.

- 각 단계 공통 소제목: **수행 내용 / 의사결정 / 막힌 지점·해결 / 참고**
- 모든 기록에 Day N + 날짜(KST) 병기
- AI 활용 상세(질문·답변·수용/수정/거부)는 [docs/ai-history.md](docs/ai-history.md)가 원본

## 목차

- [0. 사전 세팅](#0-사전-세팅)
- [1. 도메인 분석](#1-도메인-분석)
- [2. 설계](#2-설계)
- [3. 개발](#3-개발)
- [4. 테스트](#4-테스트)
  - 단위/통합 테스트
  - 장애 시나리오 테스트 (정상·공급사 장애·무응답, 타임아웃·부분 실패)
  - 엣지 케이스 테스트
  - 부하/동시성 테스트
- [5. 회고](#5-회고)

---

## 0. 사전 세팅

### Day 0 (2026-09-01, 화)

#### 수행 내용
- 요구사항 문서 분석: 서로 다른 API를 가진 숙박 상품 공급사 A·B를 자사 표준 모델로 통합하는 연동 백엔드. 핵심 흐름(매핑 저장 → 검색 → 병렬 조회 → 정규화 → 병합 → 응답)이 끊김 없이 동작하는 것이 최우선.
- 프로젝트명 확정: **stay-link**
- 기술 스택 확정: **Java 25 (LTS) + Spring Boot 3.5.x + Gradle (Kotlin DSL)**
- 동시성 모델 확정: **Spring MVC + Virtual Thread(요청 서빙) + WebClient/Reactor(공급사 병렬 fan-out)**
- AI 협업 체계 구축: 숙박 업계 도메인 전문가 에이전트(`hospitality-domain-expert`) + 도메인 분석 스킬(`domain-analysis`) + 대화 자동 기록(`docs/ai-history.md`)

#### 의사결정
- **프로젝트명 stay-link**: stay-hub·hotel-link·lodge-bridge 등과 비교. "stay"는 숙박 업계 표준 용어라 API 리소스(`/stays/search`)·내부 모델(Stay/RoomType/Supplier)과 용어가 한 벌로 정리되고, "link"가 연동이라는 시스템 정체성을 드러냄. 직관성만 보면 hotel-link가 우위였으나 비호텔 숙소까지 포괄하는 도메인 정확성에서 stay 선택.
- **Java 25 (21 아님)**: Virtual Thread를 실제 서빙 모델로 쓸 계획이라 21 대비 실질 개선이 있는 25 선택 — JDK 24 JEP 491(synchronized pinning 해소), JDK 25 JEP 506(Scoped Values 정식화). Spring Boot 3.5.x의 Java 25 지원을 공식 문서로 확인. Structured Concurrency는 25에서도 preview(JEP 505)라 정식 기능으로는 쓰지 않기로.
- **Kotlin 대신 Java**: 최근 실무 비중이 높아 가장 확신 있게 작성·설명할 수 있는 언어. 7일이라는 기간 제약에서 이 프로젝트의 본질은 언어가 아니라 설계 판단이므로, 언어 전환 마찰 대신 WebClient/Reactor 제어에 집중. 빌드 스크립트만 Kotlin DSL.
- **WebFlux 전면 도입 안 함**: 논블로킹이 필수인 구간은 공급사 호출뿐. 리액티브는 Supplier fan-out 경계 안에만 가두고(timeout·onErrorResume·zip 연산자로 병렬·타임아웃·부분 실패 제어), 요청 서빙은 MVC + Virtual Thread로 단순하게 유지 — 디버깅 용이성과 숙련도 기반 리스크 관리.
- **JOURNAL 구조**: 단계별 구성 + 공통 소제목 4종. 포기·미구현 항목 정리는 실제 포기 결정이 나오는 시점에 섹션 추가 예정. 부하/동시성 테스트는 vthread 서빙 모델 선택의 근거 실험을 겸해 실측하기로.

#### 막힌 지점·해결
- 없음 (세팅 단계).

#### 참고
- Spring Boot System Requirements — https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot Java 25 지원 논의 — https://github.com/spring-projects/spring-boot/issues/47245

---

## 1. 도메인 분석

(진행 예정)

## 2. 설계

(진행 예정)

## 3. 개발

(진행 예정)

## 4. 테스트

### 단위/통합 테스트
(진행 예정)

### 장애 시나리오 테스트
정상 응답 · 공급사 장애 · 무응답 3가지 상황 재현, 타임아웃·부분 실패 검증. (진행 예정)

### 엣지 케이스 테스트
(진행 예정)

### 부하/동시성 테스트
Virtual Thread 서빙 모델의 동시 요청 거동 실측 포함. (진행 예정)

## 5. 회고

(마무리 시 작성)
