package com.cyberstrike.repository;

import com.cyberstrike.entity.WebshellConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 【Go -> Java 迁移新增】WebShell 连接仓库（仅资产登记/配置管理）。
 */
@Repository
public interface WebshellConnectionRepository extends JpaRepository<WebshellConnection, String> {

    List<WebshellConnection> findAllByOrderByCreatedAtDesc();
}
