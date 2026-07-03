package com.example.expenseapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.expenseapp.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer>{
	
	// display_order の昇順で全件取得
    List<Category> findAllByOrderByDisplayOrderAsc();

}
