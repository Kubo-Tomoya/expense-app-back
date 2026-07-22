package com.example.expenseapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * メール送信を担当するService。
 *
 * 「メールを組み立てて送る」という技術的な処理と、
 * 「パスワード再設定という業務ロジック」（AuthService）を分離するために、
 * あえて別クラスとして切り出している。
 * 将来、請求書の送付メール等（Phase B）でも使い回せるようにする狙いもある
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;

    // メールの送信元として表示するアドレス
    @Value("${spring.mail.username}")
    private String fromAddress;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * パスワード再設定用リンクをメールで送信する
     */
    public void sendPasswordResetEmail(String toAddress, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject("【ExpenseNote】パスワード再設定のご案内");
        message.setText(
            "パスワード再設定のリクエストを受け付けました。\n\n" +
            "以下のリンクから新しいパスワードを設定してください（30分以内に手続きしてください）。\n" +
            resetUrl + "\n\n" +
            "このメールに心当たりがない場合は、このまま何もせず削除してください。"
        );
        mailSender.send(message);
    }
}