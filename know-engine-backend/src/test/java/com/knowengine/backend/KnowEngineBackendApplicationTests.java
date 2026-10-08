package com.knowengine.backend;

import com.alibaba.druid.pool.DruidDataSource;
import io.minio.MinioClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:know_engine;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=test-access-key",
        "minio.secret-key=test-secret-key",
        "minio.bucket-name=test-bucket"
})
class KnowEngineBackendApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private MinioClient minioClient;

    @Test
    void minioClientCanBeCreatedWithOkHttp() {
        assertThat(minioClient).isNotNull();
    }

    @Test
    void mybatisPlusUsesDruidAndCanQueryDatabase() throws Exception {
        assertThat(dataSource).isInstanceOf(DruidDataSource.class);
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource())
                .isSameAs(dataSource);

        try (var session = sqlSessionFactory.openSession();
             var statement = session.getConnection().createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
