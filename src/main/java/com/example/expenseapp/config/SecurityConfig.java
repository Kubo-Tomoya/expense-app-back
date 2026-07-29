package com.example.expenseapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.example.expenseapp.security.CsrfCookieFilter;

/**
 * 認証・認可まわりの方針をまとめて設定するクラス。
 * 「どのURLに認証が必要か」「パスワードの暗号化方式」「セッションの扱い」を宣言する。
 * セッションCookie方式（サーバー側でログイン状態を管理する方式）を採用している。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
     * パスワードのハッシュ化に使うエンコーダー。
     * BCryptは同じパスワードでも毎回異なるハッシュ値を生成する（ソルト付き）ため、
     * 万一DBが漏洩してもパスワードそのものは解読されにくい、事実上の標準アルゴリズム
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * どのURLに認証が必要かを定義するフィルターチェーン
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS設定自体はWebConfig側で一元管理しているため、ここでは有効化のみ指定
            .cors(cors -> {})
	         // 修正後：
	         // CSRFトークンをCookie（XSRF-TOKEN）として発行する。withHttpOnlyFalse()により
	         // JavaScript側からCookieの値を読み取れるようにする
	         // （セッションCookie自体は引き続きHttpOnlyのままなので、認証情報の安全性は損なわれない）
	         .csrf(csrf -> csrf
	             .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
	             // BREACH対策のXOR方式ではなく、生のトークン値をそのまま扱うハンドラーに変更。
	             // フロント側でCookieの値をそのままヘッダーに載せ返すシンプルな方式と合わせるため
	             .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
	         )
	         // CsrfFilterの直後に差し込み、全リクエストでトークンを強制的に解決させる
	         .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            // 必要な時だけセッションを作成する（ログインしていないアクセスで無駄にセッションを作らない）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
            	    // 登録・ログイン等はまだログインしていない人が使うAPIのため認証不要にする
            	    .requestMatchers("/api/auth/**").permitAll()
            	    // Spring Bootが例外発生時に内部転送する専用パス。
            	    // ここを保護対象にしてしまうと、未ログイン状態で発生したエラーの内容が
            	    // 本来のステータスコードではなく401にすり替わってしまうため、認証不要にする
            	    .requestMatchers("/error").permitAll()
            	    // それ以外の/api配下は全て認証必須
            	    .anyRequest().authenticated()
            	)
            // 未認証アクセス時、ログイン画面へのリダイレクトではなく401(Unauthorized)を返す。
            // SPA（React）側でエラーを検知してログイン画面へ遷移させる設計のため
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
    
    /**
     * AuthController#loginから呼び出される認証実行役のBean。
     * 内部でCustomUserDetailsService（ユーザー検索）とPasswordEncoder（パスワード照合）を
     * 自動的に組み合わせて使う設定になっている（SpringBootの自動構成による）
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
