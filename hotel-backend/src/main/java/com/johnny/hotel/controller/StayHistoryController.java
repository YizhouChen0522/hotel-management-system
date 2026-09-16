package com.johnny.hotel.controller;

import com.johnny.hotel.common.Result;
import com.johnny.hotel.service.StayHistoryService;
import com.johnny.hotel.service.SysUserService;
import com.johnny.hotel.vo.StayHistoryVO;
import com.johnny.hotel.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stay-history")
@RequiredArgsConstructor
public class StayHistoryController {

    private final StayHistoryService stayHistoryService;
    private final SysUserService sysUserService;

    @GetMapping("/me")
    public Result<com.johnny.hotel.pagination.PageResult<StayHistoryVO>> getMyStayHistory(Authentication authentication,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize) {

        Long currentUserId = getCurrentUserId(authentication);

        return Result.success(
                stayHistoryService.pageByUser(currentUserId,page,pageSize)
        );
    }

    @GetMapping("/me/folios/{folioId}")
    public Result<com.johnny.hotel.pagination.PageResult<StayHistoryVO>> getMyStayHistoryByFolio(
            @PathVariable Long folioId,
            Authentication authentication,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer pageSize) {

        Long currentUserId = getCurrentUserId(authentication);

        return Result.success(
                stayHistoryService.pageByFolioUser(folioId,currentUserId,page,pageSize)
        );
    }

    private Long getCurrentUserId(Authentication authentication) {

        String email = authentication.getName();

        UserVO currentUser =
                sysUserService.getUserByEmail(email);

        return currentUser.getId();
    }
}
