package com.example.expenseapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.expenseapp.dto.request.ClientRequestDto;
import com.example.expenseapp.dto.response.ClientResponseDto;
import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.ClientRepository;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientService clientService;

    private User userA;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1);
        userA.setEmail("userA@example.com");
    }

    @Test
    void createは正しい入力で登録でき_is_activeがtrueで作成される() {
        ClientRequestDto dto = new ClientRequestDto();
        dto.setName("株式会社サンプル商事");
        dto.setHonorific("御中");
        dto.setContactPerson("山田太郎");
        dto.setEmail("yamada@sample.co.jp");

        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client c = invocation.getArgument(0);
            c.setId(100);
            return c;
        });

        ClientResponseDto result = clientService.create(userA, dto);

        assertThat(result.getId()).isEqualTo(100);
        assertThat(result.getIsActive()).isTrue();

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(userA);
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void createは敬称のデフォルト値_御中が適用される() {
        ClientRequestDto dto = new ClientRequestDto(); // honorificは初期値のまま
        dto.setName("個人事業主のサンプルさん");

        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = clientService.create(userA, dto);

        assertThat(result.getHonorific()).isEqualTo("御中");
    }

    @Test
    void updateは正しい入力で更新される() {
        Client existing = new Client();
        existing.setId(100);
        existing.setUser(userA);
        existing.setName("旧会社名");
        existing.setHonorific("御中");
        existing.setIsActive(true);

        ClientRequestDto dto = new ClientRequestDto();
        dto.setName("新会社名");
        dto.setHonorific("御中");

        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = clientService.update(userA, 100, dto);

        assertThat(result.getName()).isEqualTo("新会社名");
    }

    @Test
    void updateは他人の取引先は更新できない() {
        ClientRequestDto dto = new ClientRequestDto();
        dto.setName("不正アクセステスト");

        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.update(userA, 100, dto))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(clientRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void deactivateはis_activeがfalseに更新されレコードは削除されない() {
        Client existing = new Client();
        existing.setId(100);
        existing.setUser(userA);
        existing.setIsActive(true);

        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = clientService.deactivate(userA, 100);

        assertThat(result.getIsActive()).isFalse();
        // 論理的な無効化のみで、削除メソッド自体は一切呼ばれないことを確認する
        verify(clientRepository, org.mockito.Mockito.never()).delete(any());
        verify(clientRepository, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void deactivateは他人の取引先は無効化できない() {
        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deactivate(userA, 100))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(clientRepository, org.mockito.Mockito.never()).save(any());
    }

    // F-16 No.15
    @Test
    void activateはis_activeがtrueに戻る() {
        Client existing = new Client();
        existing.setId(100);
        existing.setUser(userA);
        existing.setIsActive(false);

        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = clientService.activate(userA, 100);

        assertThat(result.getIsActive()).isTrue();
        verify(clientRepository).save(any(Client.class));
    }

    // F-16 No.16
    @Test
    void activateは他人の取引先は再有効化できない() {
        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.activate(userA, 100))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(clientRepository, org.mockito.Mockito.never()).save(any());
    }

    // 追加：既に有効な取引先に対しても例外にせず、有効のまま返す（冪等）
    @Test
    void activateは既に有効な取引先でも有効のまま返す() {
        Client existing = new Client();
        existing.setId(100);
        existing.setUser(userA);
        existing.setIsActive(true);

        when(clientRepository.findByIdAndUserId(100, 1)).thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponseDto result = clientService.activate(userA, 100);

        assertThat(result.getIsActive()).isTrue();
    }
}