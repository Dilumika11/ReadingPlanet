package com.epms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Boots against the real MySQL "epms" database (application.properties).
@SpringBootTest(properties = {
        "epms.uploads.dir=${java.io.tmpdir}/epms-test-uploads"
})
class EpmsApplicationTests {

    @Test
    void contextLoads() {
    }

}
