# know-engine-backend

基于 Java 21、Maven 和 Spring Boot 3.5.16 的后端项目。

## 依赖

| 依赖                    | 版本 / 用途                                               |
|-----------------------|-------------------------------------------------------|
| Spring Web            | Spring Boot 3.5.16 管理，提供 Spring MVC 和内嵌 Tomcat        |
| Spring Validation     | Spring Boot 管理，校验知识文档和片段接口参数                          |
| Lombok                | Spring Boot 管理，已配置注解处理器                               |
| MySQL Connector/J     | Spring Boot 管理，运行时驱动                                  |
| MyBatis-Plus          | 3.5.17，Spring Boot 3 Starter，包含同版本 JSQLParser 分页扩展    |
| Druid                 | 1.2.28，Spring Boot 3 Starter                          |
| Fastjson2             | 2.0.65，JSON 解析与序列化                                    |
| OkHttp                | 4.12.0，HTTP 客户端，显式统一 MinIO 使用的 OkHttp 版本              |
| MinIO Java SDK        | 8.5.1，S3 兼容对象存储，提供文件上传、下载、删除及签名链接                     |
| LangChain4j           | BOM 1.22.0，核心模块和 Spring Boot 3 Starter（1.22.0-beta32） |
| Spring Boot Test / H2 | 仅用于测试，不要求本地 MySQL 或模型 API Key                         |

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

| 环境变量                | 默认值                                                                                                            |
|---------------------|----------------------------------------------------------------------------------------------------------------|
| `SERVER_PORT`       | `8080`                                                                                                         |
| `DB_URL`            | `jdbc:mysql://localhost:3307/know_engine?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME`       | `root`                                                                                                         |
| `DB_PASSWORD`       | 空，运行时按实际数据库配置                                                                                                  |
| `MINIO_ENDPOINT`    | `http://111.229.99.8:9000`，MinIO S3 API 地址                                                                     |
| `MINIO_ACCESS_KEY`  | 空，运行时配置                                                                                                        |
| `MINIO_SECRET_KEY`  | 空，运行时配置                                                                                                        |
| `MINIO_BUCKET_NAME` | `know-engine`                                                                                                  |

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

## 知识文档与片段 CRUD

代码位于 `com.knowengine.backend.document` 包下，包含 `entity`、`dto`、`mapper`、
`service`、`service.impl` 和 `controller`。Mapper 继承 MyBatis-Plus `BaseMapper`，
Service 继承 `IService` / `ServiceImpl`，基础 CRUD 不需要 XML。
运行前需要已存在的 `knowledge_document`、`knowledge_segment` 表；应用不会自动建表。

| 操作      | 文档接口                                      | 片段接口                                  |
|---------|-------------------------------------------|---------------------------------------|
| 新增      | `POST /api/knowledge/documents`           | `POST /api/knowledge/segments`        |
| 按 ID 查询 | `GET /api/knowledge/documents/{docId}`    | `GET /api/knowledge/segments/{id}`    |
| 分页查询    | `GET /api/knowledge/documents`            | `GET /api/knowledge/segments`         |
| 更新      | `PUT /api/knowledge/documents/{docId}`    | `PUT /api/knowledge/segments/{id}`    |
| 删除      | `DELETE /api/knowledge/documents/{docId}` | `DELETE /api/knowledge/segments/{id}` |

新增返回 HTTP `201` 和含自增 ID 的对象；查询和更新返回 HTTP `200`；删除返回 HTTP `204`。
不存在的记录或所属文档返回 HTTP `404`；参数不合法返回 HTTP `400`，错误体使用 Spring `ProblemDetail`。
请求体使用驼峰字段名，主键由数据库生成，更新时以 URL 中的 ID 为准。

两个分页接口均接受 `pageNum`（默认 `1`）和 `pageSize`（默认 `10`，范围 `1`～`100`），
返回 MyBatis-Plus 分页对象，主要字段为 `records`、`total`、`current`、`size`、`pages`。
文档支持 `docTitle` 模糊匹配、`uploadUser` 和 `status` 精确匹配，按 `docId` 倒序；
片段支持 `documentId`、`chunkId`、`status`、`skipEmbedding` 精确匹配，按 `documentId`、
`chunkOrder`、`id` 升序。例如：

