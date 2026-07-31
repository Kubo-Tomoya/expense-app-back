package com.example.expenseapp.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.expenseapp.dto.request.CategoryOrderRequestDto;
import com.example.expenseapp.dto.request.CategoryRequestDto;
import com.example.expenseapp.dto.response.CategoryResponseDto;
import com.example.expenseapp.entity.Category;
import com.example.expenseapp.entity.User;
import com.example.expenseapp.exception.DuplicateCategoryException;
import com.example.expenseapp.exception.ResourceNotFoundException;
import com.example.expenseapp.repository.CategoryRepository;

@Service
@Transactional
public class CategoryService {

	private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * カテゴリ一覧取得。
     *
     * is_active を問わず全件返し、「有効のみ／無効を含む全件」の絞り込みはフロント側で行う
     * （F-16の取引先一覧と同じ方針。件数が少なく、追加リクエストを増やす意味がないため）。
     * 表示順は display_order に従う
     */
    public List<CategoryResponseDto> findAll(User user) {
        Map<Integer, Long> usageCounts = usageCountMap(user);
        return categoryRepository.findAllByUserIdOrderByDisplayOrderAsc(user.getId()).stream()
            .map(category -> toResponseDto(category, usageCounts.getOrDefault(category.getId(), 0L)))
            .collect(Collectors.toList());
    }

    /**
     * 新規登録。display_orderは既存の最大値+1で自動採番する
     */
    public CategoryResponseDto create(User user, CategoryRequestDto dto) {
        String name = normalizeName(dto.getName());
        assertNameNotDuplicated(user, name, null);

        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setTaxFormCategory(normalizeTaxFormCategory(dto.getTaxFormCategory()));
        category.setDisplayOrder(categoryRepository.findMaxDisplayOrderByUserId(user.getId()) + 1);
        category.setIsActive(true); // 新規登録時は必ず有効な状態で作成する
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        Category saved = categoryRepository.save(category);
        return toResponseDto(saved, 0L);
    }

    /**
     * 更新（カテゴリ名・勘定科目）。
     * display_order は並び替え（updateOrder）でのみ変更する
     */
    public CategoryResponseDto update(User user, Integer id, CategoryRequestDto dto) {
        Category category = findOwnCategory(user, id);
        String name = normalizeName(dto.getName());
        assertNameNotDuplicated(user, name, id);

        category.setName(name);
        category.setTaxFormCategory(normalizeTaxFormCategory(dto.getTaxFormCategory()));
        category.setUpdatedAt(LocalDateTime.now());
        Category saved = categoryRepository.save(category);
        return toResponseDto(saved, usageCountOf(user, id));
    }

    /**
     * 並び替え。受け取った配列の順に display_order を1から振り直す。
     *
     * 1件でも他人のカテゴリIDが混ざっていた場合は例外とし、@Transactionalにより
     * 1件も更新されない状態へロールバックする。部分的に並び順が変わる状態を作らないため
     */
    public List<CategoryResponseDto> updateOrder(User user, CategoryOrderRequestDto dto) {
        int order = 1;
        for (Integer id : dto.getCategoryIds()) {
            Category category = findOwnCategory(user, id);
            category.setDisplayOrder(order++);
            category.setUpdatedAt(LocalDateTime.now());
            categoryRepository.save(category);
        }
        return findAll(user);
    }

    /**
     * 無効化。使用中（そのカテゴリを使う経費がある）でも無効化できる。
     *
     * 過去の経費のカテゴリ表示・集計は変わらない。
     * 無効化は「今後の選択肢から外す」操作であり、実績を消す操作ではない
     */
    public CategoryResponseDto deactivate(User user, Integer id) {
        Category category = findOwnCategory(user, id);
        category.setIsActive(false);
        category.setUpdatedAt(LocalDateTime.now());
        Category saved = categoryRepository.save(category);
        return toResponseDto(saved, usageCountOf(user, id));
    }

    /**
     * 再有効化。既に有効な場合も例外とせず、有効のまま返す（F-16のactivateと同じ冪等な扱い）
     */
    public CategoryResponseDto activate(User user, Integer id) {
        Category category = findOwnCategory(user, id);
        category.setIsActive(true);
        category.setUpdatedAt(LocalDateTime.now());
        Category saved = categoryRepository.save(category);
        return toResponseDto(saved, usageCountOf(user, id));
    }

    // --- 内部処理 -----------------------------------------------------------

    private Category findOwnCategory(User user, Integer id) {
        return categoryRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("カテゴリが見つかりません。ID: " + id));
    }

    /** 前後の空白を除去する。「 交通費」と「交通費」が別カテゴリとして並ぶのを防ぐ */
    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    /** 空文字はフォームで一度入力してから消した場合に送られてくるためnullに寄せる */
    private String normalizeTaxFormCategory(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 同一ユーザー内での名前の重複を確認する。
     * excludeIdには更新対象のIDを渡し、自分自身との衝突を除外する
     */
    private void assertNameNotDuplicated(User user, String name, Integer excludeId) {
        Optional<Category> existing = categoryRepository.findByUserIdAndName(user.getId(), name);
        if (existing.isEmpty()) return;
        if (excludeId != null && existing.get().getId().equals(excludeId)) return;
        throw new DuplicateCategoryException("同じ名前のカテゴリが既に登録されています：" + name);
    }

    private Map<Integer, Long> usageCountMap(User user) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : categoryRepository.countExpensesByCategory(user.getId())) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Long usageCountOf(User user, Integer categoryId) {
        return usageCountMap(user).getOrDefault(categoryId, 0L);
    }

    // Entity → ResponseDto 変換
    private CategoryResponseDto toResponseDto(Category category, Long usageCount) {
        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDisplayOrder(category.getDisplayOrder());
        dto.setTaxFormCategory(category.getTaxFormCategory());
        dto.setIsActive(category.getIsActive());
        dto.setUsageCount(usageCount);
        return dto;
    }
}
