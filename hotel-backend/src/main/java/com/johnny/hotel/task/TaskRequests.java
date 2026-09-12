package com.johnny.hotel.task;
import jakarta.validation.constraints.*; import lombok.*; import java.util.List;
public final class TaskRequests { private TaskRequests(){}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class CreateGeneral { @NotBlank @Size(max=120) private String title; @Size(max=500) private String description; @NotBlank @Size(max=100) private String requestKey; }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Assign { @NotEmpty private List<@NotNull Long> assigneeUserIds; @Size(max=500) private String note; }
 @Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Note { @Size(max=500) private String note; }
}
