package com.epms.dto.response;

import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyCalculationLine;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** A calculation (saved, or a preview that was not saved) with every line behind it. */
@Data
@AllArgsConstructor
public class RoyaltyCalculationDetail {
    private boolean preview;
    private RoyaltyCalculation calculation;
    private RoyaltyAgreement agreement;
    private String authorName;
    private String bookTitle;
    private Map<String, Integer> unitsByChannel;
    private List<RoyaltyCalculationLine> lines;
}
