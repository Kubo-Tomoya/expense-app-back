package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.expenseapp.dto.request.PasswordResetConfirmDto;
import com.example.expenseapp.dto.request.PasswordResetRequestDto;
import com.example.expenseapp.dto.request.RegisterRequestDto;
import com.example.expenseapp.dto.response.UserResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.PasswordResetToken;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateEmailException;
import com.example.expenseapp.exception.InvalidResetTokenException;
import com.example.expenseapp.repository.CategoryRepository;
import com.example.expenseapp.repository.PasswordResetTokenRepository;
import com.example.expenseapp.repository.UserRepository;

/**
 * AuthServiceのService層テスト（F-12：register()関連）。
 *
 * UserRepository・CategoryRepository・PasswordEncoderをMockito化し、
 * DBに一切接続せず「登録という業務ロジックの分岐」だけを検証する。
 * メール送信(MailService)・パスワード再設定関連のRepositoryは
 * register()には使われないが、コンストラクタの都合上Mockとして必要
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private AuthService authService;

    private RegisterRequestDto registerDto;

    @BeforeEach
    void setUp() {
        registerDto = new RegisterRequestDto();
        registerDto.setEmail("newuser@example.com");
        registerDto.setPassword("password123");
    }

    @Test
    void registerは新規メールアドレスで登録できる() {
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        // save()に渡されたUserにIDが採番されたかのように振る舞わせる
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1);
            return user;
        });

        UserResponseDto result = authService.register(registerDto);

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
    }

    @Test
    void registerは登録時にデフォルトカテゴリ5件が自動作成される() {
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1);
            return user;
        });

        authService.register(registerDto);

        // categoryRepository.save()が5回呼ばれた（＝5件のカテゴリが作成された）ことを確認する
        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(5)).save(categoryCaptor.capture());

        assertThat(categoryCaptor.getAllValues())
            .extracting(Category::getName)
            .containsExactly("交通費", "食費", "通信費", "消耗品費", "その他");
    }

    @Test
    void registerはパスワードが平文で保存されない() {
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password-xyz");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(registerDto);

        // save()に渡されたUserのpasswordHashが、平文ではなくエンコード後の値になっていることを確認する
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password-xyz");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void registerは既存メールアドレスでは登録できない() {
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerDto))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("既に登録されています");

        // 重複と判定された時点で処理が中断され、
        // パスワードのハッシュ化やDB保存が一切実行されていないことを確認する
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(any());
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

 // ===== F-13：パスワード再設定 =====

    @Test
    void requestPasswordResetは存在するメールアドレスでトークンが発行される() {
        User existingUser = new User();
        existingUser.setId(1);
        existingUser.setEmail("existing@example.com");

        PasswordResetRequestDto dto = new PasswordResetRequestDto();
        dto.setEmail("existing@example.com");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        authService.requestPasswordReset(dto);

        // トークンがpassword_reset_tokensとして保存されたことを確認する
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUser()).isEqualTo(existingUser);
        assertThat(tokenCaptor.getValue().getToken()).isNotBlank();

        // メール送信メソッドが呼ばれたことを確認する（実際に送信されるかはMailService自体のテスト範囲外）
        verify(mailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("existing@example.com"), any());
    }

    @Test
    void requestPasswordResetは存在しないメールアドレスでも例外が発生しない() {
        PasswordResetRequestDto dto = new PasswordResetRequestDto();
        dto.setEmail("notfound@example.com");

        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // 例外が投げられないことを確認する（メールエニュメレーション対策のため、
        // 存在しないメールアドレスでも呼び出し元には常に正常終了として振る舞う）
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> authService.requestPasswordReset(dto));

        // ユーザーが見つからない以上、トークン発行・メール送信は一切行われないことを確認する
        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void confirmPasswordResetは正しいトークンでパスワードが更新される() {
        User targetUser = new User();
        targetUser.setId(1);
        targetUser.setEmail("target@example.com");
        targetUser.setPasswordHash("old-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setId(1);
        token.setUser(targetUser);
        token.setToken("valid-token");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // まだ期限内
        token.setUsedAt(null); // 未使用

        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("valid-token");
        dto.setNewPassword("newPassword123");

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

        authService.confirmPasswordReset(dto);

        // ユーザーのパスワードが更新されたことを確認する
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("new-hash");

        // トークンが使用済みとして記録されたことを確認する
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void confirmPasswordResetは存在しないトークンは拒否される() {
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("invalid-token");
        dto.setNewPassword("newPassword123");

        when(passwordResetTokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmPasswordReset(dto))
            .isInstanceOf(InvalidResetTokenException.class)
            .hasMessageContaining("トークンが無効です");

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmPasswordResetは使用済みトークンは再利用できない() {
        User targetUser = new User();
        targetUser.setId(1);

        PasswordResetToken usedToken = new PasswordResetToken();
        usedToken.setUser(targetUser);
        usedToken.setToken("used-token");
        usedToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        usedToken.setUsedAt(LocalDateTime.now().minusMinutes(5)); // 既に使用済み

        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("used-token");
        dto.setNewPassword("newPassword123");

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.confirmPasswordReset(dto))
            .isInstanceOf(InvalidResetTokenException.class)
            .hasMessageContaining("既に使用されています");

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmPasswordResetは期限切れトークンは拒否される() {
        User targetUser = new User();
        targetUser.setId(1);

        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setUser(targetUser);
        expiredToken.setToken("expired-token");
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // 1分前に期限切れ
        expiredToken.setUsedAt(null);

        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("expired-token");
        dto.setNewPassword("newPassword123");

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.confirmPasswordReset(dto))
            .isInstanceOf(InvalidResetTokenException.class)
            .hasMessageContaining("有効期限が切れています");

        verify(userRepository, never()).save(any());
    }
}