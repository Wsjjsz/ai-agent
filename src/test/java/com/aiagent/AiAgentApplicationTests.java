package com.aiagent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Full application context requires PostgreSQL and model credentials; run as an integration smoke test in a provisioned environment.")
@SpringBootTest
class AiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
