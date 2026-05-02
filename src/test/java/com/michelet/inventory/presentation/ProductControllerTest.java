package com.michelet.inventory.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michelet.common.auth.webmvc.config.AuthWebMvcAutoConfiguration;
import com.michelet.inventory.application.ProductCommandService;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.infrastructure.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProductController.class, excludeAutoConfiguration = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
@AutoConfigureRestDocs(uriScheme = "http", uriHost = "localhost", uriPort = 19900)
@ActiveProfiles("test")
@EnableAspectJAutoProxy // @RequireRole AOP가 동작하려면 필수!
@Import({RestDocsConfig.class, SecurityConfig.class, AuthWebMvcAutoConfiguration.class})
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @Test
    @DisplayName("상태 확인: 서비스가 정상 동작하면 200을 반환한다")
    void healthCheck() throws Exception {
        given(productCommandService.checkHealth()).willReturn("Inventory Command Service is Healthy");

        mockMvc.perform(get("/api/v1/admin/products/health")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(document("{class-name}/{method-name}"));
    }

    @Test
    @DisplayName("성공: 상품 등록 시 모든 필드가 정상이면 200을 반환한다 (OWNER 권한)")
    void createProduct() throws Exception {
        // Given
        UUID productId = UUID.randomUUID();
        given(productCommandService.createProduct(any())).willReturn(new ProductResult(productId));

        String requestJson = """
            {
                "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
                "name": "밀키트",
                "category": "MEALKIT",
                "basePrice": 45000,
                "attributes": {"servings": 2},
                "exhibition": {
                    "startAt": "2026-05-01T10:00:00",
                    "endAt": "2026-05-31T23:59:59"
                },
                "options": [
                    {"name": "기본", "addPrice": 0, "totalQuantity": 100, "dailyLimit": 20, "maxLimit": 2}
                ]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "OWNER")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andDo(document("{class-name}/{method-name}",
                // 요청 필드 문서화
                relaxedRequestFields(
                    fieldWithPath("restaurantId").type(JsonFieldType.STRING).description("식당 식별 ID"),
                    fieldWithPath("name").type(JsonFieldType.STRING).description("상품명"),
                    fieldWithPath("category").type(JsonFieldType.STRING).description("상품 카테고리"),
                    fieldWithPath("basePrice").type(JsonFieldType.NUMBER).description("기본 판매가"),
                    fieldWithPath("attributes").type(JsonFieldType.OBJECT).description("가변 속성 (JSON)"),
                    fieldWithPath("exhibition.startAt").type(JsonFieldType.STRING).description("전시 시작일시"),
                    fieldWithPath("exhibition.endAt").type(JsonFieldType.STRING).description("전시 종료일시"),
                    fieldWithPath("options[].name").type(JsonFieldType.STRING).description("옵션명"),
                    fieldWithPath("options[].addPrice").type(JsonFieldType.NUMBER).description("옵션 추가 금액"),
                    fieldWithPath("options[].totalQuantity").type(JsonFieldType.NUMBER).description("전체 가용 재고"),
                    fieldWithPath("options[].dailyLimit").type(JsonFieldType.NUMBER).description("일일 판매 한도"),
                    fieldWithPath("options[].maxLimit").type(JsonFieldType.NUMBER).description("1인당 최대 구매 수량")
                ),
                // 응답 필드 문서화 (common의 ApiResponse 구조 반영)
                relaxedResponseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                    fieldWithPath("data.productId").type(JsonFieldType.STRING).description("생성된 상품 ID"),
                    fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간"),
                    fieldWithPath("message").type(JsonFieldType.STRING).description("결과 메시지").optional(),
                    fieldWithPath("code").type(JsonFieldType.STRING).description("상태 코드").optional(),
                    fieldWithPath("traceId").type(JsonFieldType.STRING).description("분산 추적 TraceID").optional()
                )
            ));
    }

    @Test
    @DisplayName("실패: OWNER 권한이 아닌 사용자가 상품 등록을 시도하면 403 Forbidden 에러가 발생해야 한다")
    void createProduct_Unauthorized_returns403() throws Exception {
        String requestJson = """
            {
                "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
                "name": "권한 없는 밀키트",
                "category": "MEALKIT",
                "basePrice": 45000,
                "exhibition": {
                    "startAt": "2026-05-01T10:00:00"
                },
                "options": [
                    {"name": "기본", "addPrice": 0, "totalQuantity": 100, "dailyLimit": 20, "maxLimit": 2}
                ]
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "USER")
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andDo(document("{class-name}/{method-name}"));
    }

    // 익명(헤더 누락) 접근 시 401 테스트 추가
    @Test
    @DisplayName("실패: 인증 헤더(X-User-Id, X-User-Role) 없이 상품 등록을 시도하면 401 Unauthorized 에러가 발생해야 한다")
    void createProduct_MissingHeaders_returns401() throws Exception {
        String requestJson = """
            {
                "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
                "name": "익명 밀키트",
                "category": "MEALKIT",
                "basePrice": 45000,
                "exhibition": {
                    "startAt": "2026-05-01T10:00:00"
                },
                "options": [
                    {"name": "기본", "addPrice": 0, "totalQuantity": 100, "dailyLimit": 20, "maxLimit": 2}
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/products")
                // 인증 헤더를 아무것도 넣지 않음
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()) // 인터셉터에서 UserContext를 만들지 못해 401 던짐
            .andDo(document("{class-name}/{method-name}"));
    }

    @Test
    @DisplayName("실패: 필수 데이터(exhibition 등) 누락 시 400 에러를 반환해야 한다")
    void createProductFailInvalidInput() throws Exception {
        // Given: exhibition 필드가 없는 불완전한 JSON
        String invalidJson = """
            {
                "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
                "name": "잘못된 요청",
                "basePrice": 45000
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/products")
                // 인터셉터와 AOP를 모두 무사 통과하고, Validation 단계에서 걸리도록 설정
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "OWNER")
                .content(invalidJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest()) // DTO 검증(@Valid)에 의해 400 반환
            .andDo(document("{class-name}/{method-name}"));
    }
}
