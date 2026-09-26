package com.epms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Boots the whole application on a throwaway in-memory database
// (src/test/resources/application-test.properties), never the real MySQL.
@SpringBootTest
@ActiveProfiles("test")
class EpmsApplicationTests {

    @Test
    void contextLoads() {
    }

}
