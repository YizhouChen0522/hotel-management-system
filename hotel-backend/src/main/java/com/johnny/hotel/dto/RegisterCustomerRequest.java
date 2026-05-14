package com.johnny.hotel.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterCustomerRequest {
    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Real name is required")
    private String realName;

    private String phone;

    @Email(message = "Invalid email format")
    private String email;
}