```text
GET /api/knowledge/documents?pageNum=1&pageSize=10&status=INIT
GET /api/knowledge/segments?documentId=1&pageNum=1&pageSize=20
```

新增文档请求示例（`Content-Type: application/json`）：

```json
{
   "docTitle": "知识库使用说明",
   "uploadUser": "demo",
   "docUrl": "https://example.com/manual.pdf",
   "expireDate": "2027-10-21",
   "status": "UPLOADED",
   "accessibleBy": "developers",
   "extension": "{\"language\":\"zh\"}"
}
```

文档的 `docTitle` 必填；新增时 `status` 不传默认为 `INIT`。
`status` 允许 `INIT`、`UPLOADED`、`CONVERTING`、`CONVERTED`、`CHUNKED`、`VECTOR_STORED`。
`expireDate` 使用 `yyyy-MM-dd`；`extension` 和片段的 `metadata` 均按字符串存储。

新增片段请求示例，`documentId` 需替换为实际文档 ID：

```json
{
  "text": "这是第一个知识片段。",
  "chunkId": "chunk-001",
  "metadata": "{\"heading\":\"介绍\"}",
  "documentId": 1,
  "chunkOrder": 0,
  "status": "INIT",
  "skipEmbedding": 0
}
```

片段的 `text`、`documentId`、`chunkOrder` 必填，`chunkOrder` 从 `0` 起；
新增时 `status` 不传默认为 `INIT`，`skipEmbedding` 不传默认为 `0`。
片段状态允许 `INIT`、`VECTOR_STORED`，`skipEmbedding` 允许 `0`、`1`。

`PUT` 使用与新增相同的请求结构，并重新提交必填字段。可选字段省略或传 `null` 时会清空，
其中文档的 `status` 省略或传 `null` 时保留原值；片段的 `status`、`skipEmbedding` 可以清空为 `null`。
删除文档会在同一事务内物理删除其所有片段；片段写入和文档删除通过父文档行锁协调。
这些接口只管理上述 MySQL 记录；MinIO 文件和 Elasticsearch 向量的同步处理需在后续文档流程中接入。

`KnowledgeCrudIntegrationTests` 使用独立 H2 数据库和测试建表脚本验证 CRUD、分页筛选、参数校验、
片段归属、级联删除和删除失败时的事务回滚，不会修改本地 MySQL 表。

