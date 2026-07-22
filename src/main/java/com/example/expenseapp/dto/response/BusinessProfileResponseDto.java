package com.example.expenseapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事業者プロフィール取得（GET /api/business-profile）のレスポンス形式。
 *
 * まだ一度もプロフィールを設定していないユーザーの場合、
 * 404ではなく「全項目null」の200を返す設計にしている。
 * 理由：これは「見つからない＝異常」ではなく「未入力＝これから入力する平常状態」であり、
 * F-14で経費・カテゴリを404にした（＝他人のデータを見せない）判断とは性質が異なるため
 */
@Getter
@AllArgsConstructor
public class BusinessProfileResponseDto {
    private String businessName;
    private String ownerName;
    private String address;
    private String invoiceRegistrationNumber;
}