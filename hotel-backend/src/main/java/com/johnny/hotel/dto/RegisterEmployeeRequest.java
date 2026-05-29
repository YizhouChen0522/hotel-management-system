package com.johnny.hotel.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterEmployeeRequest {
    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Real name is required")
    private String realName;

    private String phone;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Applied Role code cannot be empty")
    private String applyRoleCode;

    private String applyReason;
}


