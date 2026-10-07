package com.linelock.linelock.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linelock.linelock.auth.dto.LoginRequest;
import com.linelock.linelock.auth.dto.LoginResponse;
import com.linelock.linelock.auth.dto.SignupRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequiredArgsConstructor 
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;

    // 가입 성공 후 클라이언트가 추가로 필요한 데이터가 없어서 void
    // @Valid: SignupRequest의 규칙(필수 입력, 비밀번호 8자 이상)을 컨트롤러 진입 전에 검사. 위반하면 400 + 필드별 사유로 응답됨
    @PostMapping("/signup")
    public void signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
    }

    // 토큰을 반환하지 않으면 클라이언트가 이후 요청에 실을 인증 수단이 없어지므로 반드시 반환
    // @Valid: LoginRequest에 선언한 규칙(@NotBlank 등)을 컨트롤러 진입 전에 실제로 검사하게 함. 위반하면
    // MethodArgumentNotValidException이 터지고, GlobalExceptionHandler가 400 + 필드별 사유로 응답함
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
