<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search, Edit, Delete, Link, InfoFilled } from '@element-plus/icons-vue';

interface Vulnerability {
  id: string;
  title: string;
  description?: string;
  severity: string;
  status: string;
  targetUrl?: string;
  affectedComponent?: string;
  evidence?: string;
  recommendation?: string;
  conversationId?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface Stats {
  total: number;
  bySeverity: Record<string, number>;
  byStatus: Record<string, number>;
}

const vulnerabilities = ref<Vulnerability[]>([]);
const stats = ref<Stats>({ total: 0, bySeverity: {}, byStatus: {} });
const loading = ref(false);
const searchQuery = ref('');
const filterSeverity = ref('');
const filterStatus = ref('');
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentVuln = ref<Vulnerability>({
  id: '',
  title: '',
  severity: 'medium',
  status: 'open',
  description: '',
  targetUrl: '',
  recommendation: ''
});

const severityOptions = [
  { value: 'critical', label: '严重', color: '#f56c6c' },
  { value: 'high', label: '高危', color: '#e6a23c' },
  { value: 'medium', label: '中危', color: '#f4a460' },
  { value: 'low', label: '低危', color: '#67c23a' },
  { value: 'info', label: '信息', color: '#909399' }
];

const statusOptions = [
  { value: 'open', label: '待处理' },
  { value: 'confirmed', label: '已确认' },
  { value: 'fixed', label: '已修复' },
  { value: 'false_positive', label: '误报' }
];

const getSeverityColor = (severity: string) => {
  return severityOptions.find(s => s.value === severity)?.color || '#909399';
};

const getSeverityLabel = (severity: string) => {
  return severityOptions.find(s => s.value === severity)?.label || severity;
};

const getStatusLabel = (status: string) => {
  return statusOptions.find(s => s.value === status)?.label || status;
};

const filteredVulnerabilities = computed(() => {
  return vulnerabilities.value.filter(v => {
    const matchSearch = !searchQuery.value || 
      v.title.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      v.targetUrl?.toLowerCase().includes(searchQuery.value.toLowerCase());
    const matchSeverity = !filterSeverity.value || v.severity === filterSeverity.value;
    const matchStatus = !filterStatus.value || v.status === filterStatus.value;
    return matchSearch && matchSeverity && matchStatus;
  });
});

const loadVulnerabilities = async () => {
  loading.value = true;
  try {
    const res = await fetch('/api/vulnerabilities?limit=200');
    if (res.ok) {
      const data = await res.json();
      vulnerabilities.value = data.vulnerabilities || [];
    }
  } catch (e) {
    ElMessage.error('加载漏洞列表失败');
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const res = await fetch('/api/vulnerabilities/stats');
    if (res.ok) {
      stats.value = await res.json();
    }
  } catch (e) {
    console.error('Failed to load stats');
  }
};

const handleAdd = () => {
  isEdit.value = false;
  currentVuln.value = {
    id: '',
    title: '',
    severity: 'medium',
    status: 'open',
    description: '',
    targetUrl: '',
    recommendation: ''
  };
  dialogVisible.value = true;
};

const handleEdit = (vuln: Vulnerability) => {
  isEdit.value = true;
  currentVuln.value = { ...vuln };
  dialogVisible.value = true;
};

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定要删除此漏洞吗?', '警告', { type: 'warning' });
    const res = await fetch(`/api/vulnerabilities/${id}`, { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('删除成功');
      loadVulnerabilities();
      loadStats();
    }
  } catch (e) {
    // cancelled
  }
};

const handleSave = async () => {
  if (!currentVuln.value.title) {
    ElMessage.warning('请填写漏洞标题');
    return;
  }
  
  try {
    const url = isEdit.value ? `/api/vulnerabilities/${currentVuln.value.id}` : '/api/vulnerabilities';
    const method = isEdit.value ? 'PUT' : 'POST';
    
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentVuln.value)
    });
    
    if (res.ok) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      loadVulnerabilities();
      loadStats();
    } else {
      ElMessage.error('保存失败');
    }
  } catch (e) {
    ElMessage.error('保存出错');
  }
};

onMounted(() => {
  loadVulnerabilities();
  loadStats();
});
</script>

