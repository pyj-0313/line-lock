package com.linelock.linelock.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.linelock.linelock.auth.dto.LoginRequest;
import com.linelock.linelock.auth.dto.LoginResponse;
import com.linelock.linelock.auth.dto.SignupRequest;
import com.linelock.linelock.global.security.JwtTokenProvider;
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.user.UserRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public void signup(SignupRequest request) {
        // 같은 loginId로 중복 가입하는 것을 막기 위한 사전 체크
        if (userRepository.findByLoginId(request.getLoginId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }
        // 비밀번호를 원본 그대로 저장하면 안 되므로 BCrypt로 암호화한 값을 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User();
        user.setLoginId(request.getLoginId());
        user.setPassword(encodedPassword);
        user.setName(request.getName());
        // role은 클라이언트 요청값을 쓰지 않고 서버에서 강제로 USER 고정
        // (요청 DTO에 role 필드를 아예 두지 않은 것도 같은 이유: 클라이언트가 ADMIN을 자칭하지 못하게 막기 위함)
        user.setRole(UserRole.USER);
        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 저장된 값은 해시라서 복호화가 불가능 -> 입력값을 같은 방식으로 재해싱해서 비교(matches)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 아이디/비밀번호 검증이 끝난 시점에만 토큰을 발급 -> 이후 요청은 이 토큰만으로 신원 증명
        String token = jwtTokenProvider.generateToken(user.getLoginId());

        return new LoginResponse(token);
    }
}
