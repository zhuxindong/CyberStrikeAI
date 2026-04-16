<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search, Edit, Delete, Link } from '@element-plus/icons-vue';

interface Vulnerability {
  id: string;
  title: string;
  type: string;
  description?: string;
  severity: string;
  status: string;
  targetUrl?: string;
  affectedComponent?: string;
  evidence?: string;
  recommendation?: string;
  conversationId?: string;
  createdAt: string;
  updatedAt: string;
  expand?: boolean;
}

interface Stats {
  total: number;
  bySeverity: Record<string, number>;
  byStatus: Record<string, number>;
}

const autoSizeConfig = {
  minRows: 3
};

const vulnerabilities = ref<Vulnerability[]>([]);
const stats = ref<Stats>({ total: 0, bySeverity: {}, byStatus: {} });
const loading = ref(false);
const idQuery = ref('');
const conversationIdQuery = ref('');

const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

const filterSeverity = ref('');
const filterStatus = ref('');

const dialogVisible = ref(false);
const isEdit = ref(false);
const currentVuln = ref<Vulnerability>({
  id: '',
  title: '',
  type: '',
  severity: 'medium',
  status: 'open',
  description: '',
  targetUrl: '',
  recommendation: '',
  createdAt: '',
  updatedAt: ''
});

const severityOptions = [
  { value: 'critical', label: '严重', color: '#dc3545' },
  { value: 'high', label: '高危', color: '#fd7e14' },
  { value: 'medium', label: '中危', color: '#ffc107' },
  { value: 'low', label: '低危', color: '#20c997' },
  { value: 'info', label: '信息', color: '#6c757d' }
];

const statusOptions = [
  { value: 'open', label: '待处理', color: '#0066ff' },
  { value: 'confirmed', label: '已确认', color: '#28a745' },
  { value: 'fixed', label: '已修复', color: '#6c757d' },
  { value: 'false_positive', label: '误报', color: '#dc3545' }
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

const getStatusColor = (status: string) => {
  return statusOptions.find(s => s.value === status)?.color || '#909399';
};

const loadVulnerabilities = async (reset: boolean = false) => {
  if (reset) {
    pageNum.value = 1;
  }
  loading.value = true;
  try {
    let query = '';
    query += `page=${pageNum.value}`;
    query += `&size=${pageSize.value}`;
    query += `&severity=${filterSeverity.value || ''}`;
    query += `&status=${filterStatus.value || ''}`;
    query += `&id=${idQuery.value}`;
    query += `&conversationId=${conversationIdQuery.value}`;

    const res = await fetch(`/api/vulnerabilities?${query}`);
    if (res.ok) {
      const data = await res.json();
      vulnerabilities.value = data.vulnerabilities || [];
      total.value = data.total;
    }
  } catch (e) {
    ElMessage.error('加载漏洞列表失败');
  } finally {
    loading.value = false;
  }
};

const reset = () => {
  idQuery.value = conversationIdQuery.value = filterSeverity.value = filterStatus.value = '';
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
    type: '',
    severity: 'medium',
    status: 'open',
    description: '',
    targetUrl: '',
    recommendation: '',
    createdAt: '',
    updatedAt: ''
  };
  dialogVisible.value = true;
};

