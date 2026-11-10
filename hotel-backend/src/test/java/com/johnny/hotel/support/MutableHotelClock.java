package com.johnny.hotel.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

public class MutableHotelClock extends Clock {
    private volatile LocalDate baseDate = LocalDate.now(getZone());
    private volatile Instant current = baseDate.atTime(12, 0).atZone(getZone()).toInstant();
    private volatile Consumer<LocalDate> onDateChange;

    public void onDateChange(Consumer<LocalDate> callback) {
        onDateChange = callback;
    }

    public void baseDate(LocalDate date) {
        baseDate = date;
        day(0);
    }

    public void day(int offset) {
        setCurrent(baseDate.plusDays(offset).atTime(12, 0));
    }

    public void at(LocalDateTime time) {
        setCurrent(time);
    }

    private void setCurrent(LocalDateTime time) {
        current = time.atZone(getZone()).toInstant();
        var callback = onDateChange;
        if (callback != null) {
            callback.accept(time.toLocalDate());
        }
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("Asia/Shanghai");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
