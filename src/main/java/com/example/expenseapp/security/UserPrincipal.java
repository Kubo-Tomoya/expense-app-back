package com.example.expenseapp.security;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.expenseapp.entity.User;

/**
 * SpringSecurityが認証処理の中で扱う「認証済みユーザー」を表現するアダプタークラス。
 *
 * SpringSecurityは独自のUserエンティティを直接は扱えず、
 * 必ずUserDetailsインターフェースを実装したオブジェクトとして認証情報を受け渡しする。
 * このクラスは、アプリ側のUserエンティティをUserDetailsとして
 * SpringSecurityに渡すための「橋渡し役」にあたる。
 *
 * Controller側（AuthController）で認証結果からUserを取り出す際に、
 * このクラス経由でUser本体（id・email等）を取得できるようにしている
 */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    // Controller側でUserの生データ（id等）を取り出すためのアクセサ
    public User getUser() {
        return user;
    }

    // ログインIDとして扱う値。今回はメールアドレスをそのままユーザー名として使う
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // 認証時に照合されるハッシュ化済みパスワード
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    // 権限（ロール）機能は今回のアプリでは使わないため空リストを返す
    // 将来、管理者ロール等が必要になった場合はここで権限リストを返すよう拡張する
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    // 以下4つは「アカウントの有効性」に関する判定メソッド。
    // アカウントロック機能や有効期限管理は現時点で未実装のため、すべて true（常に有効）を返す
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}