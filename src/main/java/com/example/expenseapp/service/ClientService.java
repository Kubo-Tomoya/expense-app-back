package com.example.expenseapp.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.expenseapp.dto.request.ClientRequestDto;
import com.example.expenseapp.dto.response.ClientResponseDto;
import com.example.expenseapp.entity.Client;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.ClientRepository;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    // 取引先一覧取得。F-14と同じくuser_idによる絞り込みのみRepositoryに任せ、
    // キーワード検索・有効/無効の絞り込みはフロント側（F-10と同じ設計方針）で行う想定
    public List<ClientResponseDto> findAll(User user) {
        return clientRepository.findAllByUserIdOrderByName(user.getId()).stream()
            .map(this::toResponseDto)
            .collect(Collectors.toList());
    }

    // 取引先1件取得（編集画面の初期値セット用）
    public ClientResponseDto findById(User user, Integer id) {
        Client client = clientRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("取引先が見つかりません。ID: " + id));
        return toResponseDto(client);
    }

    // 新規登録
    public ClientResponseDto create(User user, ClientRequestDto dto) {
        Client client = new Client();
        client.setUser(user);
        applyDto(client, dto);
        client.setIsActive(true); // 新規登録時は必ず有効な状態で作成する
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        Client saved = clientRepository.save(client);
        return toResponseDto(saved);
    }

    // 更新
    public ClientResponseDto update(User user, Integer id, ClientRequestDto dto) {
        Client client = clientRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("取引先が見つかりません。ID: " + id));
        applyDto(client, dto);
        client.setUpdatedAt(LocalDateTime.now());
        Client saved = clientRepository.save(client);
        return toResponseDto(saved);
    }

    /**
     * 無効化（論理的な状態変更）。
     * 経費のdelete()（物理的なdeleted_atセット）とは異なり、
     * is_activeをfalseにするだけで、レコード自体は残り続ける。
     * 過去の請求書（F-17）との整合性を保つため、物理削除・deleted_atどちらの方式も採用しない
     */
    public ClientResponseDto deactivate(User user, Integer id) {
        Client client = clientRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("取引先が見つかりません。ID: " + id));
        client.setIsActive(false);
        client.setUpdatedAt(LocalDateTime.now());
        Client saved = clientRepository.save(client);
        return toResponseDto(saved);
    }

    private void applyDto(Client client, ClientRequestDto dto) {
        client.setName(dto.getName());
        client.setHonorific(dto.getHonorific());
        client.setContactPerson(dto.getContactPerson());
        client.setAddress(dto.getAddress());
        client.setEmail(dto.getEmail());
        client.setPhone(dto.getPhone());
        client.setMemo(dto.getMemo());
    }

    private ClientResponseDto toResponseDto(Client client) {
        return new ClientResponseDto(
            client.getId(),
            client.getName(),
            client.getHonorific(),
            client.getContactPerson(),
            client.getAddress(),
            client.getEmail(),
            client.getPhone(),
            client.getMemo(),
            client.getIsActive(),
            client.getCreatedAt()
        );
    }
}