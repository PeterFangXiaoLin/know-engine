package com.knowengine.backend.document.service;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    // 确保 bucket 存在
    private void createBucketIfNotExists(boolean publicRead) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            // 设置 bucket 策略为公共读
            if (publicRead) {
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(bucketName)
                                .config(policy)
                                .build()
                );
            }
        }
    }

    /**
     * 上传用户文件，完成后关闭文件流。
     *
     * @param file 上传文件
     * @param objectName MinIO 对象名称
     * @return 文件访问URL
     * @throws Exception 上传失败
     */
    public String uploadFile(MultipartFile file, String objectName) throws Exception {
        createBucketIfNotExists(true);// 这里可根据你自己的情况改成false，如果改成false，需要在这个方法最后调一次getPresignedUrl
        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);

    }

    /**
     * 上传字节内容，适用于改写后的 Markdown。
     *
     * @param objectName MinIO 对象名称
     * @param content 文件字节
     * @param contentType 文件媒体类型
     * @return 文件访问URL
     * @throws Exception 上传失败
     */
    public String uploadFile(String objectName, byte[] content, String contentType) throws Exception {
        createBucketIfNotExists(true);
        try (InputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, content.length, -1)
                            .contentType(contentType)
                            .build()
            );

            return String.format("%s/%s/%s", endpoint, bucketName, objectName);
        }
    }

    /**
     * 流式上传临时文件，避免将解析图片整体读入内存。
     *
     * @param objectName MinIO 对象名称
     * @param file 本地文件路径
     * @param contentType 文件媒体类型
     * @return 文件访问URL
     * @throws Exception 上传失败
     */
    public String uploadFile(String objectName, Path file, String contentType) throws Exception {
        // 1. 确保存储桶可用。
        createBucketIfNotExists(true);

        // 2. 按文件实际长度流式上传，并关闭输入流。
        try (InputStream input = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(input, Files.size(file), -1)
                    .contentType(contentType)
                    .build());
        }
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    /**
     * 下载文件，由调用方关闭返回的输入流。
     *
     * @param objectName MinIO 对象名称
     * @return 文件输入流
     * @throws Exception 下载失败
     */
    public InputStream downloadFile(String objectName) throws Exception {
        GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
        return response;
    }

    /**
     * 删除指定 MinIO 对象。
     *
     * @param objectName MinIO 对象名称
     * @throws Exception 删除失败
     */
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    /**
     * 生成有效期为 7 天的签名下载链接。
     *
     * @param objectName MinIO 对象名称
     * @return 签名下载URL
     * @throws Exception 生成链接失败
     */
    public String getPresignedUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
    }
}
