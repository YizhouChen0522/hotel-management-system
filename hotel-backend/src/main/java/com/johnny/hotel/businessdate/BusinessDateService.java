package com.johnny.hotel.businessdate;

import com.johnny.hotel.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessDateService {
    private final BusinessDateMapper db;
    private final Clock clock;

    @Transactional
    public void initializeOnce() {
        var control = db.lockExclusive();
        if (control == null) {
            throw new BusinessException("Business Date control row is missing");
        }
        if (control.getBusinessDate() != null) {
            return;
        }
        var date = LocalDate.now(clock);
        var at = LocalDateTime.now(clock);
        if (db.initialize(date, at) != 1) {
            throw new BusinessException("Business Date initialization failed");
        }
        if (db.history(date, at) != 1) {
            throw new BusinessException("Business Date initialization history failed");
        }
    }

    @Transactional(readOnly = true)
    public BusinessDateControl current() {
        var control = db.current();
        if (control == null || control.getBusinessDate() == null) {
            throw new BusinessException("Business Date is not initialized");
        }
        return control;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate postingDate() {
        var control = db.lockForPosting();
        if (control == null || control.getBusinessDate() == null || !"OPEN".equals(control.getState())) {
            throw new BusinessException("Business Date is not open for posting");
        }
        LocalDate today = LocalDate.now(clock);
        if (control.getBusinessDate().isAfter(today)
                || control.getBusinessDate().isBefore(today.minusDays(1))) {
            throw new BusinessException("Business Date is out of sync with Hotel Date; complete Night Audit before posting");
        }
        return control.getBusinessDate();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate closingPostingDate(LocalDate expected) {
        var control = db.lockForPosting();
        if (control == null || !expected.equals(control.getBusinessDate()) || !"CLOSING".equals(control.getState())) {
            throw new BusinessException("Night Audit closing date is no longer available");
        }
        return control.getBusinessDate();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessDateControl lockForFutureAdvance() {
        var control = db.lockExclusive();
        if (control == null || control.getBusinessDate() == null) {
            throw new BusinessException("Business Date is not initialized");
        }
        return control;
    }

    public Clock clock() {
        return clock;
    }
}