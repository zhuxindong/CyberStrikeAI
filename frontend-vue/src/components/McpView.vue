<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import MCPDialog from './MCPDialog.vue';

interface Tool {
  name: string;
  description: string;
  source: string;
  enabled: boolean;
}

export interface McpServer {
  id: string;
  name: string;
  transport: string;
  url?: string;
  description?: string;
  timeout?: number;
  command?: string;
  args?: string;
  enabled?: string;
  toolEnabled?: string;
  status: string;
  toolCount: number;
  lastConnectedAt?: string;
}

const tools = ref<Tool[]>([]);
const servers = ref<McpServer[]>([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const toolStats = ref({
  page_enabled: 0,
  page_total: 0,
  total: 0,
  total_enabled: 0
});
const MCPStats = ref({
  disabled: 0,
  enabled: 0,
  status: 0,
  total: 0
});
const searchQuery = ref('');
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentServer = ref<McpServer>({
  id: '',
  name: '',
  transport: 'stdio',
  url: '',
  command: '',
  args: '',
  status: 'disconnected',
  toolCount: 0
});

// 工具配置修改缓存
const toolModifyCache = ref<any>({});

const loadTools = async () => {
  try {
    const params: any = {
      page: currentPage.value,
      page_size: pageSize.value,
      search: searchQuery.value
    };
    const query = Object.keys(params).map((key: string) => {
      return `${key}=${params[key]}`;
    });
    const url = '/api/config/tools?' + query.join('&');

    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      tools.value = data.tools || [];
      total.value = data.total;
      let page_enabled = 0, page_total = Math.min(pageSize.value, tools.value.length);
      tools.value.forEach((tool: Tool) => {
        if (tool.enabled) {
          page_enabled++;
        }
      });
      toolStats.value = {
        page_enabled,
        page_total,
        total_enabled: data.total_enabled,
        total: total.value
      };
      toolModifyCache.value = {};
    }
  } catch (e) {
    console.error('Failed to load tools');
  }
};

const loadServers = async () => {
  loading.value = true;
  try {
    const res = await fetch('/api/mcp/servers');
    if (res.ok) {
      servers.value = await res.json();
    }
  } catch (e) {
    ElMessage.error('加载 MCP 服务器失败');
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const res = await fetch('/api/mcp/stats');
    if (res.ok) {
      MCPStats.value = await res.json();
    }
  } catch (e) {
    console.error('Failed to load stats');
  }
};

// 保存工具配置
const saveSettings = async () => {
  const body = Object.keys(toolModifyCache.value).map((key: string) => {
    const enabled: boolean = toolModifyCache.value[key];
    return {
      name: key,
      enabled
    };
  });
  const res = await fetch('/api/config/tools/update', {
    method: 'post',
    headers: {
      'Content-type': 'application/json'
    },
    body: JSON.stringify(body)
  });
  if (res.ok) {
    ElMessage.success('保存配置成功');
    loadTools();
    toolModifyCache.value = {};
  } else {
    ElMessage.error('保存配置失败');
  }
};

// 切换全选
const toggleSelectAll = (flag: boolean) => {
  let modifiedCount = 0;
  tools.value = tools.value.map((tool: Tool) => {
    toolModifyCache.value[tool.name] = flag;
    if (tool.enabled !== flag) {
      tool.enabled = flag;
      modifiedCount++;
    }
    return tool;
  });
  if (flag) {
    toolStats.value.page_enabled += modifiedCount;
    toolStats.value.total_enabled += modifiedCount;
  } else {
    toolStats.value.page_enabled -= modifiedCount;
    toolStats.value.total_enabled -= modifiedCount;
  }
};

// 选框事件
const onToolEnableChange = (tool: Tool) => {
  toolModifyCache.value[tool.name] = tool.enabled;
  if (tool.enabled) {
    toolStats.value.page_enabled++;
    toolStats.value.total_enabled++;
  } else {
    toolStats.value.page_enabled--;
    toolStats.value.total_enabled--;
  }
};

// 翻页事件
const onPageChange = (page: number) => {
  currentPage.value = page;
  loadTools();
};

const handleAddServer = () => {
  isEdit.value = false;
  currentServer.value = {
    id: '',
    name: '',
    transport: 'stdio',
    url: '',
    command: '',
    args: '',
    status: 'disconnected',
    toolCount: 0
  };
  dialogVisible.value = true;
};

const handleEditServer = (server: McpServer) => {
  isEdit.value = true;
  currentServer.value = { ...server };
  dialogVisible.value = true;
};

const handleDeleteServer = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定要删除此 MCP 服务器吗?', '警告', { type: 'warning' });
    const res = await fetch(`/api/mcp/servers/${id}`, { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('删除成功');
      loadServers();
      loadStats();
    }
  } catch (e) {
    // cancelled
  }
};

