package com.johnny.hotel.service.impl;
import com.johnny.hotel.entity.*;
import com.johnny.hotel.enums.TurnoverTaskStatus;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.RoomTurnoverTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service @RequiredArgsConstructor
public class RoomTurnoverTaskServiceImpl implements RoomTurnoverTaskService {
    private final RoomTurnoverTaskMapper tasks;
    private final BookingMapper bookings;
    private final BookingRoomAssignmentMapper assignments;
    private final RoomMapper rooms;
    private final SysUserMapper users;
    private final SysRoleMapper roles;
    private final SysAuditLogMapper audits;
    private final Clock clock;
    private record Actor(Long id,boolean manager) {}
    private Actor actor(){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||!(auth.getDetails() instanceof Long id))throw denied();
        var user=users.selectById(id);var r=roles.selectRolesByUserId(id).stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        if(user==null||!Integer.valueOf(1).equals(user.getStatus())||r.contains("HR_ADMIN")||r.contains("CUSTOMER"))throw denied();
        boolean manager=r.stream().anyMatch(Set.of("MANAGER","OWNER","SUPER_ADMIN")::contains);
        if(!manager&&!r.contains("STAFF"))throw denied();return new Actor(id,manager);
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Turnover task access denied");}
    private void conflict(boolean valid){if(!valid)throw new BusinessException(409,"Task state changed or transition is not allowed");}
    private RoomTurnoverTask found(RoomTurnoverTask task){if(task==null)throw new BusinessException(404,"Turnover task does not exist");return task;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public RoomTurnoverTask createForClosedAssignment(Long id){
        // Immutable identity lookup; lifecycle lock order remains Booking -> Room -> Assignment -> Task.
        var identity=tasks.assignmentIdentity(id);require(identity!=null,"Assignment does not exist");
        var booking=bookings.selectByIdForUpdate(identity.getBookingId());
        var room=rooms.selectByIdForUpdate(identity.getRoomId());
        var assignment=assignments.selectById(id);
        require(booking!=null&&room!=null&&assignment!=null&&assignment.getEndTime()!=null&&!assignment.getEndTime().isBefore(assignment.getStartTime())
                &&Set.of("CHECK_IN","ROOM_CHANGE").contains(assignment.getAssignmentType()),"A closed actual stay assignment is required");
        var existing=tasks.byAssignment(id);
        if(existing!=null){require(existing.getBookingId().equals(booking.getId())&&existing.getRoomId().equals(room.getId()),"Task assignment identity mismatch");return existing;}
        require(Set.of(2,3).contains(booking.getStatus())&&room!=null&&room.getStatus()==3,"Closed stay must leave its room in maintenance");
        require(assignments.selectByBookingId(booking.getId()).stream().anyMatch(a->"CHECK_IN".equals(a.getAssignmentType())),"Actual check-in history is missing");
        var task=RoomTurnoverTask.builder().bookingId(booking.getId()).roomId(room.getId()).assignmentId(id).build();
        one(tasks.insert(task));return found(tasks.find(task.getId()));
    }
    @Override public List<RoomTurnoverTask> list(Integer status,Long roomId,Integer page,Integer size){
        actor();if(status!=null)require(status>=0&&status<=2,"Invalid turnover status");require(roomId==null||roomId>0,"Invalid room id");
        int p=page==null?1:page,s=size==null?50:size;require(p>0&&s>0&&s<=100&&((long)p-1)*s<=Integer.MAX_VALUE,"Invalid pagination");return tasks.page(status,roomId,(p-1)*s,s);
    }
    @Override public RoomTurnoverTask get(Long id){actor();return found(tasks.find(id));}
    @Override @Transactional public RoomTurnoverTask accept(Long id){
        var actor=actor();var task=found(tasks.lock(id));conflict(task.getStatus()==TurnoverTaskStatus.PENDING.getCode());
        conflict(tasks.accept(id,actor.id(),LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS))==1);audit(actor.id(),task,"ACCEPT_TURNOVER_TASK");return found(tasks.find(id));
    }
    @Override @Transactional public RoomTurnoverTask complete(Long id,String note){
        var actor=actor();require(note==null||note.length()<=500,"Note must be at most 500 characters");var task=found(tasks.lock(id));
        conflict(task.getStatus()!=TurnoverTaskStatus.COMPLETED.getCode());
        if(!actor.manager()&&!(task.getStatus()==TurnoverTaskStatus.ACCEPTED.getCode()&&actor.id().equals(task.getAcceptedBy())))throw denied();
        conflict(task.getStatus()==0||task.getStatus()==1);
        conflict(tasks.complete(id,task.getStatus(),actor.id(),LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS),note==null||note.isBlank()?null:note.trim())==1);
        audit(actor.id(),task,"COMPLETE_TURNOVER_TASK");return found(tasks.find(id));
    }
    private void audit(Long actor,RoomTurnoverTask task,String action){one(audits.insert(SysAuditLog.builder().operatorId(actor).action(action).detail("Turnover task "+task.getId()+", room "+task.getRoomId()+", assignment "+task.getAssignmentId()).build()));}
}
