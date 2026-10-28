package com.shop.api.security;

import com.shop.api.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 토큰을 검증한다. 실패해도 체인을 계속 진행하며(공개 경로는 익명 통과),
 * 실패 원인은 request attribute 로 남겨 EntryPoint 가 코드를 결정하게 한다. DB 조회는 하지 않는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            if (!header.startsWith(BEARER) || header.substring(BEARER.length()).isBlank()) {
                fail(request, ErrorCode.INVALID_TOKEN);
            } else {
                JwtTokenProvider.TokenValidation result = tokenProvider.validate(header.substring(BEARER.length()).trim());
                if (result.isValid()) {
                    AuthPrincipal p = result.principal();
                    var auth = new UsernamePasswordAuthenticationToken(
                            p, null, List.of(new SimpleGrantedAuthority(p.role())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    fail(request, result.error());
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static void fail(HttpServletRequest request, ErrorCode code) {
        SecurityContextHolder.clearContext();
        request.setAttribute(ERROR_ATTRIBUTE, code);
    }
}
