package com.johnny.hotel.task;
public interface TaskLifecycleObserver {
    default void acknowledged(HotelTask task, Todo todo, Long actorId) { }
}
