package com.johnny.hotel.reservation;

import com.johnny.hotel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.util.*;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class ReservationPolicyService {
    private final ReservationPolicyMapper mapper;
    private final ReservationAccess access;
    public record View(ReservationPolicy policy,List<ReservationPolicyBand> bands) {}
    public View active(){access.policyReader();var p=mapper.active();return p==null?null:view(p);}
    public View get(Long id){access.policyReader();var p=mapper.find(id);if(p==null)throw new BusinessException(404,"Policy not found");return view(p);}
    public List<ReservationPolicy> list(){access.policyReader();return mapper.list();}
    private View view(ReservationPolicy p){return new View(p,mapper.bands(p.getId()));}
    public View bound(Long id){var p=mapper.find(id);if(p==null)throw new BusinessException("Bound policy is missing");return view(p);}
    public ReservationPolicy activeForBooking(boolean required){var p=mapper.active();if(required)require(p!=null,"An active reservation policy is required");return p;}
    public BigDecimal percent(Long policyId,int leadDays){
        var bands=bound(policyId).bands();
        return bands.stream().filter(b->leadDays>=b.getMinLeadDays()&&(b.getMaxLeadDays()==null||leadDays<=b.getMaxLeadDays()))
                .findFirst().orElseThrow(()->new BusinessException("Bound policy has no cancellation window")).getRefundPercent();
    }
    private void validate(List<ReservationPolicyRequests.Band> bands){
        require(bands!=null&&!bands.isEmpty(),"Cancellation windows are required");
        require(bands.stream().allMatch(b->b!=null&&b.getMinLeadDays()!=null),"Cancellation window start is required");
        var sorted=bands.stream().sorted(Comparator.comparing(ReservationPolicyRequests.Band::getMinLeadDays)).toList();
        long expected=0;
        for(int i=0;i<sorted.size();i++){
            var b=sorted.get(i);
            require(b!=null&&b.getMinLeadDays()!=null&&b.getMinLeadDays()>=0&&b.getMinLeadDays()==expected,"Cancellation windows must start at day 0 with no gap or overlap");
            var p=money(b.getRefundPercent(),5);
            require(p.signum()>=0&&p.compareTo(new BigDecimal("100.00"))<=0,"Refund percentage must be between 0 and 100");
            require(b.getMaxLeadDays()==null?i==sorted.size()-1:b.getMaxLeadDays()>=b.getMinLeadDays(),"Only the last cancellation window may be open-ended");
            if(b.getMaxLeadDays()!=null)expected=(long)b.getMaxLeadDays()+1;
        }
        require(sorted.get(sorted.size()-1).getMaxLeadDays()==null,"The final cancellation window must be open-ended");
    }
    private void writeBands(Long id,List<ReservationPolicyRequests.Band> bands){
        for(var b:bands)one(mapper.insertBand(ReservationPolicyBand.builder().policyId(id).minLeadDays(b.getMinLeadDays())
                .maxLeadDays(b.getMaxLeadDays()).refundPercent(money(b.getRefundPercent(),5)).build()));
    }
    @Transactional public View create(ReservationPolicyRequests.Save request){var actor=access.owner();require(request!=null&&request.getName()!=null&&!request.getName().isBlank(),"Policy name is required");validate(request.getBands());require(Integer.valueOf(1).equals(mapper.lockVersions()),"Policy version lock is missing");
        var p=ReservationPolicy.builder().versionNo(mapper.nextVersion()).name(request.getName().trim()).status(0).createdBy(actor.id()).build();one(mapper.insert(p));writeBands(p.getId(),request.getBands());return view(mapper.find(p.getId()));}
    @Transactional public View edit(Long id,ReservationPolicyRequests.Save request){access.owner();require(request!=null&&request.getName()!=null&&!request.getName().isBlank(),"Policy name is required");validate(request.getBands());var p=mapper.lock(id);require(p!=null&&p.getStatus()==0,"Only a draft policy may be edited");one(mapper.updateDraft(id,request.getName().trim()));mapper.deleteDraftBands(id);writeBands(id,request.getBands());return view(mapper.find(id));}
    @Transactional public View validate(Long id){access.owner();var p=mapper.lock(id);require(p!=null,"Policy not found");var bands=mapper.bands(id).stream().map(b->ReservationPolicyRequests.Band.builder().minLeadDays(b.getMinLeadDays()).maxLeadDays(b.getMaxLeadDays()).refundPercent(b.getRefundPercent()).build()).toList();validate(bands);return view(p);}
    @Transactional public View activate(Long id){var actor=access.owner();require(Integer.valueOf(1).equals(mapper.lockVersions()),"Policy activation lock is missing");var p=mapper.lock(id);require(p!=null&&p.getStatus()==0,"Only a draft policy may be activated");validate(id);mapper.disableActive();one(mapper.activate(id,actor.id()));return view(mapper.find(id));}
    @Transactional public void disable(){access.owner();require(Integer.valueOf(1).equals(mapper.lockVersions()),"Policy activation lock is missing");mapper.disableActive();}
}
