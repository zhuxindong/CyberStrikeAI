package com.cyberstrike.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移】Skills管理器
 * 对齐 Go 实现：cyberstrike-ai/internal/skills/manager.go Manager
 * 以及 handler/skills.go 中的CRUD操作
 */
@Component
public class SkillsManager {

    private static final Logger logger = LoggerFactory.getLogger(SkillsManager.class);

    @Value("${security.skills-dir:skills}")
    private String skillsDir;

    private final Map<String, Skill> skillsCache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SkillsManager() {
        // 默认使用项目根目录下的 skills 文件夹
        this.skillsDir = "skills";
    }

    public SkillsManager(String skillsDir) {
        this.skillsDir = skillsDir;
    }

    /**
     * 获取skills目录
     */
    public String getSkillsDir() {
        return skillsDir;
    }

    /**
     * 设置skills目录
     */
    public void setSkillsDir(String skillsDir) {
        this.skillsDir = skillsDir;
    }

    /**
     * 加载单个skill
     * 对齐 Go：LoadSkill
     */
    public Skill loadSkill(String skillName) {
        // 先尝试读锁检查缓存
        lock.readLock().lock();
        try {
            if (skillsCache.containsKey(skillName)) {
                return skillsCache.get(skillName);
            }
        } finally {
            lock.readLock().unlock();
        }

        // 构建skill路径
        Path skillPath = Paths.get(skillsDir, skillName);

        // 检查目录是否存在
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            logger.warn("Skill {} not found", skillName);
            return null;
        }

        // 查找SKILL.md文件
        Path skillFile = findSkillFile(skillPath);
        if (skillFile == null) {
            logger.warn("Skill file not found for {}", skillName);
            return null;
        }

