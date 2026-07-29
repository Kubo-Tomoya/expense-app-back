package com.example.expenseapp.exception;

/**
 * 指定されたIDのリソース（経費・カテゴリ等）が存在しない場合に投げる専用例外。
 *
 * 「そもそも存在しない」場合と「他人の所有物で自分には見せられない」場合を、
 * あえて同じ例外・同じ404で扱っている。
 * この2つを区別して返してしまうと、存在有無から他人のIDを推測する
 * 手がかりを与えてしまうため（F-14実装時の設計判断を踏襲）
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}