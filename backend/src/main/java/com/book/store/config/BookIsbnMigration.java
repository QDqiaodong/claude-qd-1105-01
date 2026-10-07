package com.book.store.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 图书 ISBN 列的兼容迁移：在应用启动后、对外提供服务前执行一次。
 *
 * <p>本项目 ddl-auto=none，全新环境由 mysql 镜像构建期导入的 schema.sql 建表（已含 isbn 列）；
 * 对于已经在运行的旧库（book 表还没有 isbn 列），这里做幂等升级：</p>
 * <ol>
 *   <li>补齐 book.isbn 列，允许 NULL —— 旧图书数据没有 ISBN 时保持「未登记」；</li>
 *   <li>故意不加唯一约束：旧数据里可能已存在重复 / 空白 ISBN，强加 UNIQUE 会让迁移失败；
 *       重复档案由查询层检测并在页面上明确提示，新建 / 编辑由应用层拦截重复。</li>
 * </ol>
 *
 * <p>判断走 information_schema，重复执行无副作用。</p>
 */
@Component
@Order(1)
public class BookIsbnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public BookIsbnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'book' AND column_name = 'isbn'
                """, Integer.class);
        if (cnt == null || cnt == 0) {
            jdbc.execute("ALTER TABLE book ADD COLUMN isbn VARCHAR(32) NULL COMMENT 'ISBN（旧数据可能未登记）' AFTER id");
        }
        // 空串统一归一为 NULL，避免「空串」被当成一个真实 ISBN 参与匹配或判重
        jdbc.update("UPDATE book SET isbn = NULL WHERE isbn IS NOT NULL AND TRIM(isbn) = ''");
    }
}