// 将漏洞格式化为Markdown
const formatVulnerabilityAsMarkdown = (vuln: Vulnerability) => {
  const severityText = {
    'critical': '严重',
    'high': '高危',
    'medium': '中危',
    'low': '低危',
    'info': '信息'
  }[vuln.severity] || vuln.severity;

  const statusText = {
    'open': '待处理',
    'confirmed': '已确认',
    'fixed': '已修复',
    'false_positive': '误报'
  }[vuln.status] || vuln.status;

  const createdDate = new Date(vuln.createdAt).toLocaleString('zh-CN');
  const updatedDate = new Date(vuln.updatedAt).toLocaleString('zh-CN');

  let markdown = `# ${vuln.title}\n\n`;

  markdown += `## 基本信息\n\n`;
  markdown += `- **漏洞ID**: \`${vuln.id}\`\n`;
  markdown += `- **严重程度**: ${severityText}\n`;
  markdown += `- **状态**: ${statusText}\n`;
  if (vuln.type) {
    markdown += `- **类型**: ${vuln.type}\n`;
  }
  if (vuln.targetUrl) {
    markdown += `- **目标**: ${vuln.targetUrl}\n`;
  }
  markdown += `- **会话ID**: \`${vuln.conversationId}\`\n`;
  markdown += `- **创建时间**: ${createdDate}\n`;
  markdown += `- **更新时间**: ${updatedDate}\n\n`;

  if (vuln.description) {
    markdown += `## 描述\n\n${vuln.description}\n\n`;
  }

  if (vuln.evidence) {
    markdown += `## 证明（POC）\n\n\`\`\`\n${vuln.evidence}\n\`\`\`\n\n`;
  }

  if (vuln.affectedComponent) {
    markdown += `## 影响\n\n${vuln.affectedComponent}\n\n`;
  }

  if (vuln.recommendation) {
    markdown += `## 修复建议\n\n${vuln.recommendation}\n\n`;
  }

  return markdown;
};

const handleDownload = (vuln: Vulnerability) => {
  const md = formatVulnerabilityAsMarkdown(vuln);
  // 创建Blob对象
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });

  // 创建下载链接
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;

  // 生成文件名（使用漏洞标题，清理特殊字符，保留中文）
  const cleanTitle = vuln.title
    .replace(/[<>:"/\\|?*]/g, '') // 移除Windows不允许的字符
    .replace(/\s+/g, '_') // 空格替换为下划线
    .substring(0, 50); // 限制长度
  const fileName = `${cleanTitle}_${vuln.id.substring(0, 8)}.md`;
  link.download = fileName;

  // 触发下载
  document.body.appendChild(link);
  link.click();

  // 清理
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
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
  if (!currentVuln.value.conversationId) {
    ElMessage.warning('请填写会话ID');
    return;
  }
  
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
  loadVulnerabilities(true);
  loadStats();
});
</script>

