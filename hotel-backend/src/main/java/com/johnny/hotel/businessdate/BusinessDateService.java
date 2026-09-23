package com.johnny.hotel.businessdate;
import com.johnny.hotel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
@Service @RequiredArgsConstructor public class BusinessDateService {
 private final BusinessDateMapper db; private final Clock clock;
 @Transactional public void initializeOnce(){var c=db.lockExclusive();if(c==null)throw new BusinessException("Business Date control row is missing");if(c.getBusinessDate()!=null)return;var date=LocalDate.now(clock);var at=LocalDateTime.now(clock);if(db.initialize(date,at)!=1)throw new BusinessException("Business Date initialization failed");if(db.history(date,at)!=1)throw new BusinessException("Business Date initialization history failed");}
 @Transactional(readOnly=true) public BusinessDateControl current(){var c=db.current();if(c==null||c.getBusinessDate()==null)throw new BusinessException("Business Date is not initialized");return c;}
 @Transactional(propagation=Propagation.MANDATORY) public LocalDate postingDate(){var c=db.lockForPosting();if(c==null||c.getBusinessDate()==null||!"OPEN".equals(c.getState()))throw new BusinessException("Business Date is not open for posting");return c.getBusinessDate();}
 @Transactional(propagation=Propagation.MANDATORY) public LocalDate closingPostingDate(LocalDate expected){var c=db.lockForPosting();if(c==null||!expected.equals(c.getBusinessDate())||!"CLOSING".equals(c.getState()))throw new BusinessException("Night Audit closing date is no longer available");return c.getBusinessDate();}
 @Transactional(propagation=Propagation.MANDATORY) public BusinessDateControl lockForFutureAdvance(){var c=db.lockExclusive();if(c==null||c.getBusinessDate()==null)throw new BusinessException("Business Date is not initialized");return c;}
 public Clock clock(){return clock;}
}
