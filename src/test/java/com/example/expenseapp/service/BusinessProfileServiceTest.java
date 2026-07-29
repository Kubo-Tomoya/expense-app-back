package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.request.BusinessProfileRequestDto;
import com.example.expenseapp.dto.response.BusinessProfileResponseDto;
import com.example.expenseapp.entity.BusinessProfile;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.repository.BusinessProfileRepository;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @InjectMocks
    private BusinessProfileService businessProfileService;

    private User userA;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");
    }

    @Test
    void findは未設定時は全項目nullを返す() {
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());

        BusinessProfileResponseDto result = businessProfileService.find(userA);

        assertThat(result.getBusinessName()).isNull();
        assertThat(result.getOwnerName()).isNull();
        assertThat(result.getAddress()).isNull();
        assertThat(result.getInvoiceRegistrationNumber()).isNull();
    }

    @Test
    void saveは初回登録できる() {
        BusinessProfileRequestDto dto = new BusinessProfileRequestDto();
        dto.setBusinessName("〇〇デザイン事務所");
        dto.setOwnerName("久保友哉");
        dto.setAddress("東京都渋谷区〇〇1-2-3");
        dto.setInvoiceRegistrationNumber("T1234567890123");

        // 既存レコードが無いことをMock化する（初回登録のケース）
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.empty());
        when(businessProfileRepository.save(any(BusinessProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfileResponseDto result = businessProfileService.save(userA, dto);

        assertThat(result.getOwnerName()).isEqualTo("久保友哉");

        // 新規作成のため、userが正しく紐付けられていることを確認する
        ArgumentCaptor<BusinessProfile> captor = ArgumentCaptor.forClass(BusinessProfile.class);
        verify(businessProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(userA);
    }

    @Test
    void saveは2回目以降は更新される() {
        BusinessProfile existing = new BusinessProfile();
        existing.setId(100);
        existing.setUser(userA);
        existing.setBusinessName("旧屋号");
        existing.setOwnerName("久保友哉");
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));

        BusinessProfileRequestDto dto = new BusinessProfileRequestDto();
        dto.setBusinessName("新屋号");
        dto.setOwnerName("久保友哉");
        dto.setAddress("東京都渋谷区〇〇1-2-3");
        dto.setInvoiceRegistrationNumber("T1234567890123");

        // 既存レコードが1件存在する状況をMock化する（更新のケース）
        when(businessProfileRepository.findByUserId(1)).thenReturn(Optional.of(existing));
        when(businessProfileRepository.save(any(BusinessProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfileResponseDto result = businessProfileService.save(userA, dto);

        assertThat(result.getBusinessName()).isEqualTo("新屋号");

        // save()が「既存のEntity（id=100）」に対して呼ばれ、
        // 新規レコードが作られていないことを確認する
        ArgumentCaptor<BusinessProfile> captor = ArgumentCaptor.forClass(BusinessProfile.class);
        verify(businessProfileRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(100);
    }
}