package com.example.expenseapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 請求書の取消リクエスト。
 * 取消理由を必須にしているのは、後から「なぜ番号が取消になっているか」を
 * 説明できるようにするため（連番の欠番を監査時に説明する必要がある）
 */
@Getter
@Setter
public class InvoiceCancelRequestDto {

    @NotBlank(message = "取消理由は必須です")
    @Size(max = 255, message = "取消理由は255文字以内で入力してください")
    private String reason;
}
