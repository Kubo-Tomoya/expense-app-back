package com.example.expenseapp.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.expenseapp.dto.request.BusinessProfileRequestDto;
import com.example.expenseapp.dto.response.BusinessProfileResponseDto;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.BusinessProfileService;

/**
 * 事業者プロフィール関連のHTTPリクエストを受け付けるController。
 * F-14と同様、@AuthenticationPrincipalでログイン中ユーザーを取得し、
 * 常にそのユーザー自身のプロフィールのみを対象とする
 */
@RestController
@RequestMapping("/api/business-profile")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    // GET /api/business-profile：プロフィール取得（未設定でも200・全項目null）
    @GetMapping
    public ResponseEntity<BusinessProfileResponseDto> get(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(businessProfileService.find(principal.getUser()));
    }

    // PUT /api/business-profile：プロフィール登録・更新（Upsert）
    @PutMapping
    public ResponseEntity<BusinessProfileResponseDto> save(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BusinessProfileRequestDto dto) {
        return ResponseEntity.ok(businessProfileService.save(principal.getUser(), dto));
    }
}