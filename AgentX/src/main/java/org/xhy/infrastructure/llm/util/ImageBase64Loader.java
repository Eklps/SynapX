package org.xhy.infrastructure.llm.util;

import org.dromara.x.file.storage.core.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/** 图片转 base64 工具
 *
 * <p>
 * 负责把后端 file storage 的图片 URL 转成 OpenAI 多模态协议可消费的 data URL ({@code data:image/<mime>;base64,<...>}).
 * </p>
 *
 * <p>
 * 为什么不直接传 URL 给 LLM：本地 storage URL 是 {@code http://localhost:8088/...}， LLM API 是公网服务，访问不到本机，必须把字节内联进请求体。
 * </p>
 *
 * <p>
 * 设计原则：单图失败不抛异常，返回 {@code null}，由上层工厂决定如何降级（通常降级为只发文本消息，不打断主流程）。
 * </p>
 *
 * <h3>下载通道优先级</h3>
 * <ol>
 * <li><b>通道 A</b>：{@link FileStorageService#download(String)} —— dromara x-file-storage 通过 yml 配置的 domain/base-path 解析。</li>
 * <li><b>通道 B</b>：当 fileUrl 与 {@code file.access.url-prefix} ({@code http://localhost:8088/api/file/}) 同源时，提取相对路径直接从
 * {@code FILE_STORAGE_PATH} 物理目录读字节 —— 兜底通道 A 因 yml 配置/容器环境差异失效的场景。</li>
 * <li><b>通道 C</b>：当 A、B 都失败，调用本地 HTTP 客户端访问 {@code http://127.0.0.1:8088/api/file/...}，经 {@code FileAccessController} 拿字节
 * —— 保证容器内能稳定获取已上传文件字节。</li>
 * </ol>
 *
 * 三通道均失败才返回 null，绝不抛异常。
 */
@Component
public class ImageBase64Loader {

    private static final Logger log = LoggerFactory.getLogger(ImageBase64Loader.class);

    private final FileStorageService fileStorageService;

    /** 文件访问 URL 前缀，与 {@code FileAccessController} / {@code UploadController.fileAccessUrlPrefix} 对齐。 */
    @Value("${file.access.url-prefix:http://localhost:8088/api/file/}")
    private String fileAccessUrlPrefix;

    /** 物理存储根目录（容器内通常为 {@code /app/storage}）。 */
    @Value("${FILE_STORAGE_PATH:${file.storage.path:/app/storage}}")
    private String fileStoragePath;

    /** 通道 C 的本机回环地址（容器内访问自身的 :8088，避免依赖公网/外部域名）。 */
    private static final String LOOPBACK_HOST = "127.0.0.1";

    /** 通道 C 的本机回环端口。 */
    @Value("${server.port:8088}")
    private int serverPort;

    private HttpClient httpClient;

    public ImageBase64Loader(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    void initHttpClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    /** 把 file URL 转成 data URL。多通道重试，任一成功即返回。 */
    public String toDataUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            log.warn("ImageBase64Loader.toDataUrl: 收到空 url，跳过");
            return null;
        }
        log.info("ImageBase64Loader.toDataUrl ENTER: url={}", fileUrl);

        // 通道 A：file-storage 原生 download
        byte[] bytes = tryChannelA(fileUrl);
        if (bytes == null) {
            // 通道 B：URL 与 file.access.url-prefix 同源 → 直接从磁盘读
            bytes = tryChannelB(fileUrl);
        }
        if (bytes == null) {
            // 通道 C：通过本机回环 HTTP 调用 FileAccessController
            bytes = tryChannelC(fileUrl);
        }
        if (bytes == null || bytes.length == 0) {
            log.warn("ImageBase64Loader.toDataUrl ALL CHANNELS FAILED: url={}", fileUrl);
            return null;
        }
        String mime = guessMimeType(fileUrl);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        log.info("ImageBase64Loader.toDataUrl OK: url={}, bytes={}, mime={}, dataUrlLen={}", fileUrl, bytes.length,
                mime, b64.length());
        return "data:" + mime + ";base64," + b64;
    }

    // ================= 通道 A: fileStorageService.download =================
    private byte[] tryChannelA(String fileUrl) {
        try {
            byte[] bytes = fileStorageService.download(fileUrl).bytes();
            if (bytes != null && bytes.length > 0) {
                log.info("ImageBase64Loader.channelA OK: url={}, bytes={}", fileUrl, bytes.length);
                return bytes;
            }
            log.warn("ImageBase64Loader.channelA: 下载到空字节, url={}", fileUrl);
            return null;
        } catch (Throwable e) {
            log.warn("ImageBase64Loader.channelA FAILED: url=" + fileUrl + ", err=" + e.getMessage());
            return null;
        }
    }

    // ================= 通道 B: 从磁盘直接读 (URL 同源) =================
    private byte[] tryChannelB(String fileUrl) {
        try {
            String relative = extractRelativePath(fileUrl);
            if (relative == null) {
                return null;
            }
            Path root = Paths.get(fileStoragePath).toAbsolutePath().normalize();
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) {
                log.warn("ImageBase64Loader.channelB: 路径越界, url={}, target={}", fileUrl, target);
                return null;
            }
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                log.warn("ImageBase64Loader.channelB: 文件不存在, url={}, target={}", fileUrl, target);
                return null;
            }
            byte[] bytes = Files.readAllBytes(target);
            log.info("ImageBase64Loader.channelB OK: url={}, bytes={}, target={}", fileUrl, bytes.length, target);
            return bytes;
        } catch (Throwable e) {
            log.warn("ImageBase64Loader.channelB FAILED: url=" + fileUrl + ", err=" + e.getMessage());
            return null;
        }
    }

    /** 从 fileUrl 提取相对于 file-access URL 前缀的 path，例如 {@code http://localhost:8088/api/file/chat/abc.png}
     * → {@code chat/abc.png}。 */
    private String extractRelativePath(String fileUrl) {
        if (fileAccessUrlPrefix == null || fileAccessUrlPrefix.isEmpty()) {
            return null;
        }
        if (!fileUrl.startsWith(fileAccessUrlPrefix)) {
            return null;
        }
        String rel = fileUrl.substring(fileAccessUrlPrefix.length());
        if (rel.isEmpty() || rel.contains("..")) {
            return null;
        }
        return rel;
    }

    // ================= 通道 C: 容器内本地回环 HTTP 调用 FileAccessController =================
    private byte[] tryChannelC(String fileUrl) {
        try {
            String relative = extractRelativePath(fileUrl);
            if (relative == null) {
                return null;
            }
            // 容器内走 127.0.0.1 直连，避免依赖容器外的 localhost 解析
            String loopbackUrl = "http://" + LOOPBACK_HOST + ":" + serverPort + "/api/file/" + relative;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(loopbackUrl)).timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body() != null
                    && resp.body().length > 0) {
                log.info("ImageBase64Loader.channelC OK: url={}, bytes={}", loopbackUrl, resp.body().length);
                return resp.body();
            }
            log.warn("ImageBase64Loader.channelC 失败: url={}, status={}", loopbackUrl, resp.statusCode());
            return null;
        } catch (Throwable e) {
            log.warn("ImageBase64Loader.channelC FAILED: url=" + fileUrl + ", err=" + e.getMessage());
            return null;
        }
    }

    /** 根据 URL 后缀推断 mime type。
     *
     * @param url 文件 URL
     * @return mime type（默认 image/png） */
    private String guessMimeType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }
}
