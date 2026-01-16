<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search, Delete, Edit, Connection, Link, Check, Close } from '@element-plus/icons-vue';

interface Tool {
  name: string;
  description: string;
  source: string;
  enabled: boolean;
}

interface McpServer {
  id: string;
  name: string;
  transport: string;
  url?: string;
  command?: string;
  args?: string;
  status: string;
  toolCount: number;
  lastConnectedAt?: string;
}

const tools = ref<Tool[]>([]);
const servers = ref<McpServer[]>([]);
const stats = ref({ totalServers: 0, connectedServers: 0, totalTools: 0 });
const loading = ref(false);
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

const filteredTools = computed(() => {
  if (!searchQuery.value) return tools.value;
  const q = searchQuery.value.toLowerCase();
  return tools.value.filter(t => 
    t.name.toLowerCase().includes(q) || 
    t.description?.toLowerCase().includes(q)
  );
});

const loadTools = async () => {
  try {
    const res = await fetch('/api/mcp/tools');
    if (res.ok) {
      const data = await res.json();
      tools.value = data.tools || [];
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
      stats.value = await res.json();
    }
  } catch (e) {
    console.error('Failed to load stats');
  }
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

const handleSaveServer = async () => {
  if (!currentServer.value.name) {
    ElMessage.warning('请填写服务器名称');
    return;
  }
  
  try {
    const url = isEdit.value ? `/api/mcp/servers/${currentServer.value.id}` : '/api/mcp/servers';
    const method = isEdit.value ? 'PUT' : 'POST';
    
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentServer.value)
    });
    
    if (res.ok) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      loadServers();
      loadStats();
    }
  } catch (e) {
    ElMessage.error('保存失败');
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

const handleRefreshTools = async () => {
  try {
    const res = await fetch('/api/mcp/refresh-tools', { method: 'POST' });
    if (res.ok) {
      ElMessage.success('工具列表已刷新');
      loadTools();
    }
  } catch (e) {
    ElMessage.error('刷新失败');
  }
};

const getStatusType = (status: string) => {
  switch (status) {
    case 'connected': return 'success';
    case 'error': return 'danger';
    default: return 'info';
  }
};

const getTransportLabel = (transport: string) => {
  switch (transport) {
    case 'http': return 'HTTP';
    case 'sse': return 'SSE';
    case 'stdio': return 'STDIO';
    default: return transport;
  }
};

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
          <el-input
            v-model="searchQuery"
            placeholder="搜索工具..."
            :prefix-icon="Search"
            clearable
            style="width: 200px"
          />
          <el-button :icon="Refresh" @click="handleRefreshTools">刷新工具列表</el-button>
        </div>
      </div>
      
      <div class="tools-stats">
        <el-tag type="success">已启用: {{ stats.totalTools }}</el-tag>
        <el-tag type="info">已禁用: 0</el-tag>
        <el-tag>总计: {{ stats.totalTools }}</el-tag>
      </div>
      
      <div class="tools-list">
        <el-scrollbar height="300px">
          <div 
            v-for="tool in filteredTools" 
            :key="tool.name" 
            class="tool-item"
          >
            <div class="tool-info">
              <span class="tool-name">{{ tool.name }}</span>
              <el-tag size="small" type="success">{{ tool.source }}</el-tag>
            </div>
            <div class="tool-desc">{{ tool.description }}</div>
          </div>
          <el-empty v-if="filteredTools.length === 0" description="未找到匹配的工具" />
        </el-scrollbar>
      </div>
    </div>

    <!-- 外部 MCP 配置区域 -->
    <div class="section servers-section">
      <div class="section-header">
        <h3>外部 MCP 配置</h3>
        <div class="header-actions">
          <el-tag type="success">已连接: {{ stats.connectedServers }}</el-tag>
          <el-tag type="info">已断开: {{ stats.totalServers - stats.connectedServers }}</el-tag>
          <el-button type="primary" :icon="Plus" @click="handleAddServer">添加外部 MCP</el-button>
        </div>
      </div>
      
      <div class="servers-list" v-loading="loading">
        <template v-if="servers.length === 0">
          <el-empty description="暂无外部 MCP 配置" />
        </template>
        <el-table v-else :data="servers" stripe>
          <el-table-column prop="name" label="服务器名称" width="150" />
          <el-table-column label="连接类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ getTransportLabel(row.transport) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="地址/命令" min-width="200">
            <template #default="{ row }">
              <span class="mono-text">{{ row.transport === 'stdio' ? row.command : row.url }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工具数" width="80" align="center">
            <template #default="{ row }">
              {{ row.toolCount || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ row.status === 'connected' ? '已连接' : '已断开' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button 
                v-if="row.status !== 'connected'" 
                link 
                type="success" 
                :icon="Connection" 
                @click="handleConnect(row)"
              >
                连接
              </el-button>
              <el-button 
                v-else 
                link 
                type="warning" 
                :icon="Close" 
                @click="handleDisconnect(row)"
              >
                断开
              </el-button>
              <el-button link :icon="Edit" @click="handleEditServer(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDeleteServer(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 MCP 服务器' : '添加外部 MCP'"
      width="500px"
    >
      <el-form label-position="top">
        <el-form-item label="服务器名称" required>
          <el-input v-model="currentServer.name" placeholder="输入服务器名称" />
        </el-form-item>
        
        <el-form-item label="连接类型">
          <el-radio-group v-model="currentServer.transport">
            <el-radio value="stdio">STDIO (进程)</el-radio>
            <el-radio value="http">HTTP</el-radio>
            <el-radio value="sse">SSE</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <template v-if="currentServer.transport === 'stdio'">
          <el-form-item label="命令">
            <el-input v-model="currentServer.command" placeholder="例如: python3" />
          </el-form-item>
          <el-form-item label="参数 (JSON 数组)">
            <el-input 
              v-model="currentServer.args" 
              type="textarea" 
              :rows="2"
              placeholder='例如: ["-m", "mcp_server"]' 
            />
          </el-form-item>
        </template>
        
        <template v-else>
          <el-form-item label="URL">
            <el-input v-model="currentServer.url" placeholder="https://mcp-server.example.com" />
          </el-form-item>
        </template>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveServer">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
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
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
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
  gap: 12px;
  align-items: center;
}

.tools-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.tools-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
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
</style>
