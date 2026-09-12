package com.johnny.hotel.task;
import lombok.*; import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskView { private HotelTask task; private List<TaskAssignment> assignments; private List<TaskRecord> records; }
