package org.xhy.interfaces.api.portal.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.storage.OssUploadService;
import org.xhy.infrastructure.storage.OssUploadService.UploadCredential;
import org.xhy.interfaces.api.common.Result;

/** 文件上传控制器。支持本地直传（POST /upload）与 OSS 直传凭证（GET /upload/credential）。 */
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final OssUploadService ossUploadService;

    @Value("${FILE_STORAGE_PATH:${file.storage.path:/app/storage}}")
    private String fileStoragePath;

    @Value("${file.access.url-prefix:http://localhost:8088/api/file/}")
    private String fileAccessUrlPrefix;

    public UploadController(OssUploadService ossUploadService) {
        this.ossUploadService = ossUploadService;
    }

    /** 本地直传：接收 multipart 文件，存到本地存储，返回可访问的 URL。 */
    @PostMapping
    public Result<LocalUploadResult> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        String subDir = "chat";

        try {
            Path dir = Paths.get(fileStoragePath, subDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            String url = fileAccessUrlPrefix + subDir + "/" + storedName;
            logger.info("文件上传成功: {} -> {}", originalName, url);

            return Result.success(new LocalUploadResult(url, originalName, file.getSize(), file.getContentType()));
        } catch (IOException e) {
            logger.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /** 获取 OSS 直传凭证（保留兼容旧 OSS 直传流程） */
    @GetMapping("/credential")
    public Result<UploadCredential> getUploadCredential() {
        UploadCredential credential = ossUploadService.generateUploadCredential();
        return Result.success(credential);
    }

    /** 本地文件上传结果 */
    public static class LocalUploadResult {
        private String url;
        private String fileName;
        private Long fileSize;
        private String fileType;

        public LocalUploadResult() {
        }

        public LocalUploadResult(String url, String fileName, Long fileSize, String fileType) {
            this.url = url;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileType = fileType;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }
    }
}
