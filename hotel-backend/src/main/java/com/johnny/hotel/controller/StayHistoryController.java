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
    public Result<List<StayHistoryVO>> getMyStayHistory(
            Authentication authentication) {

        Long currentUserId = getCurrentUserId(authentication);

        return Result.success(
                stayHistoryService.getByUserId(currentUserId)
        );
    }

    @GetMapping("/me/folios/{folioId}")
    public Result<List<StayHistoryVO>> getMyStayHistoryByFolio(
            @PathVariable Long folioId,
            Authentication authentication) {

        Long currentUserId = getCurrentUserId(authentication);

        return Result.success(
                stayHistoryService.getByFolioIdAndUserId(
                        folioId,
                        currentUserId
                )
        );
    }

    private Long getCurrentUserId(Authentication authentication) {

        String email = authentication.getName();

        UserVO currentUser =
                sysUserService.getUserByEmail(email);

        return currentUser.getId();
    }
}