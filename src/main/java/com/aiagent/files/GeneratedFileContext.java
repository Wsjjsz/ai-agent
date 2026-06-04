package com.aiagent.files;

import com.aiagent.constant.FileConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class GeneratedFileContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private GeneratedFileContext() {
    }

    public static void set(Scope scope) {
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
    }

    public static Scope current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Path baseDir() {
        Scope scope = current();
        if (scope == null) {
            return Paths.get(FileConstant.FILE_SAVE_DIR).toAbsolutePath().normalize();
        }
        return sessionRoot(scope.userId(), scope.sessionId());
    }

    public static Path resolve(String directory, String fileName) {
        return baseDir().resolve(directory).resolve(fileName).toAbsolutePath().normalize();
    }

    public static Path userRoot(long userId) {
        return Paths.get(FileConstant.FILE_SAVE_DIR, "users", String.valueOf(userId)).toAbsolutePath().normalize();
    }

    public static Path sessionRoot(long userId, String sessionId) {
        return userRoot(userId).resolve(sessionId).toAbsolutePath().normalize();
    }

    public record Scope(long userId, String sessionId) {
    }
}
