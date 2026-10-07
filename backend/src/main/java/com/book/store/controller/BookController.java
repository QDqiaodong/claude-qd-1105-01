package com.book.store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Book;
import com.book.store.service.BookService;
import com.book.store.service.BookService.BookPage;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * 列表：status / keyword / isbn 与分页 page / size 共用同一套过滤规则（见 BookService.buildSpec），
     * 空参数不参与过滤，刷新或返回列表后结果保持一致。
     */
    @GetMapping
    public ApiResponse<BookPage> list(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String isbn,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(bookService.list(status, keyword, isbn, page, size));
    }

    /**
     * 详情：按 id 直查，与列表排序 / 分页无关，销售单明细等入口跳入不会错书。
     */
    @GetMapping("/{id}")
    public ApiResponse<Book> detail(@PathVariable Long id) {
        return ApiResponse.success(bookService.detail(id));
    }

    @PostMapping
    public ApiResponse<Book> create(@RequestBody Book book) {
        return ApiResponse.success(bookService.create(book));
    }

    @PutMapping("/{id}")
    public ApiResponse<Book> update(@PathVariable Long id, @RequestBody Book book) {
        return ApiResponse.success(bookService.update(id, book));
    }
}
