package com.example.expenseapp.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRFトークンを、リクエストのたびに強制的に解決（resolve）させるフィルター。
 *
 * Spring SecurityのCSRFトークンは「実際に参照されるまでCookieに書き込まれない」
 * という遅延生成の仕組みになっている。SPA（React）側は明示的にトークンを
 * 参照する処理を書かないため、何もしないと最初のリクエストでCookieが発行されず、
 * 次のPOSTリクエストがCSRFエラーになってしまう。
 * このフィルターで毎回トークンを強制的に読み出すことで、確実にCookieへ反映させる
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            // getToken()を呼ぶこと自体が「参照した」とみなされ、Cookie書き込みのトリガーになる
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}