package com.epms.config;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single dummy Epic 2 book catalogue used across the demo: the online
 * store shows these books, the sales seeder generates sales for them, and the
 * royalty agreements are seeded per book/author. Category and genre are
 * referenced by NAME here and resolved against the admin-managed
 * {@code categories} / {@code genres} tables at startup, so the admin pages
 * stay the source of truth for how books are grouped.
 */
public final class DemoCatalog {

    private DemoCatalog() {}

    public record DemoBook(
            long bookId,
            long authorId,
            String title,
            String author,
            String category,
            String genre,
            BigDecimal price,
            boolean newArrival,
            String blurb
    ) {}

    public static final List<DemoBook> BOOKS = List.of(
            new DemoBook(1L, 101L, "The Silent Harbour", "Nadeesha Fernando",
                    "Fiction", "Literary Fiction", new BigDecimal("1850.00"), true,
                    "A fishing village on the southern coast keeps a secret that one returning daughter is determined to surface."),
            new DemoBook(2L, 101L, "Monsoon Letters", "Nadeesha Fernando",
                    "Short Story", "Contemporary", new BigDecimal("1200.00"), false,
                    "Twelve short stories, one for every month of rain, about the people who write to each other and never send it."),
            new DemoBook(3L, 102L, "Kandy Nights", "Ruwan Jayasinghe",
                    "Mystery", "Cozy", new BigDecimal("2400.00"), true,
                    "A retired schoolteacher and a very loud tuk-tuk driver solve the case nobody in Kandy wants solved."),
            new DemoBook(4L, 103L, "Java for Beginners", "Dilshan Perera",
                    "Non-Fiction", "Technology", new BigDecimal("3200.00"), false,
                    "A friendly first course in Java, written for Sri Lankan students with local examples throughout."),
            new DemoBook(5L, 104L, "Ceylon Tea Stories", "Amaya Wickramasinghe",
                    "Short Story", "Heritage", new BigDecimal("1500.00"), true,
                    "Stories from the estates: pluckers, planters, and the hill-country mist that remembers everything."),
            new DemoBook(6L, 104L, "The Last Lighthouse", "Amaya Wickramasinghe",
                    "Fiction", "Historical Fiction", new BigDecimal("1950.00"), true,
                    "1948, Galle. The keeper of the last manned lighthouse must choose between the light and the island."),
            // Sinhala title/author — proves Unicode survives DB, API and fonts.
            new DemoBook(7L, 105L, "සයිකෝ", "සුසිත් රුවන්",
                    "Fiction", "Literary Fiction", new BigDecimal("1800.00"), true,
                    "මනෝවිද්‍යාත්මක ත්‍රාසජනක නවකතාවක්. කොළඹ නගරයේ රාත්‍රී ජීවිතය පසුබිම් කරගත් අඳුරු කතාවකි.")
    );
}
