package com.epms.service;

import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculationLine;
import com.epms.entity.SalesRecord;
import com.epms.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The royalty formula, with no database access so it can be tested directly.
 *
 * For each completed sale line of the book in the period:
 * <ul>
 *   <li>base = NET_SALES: sale amount - discount; LIST_PRICE: quantity x book list price</li>
 *   <li>rate = wholesale rate for BOOKSTORE (wholesale) sales when the agreement sets one, else the standard rate</li>
 *   <li>line royalty = base x rate / 100</li>
 * </ul>
 * Returned sales never earn royalty; they are listed on the statement.
 * Deductions reduce the base (and the royalty at the standard rate).
 * The remaining unrecouped advance is then recouped first:
 * payable = max(0, gross royalty - remaining advance) + amounts carried forward.
 */
public final class RoyaltyCalculator {

    public static final String CHANNEL_WHOLESALE = "BOOKSTORE";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RoyaltyCalculator() {
    }

    public record Result(
            List<RoyaltyCalculationLine> lines,
            int booksSold,
            Map<String, Integer> unitsByChannel,
            int unitsReturned,
            BigDecimal returnsAmount,
            BigDecimal grossSales,
            BigDecimal royaltyBase,
            BigDecimal grossRoyalty,
            BigDecimal advanceRecouped,
            BigDecimal carriedForwardIn,
            BigDecimal payable) {
    }

    public static Result calculate(RoyaltyAgreement agreement, BigDecimal listPrice,
                                   List<SalesRecord> completedSales, List<SalesRecord> returnedSales,
                                   BigDecimal deductions, BigDecimal carriedForwardIn) {

        boolean listPriceBasis = "LIST_PRICE".equals(agreement.getBasis());
        if (listPriceBasis && listPrice == null) {
            throw new BusinessRuleException("Agreement " + agreement.getAgreementNumber()
                    + " is on a LIST_PRICE basis but the book has no list price in the catalogue");
        }
        BigDecimal standardRate = agreement.getRoyaltyPercentage();
        BigDecimal wholesaleRate = agreement.getWholesaleRoyaltyPercentage() != null
                ? agreement.getWholesaleRoyaltyPercentage() : standardRate;

        List<RoyaltyCalculationLine> lines = new ArrayList<>();
        Map<String, Integer> unitsByChannel = new LinkedHashMap<>();
        int booksSold = 0;
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal baseTotal = BigDecimal.ZERO;
        BigDecimal royaltyTotal = BigDecimal.ZERO;

        for (SalesRecord sale : completedSales) {
            BigDecimal discount = sale.getDiscount() == null ? BigDecimal.ZERO : sale.getDiscount();
            BigDecimal net = sale.getSaleAmount().subtract(discount);
            BigDecimal base = listPriceBasis ? listPrice.multiply(BigDecimal.valueOf(sale.getQuantity())) : net;
            BigDecimal rate = CHANNEL_WHOLESALE.equalsIgnoreCase(sale.getChannel()) ? wholesaleRate : standardRate;
            BigDecimal royalty = money(base.multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP));

            lines.add(line(sale, RoyaltyCalculationLine.SALE, discount, money(base), rate, royalty));
            booksSold += sale.getQuantity();
            unitsByChannel.merge(sale.getChannel(), sale.getQuantity(), Integer::sum);
            grossSales = grossSales.add(net);
            baseTotal = baseTotal.add(base);
            royaltyTotal = royaltyTotal.add(royalty);
        }

        int unitsReturned = 0;
        BigDecimal returnsAmount = BigDecimal.ZERO;
        for (SalesRecord ret : returnedSales) {
            BigDecimal discount = ret.getDiscount() == null ? BigDecimal.ZERO : ret.getDiscount();
            lines.add(line(ret, RoyaltyCalculationLine.RETURN, discount, money(BigDecimal.ZERO), BigDecimal.ZERO,
                    money(BigDecimal.ZERO)));
            unitsReturned += ret.getQuantity();
            returnsAmount = returnsAmount.add(ret.getSaleAmount().subtract(discount));
        }

        BigDecimal ded = deductions == null ? BigDecimal.ZERO : deductions;
        if (ded.signum() < 0) {
            throw new BusinessRuleException("Deductions cannot be negative");
        }
        if (ded.compareTo(baseTotal) > 0) {
            throw new BusinessRuleException(
                    "Deductions " + money(ded) + " exceed gross sales " + money(baseTotal) + " for the period");
        }
        BigDecimal royaltyBase = money(baseTotal.subtract(ded));
        BigDecimal deductionRoyalty = ded.multiply(standardRate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal grossRoyalty = money(royaltyTotal.subtract(deductionRoyalty).max(BigDecimal.ZERO));

        BigDecimal advance = agreement.getAdvanceAmount() == null ? BigDecimal.ZERO : agreement.getAdvanceAmount();
        BigDecimal alreadyRecouped = agreement.getAdvanceRecouped() == null ? BigDecimal.ZERO : agreement.getAdvanceRecouped();
        BigDecimal remainingAdvance = advance.subtract(alreadyRecouped).max(BigDecimal.ZERO);
        BigDecimal recouped = money(grossRoyalty.min(remainingAdvance));
        BigDecimal carried = money(carriedForwardIn == null ? BigDecimal.ZERO : carriedForwardIn);
        BigDecimal payable = money(grossRoyalty.subtract(recouped).add(carried));

        return new Result(lines, booksSold, unitsByChannel, unitsReturned, money(returnsAmount), money(grossSales),
                royaltyBase, grossRoyalty, recouped, carried, payable);
    }

    private static RoyaltyCalculationLine line(SalesRecord sale, String type, BigDecimal discount,
                                               BigDecimal base, BigDecimal rate, BigDecimal royalty) {
        RoyaltyCalculationLine l = new RoyaltyCalculationLine();
        l.setSaleId(sale.getSaleId());
        l.setSaleReference(sale.getSaleReference());
        l.setSaleDate(sale.getSaleDate());
        l.setChannel(sale.getChannel());
        l.setLineType(type);
        l.setQuantity(sale.getQuantity());
        l.setUnitPrice(sale.getUnitPrice());
        l.setDiscount(money(discount));
        l.setBaseAmount(base);
        l.setRateApplied(rate);
        l.setRoyaltyAmount(royalty);
        return l;
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
