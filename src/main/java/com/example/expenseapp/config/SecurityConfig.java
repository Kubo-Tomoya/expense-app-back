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
            // 本来セッションCookie方式ではCSRF対策が必須だが、
            // フロント側にCSRFトークンの送受信を実装するコストを避けるため、
            // 開発優先度を考慮して現時点では無効化している。
            // 【重要・要対応】本番リリース前には必ずCSRFトークン対応を追加すること
            .csrf(csrf -> csrf.disable())
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
