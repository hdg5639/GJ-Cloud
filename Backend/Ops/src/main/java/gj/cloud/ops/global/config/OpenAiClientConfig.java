package gj.cloud.ops.global.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// AiSpecGeneratorClient(D-3 생성)와 AiComposeReviewer(D.5-1 검수)가 같은 OpenAIClient를 공유 —
// 컴포넌트마다 별도 OkHttp 클라이언트를 만들지 않도록 빈으로 분리
@Configuration
public class OpenAiClientConfig {

    @Bean
    public OpenAIClient openAIClient(@Value("${ai.api-key}") String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }
}
