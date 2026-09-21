package com.johnny.hotel.pricing;

import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import static com.johnny.hotel.service.support.BillingRules.one;

/** Installs the original V37 editable template after a real privileged user exists. */
@Service @RequiredArgsConstructor
public class DynamicPricingBootstrapService {
    private final PricingMapper pricing;
    private final SysAuditLogMapper audit;

    public Long firstEligibleCreator() { return pricing.firstActivePrivilegedCreator(); }

    @Transactional
    public boolean ensureDefaultTemplate(Long operatorUserId) {
        if (operatorUserId == null || !operatorUserId.equals(pricing.activePrivilegedCreatorForUpdate(operatorUserId)))
            throw new BusinessException("Default pricing template requires an active OWNER or SUPER_ADMIN");
        if (!Integer.valueOf(1).equals(pricing.lockControl()))
            throw new BusinessException("Dynamic pricing control row is missing");
        // A locking current read is required even if another transaction committed after our transaction began.
        if (pricing.firstPolicyForUpdate() != null) return false;

        var policy = DynamicPolicy.builder().versionNo(1).name("Default Dynamic Pricing Template")
                .status(0).minimumMultiplier(new BigDecimal("0.8000"))
                .maximumMultiplier(new BigDecimal("2.0000")).createdBy(operatorUserId).build();
        one(pricing.insertPolicy(policy));

        String[] occupancyLabels = {"0-39%", "40-69%", "70-84%", "85-94%", "95-100%"};
        int[] occupancyStarts = {0, 40, 70, 85, 95};
        int[] occupancyEnds = {40, 70, 85, 95, 101};
        String[] windowLabels = {"0-7 days", "8-14 days", "15-30 days", "31+ days"};
        int[] windowStarts = {0, 8, 15, 31};
        Integer[] windowEnds = {7, 14, 30, null};
        int[][] adjustments = {
                {-15, -10, -5, 0},
                {-5, 0, 0, 0},
                {20, 15, 15, 10},
                {30, 25, 25, 20},
                {40, 35, 35, 30}
        };
        var occupancy = new ArrayList<OccupancyBand>();
        var windows = new ArrayList<BookingWindowBand>();
        for (int i = 0; i < occupancyLabels.length; i++) {
            var band = OccupancyBand.builder().policyId(policy.getId()).label(occupancyLabels[i])
                    .lowerInclusive(occupancyStarts[i]).upperExclusive(occupancyEnds[i]).sortOrder(i + 1).build();
            one(pricing.insertOccupancy(band));
            occupancy.add(band);
        }
        for (int i = 0; i < windowLabels.length; i++) {
            var band = BookingWindowBand.builder().policyId(policy.getId()).label(windowLabels[i])
                    .minDays(windowStarts[i]).maxDays(windowEnds[i]).sortOrder(i + 1).build();
            one(pricing.insertWindow(band));
            windows.add(band);
        }
        for (int i = 0; i < occupancy.size(); i++) {
            for (int j = 0; j < windows.size(); j++) {
                one(pricing.insertCell(DynamicPricingCell.builder().policyId(policy.getId())
                        .occupancyBandId(occupancy.get(i).getId()).bookingWindowBandId(windows.get(j).getId())
                        .adjustmentPercent(BigDecimal.valueOf(adjustments[i][j]).setScale(4)).build()));
            }
        }
        one(audit.insert(SysAuditLog.builder().operatorId(operatorUserId).action("DYNAMIC_DEFAULT_BOOTSTRAP")
                .detail("Original V37 editable draft initialized after real administrator became active").build()));
        return true;
    }
}
