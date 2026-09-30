package com.linelock.linelock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.linelock.linelock.auth.dto.LoginRequest;
import com.linelock.linelock.auth.dto.LoginResponse;
import com.linelock.linelock.auth.dto.SignupRequest;
import com.linelock.linelock.global.security.JwtTokenProvider;
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;

@ExtendWith(MockitoExtension.class) // 이 테스트 클래스에서 Mockito(@Mock, @InjectMocks)를 쓸 수 있게 활성화
public class AuthServiceTest {

    @Mock // 진짜 DB 대신 가짜 UserRepository 생성 -> AuthService의 로직만 순수하게 검증하기 위함
    private UserRepository userRepository;

    @Mock // 진짜 BCrypt 암호화 대신 가짜 PasswordEncoder 생성
    private PasswordEncoder passwordEncoder;

    @Mock // 이번 테스트(signup)에서는 안 쓰이지만, AuthService 생성자가 요구하는 의존성이라 같이 가짜로 만들어줘야 함
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks // 위 3개의 가짜 객체들을 실제로 주입받는, 우리가 진짜로 테스트하려는 대상
    private AuthService authService;

    @Test
    void signup_성공() {
        // given: 회원가입 요청 데이터 준비 + 가짜 객체들의 동작을 미리 약속(stubbing)
        SignupRequest request = new SignupRequest("test", "1234", "테스트");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty()); // "같은 아이디 없음"을 가정
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword"); // 암호화 결과를 가정

        // when: 실제 테스트 대상 메서드 실행
        authService.signup(request);

        // then: userRepository.save()가 User 타입 객체와 함께 실제로 호출됐는지 검증
        verify(userRepository).save(any(User.class));
    }

    @Test
    void signup_중복아이디_예외() {
        // given: 회원가입 요청 준비 + "이미 같은 아이디의 User가 존재한다"고 가정(stubbing)
        SignupRequest request = new SignupRequest("test", "1234", "테스트");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(new User()));

        // when & then: signup() 실행 시 중복 체크 로직이 IllegalArgumentException을 던지는지 한 번에 검증
        assertThrows(IllegalArgumentException.class, () -> authService.signup(request));
    }

    @Test
    void login_성공() {
        // given: 로그인 시도 대상 User 준비 (loginId/password를 실제 값으로 채워야 함 -
        // 비워두면 login() 내부에서 이 값들을 anyString() stubbing에 넘길 때 null이 되어 매칭 실패함)
        User user = new User();
        user.setLoginId("test");
        user.setPassword("encodedPassword");

        LoginRequest request = new LoginRequest("test", "1234");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(user)); // "해당 아이디의 유저가 존재함"을 가정
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true); // "비밀번호 일치"를 가정
        when(jwtTokenProvider.generateToken(anyString())).thenReturn("token123"); // 발급될 토큰 값을 가정

        // when: 실제 테스트 대상 메서드 실행, 결과를 변수에 받음
        LoginResponse response = authService.login(request);

        // then: 응답에 담긴 토큰이 우리가 가정한 값과 일치하는지 검증
        assertThat(response.getToken()).isEqualTo("token123");
    }

    @Test
    void login_비밀번호불일치_예외() {
        // given: User는 존재하지만, 비밀번호는 일치하지 않는다고 가정(stubbing)
        User user = new User();
        user.setLoginId("test");
        user.setPassword("encodedPassword");

        LoginRequest reuqest = new LoginRequest("test", "1234");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false); // "비밀번호 불일치"를 가정

        // when & then: login() 실행 시 비밀번호 검증 로직이 IllegalArgumentException을 던지는지 한 번에 검증
        assertThrows(IllegalArgumentException.class, () -> authService.login(reuqest));
    }
}
