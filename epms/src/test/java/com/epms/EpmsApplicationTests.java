package com.epms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Boots the dev profile (H2, no MySQL) but on a throwaway in-memory database
// so the suite never touches the developer's persistent ~/.readingplanet data.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:epms-test;DB_CLOSE_DELAY=-1",
        "epms.demo-data.enabled=false",
        "epms.uploads.dir=${java.io.tmpdir}/epms-test-uploads"
})
@ActiveProfiles("dev")
class EpmsApplicationTests {

    @Test
    void contextLoads() {
    }

}
