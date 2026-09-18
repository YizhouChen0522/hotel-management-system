package com.johnny.hotel.pricing; import lombok.*;import java.util.List;
@Value @Builder public class PolicyView{DynamicPolicy policy;List<OccupancyBand> occupancyBands;List<BookingWindowBand> bookingWindowBands;List<DynamicPricingCell> cells;boolean valid;List<String> validationErrors;}
