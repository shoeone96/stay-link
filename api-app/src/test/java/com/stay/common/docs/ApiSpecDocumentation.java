package com.stay.common.docs;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import java.net.URI;
import java.util.Collection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.operation.OperationRequest;
import org.springframework.restdocs.operation.OperationRequestPart;
import org.springframework.restdocs.operation.RequestCookie;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;

/**
 * 컨트롤러 테스트가 OpenAPI 조각을 남기는 통로. 테스트가 통과할 때만 문서가 만들어지고, 문서에 적은
 * 필드가 응답에 없으면 테스트가 깨진다 (D-F7-10).
 *
 * <p>전처리기를 끼우는 이유는 {@link MapAccessibleHttpHeaders} 에 적었다. 스펙 생성기가 요청 헤더를
 * {@code Map} 으로 캐스팅하는데 Spring 7 의 {@code HttpHeaders} 는 더 이상 그것이 아니라,
 * 문서를 만드는 모든 호출이 이 자리를 지나야 한다. 테스트가 직접
 * {@code MockMvcRestDocumentationWrapper.document} 를 부르면 다시 깨지므로 통로를 하나로 둔다.
 */
public final class ApiSpecDocumentation {

    private static final OperationRequestPreprocessor MAP_ACCESSIBLE_HEADERS = MapAccessibleRequest::new;

    private ApiSpecDocumentation() {
    }

    public static RestDocumentationResultHandler document(String identifier, ResourceSnippetParameters parameters) {
        return MockMvcRestDocumentationWrapper.document(identifier, MAP_ACCESSIBLE_HEADERS, resource(parameters));
    }

    /** 헤더 하나만 바꿔 끼우는 위임체. 나머지는 원본 그대로여야 문서에 실린 예시가 실제 요청과 같다. */
    private record MapAccessibleRequest(OperationRequest delegate) implements OperationRequest {

        @Override
        public byte[] getContent() {
            return delegate.getContent();
        }

        @Override
        public String getContentAsString() {
            return delegate.getContentAsString();
        }

        @Override
        public HttpHeaders getHeaders() {
            return new MapAccessibleHttpHeaders(delegate.getHeaders());
        }

        @Override
        public HttpMethod getMethod() {
            return delegate.getMethod();
        }

        @Override
        public Collection<OperationRequestPart> getParts() {
            return delegate.getParts();
        }

        @Override
        public URI getUri() {
            return delegate.getUri();
        }

        @Override
        public Collection<RequestCookie> getCookies() {
            return delegate.getCookies();
        }
    }
}
