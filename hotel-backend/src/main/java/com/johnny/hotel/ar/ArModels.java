package com.johnny.hotel.ar;
import lombok.*;import jakarta.validation.constraints.*;import java.math.*;import java.util.*;
public final class ArModels {private ArModels(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class AccountRequest {@NotBlank @Size(max=30)String accountType;@NotBlank @Size(max=150)String name;@NotBlank @Pattern(regexp="[A-Z]{3}")String currency;@DecimalMin("0.00")@Digits(integer=10,fraction=2)BigDecimal creditLimit;@Min(0)@Max(3650)Integer paymentTermsDays;@Size(max=120)String contactName;@Email @Size(max=120)String contactEmail;@Size(max=100)String externalReference;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class TransferRequest {@NotNull @DecimalMin("0.01")@Digits(integer=10,fraction=2)BigDecimal amount;@NotBlank @Size(max=100)String requestKey;@Size(max=500)String reason;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class PaymentRequest {@NotNull @DecimalMin("0.01")@Digits(integer=10,fraction=2)BigDecimal amount;@NotBlank @Size(max=100)String requestKey;@NotBlank @Size(max=30)String paymentMethod;@Size(max=100)String externalReference;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class CreditRequest {@NotNull @DecimalMin("0.01")@Digits(integer=10,fraction=2)BigDecimal amount;@NotBlank @Size(max=100)String requestKey;@NotBlank @Size(max=500)String reason;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class AccountView {ArAccount account;BigDecimal outstanding,availableCredit;}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Statement {AccountView account;List<ArLedgerEntry> entries;}
}
