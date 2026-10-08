package com.linelock.linelock.concurrencydemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.StandardEnvironment;

// 동시성 데모 코드(concurrencydemo)가 demo 프로필에서만 활성화되는지 지키는 테스트
// 데모 엔드포인트는 엔티티를 그대로 받아 요청자를 위조할 수 있는 옛 방식이라 운영에 열려 있으면 안 됨
// 누가 실수로 @Profile("demo")를 지우거나, 새 데모 클래스를 추가하고 표시를 빼먹으면 CI가 실패하도록 고정함
//
// 방식: 스프링이 빈으로 등록할 후보(@Component 계열: @Controller, @RestController, @Service 등)를 찾는 스캐너를 쓴다.
// 이 스캐너는 클래스를 찾을 때 @Profile 같은 조건도 평가하므로, 환경(프로필)에 따라 찾아지는 개수가 달라진다.
// 서버를 띄우지 않고 스캔만 하므로 MySQL/Redis 없이 빠르게 돈다
public class ConcurrencyDemoProfileTest {

    private static final String DEMO_PACKAGE = "com.linelock.linelock.concurrencydemo";

    // 지정한 프로필이 켜진 환경에서 데모 패키지의 빈 후보를 찾는 도우미
    // 생성자의 true는 @Component 계열을 후보로 인정하는 기본 필터를 켠다는 뜻 (false면 아무 클래스도 후보로 인정하지 않음)
    private Set<BeanDefinition> scanDemoPackage(String... activeProfiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true,
                environment);
        return scanner.findCandidateComponents(DEMO_PACKAGE);
    }

    @Test
    void 프로필이_없는_기본_실행에서는_데모_빈이_하나도_등록되지_않는다() {
        // when: 아무 프로필도 켜지 않은 환경(운영 기본 실행과 같은 상태)에서 스캔
        Set<BeanDefinition> candidates = scanDemoPackage();

        // then: @Profile("demo") 조건에 걸려 후보가 0개여야 함
        // 누가 어떤 클래스의 @Profile을 지우면 그 클래스가 후보로 잡혀서 이 테스트가 실패함
        // (실패 메시지에 어떤 클래스가 새어 나왔는지 나오도록 클래스 이름 목록을 함께 보여줌)
        assertThat(candidates.stream().map(BeanDefinition::getBeanClassName).toList())
                .as("기본 실행에서 등록되면 안 되는 데모 빈")
                .isEmpty();
    }

    @Test
    void demo_프로필을_켜면_데모_빈_7개가_모두_등록된다() {
        // when: demo 프로필을 켠 환경에서 스캔
        Set<BeanDefinition> candidates = scanDemoPackage("demo");

        // then: 컨트롤러 2개 + 서비스 5개 = 7개
        // 이 테스트가 없으면 위 테스트는 "패키지 이름이 틀려서 아무것도 못 찾아도 통과"하는 빈 검사가 될 수 있음
        // (데모 클래스를 일부러 추가하거나 지웠다면 이 숫자도 같이 고쳐야 함)
        assertThat(candidates).hasSize(7);
    }

}