<template>
  <div class="vulns-view">
    <div class="actions">
      <div>
        <el-button :icon="Refresh" @click="loadVulnerabilities(false)">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加漏洞</el-button>
      </div>
    </div>

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
      <el-form class="filters" inline label-position="top">
        <el-form-item label="漏洞ID">
          <el-input
            v-model="idQuery"
            placeholder="搜索漏洞ID"
            :prefix-icon="Search"
            clearable
          />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input
            v-model="conversationIdQuery"
            placeholder="筛选会话ID"
            :prefix-icon="Search"
            clearable
          />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="filterSeverity" placeholder="严重程度" clearable>
            <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="状态" clearable>
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadVulnerabilities(true)">筛选</el-button>
          <el-button @click="reset">清除</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 漏洞列表 -->
    <div class="vuln-list" v-loading="loading">
      <template v-if="vulnerabilities.length === 0">
        <el-empty description="暂无漏洞数据" />
      </template>
      <div v-else class="vuln-items">
        <div 
          v-for="vuln in vulnerabilities" 
          :key="vuln.id" 
          class="vuln-item"
          :style="{borderLeftColor: getSeverityColor(vuln.severity)}"
        >
          <div class="vuln-content" @click="vuln.expand = !vuln.expand">
            <div class="vuln-header">
              <div class="vuln-title">
                <el-icon :class="{ 'rotated': vuln.expand }"><ArrowRight /></el-icon>
                {{ vuln.title }}
              </div>
              <div class="vuln-actions">
                <el-button link icon="download" @click="handleDownload(vuln)">下载</el-button>
                <el-button link :icon="Edit" @click="handleEdit(vuln)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="handleDelete(vuln.id)">删除</el-button>
              </div>
            </div>
            <div class="vuln-meta">
              <el-tag :color="getSeverityColor(vuln.severity)" effect="dark" size="large">
                {{ getSeverityLabel(vuln.severity) }}
              </el-tag>
              <el-tag :color="getStatusColor(vuln.status)" effect="dark" size="large">
                {{ getStatusLabel(vuln.status) }}
              </el-tag>
              <span v-if="vuln.targetUrl">
                <el-icon><Link /></el-icon>
                {{ vuln.targetUrl }}
              </span>
              <span>
                {{ vuln.createdAt ? new Date(vuln.createdAt).toLocaleString() : '' }}
              </span>
            </div>
          </div>
          <div :class="['vuln-info', { 'expand': vuln.expand }]">
            <div>
              <p>漏洞ID: </p>
              <p>{{ vuln.id }}</p>
            </div>
            <div v-if="vuln.type">
              <p>类型: </p>
              <p>{{ vuln.type }}</p>
            </div>
            <div v-if="vuln.targetUrl">
              <p>目标URL: </p>
              <p>{{ vuln.targetUrl }}</p>
            </div>
            <div>
              <p>会话ID: </p>
              <p>{{ vuln.conversationId }}</p>
            </div>
          </div>
        </div>
      </div>
     <el-pagination v-model:currentPage="pageNum" :page-size="pageSize" hide-on-single-page
        layout="->, prev, pager, next, total" :total="total" background @change="loadVulnerabilities(false)" />
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑漏洞' : '添加漏洞'"
      width="600px"
    >
      <el-form label-position="top">
        <el-form-item label="会话ID" required>
          <el-input v-model="currentVuln.conversationId" :disabled="isEdit" placeholder="输入会话ID" />
        </el-form-item>
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
        <el-form-item label="漏洞类型">
          <el-input v-model="currentVuln.type" placeholder="如: SQL注入、XSS、CSRF等" />
        </el-form-item>
        <el-form-item label="目标 URL">
          <el-input v-model="currentVuln.targetUrl" placeholder="https://example.com/vulnerable-path" />
        </el-form-item>
        <el-form-item label="漏洞描述">
          <el-input v-model="currentVuln.description" type="textarea" :autosize="autoSizeConfig"
            placeholder="详细描述漏洞情况..." />
        </el-form-item>
        <el-form-item label="证明（POC）">
          <el-input v-model="currentVuln.evidence" type="textarea" :autosize="autoSizeConfig"
            placeholder="漏洞证明，如请求/响应、截图等" />
        </el-form-item>
        <el-form-item label="影响">
          <el-input v-model="currentVuln.affectedComponent" type="textarea" :autosize="autoSizeConfig"
            placeholder="漏洞影响说明" />
        </el-form-item>
        <el-form-item label="修复建议">
          <el-input v-model="currentVuln.recommendation" type="textarea" :autosize="autoSizeConfig"
            placeholder="修复建议..." />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.vulns-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
  overflow: auto;
}

.actions {
  display: flex;
  flex-direction: row-reverse;
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
  align-items: center;
  gap: 20px;
  padding: 12px 16px;

  .filters {
    align-items: flex-end;

    :deep(.el-input__wrapper) {
      width: 200px;
    }
    
    :deep(.el-select__wrapper) {
      width: 120px;
    }
  }
}

.vuln-list {
  >.el-pagination {
    margin-top: 12px;
  }
}

.vuln-items {
  padding: 8px 12px 8px 8px;
}

.vuln-item {
  padding: 12px;
  border-radius: 8px;
  border-left: 3px solid;
  margin-bottom: 12px;
  box-shadow: 0 0 8px #dfdfdf;
}

.vuln-content {
  padding: 12px;
  border-radius: 8px;
  transition: background-color 0.2s;
  cursor: pointer;

  &:hover {
    background: #fafafa;
  }
}

.vuln-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;

  .vuln-title {
    color: #303133;
    font-weight: bold;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 400px;

    .el-icon {
      font-size: 12px;
      transition: transform 0.2s;
      &.rotated {
        transform: rotate(90deg);
      }
    }
  }

  .vuln-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }
}


.vuln-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;

  .el-tag {
    border-color: transparent;
  }
}

.vuln-info {
  display: flex;
  height: 0;
  overflow: hidden;
  border-radius: 8px;
  margin-top: 12px;
  margin-left: 50px;
  transition: height 0.2s;
  background: #fafafa;

  &.expand {
    padding: 8px;
    height: auto;
  }

  >div {
    margin-right: 20px;
  }

  p {
    font-size: 14px;
  }
}
</style>
