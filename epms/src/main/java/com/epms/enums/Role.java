package com.epms.enums;

public enum Role {

    ADMIN,
    AUTHOR,
    EDITOR,
    // Assigns manuscripts to editors and monitors the editorial workflow (Epic 2)
    CHIEF_EDITOR,
    PROOFREADER,
    DESIGNER,
    PRODUCTION_MANAGER,
    INVENTORY_STAFF,
    SALES_STAFF,
    FINANCE_STAFF,
    // Read-only executive dashboards (Epic 4 analytics)
    EXECUTIVE,
    CUSTOMER

}