package com.shop.api.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 DB 에 한 번 접속해 연결 결과를 로그로 남긴다.
 * 실패해도 서버는 계속 뜨고, 원인 메시지만 ERROR 로그로 보여 준다.
 */
@Component
public class DbConnectionChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DbConnectionChecker.class);

    private final DataSource dataSource;

    public DbConnectionChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            log.info("DB 연결 성공: {} {} / url={} / user={}",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion(),
                    meta.getURL(), meta.getUserName());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("DB 연결 실패: {}", root.getMessage());
        }
    }
}
