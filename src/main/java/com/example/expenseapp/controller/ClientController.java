package com.example.expenseapp.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.expenseapp.dto.request.ClientRequestDto;
import com.example.expenseapp.dto.response.ClientResponseDto;
import com.example.expenseapp.security.UserPrincipal;
import com.example.expenseapp.service.ClientService;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public ResponseEntity<List<ClientResponseDto>> getAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clientService.findAll(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponseDto> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(clientService.findById(principal.getUser(), id));
    }

    @PostMapping
    public ResponseEntity<ClientResponseDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ClientRequestDto dto) {
        ClientResponseDto client = clientService.create(principal.getUser(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody ClientRequestDto dto) {
        return ResponseEntity.ok(clientService.update(principal.getUser(), id, dto));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ClientResponseDto> deactivate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(clientService.deactivate(principal.getUser(), id));
    }

    // 再有効化（F-16の追加要件）。誤って無効化した取引先を戻すための操作
    @PutMapping("/{id}/activate")
    public ResponseEntity<ClientResponseDto> activate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(clientService.activate(principal.getUser(), id));
    }
}