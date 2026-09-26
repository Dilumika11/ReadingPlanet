package com.epms.service.impl;

import com.epms.dto.request.SettingUpdateRequest;
import com.epms.entity.AuditLog;
import com.epms.entity.SystemSetting;
import com.epms.exception.InvalidRequestException;
import com.epms.repository.SystemSettingRepository;
import com.epms.service.AuditService;
import com.epms.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class SettingsServiceImpl implements SettingsService {

    /** key -> {default value, description}, in display order. */
    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(CURRENCY, new String[] {"LKR", "Currency code used on invoices, statements and reports"});
        DEFAULTS.put(TAX_RATE_PERCENT, new String[] {"0.00", "Tax rate (%) applied to new invoices"});
        DEFAULTS.put(INVOICE_PREFIX, new String[] {"INV-", "Prefix of generated invoice numbers"});
        DEFAULTS.put(ROYALTY_PAYMENT_THRESHOLD, new String[] {"1000.00",
                "Royalty payable below this amount is carried forward instead of approved"});
        DEFAULTS.put(COMPANY_NAME, new String[] {"Reading Planet", "Company name printed on invoices and statements"});
        DEFAULTS.put(COMPANY_ADDRESS, new String[] {"1470/1F, Delgahawaththa Road, Pannipitiya",
                "Company address printed on invoices and statements"});
    }

    private static final List<String> PUBLIC_KEYS =
            List.of(CURRENCY, TAX_RATE_PERCENT, INVOICE_PREFIX, COMPANY_NAME, COMPANY_ADDRESS);

    private final SystemSettingRepository settingRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<SystemSetting> getAll() {
        return settingRepository.findAll();
    }

    @Override
    public SystemSetting upsert(SettingUpdateRequest request, Long updatedBy) {

        String key = request.getSettingKey().trim();
        String value = normalize(key, request.getSettingValue());

        SystemSetting setting = settingRepository.findBySettingKey(key).orElseGet(SystemSetting::new);
        String oldValue = setting.getSettingId() == null ? null : setting.getSettingValue();

        setting.setSettingKey(key);
        setting.setSettingValue(value);
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            setting.setDescription(request.getDescription().trim());
        } else if (setting.getDescription() == null && DEFAULTS.containsKey(key)) {
            setting.setDescription(DEFAULTS.get(key)[1]);
        }
        setting.setUpdatedBy(updatedBy);

        SystemSetting saved = settingRepository.save(setting);
        if (!Objects.equals(oldValue, value)) {
            auditService.record(updatedBy, "SETTING_CHANGED", "SystemSetting", saved.getSettingId(),
                    key + ": " + (oldValue == null ? "(not set)" : oldValue) + " -> " + (value == null ? "(empty)" : value));
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> getHistory() {
        return auditService.history("SystemSetting");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : PUBLIC_KEYS) {
            out.put(key, value(key));
        }
        return out;
    }

    @Override
    public void ensureDefaults() {
        DEFAULTS.forEach((key, def) -> {
            if (settingRepository.findBySettingKey(key).isEmpty()) {
                SystemSetting s = new SystemSetting();
                s.setSettingKey(key);
                s.setSettingValue(def[0]);
                s.setDescription(def[1]);
                settingRepository.save(s);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public String currency() {
        return value(CURRENCY);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal taxRatePercent() {
        return decimal(TAX_RATE_PERCENT);
    }

    @Override
    @Transactional(readOnly = true)
    public String invoicePrefix() {
        return value(INVOICE_PREFIX);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal royaltyPaymentThreshold() {
        return decimal(ROYALTY_PAYMENT_THRESHOLD);
    }

    @Override
    @Transactional(readOnly = true)
    public String companyName() {
        return value(COMPANY_NAME);
    }

    @Override
    @Transactional(readOnly = true)
    public String companyAddress() {
        return value(COMPANY_ADDRESS);
    }

    private String value(String key) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .filter(v -> !v.isBlank())
                .orElse(DEFAULTS.containsKey(key) ? DEFAULTS.get(key)[0] : null);
    }

    private BigDecimal decimal(String key) {
        try {
            return new BigDecimal(value(key)).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            return new BigDecimal(DEFAULTS.get(key)[0]);
        }
    }

    /** Validates well-known keys; other keys are stored as given. */
    private static String normalize(String key, String raw) {
        String v = raw == null ? null : raw.trim();
        switch (key) {
            case CURRENCY -> {
                if (v == null || !v.toUpperCase().matches("[A-Z]{3}")) {
                    throw new InvalidRequestException("Currency must be a 3-letter code such as LKR or USD");
                }
                return v.toUpperCase();
            }
            case TAX_RATE_PERCENT -> {
                BigDecimal rate = parse(v, "Tax rate");
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new InvalidRequestException("Tax rate must be between 0 and 100");
                }
                return rate.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            case ROYALTY_PAYMENT_THRESHOLD -> {
                BigDecimal t = parse(v, "Royalty payment threshold");
                if (t.signum() < 0) {
                    throw new InvalidRequestException("Royalty payment threshold cannot be negative");
                }
                return t.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            case INVOICE_PREFIX -> {
                if (v == null || !v.matches("[A-Za-z0-9/-]{1,10}")) {
                    throw new InvalidRequestException(
                            "Invoice prefix must be 1 to 10 letters, digits, '-' or '/' (e.g. INV-)");
                }
                return v.toUpperCase();
            }
            case COMPANY_NAME, COMPANY_ADDRESS -> {
                if (v == null || v.isEmpty()) {
                    throw new InvalidRequestException(key + " cannot be empty");
                }
                return v;
            }
            default -> {
                return v == null || v.isEmpty() ? null : v;
            }
        }
    }

    private static BigDecimal parse(String v, String label) {
        try {
            return new BigDecimal(v);
        } catch (RuntimeException e) {
            throw new InvalidRequestException(label + " must be a number");
        }
    }
}
