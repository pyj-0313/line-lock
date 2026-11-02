package com.linelock.linelock.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.linelock.linelock.equipment.EquipmentController;
import com.linelock.linelock.equipment.EquipmentService;
import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtTokenProvider;

// CORS 설정(SecurityConfig의 corsConfigurationSource)이 허용 출처는 통과시키고 그 외는 거부하는지 지키는 테스트
// 허용 출처 값은 로컬/CI의 설정 파일과 무관하게 같은 결과가 나오도록 테스트에서 고정함
// 컨트롤러 로직이 아니라 보안 필터 단계에서 끝나는 요청이지만, 웹 계층을 띄우려면 컨트롤러와 그 의존성(가짜)이 필요해서 EquipmentController를 사용함
// 지켜주는 범위: 빈의 허용 출처/등록 설정 (빈을 지우거나 허용 출처를 지우면 실패함). http.cors(...) 줄 자체는 빈이 있으면 없어도 동작해서 이 테스트로는 보호되지 않음
@WebMvcTest(value = EquipmentController.class, properties = "cors.allowed-origins=http://localhost:5500")
@Import({ SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class })
public class CorsConfigTest {

    @Autowired // 실제 HTTP 요청을 흉내 내는 도구
    private MockMvc mockMvc;

    @MockitoBean // 컨트롤러 로딩에 필요한 가짜 의존성 (이 테스트에서는 호출되지 않음)
    private EquipmentService equipmentService;

    @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성
    private JwtTokenProvider jwtTokenProvider;

    // @WithMockUser를 일부러 안 붙임: 사전 요청(preflight)은 토큰 없이 오기 때문에, 로그인 없이도 통과해야 실제 브라우저에서 동작함
    // 통과하면 200 + 허용 출처가 Access-Control-Allow-Origin 헤더로 돌아옴
    @Test
    void 허용된_출처의_사전요청은_토큰없이_통과한다() throws Exception {
        mockMvc.perform(options("/api/equipments/1")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    // 허용 목록에 없는 출처는 403, 그리고 브라우저가 통과 신호로 보는 Access-Control-Allow-Origin 헤더가 아예 없어야 함
    @Test
    void 허용되지_않은_출처의_사전요청은_거부된다() throws Exception {
        mockMvc.perform(options("/api/equipments/1")
                .header("Origin", "http://evil.com")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    
}
