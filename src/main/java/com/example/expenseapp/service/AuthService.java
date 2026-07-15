package com.example.expenseapp.service;

import java.time.LocalDateTime;

import jakarta.transaction.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.expenseapp.dto.request.RegisterRequestDto;
import com.example.expenseapp.dto.response.UserResponseDto;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateEmailException;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

}
