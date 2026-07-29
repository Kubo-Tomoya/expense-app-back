package com.example.expenseapp.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.UserRepository;

/**
 * SpringSecurityが認証処理の中で自動的に呼び出すクラス。
 *
 * ログイン時、SpringSecurityは「入力されたメールアドレス」を使って
 * このクラスのloadUserByUsernameを呼び出し、対応するユーザーをDBから探す。
 * 見つかったユーザーをUserPrincipal（UserDetails）に変換して返すことで、
 * その後のパスワード照合はSpringSecurity側が自動で行ってくれる
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 該当ユーザーが存在しない場合、SpringSecurity標準の例外を投げる。
        // これによりログイン失敗時の挙動（エラーメッセージ等）がフレームワークの標準動作に統一される
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("メールアドレスまたはパスワードが正しくありません"));
        return new UserPrincipal(user);
    }
}