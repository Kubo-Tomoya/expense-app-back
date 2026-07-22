package com.example.expenseapp.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 認証関連の業務ロジックを担当するService。
 * ユーザー登録・パスワード再設定を扱う
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final CategoryRepository categoryRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 新規ユーザーに配布する初期カテゴリ。要件定義書F-07の5件と一致させている
    private static final List<String> DEFAULT_CATEGORY_NAMES =
        List.of("交通費", "食費", "通信費", "消耗品費", "その他");

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository passwordResetTokenRepository,
            MailService mailService,
            CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailService = mailService;
        this.categoryRepository = categoryRepository;
    }

    /**
     * ユーザー新規登録。
     * メールアドレスの重複チェック→パスワードのハッシュ化→保存、
     * →デフォルトカテゴリ5件の自動コピー、の順で行う
     */
    public UserResponseDto register(RegisterRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateEmailException("このメールアドレスは既に登録されています");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        // 新規登録者にデフォルトカテゴリ5件を自動コピーする。
        // display_order・created_at・updated_atはいずれもNOT NULL制約があるため、
        // 全て明示的にセットする必要がある（過去に2回、この列のセット漏れで
        // NOT NULL制約違反エラーが発生した実績があるため、特に注意すること）
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < DEFAULT_CATEGORY_NAMES.size(); i++) {
            Category category = new Category();
            category.setUser(saved);
            category.setName(DEFAULT_CATEGORY_NAMES.get(i));
            category.setDisplayOrder(i);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryRepository.save(category);
        }

        return new UserResponseDto(saved.getId(), saved.getEmail());
    }

    /**
     * パスワード再設定の依頼（ステップ1）。
     *
     * セキュリティ上の重要な設計判断：
     * メールアドレスの存在有無に関わらず、常に同じ「成功扱い」のレスポンスを
     * 返すため、ユーザーが見つかった場合のみトークン発行・メール送信を行う
     */
    public void requestPasswordReset(PasswordResetRequestDto dto) {
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(token);
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            resetToken.setCreatedAt(LocalDateTime.now());
            passwordResetTokenRepository.save(resetToken);

            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            mailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        });
    }

    /**
     * パスワード再設定の確定（ステップ2）。
     * トークンの有効性（存在する・期限切れでない・未使用）を確認した上でパスワードを更新する
     */
    public void confirmPasswordReset(PasswordResetConfirmDto dto) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(dto.getToken())
            .orElseThrow(() -> new InvalidResetTokenException("トークンが無効です"));

        if (resetToken.getUsedAt() != null) {
            throw new InvalidResetTokenException("このリンクは既に使用されています");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException("トークンの有効期限が切れています");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }
}