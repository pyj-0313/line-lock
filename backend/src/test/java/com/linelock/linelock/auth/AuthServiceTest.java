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
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.global.security.JwtTokenProvider;
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.user.UserRole;

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
        // 비밀번호를 8자 이상으로 둔 이유: 서비스 단위 테스트는 @Valid가 동작하지 않아 실제로는 상관없지만,
        // 컨트롤러 테스트와 같은 "규칙을 만족하는 정상 요청"이라는 의미를 맞추려는 것
        SignupRequest request = new SignupRequest("test", "12345678", "테스트");
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
        SignupRequest request = new SignupRequest("test", "12345678", "테스트");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(new User()));

        // when & then: signup() 실행 시 CustomException이 터지고, 그 안의 ErrorCode가 중복 아이디인지 검증
        // (예외 타입만 보면 "다른 이유로 터진 예외"도 통과하므로, ErrorCode까지 확인해야 올바른 이유로 실패했다고 말할 수 있음)
        CustomException e = assertThrows(CustomException.class, () -> authService.signup(request));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    void login_성공() {
        // given: 로그인 시도 대상 User 준비 (loginId/password를 실제 값으로 채워야 함 -
        // 비워두면 login() 내부에서 이 값들을 anyString() stubbing에 넘길 때 null이 되어 매칭 실패함)
        // role도 채워야 함: 토큰 발급이 (loginId, role) 두 인자를 받아서, role이 null이면 any(UserRole.class) 매처에 안 맞아 약속이 안 먹음
        User user = new User();
        user.setLoginId("test");
        user.setPassword("encodedPassword");
        user.setRole(UserRole.USER);

        LoginRequest request = new LoginRequest("test", "1234");
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(user)); // "해당 아이디의 유저가 존재함"을 가정
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true); // "비밀번호 일치"를 가정
        when(jwtTokenProvider.generateToken(anyString(), any(UserRole.class))).thenReturn("token123"); // 발급될 토큰 값을 가정

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

        // when & then: login() 실행 시 CustomException이 터지고, 그 안의 ErrorCode가 로그인 실패인지 검증
        // (없는 아이디일 때와 같은 LOGIN_FAILED를 쓰므로, 응답만 봐서는 어느 쪽이 틀렸는지 구분되지 않음)
        CustomException e = assertThrows(CustomException.class, () -> authService.login(reuqest));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
    }
}
