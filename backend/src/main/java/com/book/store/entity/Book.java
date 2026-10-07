package com.book.store.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // 旧图书数据可能没有 ISBN：允许 NULL（未登记）；唯一性由应用层校验，
    // 数据库层面故意不加 UNIQUE，以兼容已存在重复 / 空白 ISBN 的旧表数据。
    @Column(name = "isbn", length = 32)
    public String isbn;

    @Column(name = "title", nullable = false, length = 128)
    public String title;

    @Column(name = "author", length = 64)
    public String author;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    public BigDecimal price;

    @Column(name = "stock", nullable = false)
    public Integer stock;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
