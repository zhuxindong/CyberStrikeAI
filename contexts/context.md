# CyberStrikeAI 项目上下文

## 项目概述

AI 原生的安全测试平台，从 Go/SQLite 重构为 Java Spring Boot + Vue 3 + MySQL。

## 技术栈

### 后端 (Java)

- **框架**: Spring Boot 3.2+
- **ORM**: Spring Data JPA (Hibernate)
- **数据库**: MySQL 8.0+
- **AI**: 原生 OpenAI API (RestClient)
- **YAML**: Jackson dataformat-yaml

### 前端 (Vue)

- **框架**: Vue 3 (Composition API)
- **构建**: Vite 5
- **UI**: Element Plus
- **包管理**: pnpm

## 核心模块

### Agent 模块 ✅ 100%

- **ReAct 循环**: 手动实现的 Think-Act-Observe 模式
- **SSE 流式输出**: `/api/agent-loop/stream`
- **任务取消**: `/api/agent-loop/cancel`
- **任务列表**: `/api/agent-loop/tasks` (运行中)
- **历史任务**: `/api/agent-loop/completed`
- **工具调用**: 通过 OpenAI Function Calling

### YAML 工具系统 ✅ NEW

支持从 `tools/*.yaml` 加载工具定义:

**核心类**:
- `YamlToolDefinition.java` - 工具定义模型
- `YamlToolLoader.java` - 工具加载器和执行器
- `ToolRegistry.java` - 统一工具注册中心

**功能**:
- 解析 YAML 工具定义
- 支持 114 个原项目工具
- 参数格式: positional, flag, combined, template
- 热加载刷新
- 启用/禁用切换

### 工具列表 (114+)

通过 YAML 定义:
- nmap, masscan, rustscan, arp-scan
- sqlmap, nikto, dirb, gobuster, feroxbuster, ffuf
- nuclei, wpscan, wafw00f, dalfox, xsser
- subfinder, amass, findomain, dnsenum
- fofa_search, zoomeye_search
- trivy, prowler, terrascan
- gdb, radare2, ghidra
- metasploit, hashcat, john
- volatility, steghide, exiftool
- linpeas, winpeas, mimikatz
- 等等...

### API 模块实现状态

| 模块 | 完成度 | 备注 |
| :--- | :---: | :--- |
| Auth | ✅ 100% | login/logout/validate/change-password |
| Agent | ✅ 100% | stream/cancel/tasks/completed |
| Conversation | ✅ 100% | CRUD + pinned |
| Vulnerability | ✅ 100% | CRUD + stats |
| Knowledge | 🔶 82% | 缺 scan, deleteLog, **vector search** |
| Monitor | ✅ 100% | executions + stats |
| Role | ✅ 100% | CRUD |
| Config | ✅ 100% | get/update/apply/tools |
| Group | ✅ 100% | CRUD + conversation management |
| **YAML Tools** | ✅ 100% | 114 个工具定义 |
| BatchTask | ❌ 0% | 待实现 |
| ExternalMCP | ❌ 0% | 待实现 |
| AttackChain | 🔶 50% | 缺 regenerate |

### 前端组件

| 组件 | 状态 | 功能 |
| :--- | :---: | :--- |
| App.vue | ✅ | 主布局，视图切换 |
| ChatWindow.vue | ✅ | 聊天界面，停止按钮 |
| Sidebar.vue | ✅ | 对话列表 |
| ToolsPanel.vue | ✅ | 工具列表 |
| ConfigView.vue | ✅ | 系统配置 |

## 配置

### 数据库

```yaml
spring.datasource.url: jdbc:mysql://127.0.0.1:3306/cbai
spring.datasource.username: root
spring.datasource.password: 8200506
```

### OpenAI

```yaml
spring.ai.openai.base-url: # 自定义 API 端点
spring.ai.openai.api-key: # API 密钥
spring.ai.openai.chat.options.model: # 模型名称
```

### 工具目录

```yaml
security.tools-dir: tools  # YAML 工具目录
```

## 运行

### 后端

```bash
cd backend-java
mvn spring-boot:run
# 访问: http://localhost:8080
```

### 前端

```bash
cd frontend-vue
pnpm install
pnpm dev
# 访问: http://localhost:5173
```

## 当前状态 (2026-01-15 16:45)

### 已完成

- [x] 后端核心 API 全部完成
- [x] Agent 模块全部接口
- [x] Group 模块全部接口
- [x] **YAML 工具配置系统** (NEW)
- [x] 114 个工具定义复用
- [x] 工具热加载
- [x] 前端聊天界面 + 停止按钮
- [x] 前端侧边栏
- [x] 前端工具面板
- [x] 前端配置页面

### 待完成

- [ ] BatchTask 模块 (批量任务)
- [ ] ExternalMCP 模块 (外部 MCP 服务器)
- [ ] 知识库向量检索 (search_knowledge_base)
- [ ] 攻击链重新生成
- [ ] 前端监控页面

## 下一步优先级

1. **BatchTask 模块** - 批量任务队列
2. **知识库向量检索** - search_knowledge_base
3. **ExternalMCP 模块** - 外部 MCP 集成