<template>
  <div class="vulns-view">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-label">总漏洞数</div>
      </div>
      <div class="stat-card critical">
        <div class="stat-value">{{ stats.bySeverity?.critical || 0 }}</div>
        <div class="stat-label">严重</div>
      </div>
      <div class="stat-card high">
        <div class="stat-value">{{ stats.bySeverity?.high || 0 }}</div>
        <div class="stat-label">高危</div>
      </div>
      <div class="stat-card medium">
        <div class="stat-value">{{ stats.bySeverity?.medium || 0 }}</div>
        <div class="stat-label">中危</div>
      </div>
      <div class="stat-card low">
        <div class="stat-value">{{ stats.bySeverity?.low || 0 }}</div>
        <div class="stat-label">低危</div>
      </div>
      <div class="stat-card info">
        <div class="stat-value">{{ stats.bySeverity?.info || 0 }}</div>
        <div class="stat-label">信息</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="searchQuery"
          placeholder="搜索漏洞..."
          :prefix-icon="Search"
          clearable
          style="width: 200px"
        />
        <el-select v-model="filterSeverity" placeholder="严重程度" clearable style="width: 120px">
          <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="loadVulnerabilities">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加漏洞</el-button>
      </div>
    </div>

    <!-- 漏洞列表 -->
    <div class="vuln-list" v-loading="loading">
      <el-scrollbar>
        <template v-if="filteredVulnerabilities.length === 0">
          <el-empty description="暂无漏洞数据" />
        </template>
        <div v-else class="vuln-items">
          <div 
            v-for="vuln in filteredVulnerabilities" 
            :key="vuln.id" 
            class="vuln-item"
          >
            <div class="severity-indicator" :style="{ backgroundColor: getSeverityColor(vuln.severity) }"></div>
            <div class="vuln-content">
              <div class="vuln-header">
                <span class="vuln-title">{{ vuln.title }}</span>
                <el-tag :color="getSeverityColor(vuln.severity)" effect="dark" size="small">
                  {{ getSeverityLabel(vuln.severity) }}
                </el-tag>
                <el-tag type="info" size="small" style="margin-left: 8px">
                  {{ getStatusLabel(vuln.status) }}
                </el-tag>
              </div>
              <div class="vuln-meta">
                <span v-if="vuln.targetUrl" class="meta-item">
                  <el-icon><Link /></el-icon>
                  {{ vuln.targetUrl }}
                </span>
                <span class="meta-item">
                  {{ vuln.createdAt ? new Date(vuln.createdAt).toLocaleString() : '' }}
                </span>
              </div>
            </div>
            <div class="vuln-actions">
              <el-button link :icon="Edit" @click="handleEdit(vuln)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(vuln.id)">删除</el-button>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑漏洞' : '添加漏洞'"
      width="600px"
    >
      <el-form label-position="top">
        <el-form-item label="漏洞标题" required>
          <el-input v-model="currentVuln.title" placeholder="输入漏洞标题" />
        </el-form-item>
        
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="严重程度">
              <el-select v-model="currentVuln.severity" style="width: 100%">
                <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value">
                  <span :style="{ color: opt.color }">● </span>{{ opt.label }}
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="currentVuln.status" style="width: 100%">
                <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="目标 URL">
          <el-input v-model="currentVuln.targetUrl" placeholder="https://example.com/vulnerable-path" />
        </el-form-item>
        
        <el-form-item label="漏洞描述">
          <el-input v-model="currentVuln.description" type="textarea" :rows="3" placeholder="详细描述漏洞情况..." />
        </el-form-item>
        
        <el-form-item label="修复建议">
          <el-input v-model="currentVuln.recommendation" type="textarea" :rows="2" placeholder="修复建议..." />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.vulns-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
}

.stats-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-card {
  flex: 1;
  min-width: 100px;
  background: white;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  border-top: 3px solid #ddd;
}

.stat-card.total { border-top-color: #409eff; }
.stat-card.critical { border-top-color: #f56c6c; }
.stat-card.high { border-top-color: #e6a23c; }
.stat-card.medium { border-top-color: #f4a460; }
.stat-card.low { border-top-color: #67c23a; }
.stat-card.info { border-top-color: #909399; }

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

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.filters {
  display: flex;
  gap: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}

.vuln-list {
  flex: 1;
  overflow: hidden;
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.vuln-items {
  padding: 8px;
}

.vuln-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.vuln-item:hover {
  background: #fafafa;
}

.severity-indicator {
  width: 4px;
  height: 40px;
  border-radius: 2px;
  margin-right: 12px;
  flex-shrink: 0;
}

.vuln-content {
  flex: 1;
  min-width: 0;
}

.vuln-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.vuln-title {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}

.vuln-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.vuln-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
