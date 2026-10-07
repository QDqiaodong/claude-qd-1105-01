package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Book;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /**
     * 按 ISBN 查全部档案：旧表数据可能没有唯一约束，同一 ISBN 可能对应多本，
     * 这里故意返回 List —— 调用方判重/判空，而不是让 JPA 抛 NonUniqueResultException。
     */
    List<Book> findAllByIsbn(String isbn);

    /**
     * 已登记 ISBN 中的重复值（空串 / NULL 视为「未登记」，不参与判重）。
     * 列表页据此给出明确提示，避免用户对着重复档案改错书。
     */
    @Query("SELECT b.isbn FROM Book b WHERE b.isbn IS NOT NULL AND b.isbn <> '' GROUP BY b.isbn HAVING COUNT(b) > 1")
    List<String> findDuplicateIsbns();

    /**
     * 入库加库存时行级加锁读取图书，串行更新 stock，避免并发入库丢失库存增量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
