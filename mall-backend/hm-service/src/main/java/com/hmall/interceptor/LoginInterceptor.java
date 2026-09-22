package com.hmall.interceptor;

import com.hmall.common.utils.UserContext;
import com.hmall.utils.AuthorizationContext;
import com.hmall.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtTool jwtTool;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 商品查询公开，但商品写接口必须继续校验登录态
        if ("GET".equalsIgnoreCase(request.getMethod()) && isItemPath(request.getRequestURI())) {
            return true;
        }
        // 1.获取请求头中的 token
        String token = request.getHeader("authorization");
        // 2.校验token
        Long userId = jwtTool.parseToken(token);
        // 3.存入上下文
        UserContext.setUser(userId);
        AuthorizationContext.set(token);
        // 4.放行
        return true;
    }

    private boolean isItemPath(String uri) {
        return "/items".equals(uri) || (uri != null && uri.startsWith("/items/"));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理用户
        UserContext.removeUser();
        AuthorizationContext.remove();
    }
}
