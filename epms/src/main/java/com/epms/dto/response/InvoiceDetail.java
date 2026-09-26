package com.epms.dto.response;

import com.epms.entity.FinancialInvoice;
import com.epms.entity.FinancialInvoiceLine;
import com.epms.entity.FinancialPayment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InvoiceDetail {
    private FinancialInvoice invoice;
    private List<FinancialInvoiceLine> lines;
    private List<FinancialPayment> payments;
    private String companyName;
    private String companyAddress;
}
