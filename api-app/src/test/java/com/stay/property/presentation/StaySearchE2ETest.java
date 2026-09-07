package com.stay.property.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.stay.common.docs.ApiSpecDocumentation.document;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder;
import com.epages.restdocs.apispec.SimpleType;
import com.stay.common.error.CommonErrorCode;
import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.application.FailedChunk;
import com.stay.property.application.Money;
import com.stay.property.application.SearchCacheUnavailableException;
import com.stay.property.application.SearchResultStore;
import com.stay.property.application.StayErrorCode;
import com.stay.property.application.StaySearchCommand;
import com.stay.property.application.SupplierAvailabilityPort;
import com.stay.property.application.SupplierAvailabilityResult;
import com.stay.property.application.SupplierErrorCode;
import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 검색 API 의 응답 계약을 고정하고, 통과할 때 OpenAPI 조각을 남긴다 (D-F7-10).
 *
 * <p>{@code @Transactional} 은 테스트 격리용이다. 매핑 테이블이 비어 있는 경우(T-19)를 확인하려면
 * 앞 테스트의 저장이 남아 있으면 안 되는데, 이 앱에는 매핑을 지우는 포트가 없다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@Transactional
@DisplayName("숙박 상품 통합 검색 E2E")
class StaySearchE2ETest {

    private static final String SEARCH_PATH = "/api/v1/stays/search";
    private static final Currency KRW = Currency.getInstance("KRW");

    /**
     * 날짜를 실행일 기준으로 잡는다. {@code @FutureOrPresent} 때문에 고정 날짜를 쓰면 그날이 지나는
     * 순간 테스트가 썩고, 아주 먼 미래로 도망가면 그 값이 그대로 API 문서의 예시가 된다.
     */
    private static final String CHECK_IN = LocalDate.now().plusDays(3).toString();
    private static final String CHECK_OUT = LocalDate.now().plusDays(6).toString();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockitoBean
    private SupplierAvailabilityPort supplierAvailabilityPort;

