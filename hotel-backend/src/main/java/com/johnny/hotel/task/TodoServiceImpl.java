package com.johnny.hotel.task;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import java.util.List;
@Service @RequiredArgsConstructor public class TodoServiceImpl implements TodoService {
 private final HotelTaskService tasks;
 public List<Todo> mine(Integer page,Integer size){return tasks.todos(page,size);}
 public Todo get(Long id){return tasks.todo(id);}
 public Todo acknowledge(Long id,String note){return tasks.updateTodo(id,"ACKNOWLEDGE",note);}
 public Todo block(Long id,String note){return tasks.updateTodo(id,"BLOCK",note);}
 public Todo resume(Long id,String note){return tasks.updateTodo(id,"RESUME",note);}
 public Todo complete(Long id,String note){return tasks.updateTodo(id,"COMPLETE",note);}
}
