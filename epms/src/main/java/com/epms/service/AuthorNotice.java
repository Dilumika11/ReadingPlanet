package com.epms.service;

/** Event: something about their royalties the author should be told (handled by AuthorFinanceService). */
public record AuthorNotice(Long authorId, String type, String title, String message) {
}
