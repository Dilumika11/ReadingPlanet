package com.epms.dto.response;

import com.epms.entity.FinancialReport;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/** A stored report with its figures exactly as generated. */
@Data
@AllArgsConstructor
public class ReportView {
    private FinancialReport report;
    private Map<String, Object> figures;
}
