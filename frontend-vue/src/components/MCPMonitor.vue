<template>
  <div class="monitor-sections">
    <section class="monitor-section monitor-overview">
      <div class="section-header">
        <h3>执行统计</h3>
      </div>
      <div class="monitor-stats-grid" v-loading="loading">
        <div class="monitor-stat-card">
          <h4>总调用次数</h4>
          <div class="monitor-stat-value">{{ stats.total }}</div>
          <div class="monitor-stat-meta">成功 {{ stats.totalSuccess }} / 失败 {{ stats.totalFail }}</div>
        </div>
        <div class="monitor-stat-card">
          <h4>成功率</h4>
          <div class="monitor-stat-value">{{ stats.successRate }}%</div>
          <div class="monitor-stat-meta">统计自全部工具调用</div>
        </div>
        <div class="monitor-stat-card">
          <h4>最近一次调用</h4>
          <div class="monitor-stat-value">{{ stats.lastCallText }}</div>
          <div class="monitor-stat-meta">最后刷新时间：{{ stats.lastUpdatedText }}</div>
        </div>
        <div class="monitor-stat-card" v-for="(item, i) in topCalls" :key="i">
          <h4>{{ escapeHtml(item.toolName || '未知工具') }}</h4>
          <div class="monitor-stat-value">{{ item.totalCalls || 0 }}</div>
          <div class="monitor-stat-meta">
            成功 {{ item.successCalls || 0 }} / 失败 {{ item.failedCalls || 0 }} · 成功率 {{ item.successRate }}%
          </div>
        </div>
      </div>
    </section>
    <section class="monitor-section monitor-executions">
      <div class="section-header">
        <h3>最新执行记录</h3>
        <div class="section-actions">
          <div>
            <label>工具搜索</label>
            <el-input v-model="toolName" placeholder="输入工具名称..." />
          </div>
          <div>
            <label>状态筛选</label>
            <el-select v-model="currentStatus" placeholder="选择状态">
              <el-option label="全部" value="all" />
              <el-option label="已完成" value="completed" />
              <el-option label="执行中" value="running" />
              <el-option label="失败" value="failed" />
            </el-select>
          </div>
        </div>
      </div>
      <div class="monitor-batch-actions">
        <div class="batch-actions-info">
          <span>已选择 {{ selectedRows.length }} 项</span>
        </div>
        <div v-if="selectedRows.length" class="batch-actions-buttons">
          <!-- <el-button type="primary" @click="selectAll">全选</el-button>
          <el-button @click="unselectAll">取消全选</el-button> -->
          <el-button type="danger" @click="batchDelete">批量删除</el-button>
        </div>
      </div>
      <div class="monitor-table-container">
        <el-table ref="table" :data="talbeData" v-loading="loading" @select="onSelect">
          <el-table-column type="selection" />
          <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label"
            :width="col.width" />
          <el-table-column label="操作">
            <template #default="{ row }">
              <div class="table-op">
                <el-button @click="showDetail">查看详情</el-button>
                <el-button type="danger" @click="deleteRow(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination layout="->, prev, pager, next, total" background :total="total"
          v-model:current-page="currentPage" @current-change="onPageChange" />
      </div>
    </section>
  </div>
  <McpCallDialog v-model:dialog-visible="dialogVisible" :detail="mcpCallDetail" />
</template>

<script setup lang="ts">
import { escapeHtml } from '../utils/escape';
import { onMounted, ref, useTemplateRef } from 'vue';
import McpCallDialog from './McpCallDialog.vue';

interface Stats {
  total: number;
  totalSuccess: number;
  totalFail: number;
  successRate: number;
  lastCallText: string;
  lastUpdatedText: string;
}

interface CallStats {
  toolName: string;
  totalCalls: number;
  successCalls: number;
  failedCalls: number;
  successRate: number;
}

const stats = ref<Stats>({
  total: 0,
  totalSuccess: 0,
  totalFail: 0,
  successRate: 0,
  lastCallText: '',
  lastUpdatedText: ''
});
const topCalls = ref<CallStats[]>([]);
const toolName = ref('');
const currentStatus = ref('');
const loading = ref(false);

interface TableItem {
  executionId: string;
  toolName: string;
  statusLabel: string;
  startTime: string;
  duration: number;
}

const columns = ref<{
  prop: string;
  label: string;
  width?: string;
}[]>([
  {
    prop: 'toolName',
    label: '工具'
  },
  {
    prop: 'statusLabel',
    label: '状态'
  },
  {
    prop: 'startTime',
    label: '开始时间'
  },
  {
    prop: 'duration',
    label: '耗时'
  }
]);
const total = ref(0);
const currentPage = ref(1);
const talbeData = ref<TableItem[]>([]);
const selectedRows = ref<TableItem[]>([]);
const table = useTemplateRef('table');

const dialogVisible = ref(false);
const mcpCallDetail = ref<any>();

onMounted(() => {
  getTableData();
});

const getTableData = () => {

};

const onSelect = () => {
  selectedRows.value = table.value.getSelectionRows();
};

const selectAll = () => {
  table.value.toggleAllSelection();
  onSelect();
};

const unselectAll = () => {
  table.value.clearSelection();
  onSelect();
};

const batchDelete = () => {
  const ids: string[] = selectedRows.value.map(row => row.executionId);
};

const showDetail = (row: TableItem) => {
  dialogVisible.value = true;
  mcpCallDetail.value = row;
};

const deleteRow = (row: TableItem) => {
    
};

const onPageChange = () => {

};
</script>

<style lang="scss" scoped>
.monitor-sections {
  display: grid;
  gap: 24px;
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}

.monitor-section {
  // background: var(--bg-primary);
  // border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 20px;
  // box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  overflow: hidden;
  box-sizing: border-box;

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-width: 0;
    flex-wrap: wrap;

    h3 {
      margin: 0;
      font-size: 1.1rem;
      color: var(--text-primary);
      flex-shrink: 0;
      min-width: 0;
    }
  }

  .section-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 0.875rem;
    color: var(--text-secondary);
    flex-wrap: wrap;
    min-width: 0;
    flex-shrink: 1;

    >div {
      display: inline-flex;
      align-items: center;

      label {
        min-width: 5em;
      }

      .el-select {
        min-width: 8em;
      }
    }
  }

  .monitor-stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 16px;
    width: 100%;
    box-sizing: border-box;

    .monitor-stat-card {
      background: var(--bg-secondary);
      border: 1px solid rgba(0, 102, 255, 0.12);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      box-shadow: var(--shadow-xs);
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;

      h4 {
        margin: 0;
        font-size: 0.95rem;
        color: var(--text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        word-break: break-word;
        max-width: 100%;
      }

      .monitor-stat-value {
        font-size: 1.8rem;
        font-weight: 600;
        color: var(--text-primary);
        word-break: break-word;
        overflow-wrap: break-word;
      }

      .monitor-stat-meta {
        font-size: 0.8rem;
        color: var(--text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        word-break: break-word;
        max-width: 100%;
      }
    }
  }

  .monitor-batch-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    margin-bottom: 16px;
    gap: 16px;

    .batch-actions-info {
      display: flex;
      align-items: center;
      color: var(--text-secondary);
      font-size: 0.875rem;
      font-weight: 500;
    }

    .batch-actions-buttons {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .monitor-table-container {
    .table-op {
      display: flex;
      gap: 8px;
    }

    .el-pagination {
      margin-top: 12px;
    }
  }
}

.monitor-empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.pager {
  margin-top: 12px;
  display: flex;
  flex-direction: row-reverse;
}
</style>