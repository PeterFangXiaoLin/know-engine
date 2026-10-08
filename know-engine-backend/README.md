# know-engine-backend

基于 Java 21、Maven 和 Spring Boot 3.5.16 的后端项目。

## 依赖

| 依赖 | 版本 / 用途 |
| --- | --- |
| Spring Web | Spring Boot 3.5.16 管理，提供 Spring MVC 和内嵌 Tomcat |
| Lombok | Spring Boot 管理，已配置注解处理器 |
| MySQL Connector/J | Spring Boot 管理，运行时驱动 |
| MyBatis-Plus | 3.5.17，Spring Boot 3 Starter |
| Druid | 1.2.28，Spring Boot 3 Starter |
| Fastjson2 | 2.0.65，JSON 解析与序列化 |
| OkHttp | 4.12.0，HTTP 客户端，显式统一 MinIO 使用的 OkHttp 版本 |
| MinIO Java SDK | 8.5.1，S3 兼容对象存储，提供文件上传、下载、删除及签名链接 |
| LangChain4j | BOM 1.22.0，核心模块和 Spring Boot 3 Starter（1.22.0-beta32） |
| Spring Boot Test / H2 | 仅用于测试，不要求本地 MySQL 或模型 API Key |

## 构建与运行

需要 JDK 21 和 Maven 3.6.3+。通过 `mvn -version` 确认 Maven 使用 Java 21。
本机 PowerShell 可以在当前终端中选择已安装的 JDK：

```powershell
$env:JAVA_HOME = 'D:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd D:\MyCode\know-engine\know-engine-backend
mvn clean verify
```

在 MySQL 中创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS know_engine
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

设置连接信息后启动（以下密码需替换为本地实际值）：

```powershell
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'your-local-password'
$env:MINIO_ACCESS_KEY = 'your-minio-access-key'
$env:MINIO_SECRET_KEY = 'your-minio-secret-key'
mvn spring-boot:run
```

默认端口为 `8080`。也可在构建完成后执行：

```powershell
java -jar target/know-engine-backend-0.0.1-SNAPSHOT.jar
```

| 环境变量 | 默认值 |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:mysql://localhost:3307/know_engine?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | 空，运行时按实际数据库配置 |
| `MINIO_ENDPOINT` | `http://111.229.99.8:9000`，MinIO S3 API 地址 |
| `MINIO_ACCESS_KEY` | 空，运行时配置 |
| `MINIO_SECRET_KEY` | 空，运行时配置 |
| `MINIO_BUCKET_NAME` | `rag-test` |

默认启用 `local` profile。本机已有的数据库密码和 MinIO 密钥保存在
`config/application-local.yml` 中，该文件由 Git 忽略且不会打入 JAR；上述环境变量可覆盖本地值。
请从 `know-engine-backend` 目录运行 Maven 或 JAR，使 Spring Boot 自动读取该外部配置。
新克隆项目需自行设置环境变量或创建本地配置文件。部署时可通过 `SPRING_PROFILES_ACTIVE=prod` 切换 profile。

## MinIO 文件存储

`document/config/MinioConfiguration` 创建 `MinioClient`，配置键为
`minio.endpoint`、`minio.access-key`、`minio.secret-key`。
`document/service/FileStorageService` 使用 `minio.bucket-name`，提供以下方法：

| 方法 | 用途 |
| --- | --- |
| `uploadFile(MultipartFile, String)` | 上传 multipart 文件，返回对象 URL |
| `uploadFile(String, byte[], String)` | 上传字节内容，返回对象 URL |
| `downloadFile(String)` | 下载文件，调用方负责关闭返回的 `InputStream` |
| `deleteFile(String)` | 删除对象 |
| `getPresignedUrl(String)` | 生成有效期为 7 天的签名下载链接 |

当前上传逻辑在 bucket 不存在时创建 bucket 并设置公共读策略；已有 bucket 的策略保持原样。
使用前需确认 MinIO 地址可访问，并为配置账号授予相应的 bucket 和对象操作权限。
OkHttp 4.12.0 作为直接依赖覆盖 MinIO 8.5.1 传递引入的 4.10.0。

## Git

Git 仓库位于父目录 `D:\MyCode\know-engine`，后端作为子目录统一管理。
`target/`、IDE 文件、日志及本地凭据配置不纳入版本控制。

## 开发约定

Java 包名为 `com.knowengine.backend`。后续 Mapper 接口添加 `@Mapper` 注解；XML 映射文件放在
`src/main/resources/mapper/` 下，已启用下划线字段到驼峰属性的映射。

已引入 LangChain4j 核心库及声明式 AI Services Starter。尚未选择模型供应商，未创建模型 Bean 或 AI Service，
启动不需要模型 API Key。接入具体模型时，按
[LangChain4j Spring Boot 官方集成文档](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
添加对应的 `langchain4j-{integration-name}-spring-boot-starter` 并配置模型参数。
版本由现有 BOM 统一管理，例如 `langchain4j-open-ai-spring-boot-starter`。

`mvn clean verify` 会启动 Spring 上下文，并使用测试专用 H2 验证 Druid 与 MyBatis-Plus 的数据源集成和 SQL 查询。
测试使用独立的 `test` profile 和虚拟 MinIO 凭据，同时验证 MinIO 客户端能在当前 OkHttp 依赖下创建，
不会访问实际 MinIO 服务。实际 MySQL 连接和 MinIO 文件操作需在配置服务后验证。
