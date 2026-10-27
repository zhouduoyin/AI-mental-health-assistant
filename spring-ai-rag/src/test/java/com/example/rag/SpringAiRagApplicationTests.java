package com.example.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "rag.demo.seed=false")
@ActiveProfiles("demo")
class SpringAiRagApplicationTests {

    @Test
    void contextLoads() {
    }
}
