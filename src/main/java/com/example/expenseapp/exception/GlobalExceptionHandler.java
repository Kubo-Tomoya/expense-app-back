package com.example.expenseapp.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * アプリ全体の例外を一箇所で受け止め、意味の伝わるHTTPステータスコードとJSONに変換するクラス。
 *
 * ResponseEntityExceptionHandlerを継承することで、
 * 「メソッド間違い→405」「リクエストボディ不正→400」といった、
 * Spring自身が標準で持っている適切なエラー判定を壊さずに残しつつ、
 * このアプリ独自の例外（重複登録・認証失敗）だけを追加で処理できるようにしている。
 * 単純に「Exception.class」を全部拾ってしまうと、
 * 上記のようなSpring標準の判定まで巻き込んで一律500にしてしまうため、この継承方式にしている
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // メールアドレス重複登録エラー → 409 Conflict（「既に存在するリソースと衝突している」の意味）
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

    // ログイン時のメールアドレス・パスワード不一致 → 401 Unauthorized
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "メールアドレスまたはパスワードが正しくありません"));
    }

    // 上記2つ以外で、かつSpring標準では拾いきれない「本当に想定外のエラー」だけを500として扱う
    // （405・400等、Spring標準の例外はResponseEntityExceptionHandlerの継承により自動で正しく処理される）
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "サーバーエラーが発生しました"));
    }
    
    // 無効なパスワード再設定トークン → 400 Bad Request
    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidResetToken(InvalidResetTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }
    
    // リソースが見つからない（他人のものを含む）→ 404 Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
    }
    
 // アップロードファイルの形式・サイズ不正 → 400 Bad Request
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFile(InvalidFileException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }
}