package com.johnny.hotel.extras;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.*;import java.math.BigDecimal;import java.util.List;public final class ExtrasRequests{private ExtrasRequests(){}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Catalog{@NotBlank@Size(max=50)private String code;@NotBlank@Size(max=120)private String name;@NotNull private ChargeType type;@NotBlank@Size(max=80)private String category;@NotNull@DecimalMin("0.01")@Digits(integer=10,fraction=2)private BigDecimal unitPrice;@Size(max=500)private String description;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class ServiceOrder{@NotNull private Long catalogId;@NotNull@DecimalMin("0.01")@Digits(integer=8,fraction=2)private BigDecimal quantity;@NotBlank@Size(max=60)private String requestKey;@Size(max=500)private String note;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class PurchaseLine{@NotNull private Long catalogId;@NotNull@DecimalMin("0.01")@Digits(integer=8,fraction=2)private BigDecimal quantity;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Purchase{@NotBlank@Size(max=100)private String requestKey;@NotEmpty@Size(max=50)private List<@Valid PurchaseLine> items;}
 @Data@Builder@NoArgsConstructor@AllArgsConstructor public static class Note{@Size(max=500)private String note;}
}

