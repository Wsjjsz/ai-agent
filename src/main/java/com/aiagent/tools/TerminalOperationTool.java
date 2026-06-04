package com.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具
 */
public class TerminalOperationTool {

    /**
     * 允许执行的命令白名单（只允许前缀匹配）
     */
    private static final List<String> ALLOWED_COMMANDS = Arrays.asList(
            "ls", "dir", "pwd", "echo", "cat", "head", "tail", "grep", "find",
            "wc", "sort", "uniq", "diff", "file", "stat", "du", "df"
    );

    /**
     * 危险片段黑名单。终端工具只允许低风险、只读命令。
     */
    private static final List<String> BLOCKED_COMMANDS = Arrays.asList(
            ";", "&&", "||", "|", "`", "$(", ">", "<",
            "..", "~", " /", "-delete", "-exec", "rm ", "mv ", "cp ",
            "chmod", "chown", "sudo", "su ", "mkfs", "dd if=", "shutdown",
            "reboot", "halt", "poweroff", "systemctl", "service"
    );

    private static final int COMMAND_TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_LENGTH = 8000;

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        // 安全检查：验证命令是否在白名单中
        if (!isCommandAllowed(command)) {
            return "Error: Command not allowed. Allowed commands: " + String.join(", ", ALLOWED_COMMANDS);
        }

        StringBuilder output = new StringBuilder();
        try {
            // 根据操作系统选择 shell
            ProcessBuilder builder;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                builder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                builder = new ProcessBuilder("/bin/sh", "-c", command);
            }

            // 设置工作目录为用户目录
            builder.directory(new java.io.File(System.getProperty("user.dir")));
            // 重定向错误流到标准输出
            builder.redirectErrorStream(true);

            Process process = builder.start();

            // 设置超时时间，避免工具长时间占用线程
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command execution timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e) {
            output.append("Error executing command: ").append(e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return limitOutput(output.toString());
    }

    /**
     * 检查命令是否允许执行
     */
    private boolean isCommandAllowed(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        String trimmedCommand = command.trim();
        if (trimmedCommand.startsWith("/")) {
            return false;
        }

        // 检查黑名单
        for (String blocked : BLOCKED_COMMANDS) {
            if (trimmedCommand.contains(blocked)) {
                return false;
            }
        }

        // 检查白名单（提取命令的第一个单词进行匹配）
        String firstWord = trimmedCommand.split("\\s+")[0];
        // 处理带路径的命令（如 /usr/bin/python）
        if (firstWord.contains("/")) {
            firstWord = firstWord.substring(firstWord.lastIndexOf("/") + 1);
        }

        for (String allowed : ALLOWED_COMMANDS) {
            if (firstWord.equals(allowed)) {
                return true;
            }
        }

        return false;
    }

    private String limitOutput(String output) {
        if (output == null || output.length() <= MAX_OUTPUT_LENGTH) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_LENGTH) + "\n... Output truncated for safety.";
    }
}
