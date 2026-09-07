package com.stay.property.presentation;

import com.stay.common.web.ApiResponse;
import com.stay.property.application.SearchStaysUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 요청의 입구. 검증·DTO 변환만 하고 판단은 두지 않는다 (LAY-4) — 전 공급사 실패도 여기서
 * 분기하지 않고 유스케이스가 던진 예외를 advice 가 상태로 옮긴다 (D-F7-3).
 */
@RestController
@RequestMapping("/api/v1/stays")
public class StaySearchController {

    private final SearchStaysUseCase searchStaysUseCase;

    public StaySearchController(SearchStaysUseCase searchStaysUseCase) {
        this.searchStaysUseCase = searchStaysUseCase;
    }

    @GetMapping("/search")
    public ApiResponse<StaySearchResponse> search(@Valid StaySearchRequest request) {
        return ApiResponse.ok(StaySearchResponse.from(searchStaysUseCase.search(request.toCommand())));
    }
}
