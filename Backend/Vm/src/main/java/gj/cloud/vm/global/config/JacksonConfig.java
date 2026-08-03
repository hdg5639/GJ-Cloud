package gj.cloud.vm.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Boot의 JacksonAutoConfiguration이 ObjectMapper 빈을 등록하지 않는 환경(Ops 서비스에서 먼저
// 발견된 문제 — 이 프로젝트의 다른 클라이언트들은 전부 `new ObjectMapper()`로 직접 생성해 써서
// SseTicketService가 생성자 주입을 요구하기 전까지 드러나지 않았음)에서도 항상 빈이 존재하도록 명시적으로
// 등록. LocalDateTime 직렬화를 위해 JavaTimeModule을 직접 등록함(자동 등록에 의존하지 않으므로 필수).
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
