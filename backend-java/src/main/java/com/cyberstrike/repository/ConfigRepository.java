package com.cyberstrike.repository;

import com.cyberstrike.entity.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Config 实体的数据库操作接口
 * 继承 JpaRepository 后，自动拥有 save, findById, findAll, delete 等方法
 */
@Repository
public interface ConfigRepository extends JpaRepository<Config, Long> {

    // Spring Data JPA 会根据方法名自动生成 SQL
    // 例如：如果你需要根据语言查询，可以添加如下方法（按需取消注释）：

    // Config findByLanguage(String language);

    // 如果需要根据主题查找
    // Config findByTheme(String theme);
}