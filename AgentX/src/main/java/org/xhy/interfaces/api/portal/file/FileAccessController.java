package org.xhy.interfaces.api.portal.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/** 上传文件的静态访问控制器。
 * <p>直接从本地存储读取文件并返回，避免依赖 dromara 的静态资源映射
 * （后者在 Docker-in-Docker 场景下 location 解析不一致）。 */
@RestController
@RequestMapping("/file")
public class FileAccessController {

    private static final Logger logger = LoggerFactory.getLogger(FileAccessController.class);

    @Value("${FILE_STORAGE_PATH:${file.storage.path:/app/storage}}")
    private String fileStoragePath;

    @GetMapping("/**")
    public ResponseEntity<byte[]> getFile(HttpServletRequest request) {
        // 提取 /file/ 之后的子路径，例如 /file/chat/xxx.png -> chat/xxx.png
        String requestURI = request.getRequestURI();
        // 去掉 context-path（/api）和 /file 前缀
        String contextPath = request.getContextPath(); // /api
        String afterContext = requestURI;
        if (contextPath != null && afterContext.startsWith(contextPath)) {
            afterContext = afterContext.substring(contextPath.length());
        }
        // afterContext 形如 /file/chat/xxx.png
        String relative = afterContext.startsWith("/file/") ? afterContext.substring("/file/".length()) : "";

        if (relative.isEmpty() || relative.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path target = Paths.get(fileStoragePath, relative).normalize();
            // 防止路径穿越：确保目标在存储根目录下
            Path storageRoot = Paths.get(fileStoragePath).normalize();
            if (!target.startsWith(storageRoot)) {
                return ResponseEntity.notFound().build();
            }
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(target);
            String contentType = Files.probeContentType(target);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(content);
        } catch (IOException e) {
            logger.error("读取文件失败: {}", relative, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