const handleConnect = async (server: McpServer) => {
  try {
    const res = await fetch(`/api/mcp/servers/${server.id}/connect`, { method: 'POST' });
    if (res.ok) {
      ElMessage.success('连接成功');
      loadServers();
      loadStats();
    } else {
      ElMessage.error('连接失败');
    }
  } catch (e) {
    ElMessage.error('连接出错');
  }
};

const handleDisconnect = async (server: McpServer) => {
  try {
    const res = await fetch(`/api/mcp/servers/${server.id}/disconnect`, { method: 'POST' });
    if (res.ok) {
      ElMessage.success('已断开连接');
      loadServers();
      loadStats();
    }
  } catch (e) {
    ElMessage.error('断开连接失败');
  }
};

const getStatusType = (status: string) => {
  switch (status) {
    case 'connected': return 'success';
    case 'error': return 'danger';
    default: return 'info';
  }
};

const getStatusName = (status: string) => {
  return {
    connected: '已连接',
    disconnected: '未连接',
    error: '连接失败',
  }[status];
}

onMounted(() => {
  loadTools();
  loadServers();
  loadStats();
});
</script>

<template>
  <div class="mcp-view">
    <!-- 工具配置区域 -->
    <div class="section tools-section">
      <div class="section-header">
        <h3>MCP 工具配置</h3>
        <div class="header-actions">
          <el-button icon="Setting" type="primary" @click="saveSettings">保存工具配置</el-button>
        </div>
      </div>

      <div class="tools-controls">
        <el-button @click="toggleSelectAll(true)">全选</el-button>
        <el-button @click="toggleSelectAll(false)">全不选</el-button>
        <el-input v-model="searchQuery" placeholder="搜索工具..." clearable @keydown.enter="loadTools">
          <template #append>
            <el-button @click="loadTools">🔍</el-button>
          </template>
        </el-input>
        <div class="tools-stats">
          <span>
            ✅ 当前页已启用:
            <strong>{{ toolStats.page_enabled }}</strong>
            / {{ toolStats.page_total }}
          </span>
          <span>
            📊 总计已启用:
            <strong>{{ toolStats.total_enabled }}</strong>
            / {{ toolStats.total }}
          </span>
        </div>
      </div>

      <div class="tools-list">
        <el-scrollbar height="300px">
          <div v-for="tool in tools" :key="tool.name" class="tool-item">
            <div class="tool-info">
              <el-checkbox v-model="tool.enabled" @change="onToolEnableChange(tool)" />
              <span class="tool-name">{{ tool.name }}</span>
            </div>
            <div class="tool-desc">{{ tool.description }}</div>
          </div>
          <el-empty v-if="tools.length === 0" description="未找到匹配的工具" />
        </el-scrollbar>
        <el-pagination background layout="->, prev, pager, next, total" hide-on-single-page :total="total"
          :page-size="pageSize" :current-change="currentPage" @current-change="onPageChange" />
      </div>
    </div>

    <!-- 外部 MCP 配置区域 -->
    <div class="section">
      <div class="section-header">
        <h3>外部 MCP 配置</h3>
        <div class="header-actions">
          <el-button type="primary" icon="Plus" @click="handleAddServer">添加外部MCP</el-button>
        </div>
      </div>
      <div class="external-mcp-controls">
        <div class="external-mcp-actions">
          <div class="external-mcp-stats">
            <span>
              📊 总数:
              <strong>{{ MCPStats.total }}</strong>
            </span>
            <span>
              ✅ 已启用:
              <strong>{{ MCPStats.enabled }}</strong>
            </span>
            <span>
              ⏸ 已停用:
              <strong>{{ MCPStats.disabled }}</strong>
            </span>
            <span>
              🔗 已连接:
              <strong>{{ MCPStats.status }}</strong>
            </span>
          </div>
        </div>
        <div class="external-mcp-list">
          <div v-if="servers.length" class="external-mcp-items">
            <div class="external-mcp-item" v-for="server in servers" :key="server.id">
              <div class="external-mcp-item-header">
                <div class="external-mcp-item-info">
                  <h4>{{ server.name }}</h4>
                  <el-tag :type="getStatusType(server.status)">{{ getStatusName(server.status) }}</el-tag>
                </div>
                <div class="external-mcp-item-actions">
                  <el-button v-if="server.status !== 'connected'" type="primary"
                    @click="handleConnect(server)">启动</el-button>
                  <el-button v-else type="danger" @click="handleDisconnect(server)">停止</el-button>
                  <el-button @click="handleEditServer(server)">编辑</el-button>
                  <el-button type="danger" @click="handleDeleteServer(server.id)">删除</el-button>
                </div>
              </div>
              <div class="external-mcp-item-details">
                <div>
                  <span>传输模式</span>
                  <strong>{{ server.transport }}</strong>
                </div>
                <div v-if="server.toolCount > 0">
                  <span>工具数量</span>
                  <span>🔧 {{ server.toolCount }} 个工具</span>
                </div>
                <div v-if="server.description">
                  <span>描述</span>
                  <span>{{ server.description }}</span>
                </div>
                <div v-if="server.timeout">
                  <span>超时时间</span>
                  <strong>{{ server.timeout }}</strong>
                </div>
                <div v-if="server.command">
                  <span>命令</span>
                  <code :title="server.command">{{ server.command }}</code>
                </div>
                <div v-if="server.url">
                  <span>URL</span>
                  <code>{{ server.url }}</code>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="external-mcp-empty">
            📋 暂无外部MCP配置
            <br>
            <span>点击"添加外部MCP"按钮开始配置</span>
          </div>
        </div>
      </div>
    </div>

    <MCPDialog v-model:visible="dialogVisible" :is-edit="isEdit" :server-info="currentServer"
      @loadServers="loadServers" />
  </div>
