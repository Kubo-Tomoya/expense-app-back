package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.expenseapp.dto.request.RegisterRequestDto;
import com.example.expenseapp.dto.response.UserResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateEmailException;
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

}