已引入 LangChain4j 核心库及声明式 AI Services Starter。尚未选择模型供应商，未创建模型 Bean 或 AI Service，
启动不需要模型 API Key。接入具体模型时，按
[LangChain4j Spring Boot 官方集成文档](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
添加对应的 `langchain4j-{integration-name}-spring-boot-starter` 并配置模型参数。
版本由现有 BOM 统一管理，例如 `langchain4j-open-ai-spring-boot-starter`。

`mvn clean verify` 会启动 Spring 上下文，并使用测试专用 H2 验证 Druid 与 MyBatis-Plus 的数据源集成和 SQL 查询。
测试使用独立的 `test` profile 和虚拟 MinIO 凭据，同时验证 MinIO 客户端能在当前 OkHttp 依赖下创建，
不会访问实际 MinIO 服务。实际 MySQL 连接和 MinIO 文件操作需在配置服务后验证。



## 文档上传功能

1、pdf文件上传

2、pdf文件存储在minio

3、将文件解析成markdown

4、基于markdown做分块

5、将分块后的数据做embedding

6、将向量和文本块保存在向量数据库

### 文档上传接口

当前上传接口为 `POST /api/knowledge/documents/upload`，请求类型为 `multipart/form-data`，
字段为 `file`、`uploadUser` 和可选的 `accessibleBy`。
PDF 在本次请求中同步解析，成功响应包含 `convertedDocUrl`，指向 MinIO 上的 Markdown。
非 PDF 沿用原文件地址作为转换结果。

```powershell
curl.exe -X POST "http://localhost:8080/api/knowledge/documents/upload" `
  -F "file=@D:\docs\example.pdf" `
  -F "uploadUser=demo" `
  -F "accessibleBy=developers"
```

`document/api/mineru/MineruClient` 使用现有 OkHttp 依赖，按
[MinerU 精准解析 API 文档](https://mineru.net/apiManage/docs) 调用以下接口：

| 步骤 | 请求 |
| --- | --- |
| 申请单文件上传链接 | `POST /api/v4/file-urls/batch`，返回 `batch_id`、`file_urls` |
| 上传 PDF 字节 | `PUT file_urls[0]`，不使用 multipart，不携带 API Token |
| 查询解析状态 | `GET /api/v4/extract-results/batch/{batch_id}` |
| 下载解析产物 | 状态为 `done` 后下载 `full_zip_url`，不携带 API Token |

`FileProcessService.processDocument` 负责以下处理：

1. 将文档从 `UPLOADED` 原子更新为 `CONVERTING`，避免重复提交。
2. 暂存 PDF 并上传，记录 `extension.mineru.batchId` 后限时轮询结果。
3. 流式下载 ZIP 并解压，校验文件路径及展开体积。
4. 将图片上传 MinIO，改写 Markdown 和 HTML 图片引用；图片描述使用明确标记的 MOCK，待接入视觉模型。
5. 保存 Markdown 后更新 `convertedDocUrl`，将文档状态更新为 `CONVERTED`。

转换失败时返回 HTTP `502`，已保存的文档恢复为 `UPLOADED`，
原因写入 `extension.mineru.error`，原文件保留，本次临时文件和未完成的转换产物会清理。
接口支持的 PDF 最大为 200MB；官方 API 同时限制页数，超限时会返回解析错误。
轮询超时不等于远程任务已取消，排查时可使用保存的 `batchId`。

### MinerU 配置

| 配置 | 默认值 / 用途 |
| --- | --- |
| `mineru.base-url` / `MINERU_BASE_URL` | `https://mineru.net` |
| `mineru.token` / `MINERU_TOKEN` | API Token，使用 `Authorization: Bearer ...` |
| `mineru.model-version` | `vlm` |
| `mineru.language` | `ch` |
| `mineru.http-timeout` | `2m`，单次 HTTP 请求超时 |
| `mineru.poll-interval` | `3s`，轮询间隔 |
| `mineru.poll-timeout` | `10m`，等待任务结果的总超时 |

本机 Token 保存在外部 `config/application-local.yml`，该文件由 Git 忽略，不在 Maven 资源目录中。
从 `know-engine-backend` 目录启动即可加载；环境变量 `MINERU_TOKEN` 可覆盖本地值。
共享 `application.yml` 仅保留环境变量占位符。部署时设置环境变量后启动：

```powershell
$env:MINERU_TOKEN = 'your-mineru-token'
mvn spring-boot:run
```

`MineruClientTests` 使用本地 HTTP 服务验证 API 报文、Bearer 鉴权、签名上传与下载不传 Token、
失败状态、超时及中断；`FileProcessServiceTests` 使用 H2 和模拟外部服务验证上传流程、
状态落库、Markdown 图片改写、不安全 ZIP 拒绝及失败清理。

### 后续文档处理流程

1. 把上传的文件MultiPartFile 保存到MinIO
2. 结合 MinIO 返回的地址 ，构建 KnowDocument 对象保存到数据库，状态为已上传
3. 根据文件类型处理
   1. 如果是 PDF
      1. 调用 MinerU API接口 https://mineru.net/apiManage/docs  解析pdf并返回zip 压缩文件
      2. 利用文件流，下载压缩包(保存到一个临时目录)，解压文件，上传里面的 markdown 和 image 到 MinIO, 修改 markdown
         中引用的图片地址为 MinIO 的地址，即![]() 里面的图片路径
      3. 调用 视觉大模型 给图片生成一个描述（方法生成里面写个MOCK就行）
      4. 遍历markdown 中的图片信息，替换为 ![生成的描述](minIO地址)
      5. 更新 KnowDocument 为转化完成
   2. 不是 PDF，更新 KnowDocument 状态为 转化完成

### 表创建

```sql
-- 知识文档表
CREATE TABLE `knowledge_document`
(
   `doc_id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文档ID',
   `doc_title`         VARCHAR(1024) NOT NULL COMMENT '文档标题',
   `upload_user`       VARCHAR(255) NULL COMMENT '上传用户',
   `doc_url`           VARCHAR(2048) NULL COMMENT '文档URL',
   `converted_doc_url` VARCHAR(2048) NULL COMMENT '转换后的文档URL',
   `status`            VARCHAR(32)   NOT NULL COMMENT '状态: INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
   `accessible_by`     VARCHAR(1024) NULL COMMENT '可见范围',
   `extension`         TEXT NULL COMMENT '扩展字段，保存JSON字符串',

   PRIMARY KEY (`doc_id`),

   -- 为状态字段添加索引，优化定时任务扫表性能
   INDEX               `idx_status` (`status`),

   -- 复合索引：状态+文档ID，优化分页查询性能
   INDEX               `idx_status_doc_id` (`status`, `doc_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT = '知识文档表';


-- 知识片段表
CREATE TABLE `knowledge_segment`
(
   `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '片段ID',
   `text`           LONGTEXT NOT NULL COMMENT '文本内容',
   `chunk_id`       VARCHAR(255) NULL COMMENT '分片ID',
   `metadata`       VARCHAR(2048) NULL COMMENT '元数据',
   `document_id`    BIGINT   NOT NULL COMMENT '所属文档ID',
   `chunk_order`    INT      NOT NULL COMMENT '顺序',
   `embedding_id`   VARCHAR(255) NULL COMMENT '嵌入ID',
   `status`         VARCHAR(255) NULL COMMENT '状态: INIT, VECTOR_STORED',
   `skip_embedding` INT NULL COMMENT '是否跳过嵌入生成',

   PRIMARY KEY (`id`),

   INDEX            `idx_segment_document_id` (`document_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT = '知识片段表';
```

AI 生成增删改查代码

定义枚举类

## 文档切分

提供两个 markdown splitter

按照标题进行切分，每个标题及其下面的内容作为一个chunk, 如果chunk 大小没有超过设置的chunkSize,则保存到数据库

> 为什么需要保存到关系型数据库
>
> 无论是百炼还是dify，他们提供的知识库，上传文档后都可以看到所有切片 chunk , 本质都是保存到关系型数据库，方便查找显示和修改，不可能是去向量数据库找不出来拼接的

如果chunk 大小超过 设置的 chunkSize,则需要分片，分片前需要把完整的 segment
保存到关系型数据库，但不保存到向量数据库，然后再进行切片，切出来的片段保存到向量数据库，同时保存到关系型数据库，并搞一个字段记录父chunkId，向量检索捞出来子片段时，从关系型数据库查出完整的父分片替换查出来子分片内容。



提示词

```
增加一个 splitter 方法，传入 documentId 之后，调用 进行切分，切片前需要先校验knowledgeDocument 的状态
```





## 向量存储 - ES



引入 ES 配置相关类



1. 编写接口 传入 knowledgeDocument 的 docId

2. 根据 docId 查 到 KnowledgeDocument 判断是否存在已经状态已经向量化

3. 根据 docId 分页查所有未向量化的 KnowSegment ，调用 embeddingModel 完成 向量化

4. 调用 ElasticSearchEmbeddingStore 存储向量

5. 修改 KnowSegment 为 VECTOR_STORED 修改 KnowledegDocument 的 状态为VECTOR_STORED



#### 向量维度如何设置

测试之后调整



目前是基于事件驱动实现的

事件可能发送失败，还需要加一个定时任务兜底



同时还要处理并发问题