</template>

<style lang="scss" scoped>
.mcp-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
  overflow: auto;
}

.section {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
}

.tools-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.tools-controls {
  display: flex;
  margin: 12px 0;

  .el-input {
    height: 100%;
    margin: 0 12px;
  }

  .tools-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: nowrap;
    width: 100%;
    overflow: visible;
  }

  .search-box {
    display: flex;
    gap: 4px;
    flex: 1;
    min-width: 150px;
    align-items: center;
  }

  .tools-stats {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-left: auto;
    padding: 6px 12px;
    background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
    border: 1px solid var(--border-color);
    border-radius: 10px;
    font-size: 0.75rem;
    color: var(--text-secondary);
    box-shadow: var(--shadow-sm);
    flex-wrap: nowrap;
    flex-shrink: 1;
    white-space: nowrap;

    span {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 0;
      white-space: nowrap;
      flex-shrink: 0;

      &:not(:last-child)::after {
        content: '';
        width: 1px;
        height: 16px;
        background: var(--border-color);
        margin-left: 8px;
        display: inline-block;
      }

      strong {
        color: var(--text-primary);
        font-weight: 600;
        margin-left: 4px;
      }
    }
  }


}

.tools-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 12px;
  padding: 8px 16px;

  .el-pagination {
    margin: 12px 0;
  }
}

.tool-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.tool-item:last-child {
  border-bottom: none;
}

.tool-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.tool-name {
  font-weight: 500;
  color: #303133;
  font-family: monospace;
}

.tool-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.servers-list {
  margin-top: 8px;
}

.mono-text {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

.external-mcp-controls {
  .external-mcp-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    .external-mcp-stats {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-left: auto;
      margin-bottom: 12px;
      padding: 10px 20px;
      background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      font-size: 0.875rem;
      color: var(--text-secondary);
      box-shadow: var(--shadow-sm);

      >span {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 4px 0;
        white-space: nowrap;
      }

      strong {
        color: var(--text-primary);
        font-weight: 600;
        margin-left: 4px;
      }
    }
  }

  .external-mcp-list {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .external-mcp-items {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .external-mcp-item {
      background: var(--bg-primary);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 20px;
      box-shadow: var(--shadow-sm);
      transition: all 0.2s ease;
      display: flex;
      flex-direction: column;
      gap: 16px;

      &:hover {
        box-shadow: var(--shadow-md);
        border-color: var(--accent-color);
        transform: translateY(-2px);
      }

      .external-mcp-item-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        flex-wrap: wrap;

        .external-mcp-item-info {
          display: flex;
          align-items: center;
          gap: 12px;
          flex: 1;
          min-width: 0;

          h4 {
            margin: 0;
            font-size: 1.125rem;
            font-weight: 600;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
          }
        }
      }

      .external-mcp-item-details {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        padding-top: 16px;
        border-top: 1px solid var(--border-color);

        >div {
          display: flex;
          flex-direction: column;
          gap: 6px;
          padding: 12px;
          background: var(--bg-secondary);
          border-radius: 8px;
          border: 1px solid var(--border-color);
          transition: all 0.2s ease;

          strong {
            font-size: 0.75rem;
            font-weight: 600;
            color: var(--text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 2px;
          }

          span {
            font-size: 0.875rem;
            color: var(--text-primary);
            word-break: break-word;
            line-height: 1.5;
          }

          code {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }
      }
    }

    .external-mcp-empty {
      margin-top: 8px;
      text-align: center;
      padding: 48px 24px;
      color: var(--text-muted);
      font-size: 0.9375rem;
      background: var(--bg-secondary);
      border: 2px dashed var(--border-color);
      border-radius: 12px;
    }
  }
}
</style>