    /**
     * 외부 저장소 경계의 포트라 mock 이 허용된다 (TST-3). 스텁하지 않은 테스트에서는 {@code find} 가 빈
     * {@code Optional} 을 돌려주므로 F7 의 다섯 갈래는 캐시 없이 돌던 때와 같은 경로를 지난다.
     */
    @MockitoBean
    private SearchResultStore searchResultStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("두 공급사가 모두 응답하면 200 과 함께 합쳐진 결과가 정해진 순서로 나온다")
    void search_bothSuppliersRespond_returnsMergedResultsInFixedOrder() throws Exception {
        // given
        Long supplierAPropertyId = givenMapping(Supplier.A, "A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double");
        Long supplierBPropertyId = givenMapping(Supplier.B, "P-88410", "Haeundae Blue Hotel", "R-201", "Family Suite");
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", 435_600L, 1)),
                                succeeded(
                                        Supplier.B,
                                        offer("P-88410", "Haeundae Blue Hotel", "R-201", "Family Suite", 756_000L, 2))));

        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", CHECK_IN)
                        .param("checkOut", CHECK_OUT)
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(2))
                .andExpect(jsonPath("$.data.results[0].propertyId").value(supplierBPropertyId))
                .andExpect(jsonPath("$.data.results[0].roomName").value("Family Suite"))
                .andExpect(jsonPath("$.data.results[0].maxOccupancy").value(2))
                .andExpect(jsonPath("$.data.results[0].bookableRooms").value(2))
                .andExpect(jsonPath("$.data.results[0].soldOut").value(false))
                .andExpect(jsonPath("$.data.results[0].totalAmount").value(756_000L))
                .andExpect(jsonPath("$.data.results[0].currency").value("KRW"))
                .andExpect(jsonPath("$.data.results[0].supplier").value("B"))
                .andExpect(jsonPath("$.data.results[1].propertyId").value(supplierAPropertyId))
                .andExpect(jsonPath("$.data.results[1].roomName").value("Ocean Double"))
                .andExpect(jsonPath("$.data.suppliers[0].supplier").value("A"))
                .andExpect(jsonPath("$.data.suppliers[0].status").value("OK"))
                .andExpect(jsonPath("$.data.suppliers[1].supplier").value("B"))
                .andExpect(jsonPath("$.data.suppliers[1].status").value("OK"))
                .andDo(document("stays-search", searchDocumentation()));
    }

    @Test
    @DisplayName("한 공급사만 실패하면 200 과 살아남은 결과가 나가고 실패 사실이 suppliers 에 남는다")
    void search_oneSupplierFailed_returnsSurvivingResultsWithFailedStatus() throws Exception {
        // given
        givenMapping(Supplier.A, "A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double");
        givenMapping(Supplier.B, "P-88410", "Haeundae Blue Hotel", "R-201", "Family Suite");
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(
                        List.of(
                                succeeded(
                                        Supplier.A,
                                        offer("A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double", 435_600L, 1)),
                                failed(Supplier.B)));

        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", CHECK_IN)
                        .param("checkOut", CHECK_OUT)
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].supplier").value("A"))
                .andExpect(jsonPath("$.data.suppliers[0].status").value("OK"))
                .andExpect(jsonPath("$.data.suppliers[1].supplier").value("B"))
                .andExpect(jsonPath("$.data.suppliers[1].status").value("FAILED"))
                .andDo(document("stays-search-partial-failure", partialFailureDocumentation()));
    }

    @Test
    @DisplayName("모든 공급사가 실패하면 502 와 전 공급사 실패 코드로 응답하고 본문에 결과를 싣지 않는다")
    void search_allSuppliersFailed_returnsBadGatewayWithoutData() throws Exception {
        // given
        givenMapping(Supplier.A, "A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double");
        givenMapping(Supplier.B, "P-88410", "Haeundae Blue Hotel", "R-201", "Family Suite");
        given(supplierAvailabilityPort.searchAll(any(AvailabilityQuery.class)))
                .willReturn(List.of(failed(Supplier.A), failed(Supplier.B)));

        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", CHECK_IN)
                        .param("checkOut", CHECK_OUT)
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(StayErrorCode.ALL_SUPPLIERS_FAILED.code()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("stays-search-all-suppliers-failed", allSuppliersFailedDocumentation()));
    }

    @Test
    @DisplayName("매핑 테이블이 비어 있으면 200 과 빈 결과·빈 공급사 목록으로 응답한다")
    void search_withoutAnyMapping_returnsEmptyResultsAndSuppliers() throws Exception {
        // given
        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", CHECK_IN)
                        .param("checkOut", CHECK_OUT)
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty())
                .andExpect(jsonPath("$.data.suppliers").isEmpty())
                .andDo(document("stays-search-without-mapping", withoutMappingDocumentation()));
    }

    @Test
    @DisplayName("검색 결과 저장소에 닿지 못하면 503 과 검색 불가 코드로 응답하고 공급사를 부르지 않는다")
    void search_searchResultStoreUnreachable_returnsServiceUnavailableWithoutData() throws Exception {
        // given
        givenMapping(Supplier.A, "A-3201", "Haeundae Blue Hotel", "OCN-DBL", "Ocean Double");
        given(searchResultStore.find(any(StaySearchCommand.class)))
                .willThrow(new SearchCacheUnavailableException(
                        "find", "stay-search:v1:" + CHECK_IN + ":" + CHECK_OUT + ":2:0", new IllegalStateException()));

        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", CHECK_IN)
                        .param("checkOut", CHECK_OUT)
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(StayErrorCode.SEARCH_UNAVAILABLE.code()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("stays-search-unavailable", searchUnavailableDocumentation()));
        then(supplierAvailabilityPort).shouldHaveNoInteractions();
    }

    private static ResourceSnippetParameters searchUnavailableDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색 — 검색 불가")
                .description("검색 결과 저장소에 닿지 못하면 공급사를 부르지 않고 503 으로 응답한다. 저장소는 공급사 "
                        + "호출 한도 안에 머무르기 위한 장치라, 그것 없이 호출을 흘리지 않는다. Retry-After 는 싣지 않는다.")
                .responseFields(errorFields())
                .build();
    }

    private static ResourceSnippetParameters partialFailureDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색 — 일부 공급사 실패")
                .description("일부 공급사가 실패해도 나머지 결과로 200 이 나가고, 실패한 공급사는 suppliers 에 "
                        + "FAILED 로 남는다. 실패 사유는 응답에 싣지 않는다.")
                .responseFields(resultFields())
                .build();
    }

    private static ResourceSnippetParameters allSuppliersFailedDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색 — 전 공급사 실패")
                .description("질의한 공급사가 모두 실패하면 502 로 응답한다. 서로 다른 시스템이 동시에 죽는 것은 "
                        + "대개 공통 원인이고 그 후보가 우리 쪽이라, 결과 0건과 구분되어야 한다.")
                .responseFields(errorFields())
                .build();
    }

    private static ResourceSnippetParameters withoutMappingDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색 — 매핑 없음")
                .description("조회할 매핑이 하나도 없으면 공급사를 부르지 않고 빈 결과로 응답한다. "
                        + "아무도 부르지 않았으므로 suppliers 도 비어 있다.")
                .responseFields(emptyResultFields())
                .build();
    }

    /** 네 갈래가 같은 요청 계약을 쓰므로 파라미터 설명은 한 벌이다. */
    private static ResourceSnippetParametersBuilder searchOperation() {
        return ResourceSnippetParameters.builder()
                .tag("stays")
                .queryParameters(
                        parameterWithName("checkIn").type(SimpleType.STRING).description("체크인일 (YYYY-MM-DD). 오늘 이후여야 한다"),
                        parameterWithName("checkOut")
                                .type(SimpleType.STRING)
                                .description("체크아웃일 (YYYY-MM-DD). 체크인일보다 뒤여야 하며 숙박일에 포함되지 않는다"),
                        parameterWithName("adults").type(SimpleType.INTEGER).description("성인 인원. 1 이상"),
                        parameterWithName("children").type(SimpleType.INTEGER).description("아동 인원. 0 이상"));
    }

    private static List<FieldDescriptor> envelopeFields() {
        return List.of(
                fieldWithPath("code").type(JsonFieldType.STRING).description("성공은 SUCCESS, 실패는 오류 코드"),
                fieldWithPath("message")
                        .type(JsonFieldType.STRING)
                        .description("오류 코드에 대응하는 고정 문구. 예외 원문은 실리지 않는다"),
                fieldWithPath("time").type(JsonFieldType.STRING).description("응답을 만든 시각 (ISO-8601)"));
    }

    private static List<FieldDescriptor> errorFields() {
        List<FieldDescriptor> fields = new ArrayList<>(envelopeFields());
        fields.add(fieldWithPath("data").type(JsonFieldType.NULL).description("실패 응답에는 본문이 없다"));
        return fields;
    }

    private static List<FieldDescriptor> emptyResultFields() {
        List<FieldDescriptor> fields = new ArrayList<>(envelopeFields());
        fields.add(fieldWithPath("data.results").type(JsonFieldType.ARRAY).description("검색 결과"));
        fields.add(
                fieldWithPath("data.suppliers")
                        .type(JsonFieldType.ARRAY)
                        .description("이 검색에서 각 공급사가 어떻게 끝났는지"));
        return fields;
    }

    private static List<FieldDescriptor> resultFields() {
        List<FieldDescriptor> fields = new ArrayList<>(emptyResultFields());
        fields.addAll(
                List.of(
                        fieldWithPath("data.results[].propertyId").type(JsonFieldType.NUMBER).description("자사 숙소 식별자"),
                        fieldWithPath("data.results[].propertyName")
                                .type(JsonFieldType.STRING)
                                .description("공급사가 지금 팔고 있는 숙소명. 매핑 테이블의 저장값이 아니다"),
                        fieldWithPath("data.results[].roomId").type(JsonFieldType.NUMBER).description("자사 객실 타입 식별자"),
                        fieldWithPath("data.results[].roomName").type(JsonFieldType.STRING).description("공급사가 준 객실 타입명"),
                        fieldWithPath("data.results[].maxOccupancy").type(JsonFieldType.NUMBER).description("최대 수용 인원"),
                        fieldWithPath("data.results[].bookableRooms")
                                .type(JsonFieldType.NUMBER)
                                .description("요청 기간 전체를 연속으로 잡을 수 있는 객실 수. 날짜별 잔여의 최솟값이다"),
                        fieldWithPath("data.results[].soldOut")
                                .type(JsonFieldType.BOOLEAN)
                                .description("예약 가능 객실 수가 0인지. 품절이어도 항목은 빠지지 않는다"),
                        fieldWithPath("data.results[].totalAmount")
                                .type(JsonFieldType.NUMBER)
                                .description("숙박 기간 전체의 세금 포함 총액. 통화의 최소 단위 정수다"),
                        fieldWithPath("data.results[].currency").type(JsonFieldType.STRING).description("통화 (ISO 4217)"),
                        fieldWithPath("data.results[].breakfastIncluded")
                                .type(JsonFieldType.BOOLEAN)
                                .description("조식 포함 여부. 총액을 같은 축에서 비교할 수 있는지를 정한다"),
                        fieldWithPath("data.results[].supplier").type(JsonFieldType.STRING).description("이 결과를 준 공급사"),
                        fieldWithPath("data.suppliers[].supplier").type(JsonFieldType.STRING).description("공급사"),
                        fieldWithPath("data.suppliers[].status")
                                .type(JsonFieldType.STRING)
                                .description("OK · PARTIAL(일부 묶음 실패) · FAILED(그 공급사 몫이 통째로 빠짐)")));
        return fields;
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("체크인이 과거", LocalDate.now().minusDays(1).toString(), CHECK_OUT, "2", "checkIn"),
                Arguments.of("체크아웃이 체크인과 같은 날", CHECK_IN, CHECK_IN, "2", "checkOut"),
                Arguments.of("성인 인원이 0", CHECK_IN, CHECK_OUT, "0", "adults"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("요청 파라미터가 제약을 위반하면 400 과 위반 필드명을 담아 응답한다")
    void search_invalidRequestParameters_returnsBadRequestWithViolatedFieldName(
            String scenario, String checkIn, String checkOut, String adults, String violatedField) throws Exception {
        // given
        // when
        // then
        mockMvc.perform(get(SEARCH_PATH)
                        .param("checkIn", checkIn)
                        .param("checkOut", checkOut)
                        .param("adults", adults)
                        .param("children", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.message").value(containsString(violatedField)))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andDo(document("stays-search-invalid-request", invalidRequestDocumentation()));
    }

    private static ResourceSnippetParameters invalidRequestDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색 — 잘못된 요청")
                .description("요청 파라미터가 제약을 위반하면 위반 필드명을 message 에 담아 400 으로 거절한다.")
                .responseFields(errorFields())
                .build();
    }

    private static ResourceSnippetParameters searchDocumentation() {
        return searchOperation()
                .summary("숙박 상품 통합 검색")
                .description("여러 공급사의 재고·요금을 한 번에 조회해 자사 표준 모델로 합쳐 돌려준다. "
                        + "일부 공급사가 실패해도 나머지 결과로 200 이 나가고, 실패 사실은 suppliers 에 남는다.")
                .responseFields(resultFields())
                .build();
    }

    /** 숙소 1 · 객실 1 을 JPA 경유로 저장하고 발급된 숙소 식별자를 돌려준다 (TST-4). */
    private Long givenMapping(
            Supplier supplier, String propertyCode, String propertyName, String roomCode, String roomName) {
        Long propertyId = propertyRepository.save(Property.create(supplier, propertyCode, propertyName)).getId();
        roomRepository.save(Room.create(propertyId, roomCode, roomName));
        return propertyId;
    }

    private static SupplierAvailabilityResult succeeded(Supplier supplier, AvailabilityOffer... offers) {
        return new SupplierAvailabilityResult(supplier, List.of(offers), List.of());
    }

    private static SupplierAvailabilityResult failed(Supplier supplier) {
        return new SupplierAvailabilityResult(
                supplier, List.of(), List.of(new FailedChunk(List.of("CHUNK-CODE"), SupplierErrorCode.TIMEOUT)));
    }

    private static AvailabilityOffer offer(
            String propertyCode,
            String propertyName,
            String roomCode,
            String roomName,
            long totalAmount,
            int bookableRooms) {
        return new AvailabilityOffer(
                propertyCode,
                propertyName,
                roomCode,
                roomName,
                2,
                true,
                new Money(totalAmount, KRW),
                bookableRooms);
    }
}
