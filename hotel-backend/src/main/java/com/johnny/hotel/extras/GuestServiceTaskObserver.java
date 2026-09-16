package com.johnny.hotel.extras;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.task.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuestServiceTaskObserver implements TaskLifecycleObserver {
    private final ExtrasMapper extras;
    @Override public void acknowledged(HotelTask task, Todo todo, Long actorId) {
        if (task.getTaskType() != TaskType.GUEST_SERVICE.getCode()) return;
        GuestServiceOrder order = extras.serviceByTask(task.getId());
        if (order == null) throw new BusinessException("Guest service task has no order");
        if (order.getStatus() == GuestServiceOrderStatus.PENDING.getCode()
                && extras.serviceStarted(order.getId()) != 1)
            throw new BusinessException(409, "Guest service state changed concurrently");
    }
}
