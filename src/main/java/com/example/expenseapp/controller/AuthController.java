package com.example.expenseapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.expenseapp.dto.request.LoginRequestDto;
import com.example.expenseapp.dto.request.PasswordResetConfirmDto;
import com.example.expenseapp.dto.request.PasswordResetRequestDto;
import com.example.expenseapp.dto.request.RegisterRequestDto;
import com.example.expenseapp.dto.response.UserResponseDto;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.AuthService;

/**
 * 認証関連（登録・ログイン・ログアウト・ログイン状態確認）のHTTPリクエストを受け付けるController
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    // 認証成功後、その状態をHTTPセッションに保存・復元するための仕組み
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, AuthenticationManager authenticationManager) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    // POST /api/auth/register：ユーザー新規登録
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto dto) {
        UserResponseDto user = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * POST /api/auth/login：ログイン
     * 認証に成功したら、その結果をセッションに保存する。
     * これにより、以降のリクエストはブラウザが自動送信するセッションCookieだけで
     * 「ログイン済みかどうか」をサーバー側が判定できるようになる
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @Valid @RequestBody LoginRequestDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {

        // メールアドレス・パスワードを渡して認証を実行（内部でUserDetailsServiceが呼ばれる）
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword());
        Authentication authResult = authenticationManager.authenticate(authRequest);

        // 認証結果をセキュリティコンテキストにセットし、セッションへ保存する
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

	    // authResult.getPrincipal()が返すのはUserPrincipal（UserDetailsのアダプター）なので、
	    // 一度UserPrincipalとして受け取り、getUser()経由で本来のUserを取り出す
	    UserPrincipal principal = (UserPrincipal) authResult.getPrincipal();
	    User user = principal.getUser();
        return ResponseEntity.ok(new UserResponseDto(user.getId(), user.getEmail()));
    }

    // POST /api/auth/logout：ログアウト（セッションを破棄する）
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me：ログイン中ユーザーの情報を取得
     * フロント側が画面初期表示時に呼び出し、「ログイン済みかどうか」を判定するために使う
     */
	 // 未ログイン時、authentication.getPrincipal()はUserPrincipalではなく
	 // "anonymousUser"という文字列を返す仕様になっている（Spring Security標準の挙動）。
	 // これをUserPrincipalに直接キャストしようとするとClassCastExceptionになるため、
	 // principalの型を確認してからキャストするように修正した
	 @GetMapping("/me")
	 public ResponseEntity<UserResponseDto> me() {
	     Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
	     Object principal = authentication.getPrincipal();
	
	     // 未ログイン（匿名ユーザー）の場合、401を返す
	     if (!(principal instanceof UserPrincipal userPrincipal)) {
	         return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	     }
	
	     User user = userPrincipal.getUser();
	     return ResponseEntity.ok(new UserResponseDto(user.getId(), user.getEmail()));
	 }
    
 // POST /api/auth/password-reset/request：パスワード再設定の依頼（ステップ1）
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto dto) {
        authService.requestPasswordReset(dto);
        // メールアドレスの存在有無に関わらず、常に同じレスポンスを返す（情報漏洩防止のため）
        return ResponseEntity.noContent().build();
    }

    // POST /api/auth/password-reset/confirm：パスワード再設定の確定（ステップ2）
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto dto) {
        authService.confirmPasswordReset(dto);
        return ResponseEntity.noContent().build();
    }

}
