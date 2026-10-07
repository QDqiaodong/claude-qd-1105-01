package com.book.store.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Book;
import com.book.store.repository.BookRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class BookService {

    /** 字典类场景（下拉选项）一次拉全量的上限 */
    private static final int MAX_PAGE_SIZE = 500;

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // ---------------- DTO ----------------
    /**
     * 分页结果：records 当前页、total 总条数、page/size 回显实际生效的分页参数；
     * duplicateIsbns 为全库重复的 ISBN（不限于当前页），供页面明确提示重复档案。
     */
    public static class BookPage {
        public List<Book> records;
        public long total;
        public int page;
        public int size;
        public List<String> duplicateIsbns;

        public BookPage(List<Book> records, long total, int page, int size, List<String> duplicateIsbns) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.duplicateIsbns = duplicateIsbns;
        }
    }

    // ---------------- 统一过滤规则 ----------------
    /**
     * 列表、分页共用的唯一过滤口径（详情按 id 直查，同样由此保证字段口径一致）：
     * <ul>
     *   <li>status：精确匹配；</li>
     *   <li>keyword：书名 / 作者 / ISBN 任一包含；</li>
     *   <li>isbn：完整或部分包含（trim 后为空则不过滤——空值绝不参与匹配，
     *       因此 ISBN 未登记（NULL/空串）的旧书不会被任何 ISBN 条件命中）；</li>
     * </ul>
     * 三个条件之间为 AND，排序固定 id 升序，保证分页与刷新后结果稳定一致。
     */
    private Specification<Book> buildSpec(String status, String keyword, String isbn) {
        String st = trim(status);
        String kw = trim(keyword);
        String ib = trim(isbn);
        return (root, query, cb) -> {
            List<Predicate> ps = new java.util.ArrayList<>();
            if (st != null) {
                ps.add(cb.equal(root.get("status"), st));
            }
            if (kw != null) {
                String like = "%" + kw + "%";
                ps.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("author"), like),
                        cb.like(root.get("isbn"), like)));
            }
            if (ib != null) {
                ps.add(cb.like(root.get("isbn"), "%" + ib + "%"));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ---------------- 查询 ----------------
    public BookPage list(String status, String keyword, String isbn, int page, int size) {
        int p = Math.max(page, 1);
        int s = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        Page<Book> data = bookRepository.findAll(
                buildSpec(status, keyword, isbn),
                PageRequest.of(p - 1, s, Sort.by(Sort.Direction.ASC, "id")));
        return new BookPage(data.getContent(), data.getTotalElements(), p, s,
                bookRepository.findDuplicateIsbns());
    }

    /**
     * 详情：按 id 直查，与列表排序 / 分页 / 过滤条件无关，
     * 从销售单明细等任意入口跳入都落到同一本书。字段缺失（旧数据）原样返回 null，由前端显示为空。
     */
    public Book detail(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BizException("图书不存在或已被删除"));
    }

    // ---------------- 新建 / 编辑 ----------------
    @Transactional
    public Book create(Book input) {
        String isbn = trim(input.isbn);
        // 旧书允许无 ISBN（置 null 表示未登记）；登记了 ISBN 则不允许与现有档案重复
        if (isbn != null && !bookRepository.findAllByIsbn(isbn).isEmpty()) {
            throw new BizException("ISBN 已存在，不可重复");
        }
        if (input.title == null || input.title.isBlank()) {
            throw new BizException("书名不能为空");
        }
        if (input.price == null || input.price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("定价必须大于 0");
        }
        if (input.stock == null || input.stock < 0) {
            throw new BizException("库存不能为负数");
        }
        Book book = new Book();
        book.isbn = isbn;
        book.title = input.title;
        book.author = input.author;
        book.price = input.price;
        book.stock = input.stock;
        // 库存为 0 默认置为缺货
        book.status = (input.stock == 0) ? "缺货" : "在售";
        return bookRepository.save(book);
    }

    @Transactional
    public Book update(Long id, Book input) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BizException("图书不存在"));
        if (input.isbn != null) {
            String isbn = trim(input.isbn);
            // 传空串视为清除 ISBN（旧书允许未登记）；非空则校验唯一
            if (isbn != null) {
                boolean usedByOther = bookRepository.findAllByIsbn(isbn).stream().anyMatch(b -> !b.id.equals(id));
                if (usedByOther) {
                    throw new BizException("ISBN 已存在，不可重复");
                }
            }
            book.isbn = isbn;
        }
        if (input.title != null) {
            if (input.title.isBlank()) {
                throw new BizException("书名不能为空");
            }
            book.title = input.title;
        }
        if (input.author != null) {
            book.author = input.author;
        }
        if (input.price != null) {
            if (input.price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("定价必须大于 0");
            }
            book.price = input.price;
        }
        if (input.stock != null) {
            if (input.stock < 0) {
                throw new BizException("库存不能为负数");
            }
            book.stock = input.stock;
        }
        if (input.status != null) {
            if (!Dict.isValidBookStatus(input.status)) {
                throw new BizException("图书状态非法（应为在售/缺货/下架）");
            }
            book.status = input.status;
        }
        // 库存归零且原状态为在售，自动置缺货
        if (book.stock == 0 && "在售".equals(book.status)) {
            book.status = "缺货";
        }
        return bookRepository.save(book);
    }
}
