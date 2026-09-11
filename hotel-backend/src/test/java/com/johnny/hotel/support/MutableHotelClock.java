package com.johnny.hotel.support;
import java.time.*;
public class MutableHotelClock extends Clock {
    private volatile Instant current = LocalDate.of(2026,10,1).atTime(12,0).atZone(getZone()).toInstant();
    public void day(int offset) { current = LocalDate.of(2026,10,1).plusDays(offset).atTime(12,0).atZone(getZone()).toInstant(); }
    public void at(LocalDateTime time){current=time.atZone(getZone()).toInstant();}
    @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    @Override public Instant instant() { return current; }
}
