package com.cyberstrike.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Python 虚拟环境管理服务
 * 提供自动创建虚拟环境、安装包和执行脚本的功能
 */
@Service
public class PythonVenvService {

    private static final Logger log = LoggerFactory.getLogger(PythonVenvService.class);

    private static final String VENV_BASE_DIR = System.getProperty("user.home") + "/.cyberstrike/venvs";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * 获取 Python 可执行文件路径
     */
    public String getPythonExecutable(String envName) {
        Path envDir = getEnvDir(envName);
        if (IS_WINDOWS) {
            return envDir.resolve("Scripts").resolve("python.exe").toString();
        } else {
            return envDir.resolve("bin").resolve("python").toString();
        }
    }

    /**
     * 获取 pip 可执行文件路径
     */
    public String getPipExecutable(String envName) {
        Path envDir = getEnvDir(envName);
        if (IS_WINDOWS) {
            return envDir.resolve("Scripts").resolve("pip.exe").toString();
        } else {
            return envDir.resolve("bin").resolve("pip").toString();
        }
    }

    /**
     * 获取虚拟环境目录
     */
    public Path getEnvDir(String envName) {
        if (envName == null || envName.isEmpty() || "default".equals(envName)) {
            // 默认使用项目目录下的 venv
            return Paths.get(System.getProperty("user.dir"), "venv");
        }
        return Paths.get(VENV_BASE_DIR, envName);
    }

    /**
     * 确保虚拟环境存在，如果不存在则创建
     */
    public boolean ensureVenv(String envName) {
        Path envDir = getEnvDir(envName);
        Path pythonPath = Paths.get(getPythonExecutable(envName));

        if (Files.exists(pythonPath)) {
            log.debug("虚拟环境已存在: {}", envDir);
            return true;
        }

        log.info("创建虚拟环境: {}", envDir);

        try {
            // 确保父目录存在
            Files.createDirectories(envDir.getParent());

            // 创建虚拟环境
            List<String> command = new ArrayList<>();
            if (IS_WINDOWS) {
                command.add("python");
            } else {
                command.add("python3");
            }
            command.add("-m");
            command.add("venv");
            command.add(envDir.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            if (!finished || exitCode != 0) {
                log.error("创建虚拟环境失败: {}", output);
                return false;
            }

            log.info("虚拟环境创建成功: {}", envDir);
            return true;

        } catch (Exception e) {
            log.error("创建虚拟环境异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 安装 Python 包
     */
    public String installPackage(String packageName, String envName, String additionalArgs) {
        if (!ensureVenv(envName)) {
            return "错误: 无法创建/激活虚拟环境";
        }

        try {
            String pip = getPipExecutable(envName);

            List<String> command = new ArrayList<>();
            command.add(pip);
            command.add("install");
            command.add(packageName);

            // 添加额外参数
            if (additionalArgs != null && !additionalArgs.isEmpty()) {
                for (String arg : additionalArgs.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        command.add(arg);
                    }
                }
            }

            log.info("执行 pip install: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: 安装超时 (300秒)";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "安装失败 (exit code " + exitCode + "):\n" + output;
            }

            return "安装成功:\n" + output;

        } catch (Exception e) {
            log.error("安装包失败: {}", e.getMessage());
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 执行 Python 脚本
     */
    public String executeScript(String script, String envName, String additionalArgs) {
        if (!ensureVenv(envName)) {
            return "错误: 无法创建/激活虚拟环境";
        }

        try {
            String python = getPythonExecutable(envName);

            List<String> command = new ArrayList<>();
            command.add(python);

            // 添加额外参数
            if (additionalArgs != null && !additionalArgs.isEmpty()) {
                for (String arg : additionalArgs.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        command.add(arg);
                    }
                }
            }

            command.add("-c");
            command.add(script);

            log.info("执行 Python 脚本: {} -c \"...\"", python);
            log.debug("脚本内容: {}", script);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: 脚本执行超时 (120秒)";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "脚本执行失败 (exit code " + exitCode + "):\n" + output;
            }

            if (output.isEmpty()) {
                return "脚本执行完成，无输出";
            }

            return output;

        } catch (Exception e) {
            log.error("执行脚本失败: {}", e.getMessage());
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 执行 Python 文件
     */
    public String executeFile(String filePath, String envName, String additionalArgs) {
        if (!ensureVenv(envName)) {
            return "错误: 无法创建/激活虚拟环境";
        }

        if (!Files.exists(Paths.get(filePath))) {
            return "错误: 文件不存在: " + filePath;
        }

        try {
            String python = getPythonExecutable(envName);

            List<String> command = new ArrayList<>();
            command.add(python);
            command.add(filePath);

            // 添加额外参数
            if (additionalArgs != null && !additionalArgs.isEmpty()) {
                for (String arg : additionalArgs.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        command.add(arg);
                    }
                }
            }

            log.info("执行 Python 文件: {} {}", python, filePath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: 执行超时 (300秒)";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "执行失败 (exit code " + exitCode + "):\n" + output;
            }

            if (output.isEmpty()) {
                return "执行完成，无输出";
            }

            return output;

        } catch (Exception e) {
            log.error("执行文件失败: {}", e.getMessage());
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 列出已安装的包
     */
    public String listPackages(String envName) {
        if (!ensureVenv(envName)) {
            return "错误: 无法创建/激活虚拟环境";
        }

        try {
            String pip = getPipExecutable(envName);

            List<String> command = List.of(pip, "list", "--format=freeze");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            process.waitFor(30, TimeUnit.SECONDS);

            return output;

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 删除虚拟环境
     */
    public boolean deleteVenv(String envName) {
        if (envName == null || envName.isEmpty() || "default".equals(envName)) {
            log.warn("不允许删除默认虚拟环境");
            return false;
        }

        Path envDir = getEnvDir(envName);
        if (!Files.exists(envDir)) {
            return true;
        }

        try {
            // 递归删除目录
            Files.walk(envDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("删除失败: {}", path);
                        }
                    });
            log.info("虚拟环境已删除: {}", envDir);
            return true;

        } catch (Exception e) {
            log.error("删除虚拟环境失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
