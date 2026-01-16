<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Delete, View, Timer, Check, Close } from '@element-plus/icons-vue';

interface ToolExecution {
  id: string;
  conversationId?: string;
  messageId?: string;
  toolName: string;
  arguments?: string;
  result?: string;
  status: string;
  durationMs?: number;
  createdAt?: string;
}

interface Stats {
  totalExecutions: number;
  statusCounts: Record<string, number>;
  toolStats: { toolName: string; count: number; avgDurationMs: number }[];
}

const executions = ref<ToolExecution[]>([]);
const stats = ref<Stats>({ totalExecutions: 0, statusCounts: {}, toolStats: [] });
const loading = ref(false);
const searchQuery = ref('');
const dialogVisible = ref(false);
const selectedExecution = ref<ToolExecution | null>(null);

const filteredExecutions = computed(() => {
  if (!searchQuery.value) return executions.value;
  const q = searchQuery.value.toLowerCase();
  return executions.value.filter(e => 
    e.toolName.toLowerCase().includes(q) ||
    e.id.toLowerCase().includes(q)
  );
});

const loadExecutions = async () => {
  loading.value = true;
  try {
    const res = await fetch('/api/monitor?limit=100');
    if (res.ok) {
      executions.value = await res.json();
    }
  } catch (e) {
    ElMessage.error('加载执行记录失败');
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const res = await fetch('/api/monitor/stats');
    if (res.ok) {
      stats.value = await res.json();
    }
  } catch (e) {
    console.error('Failed to load stats');
  }
};

const handleViewDetails = (exec: ToolExecution) => {
  selectedExecution.value = exec;
  dialogVisible.value = true;
};

const handleClearAll = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有执行记录吗?', '警告', { type: 'warning' });
    const res = await fetch('/api/monitor/executions', { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('已清空');
      loadExecutions();
      loadStats();
    }
  } catch (e) {
    // cancelled
  }
};

const getStatusType = (status: string) => {
  switch (status) {
    case 'success': return 'success';
    case 'error': return 'danger';
    case 'timeout': return 'warning';
    default: return 'info';
  }
};

const formatDuration = (ms?: number) => {
  if (!ms) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
};

const formatJson = (str?: string) => {
  if (!str) return '';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
};

onMounted(() => {
  loadExecutions();
  loadStats();
});
</script>

<template>
  <div class="monitor-view">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalExecutions }}</div>
        <div class="stat-label">总执行次数</div>
      </div>
      <div class="stat-card success">
        <div class="stat-value">{{ stats.statusCounts?.success || 0 }}</div>
        <div class="stat-label">成功</div>
      </div>
      <div class="stat-card error">
        <div class="stat-value">{{ stats.statusCounts?.error || 0 }}</div>
        <div class="stat-label">失败</div>
      </div>
      <div class="stat-card timeout">
        <div class="stat-value">{{ stats.statusCounts?.timeout || 0 }}</div>
        <div class="stat-label">超时</div>
      </div>
    </div>

    <!-- 热门工具 -->
    <div class="tool-stats" v-if="stats.toolStats?.length">
      <h4>工具使用统计</h4>
      <div class="tool-chips">
        <el-tag 
          v-for="tool in stats.toolStats.slice(0, 10)" 
          :key="tool.toolName"
          type="info"
          effect="plain"
        >
          {{ tool.toolName }} ({{ tool.count }}次, 平均{{ formatDuration(tool.avgDurationMs) }})
        </el-tag>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索工具名或 ID..."
        clearable
        style="width: 250px"
      />
      <div class="actions">
        <el-button :icon="Refresh" @click="loadExecutions">刷新</el-button>
        <el-button type="danger" :icon="Delete" @click="handleClearAll">清空记录</el-button>
      </div>
    </div>

    <!-- 执行列表 -->
    <div class="exec-list" v-loading="loading">
      <el-table :data="filteredExecutions" stripe style="width: 100%">
        <el-table-column prop="toolName" label="工具名称" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ formatDuration(row.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column label="执行时间" width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="280">
          <template #default="{ row }">
            <span class="id-text">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="View" @click="handleViewDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="执行信息"
      width="700px"
    >
      <template v-if="selectedExecution">
        <div class="detail-section">
          <div class="detail-row">
            <span class="label">工具:</span>
            <span class="value">{{ selectedExecution.toolName }}</span>
          </div>
          <div class="detail-row">
            <span class="label">状态:</span>
            <el-tag :type="getStatusType(selectedExecution.status)" size="small">
              {{ selectedExecution.status }}
            </el-tag>
          </div>
          <div class="detail-row">
            <span class="label">耗时:</span>
            <span class="value">{{ formatDuration(selectedExecution.durationMs) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">ID:</span>
            <span class="value id-text">{{ selectedExecution.id }}</span>
          </div>
        </div>
        
        <div class="detail-section">
          <h4>请求参数</h4>
          <pre class="code-block">{{ formatJson(selectedExecution.arguments) }}</pre>
        </div>
        
        <div class="detail-section">
          <h4>调用结果</h4>
          <pre class="code-block result">{{ selectedExecution.result || '(无结果)' }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.monitor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
}

.stats-row {
  display: flex;
  gap: 12px;
}

.stat-card {
  flex: 1;
  min-width: 120px;
  background: white;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  border-left: 4px solid #409eff;
}

.stat-card.success { border-left-color: #67c23a; }
.stat-card.error { border-left-color: #f56c6c; }
.stat-card.timeout { border-left-color: #e6a23c; }

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.tool-stats {
  background: white;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.tool-stats h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #606266;
}

.tool-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.actions {
  display: flex;
  gap: 8px;
}

.exec-list {
  flex: 1;
  overflow: hidden;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.id-text {
  font-family: monospace;
  font-size: 11px;
  color: #909399;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.detail-row .label {
  font-weight: 500;
  color: #606266;
  width: 60px;
}

.detail-row .value {
  color: #303133;
}

.detail-section h4 {
  margin: 12px 0 8px 0;
  font-size: 14px;
  color: #606266;
}

.code-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.code-block.result {
  max-height: 300px;
}
</style>
