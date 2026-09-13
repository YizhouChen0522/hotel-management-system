package com.johnny.hotel.task;
import java.util.List;
public interface TodoService {
 List<Todo> mine(Integer page,Integer size); Todo get(Long id);
 Todo acknowledge(Long id,String note); Todo block(Long id,String note); Todo resume(Long id,String note); Todo complete(Long id,String note);
}
