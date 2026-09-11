package com.johnny.hotel.vo;
import com.johnny.hotel.entity.FolioItem;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record FolioVO(Long id, Long bookingId, String currency, Integer status, BigDecimal totalAmount,
        BigDecimal paidAmount, BigDecimal refundedAmount, BigDecimal balanceAmount, LocalDateTime closedTime,
        List<Item> items, List<PaymentVO> payments, List<ExpenseVO> expenses) {
    @Builder
    public record Item(Long id, String itemType, String description, LocalDate businessDate,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, Long roomId,
            Long roomTypeId, Long roomAssignmentId, Long sourceItemId, Long stayAdjustmentId) {
        public static Item from(FolioItem i) {
            return Item.builder().id(i.getId()).itemType(i.getItemType()).description(i.getDescription()).businessDate(i.getBusinessDate())
                    .quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).amount(i.getAmount()).roomId(i.getRoomId()).roomTypeId(i.getRoomTypeId())
                    .roomAssignmentId(i.getRoomAssignmentId()).sourceItemId(i.getSourceItemId()).stayAdjustmentId(i.getStayAdjustmentId()).build();
        }
    }
}
