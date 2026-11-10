package com.johnny.hotel.nightaudit;

import com.johnny.hotel.exception.BusinessException;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

@Component
public class NightAuditSystemActor {
    private final ActorMapper actors;

    public NightAuditSystemActor(ActorMapper actors) {
        this.actors = actors;
    }

    public long id() {
        Long id = actors.systemNightAuditActor();
        if (id == null) {
            throw new BusinessException("SYSTEM Night Audit actor is unavailable");
        }
        return id;
    }

    @Mapper
    public interface ActorMapper {
        @Select("""
                SELECT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.username = 'system_night_audit'
                  AND u.status = 1
                  AND r.role_code = 'MANAGER'
                LIMIT 1
                """)
        Long systemNightAuditActor();
    }
}