        // 读取skill文件
        try {
            String content = Files.readString(skillFile);
            
            // 解析skill内容
            Skill skill = parseSkillContent(content, skillName, skillPath.toString());
            
            // 使用写锁缓存skill（双重检查，避免重复加载）
            lock.writeLock().lock();
            try {
                // 再次检查，可能其他线程已经加载了
                if (skillsCache.containsKey(skillName)) {
                    return skillsCache.get(skillName);
                }
                skillsCache.put(skillName, skill);
            } finally {
                lock.writeLock().unlock();
            }

            return skill;
        } catch (IOException e) {
            logger.error("Failed to read skill file for {}: {}", skillName, e.getMessage());
            return null;
        }
    }

    /**
     * 获取单个skill的详细信息（包含文件信息）
     * 对齐 Go：handler GetSkill
     */
    public Map<String, Object> getSkill(String skillName) {
        Skill skill = loadSkill(skillName);
        if (skill == null) {
            return null;
        }

        Path skillPath = Paths.get(skillsDir, skillName);
        Path skillFile = findSkillFile(skillPath);
        
        long fileSize = 0;
        String modTime = "";
        if (skillFile != null && Files.exists(skillFile)) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(skillFile, BasicFileAttributes.class);
                fileSize = attrs.size();
                modTime = attrs.lastModifiedTime().toInstant().toString();
            } catch (IOException e) {
                logger.warn("Failed to read file attributes for {}: {}", skillName, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", skill.getName());
        result.put("description", skill.getDescription());
        result.put("content", skill.getContent());
        result.put("path", skill.getPath());
        result.put("file_size", fileSize);
        result.put("mod_time", modTime);
        
        return result;
    }

    /**
     * 批量加载skills
     * 对齐 Go：LoadSkills
     */
    public List<Skill> loadSkills(List<String> skillNames) {
        List<Skill> skills = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String name : skillNames) {
            Skill skill = loadSkill(name);
            if (skill == null) {
                errors.add("failed to load skill: " + name);
                logger.warn("加载skill失败: {}", name);
            } else {
                skills.add(skill);
            }
        }

        if (!errors.isEmpty() && skills.isEmpty()) {
            logger.error("Failed to load any skills: {}", String.join("; ", errors));
            return Collections.emptyList();
        }

        return skills;
    }

    /**
     * 列出所有可用的skills
     * 对齐 Go：ListSkills
     */
    public List<String> listSkills() {
        Path dir = Paths.get(skillsDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }

        try (var entries = Files.list(dir)) {
            List<String> skills = new ArrayList<>();
            entries.filter(Files::isDirectory)
                   .forEach(entry -> {
                       String skillName = entry.getFileName().toString();
                       // 检查是否有SKILL.md文件
                       Path skillFile = entry.resolve("SKILL.md");
                       if (Files.exists(skillFile)) {
                           skills.add(skillName);
                           return;
                       }

                       // 尝试其他可能的文件名
                       String[] alternatives = {"skill.md", "README.md", "readme.md"};
                       for (String alt : alternatives) {
                           if (Files.exists(entry.resolve(alt))) {
                               skills.add(skillName);
                               break;
                           }
                       }
                   });
            return skills;
        } catch (IOException e) {
            logger.error("Failed to read skills directory: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取所有skills列表（包含详细信息，支持搜索和分页）
     * 对齐 Go：handler GetSkills
     */
    public Map<String, Object> getSkills(String searchKeyword, int page, int size) {
        List<String> skillList = listSkills();

        // 先加载所有skills的详细信息用于搜索过滤
        List<Map<String, Object>> allSkillsInfo = new ArrayList<>();
        for (String skillName : skillList) {
            Skill skill = loadSkill(skillName);
            if (skill == null) {
                continue;
            }

            // 获取文件信息
            Path skillPath = Paths.get(skillsDir, skillName);
            Path skillFile = findSkillFile(skillPath);

            long fileSize = 0;
            String modTime = "";
            if (skillFile != null && Files.exists(skillFile)) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(skillFile, BasicFileAttributes.class);
                    fileSize = attrs.size();
                    modTime = attrs.lastModifiedTime().toInstant().toString();
                } catch (IOException e) {
                    logger.warn("Failed to read file attributes: {}", e.getMessage());
                }
            }

            Map<String, Object> skillInfo = new HashMap<>();
            skillInfo.put("name", skill.getName());
            skillInfo.put("description", skill.getDescription());
            skillInfo.put("path", skill.getPath());
            skillInfo.put("file_size", fileSize);
            skillInfo.put("mod_time", modTime);
            allSkillsInfo.add(skillInfo);
        }

        // 如果有搜索关键词，进行过滤
        List<Map<String, Object>> filteredSkillsInfo = allSkillsInfo;
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            String keywordLower = searchKeyword.toLowerCase();
            filteredSkillsInfo = allSkillsInfo.stream()
                    .filter(skillInfo -> {
                        String name = ((String) skillInfo.getOrDefault("name", "")).toLowerCase();
                        String description = ((String) skillInfo.getOrDefault("description", "")).toString().toLowerCase();
                        String path = ((String) skillInfo.getOrDefault("path", "")).toLowerCase();
                        return name.contains(keywordLower) ||
                                description.contains(keywordLower) ||
                                path.contains(keywordLower);
                    })
                    .collect(Collectors.toList());
        }

        // 计算分页
        int total = filteredSkillsInfo.size();
        int offset = (page - 1) * size;
        int start = offset;
        int end = Math.min(offset + size, total);

        List<Map<String, Object>> paginatedSkillsInfo;
        if (start < end && start < total) {
            paginatedSkillsInfo = filteredSkillsInfo.subList(start, end);
        } else {
            paginatedSkillsInfo = Collections.emptyList();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("skills", paginatedSkillsInfo);
        result.put("total", total);
        result.put("size", size);
        result.put("page", page);

        return result;
    }
    /**
     * 创建新skill
     * 对齐 Go：handler CreateSkill
     */
    public Map<String, Object> createSkill(String name, String description, String content) {
        // 验证skill名称（只允许字母、数字、连字符和下划线）
        if (!isValidSkillName(name)) {
            logger.warn("Invalid skill name: {}", name);
            return null;
        }

        // 创建skill目录
        Path skillDir = Paths.get(skillsDir, name);
        try {
            Files.createDirectories(skillDir);
        } catch (IOException e) {
            logger.error("Failed to create skill directory: {}", e.getMessage());
            return null;
        }

        // 检查是否已存在
        Path skillFile = skillDir.resolve("SKILL.md");
        if (Files.exists(skillFile)) {
            logger.warn("Skill already exists: {}", name);
            return null;
        }

        // 构建SKILL.md内容
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("---\n");
        contentBuilder.append("name: ").append(name).append("\n");
        if (description != null && !description.isEmpty()) {
            // 如果描述包含特殊字符，需要加引号
            if (description.contains(":") || description.contains("\n")) {
                description = "\"" + description.replace("\"", "\\\"") + "\"";
            }
            contentBuilder.append("description: ").append(description).append("\n");
        }
        contentBuilder.append("version: 1.0.0\n");
        contentBuilder.append("---\n\n");
        contentBuilder.append(content);

        // 写入文件
        try {
            Files.writeString(skillFile, contentBuilder.toString(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            logger.error("Failed to create skill file: {}", e.getMessage());
            return null;
        }

        logger.info("Created skill: {}", name);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "skill已创建");
        result.put("skill", Map.of("name", name, "path", skillDir.toString()));
        
        return result;
    }

    /**
     * 更新skill
     * 对齐 Go：handler UpdateSkill
     */
    public Map<String, Object> updateSkill(String skillName, String description, String content) {
        // 获取skills目录
        Path skillDir = Paths.get(skillsDir, skillName);
        
        // 查找skill文件
        Path skillFile = findSkillFile(skillDir);
        if (skillFile == null) {
            logger.warn("Skill not found: {}", skillName);
            return null;
        }

        // 读取现有文件以保留front matter中的name
        String existingContent;
        try {
            existingContent = Files.readString(skillFile);
        } catch (IOException e) {
            logger.error("Failed to read skill file: {}", e.getMessage());
            return null;
        }

        // 解析现有内容，提取name
        String existingName = skillName;
        if (existingContent.startsWith("---")) {
            String[] parts = existingContent.split("---", 3);
            if (parts.length >= 2) {
                String frontMatter = parts[1];
                String[] lines = frontMatter.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("name:")) {
                        String name = line.substring(5).trim();
                        name = name.replace("\"", "").replace("'", "");
                        if (!name.isEmpty()) {
                            existingName = name;
                        }
                        break;
                    }
                }
            }
        }

        // 构建新的SKILL.md内容
        StringBuilder newContent = new StringBuilder();
        newContent.append("---\n");
        newContent.append("name: ").append(existingName).append("\n");
        if (description != null && !description.isEmpty()) {
            // 如果描述包含特殊字符，需要加引号
            if (description.contains(":") || description.contains("\n")) {
                description = "\"" + description.replace("\"", "\\\"") + "\"";
            }
            newContent.append("description: ").append(description).append("\n");
        }
        newContent.append("version: 1.0.0\n");
        newContent.append("---\n\n");
        newContent.append(content);

        // 写入文件（统一使用SKILL.md）
        Path targetFile = skillDir.resolve("SKILL.md");
        try {
            Files.writeString(targetFile, newContent.toString());
        } catch (IOException e) {
            logger.error("Failed to update skill file: {}", e.getMessage());
            return null;
        }

        // 如果原文件不是SKILL.md，删除旧文件
        if (!skillFile.equals(targetFile)) {
            try {
                Files.delete(skillFile);
            } catch (IOException e) {
                logger.warn("Failed to delete old skill file: {}", e.getMessage());
            }
        }

        // 清除缓存
        lock.writeLock().lock();
        try {
            skillsCache.remove(skillName);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Updated skill: {}", skillName);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "skill已更新");
        
        return result;
    }

    /**
     * 删除skill
     * 对齐 Go：handler DeleteSkill
     */
    public Map<String, Object> deleteSkill(String skillName) {
        Path skillDir = Paths.get(skillsDir, skillName);
        
        if (!Files.exists(skillDir)) {
            logger.warn("Skill not found: {}", skillName);
            return null;
        }

        // 删除skill目录
        try {
            Files.walk(skillDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete: {}", path);
                    }
                });
        } catch (IOException e) {
            logger.error("Failed to delete skill: {}", e.getMessage());
            return null;
        }

        // 清除缓存
        lock.writeLock().lock();
        try {
            skillsCache.remove(skillName);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Deleted skill: {}", skillName);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "skill已删除");
        
        return result;
    }

    /**
     * 获取skill的完整内容（用于注入到系统提示词）
     * 对齐 Go：GetSkillContent
     */
    public String getSkillContent(List<String> skillNames) {
        List<Skill> skills = loadSkills(skillNames);
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("## 可用Skills\n\n");
        builder.append("在执行任务前，请仔细阅读以下skills内容，这些内容包含了相关的专业知识和方法：\n\n");

        for (Skill skill : skills) {
            builder.append("### Skill: ").append(skill.getName()).append("\n");
            if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                builder.append("**描述**: ").append(skill.getDescription()).append("\n\n");
            }
            builder.append(skill.getContent());
            builder.append("\n\n---\n\n");
        }

        return builder.toString();
    }

    /**
     * 验证skill名称是否有效
     * 对齐 Go：isValidSkillName
     */
    public boolean isValidSkillName(String name) {
        if (name == null || name.isEmpty() || name.length() > 100) {
            return false;
        }
        // 只允许字母、数字、连字符和下划线
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_-]+$");
        return pattern.matcher(name).matches();
    }

    /**
     * 查找skill文件
     */
    private Path findSkillFile(Path skillPath) {
        Path skillFile = skillPath.resolve("SKILL.md");
        if (Files.exists(skillFile)) {
            return skillFile;
        }

        // 尝试其他可能的文件名
        String[] alternatives = {"skill.md", "README.md", "readme.md"};
        for (String alt : alternatives) {
            Path altPath = skillPath.resolve(alt);
            if (Files.exists(altPath)) {
                return altPath;
            }
        }
        
        return null;
    }

    /**
     * 解析skill内容
     * 支持YAML front matter格式
     * 对齐 Go：parseSkillContent
     */
    private Skill parseSkillContent(String content, String skillName, String skillPath) {
        Skill skill = new Skill();
        skill.setName(skillName);
        skill.setPath(skillPath);

        // 检查是否有YAML front matter
        if (content.startsWith("---")) {
            String[] parts = content.split("---", 3);
            if (parts.length >= 3) {
                // 解析front matter（简单实现，只提取name和description）
                String frontMatter = parts[1];
                String[] lines = frontMatter.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("name:")) {
                        String name = line.substring(5).trim();
                        name = name.replace("\"", "").replace("'", "");
                        if (!name.isEmpty()) {
                            skill.setName(name);
                        }
                    } else if (line.startsWith("description:")) {
                        String desc = line.substring(12).trim();
                        desc = desc.replace("\"", "").replace("'", "");
                        skill.setDescription(desc);
                    }
                }
                // 剩余部分是内容
                skill.setContent(parts[2].trim());
            } else {
                // 没有front matter，整个内容就是skill内容
                skill.setContent(content);
            }
        } else {
            // 没有front matter，整个内容就是skill内容
            skill.setContent(content);
        }

        // 如果内容为空，使用描述作为内容
        if (skill.getContent() == null || skill.getContent().isEmpty()) {
            skill.setContent(skill.getDescription());
        }

        return skill;
    }
}
