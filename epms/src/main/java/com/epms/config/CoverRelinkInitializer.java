package com.epms.config;

import com.epms.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * After the catalogue is seeded, re-attach any cover files already in the
 * uploads folder to their books. Protects demo data from a database reset
 * (the seeded book ids are stable, so their covers come back automatically).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class CoverRelinkInitializer implements CommandLineRunner {

    private final BookService bookService;

    @Override
    public void run(String... args) {
        bookService.relinkOrphanedCovers();
    }
}
