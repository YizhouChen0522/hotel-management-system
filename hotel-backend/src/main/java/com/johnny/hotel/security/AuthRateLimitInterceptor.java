package com.johnny.hotel.security;
import com.johnny.hotel.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
import tools.jackson.databind.ObjectMapper;

@Component @RequiredArgsConstructor
public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter limiter;
    private final ObjectMapper mapper;
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception {
        if(!"POST".equals(request.getMethod()))return true;
        // Use the resolved MVC route, so percent-encoded path characters cannot bypass the limiter.
        String path=String.valueOf(request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        String operation=switch(path){case "/api/auth/login"->"login";case "/api/auth/register/customer","/api/auth/register/employee"->"register";default->null;};
        if(operation==null)return true;
        AuthRateLimiter.Decision decision;
        try {decision=limiter.acquire(operation,request.getRemoteAddr());}
        catch(RuntimeException e){return reject(response,503,"Authentication temporarily unavailable; retry shortly",1);}
        if(!decision.allowed())return reject(response,429,"Too many authentication requests",decision.retryAfterSeconds());
        return true; // Always continues the existing validation/authentication; never authenticates a caller.
    }
    private boolean reject(HttpServletResponse response,int status,String message,long retry)throws Exception {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Retry-After",Long.toString(retry));
        response.getWriter().write(mapper.writeValueAsString(Result.error(status,message)));return false;
    }
}
