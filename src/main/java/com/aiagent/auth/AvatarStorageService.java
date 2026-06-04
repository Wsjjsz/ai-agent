package com.aiagent.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final Path avatarDir;

    public AvatarStorageService(@Value("${app.auth.avatar-dir:tmp/avatar}") String avatarDir) {
        this.avatarDir = Path.of(avatarDir).toAbsolutePath().normalize();
    }

    public String save(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像图片不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG、WebP 或 GIF 图片");
        }

        try {
            Files.createDirectories(avatarDir);
            String fileName = "user-" + userId + "-" + UUID.randomUUID() + "." + extension;
            Path target = avatarDir.resolve(fileName).normalize();
            if (!target.startsWith(avatarDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return "/api/auth/avatar/file/" + fileName;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败");
        }
    }

    public Resource load(String fileName) {
        if (fileName == null || !fileName.matches("^[A-Za-z0-9._-]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
        }
        try {
            Path file = avatarDir.resolve(fileName).normalize();
            if (!file.startsWith(avatarDir) || !Files.exists(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
            }
            return new UrlResource(file.toUri());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "头像不存在");
        }
    }
}
