package com.example.expenseapp.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.expenseapp.dto.request.BusinessProfileRequestDto;
import com.example.expenseapp.dto.response.BusinessProfileResponseDto;
import com.example.expenseapp.entity.BusinessProfile;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.BusinessProfileRepository;

/**
 * 事業者プロフィールの業務ロジックを担当するService。
 * 「登録」と「更新」を区別せず、常に同じメソッド（save）でUpsertする方針にしている。
 * 初回か2回目以降かをフロント側に判定させる必要がなく、シンプルなAPI設計になる
 */
@Service
@Transactional
public class BusinessProfileService {

    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfileService(BusinessProfileRepository businessProfileRepository) {
        this.businessProfileRepository = businessProfileRepository;
    }

    /**
     * ログイン中ユーザーのプロフィールを取得する。
     * レコードが無い（＝まだ未設定）場合も例外を投げず、
     * 全項目nullのDTOを返すことで「空の設定画面」として扱えるようにする
     */
    public BusinessProfileResponseDto find(User user) {
        return businessProfileRepository.findByUserId(user.getId())
            .map(this::toResponseDto)
            .orElseGet(() -> new BusinessProfileResponseDto(null, null, null, null));
    }

    /**
     * プロフィールの登録・更新（Upsert）。
     * 既存レコードがあれば内容を上書き、無ければ新規作成する
     */
    public BusinessProfileResponseDto save(User user, BusinessProfileRequestDto dto) {
        BusinessProfile profile = businessProfileRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                // 初回登録時のみ実行される分岐。新規Entityを作りuserを紐付ける
                BusinessProfile newProfile = new BusinessProfile();
                newProfile.setUser(user);
                newProfile.setCreatedAt(LocalDateTime.now());
                return newProfile;
            });

        profile.setBusinessName(dto.getBusinessName());
        profile.setOwnerName(dto.getOwnerName());
        profile.setAddress(dto.getAddress());
        profile.setInvoiceRegistrationNumber(dto.getInvoiceRegistrationNumber());
        profile.setUpdatedAt(LocalDateTime.now());

        BusinessProfile saved = businessProfileRepository.save(profile);
        return toResponseDto(saved);
    }

    private BusinessProfileResponseDto toResponseDto(BusinessProfile profile) {
        return new BusinessProfileResponseDto(
            profile.getBusinessName(),
            profile.getOwnerName(),
            profile.getAddress(),
            profile.getInvoiceRegistrationNumber()
        );
    }
}