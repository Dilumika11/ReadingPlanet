package com.epms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Short URLs used by the public site header and footer (/store rather than
 * /store.html). Information pages that do not exist yet open the home page.
 */
@Controller
public class PublicPageController {

    /** Every short URL here; SecurityConfig lets anyone open them. */
    public static final String[] PATHS = {
            "/store", "/catalog", "/order", "/authors", "/account", "/getting-published",
            "/author-services", "/publishing-services", "/about", "/who-we-are", "/faqs", "/contact",
            "/careers", "/delivery", "/payment", "/privacy", "/returns", "/shipping", "/sitemap", "/terms"
    };

    @GetMapping({"/store", "/catalog", "/order"})
    public String store() {
        return "forward:/store.html";
    }

    @GetMapping("/authors")
    public String authors() {
        return "forward:/authors.html";
    }

    @GetMapping("/account")
    public String account() {
        return "forward:/account.html";
    }

    @GetMapping({"/getting-published", "/author-services", "/publishing-services"})
    public String gettingPublished() {
        return "forward:/getting-published.html";
    }

    @GetMapping("/faqs")
    public String faqs() {
        return "forward:/faqs.html";
    }

    @GetMapping("/contact")
    public String contact() {
        return "forward:/contact.html";
    }

    @GetMapping({"/about", "/who-we-are", "/careers", "/delivery", "/payment",
            "/privacy", "/returns", "/shipping", "/sitemap", "/terms"})
    public String home() {
        return "redirect:/";
    }
}
