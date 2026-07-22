package com.example.expenseapp.service;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.expenseapp.dto.request.PasswordResetConfirmDto;
import com.example.expenseapp.dto.request.PasswordResetRequestDto;
import com.example.expenseapp.dto.request.RegisterRequestDto;
import com.example.expenseapp.dto.response.UserResponseDto;
import com.example.expenseapp.entity.PasswordResetToken;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateEmailException;
import com.example.expenseapp.exception.InvalidResetTokenException;
import com.example.expenseapp.repository.PasswordResetTokenRepository;
import com.example.expenseapp.repository.UserRepository;

/**
 * 認証関連の業務ロジックを担当するService。
 * 現時点ではユーザー登録のみ。ログイン処理自体はSpring Securityの認証機構に任せている
 */
@Service
@Transactional
public class AuthService {
	
	private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // コンストラクタに repository と mailService を追加する必要がある
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    @Value("${app.frontend-url}")
    private String frontendUrl;
    

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository passwordResetTokenRepository,
            MailService mailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailService = mailService;
    }
    /**
     * ユーザー新規登録。
     * メールアドレスの重複チェック→パスワードのハッシュ化→保存、の順で行う
     */
    public UserResponseDto register(RegisterRequestDto dto) {
        // DBのUNIQUE制約に任せる方法もあるが、
        // アプリ側で先にチェックすることで分かりやすいエラーメッセージを返せるようにしている
	    	// 変更後：汎用的な例外ではなく、専用の例外クラスを投げるようにする
	    	if (userRepository.existsByEmail(dto.getEmail())) {
	    	    throw new DuplicateEmailException("このメールアドレスは既に登録されています");
	    	}

        User user = new User();
        user.setEmail(dto.getEmail());
        // 平文パスワードはここで即座にハッシュ化し、以降平文は保持しない
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        // TODO（Step3・F-14で対応）：
        // このままだと新規登録者はカテゴリを1件も持たない状態になる。
        // ここでデフォルトカテゴリ5件をこのユーザー用にコピーする処理を追加する必要がある
        return new UserResponseDto(saved.getId(), saved.getEmail());
    }
    

    /**
     * パスワード再設定の依頼（ステップ1）。
     *
     * セキュリティ上の重要な設計判断：
     * 「このメールアドレスは登録されていません」という結果を返すと、
     * 第三者が「このメールアドレスは経費精算アプリに登録済みだ」と推測できてしまう
     * （メールアドレス総当たりによる情報漏洩＝メールエニュメレーション攻撃）。
     * これを防ぐため、メールアドレスが見つかっても見つからなくても、
     * 呼び出し元には常に同じ「成功扱い」のレスポンスを返す
     */
    public void requestPasswordReset(PasswordResetRequestDto dto) {
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            // 使い捨てトークンを発行する。UUIDは推測困難な文字列を生成できるため採用
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(token);
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30)); // 30分間のみ有効
            resetToken.setCreatedAt(LocalDateTime.now());
            passwordResetTokenRepository.save(resetToken);

            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            mailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        });
        // ユーザーが見つからなかった場合も、ここで何もせず正常終了する（意図的な仕様）
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

        // 使用済みとして記録し、同じトークンの再利用を防ぐ
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
    }


}
