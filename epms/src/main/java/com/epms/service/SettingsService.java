package com.epms.service;

import com.epms.dto.request.SettingUpdateRequest;
import com.epms.entity.AuditLog;
import com.epms.entity.SystemSetting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface SettingsService {

    // Well-known keys (validated on save; defaults created at startup)
    String CURRENCY = "currency";
    String TAX_RATE_PERCENT = "taxRatePercent";
    String INVOICE_PREFIX = "invoicePrefix";
    String ROYALTY_PAYMENT_THRESHOLD = "royaltyPaymentThreshold";
    String COMPANY_NAME = "companyName";
    String COMPANY_ADDRESS = "companyAddress";

    List<SystemSetting> getAll();

    /**
     * Creates or updates the setting by key, validating well-known keys and
     * recording the old and new value in the audit log. Per spec: settings
     * changes apply to future invoices/calculations only, never
     * retroactively (those records store the values they were created with).
     */
    SystemSetting upsert(SettingUpdateRequest request, Long updatedBy);

    /** Change history of all settings, newest first. */
    List<AuditLog> getHistory();

    /** Values other epics may read without logging in (currency, tax rate, invoice prefix, company). */
    Map<String, String> getPublicSettings();

    /** Creates any missing well-known setting with its default value. */
    void ensureDefaults();

    String currency();

    BigDecimal taxRatePercent();

    String invoicePrefix();

    BigDecimal royaltyPaymentThreshold();

    String companyName();

    String companyAddress();
}
