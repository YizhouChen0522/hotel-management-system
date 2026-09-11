package com.johnny.hotel.dto;
import jakarta.validation.constraints.Size;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CompleteTurnoverTaskRequest {
    @Size(max=500) private String note;
}
