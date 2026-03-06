<script setup lang="ts">
import { ref, onMounted, useTemplateRef } from 'vue';
import { dayjs, ElMessage, ElMessageBox, FormContext, FormRules } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import BatchQueueDialog from './BatchQueueDialog.vue';
import ChatStore from "@/store/Chat";

export interface BatchTask {
  id: string;
  message: string;
  conversationId: string;
  status: 'pending' | 'running' | 'error' | 'completed' | 'cancelled';
  statusLabel?: string;
  statusElType?: string;
  startedAt: string;
  completedAt: string;
  result: string;
  error: string;
}

interface BatchStats {
  total: number;
  pending: number;
  running: number;
  completed: number;
  error: number;
  cancelled: number;
  progress: number;
}

export interface BatchQueue {
  id: string;
  title: string;
  status: 'pending' | 'running' | 'paused' | 'completed' | 'cancelled' | '';
  statusLabel?: string;
  statusElType?: string;
  role: string;
  createdAt: string;
  startedAt: string;
  completedAt: string;
  currentIndex: number;
  tasks: BatchTask[];
  batchStats: BatchStats;
}

const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);
const queues = ref<BatchQueue[]>([]);
const status = ref('');
const statusOptions = ref([
  {
    label: '待执行',
    value: 'pending'
  },
  {
    label: '执行中',
    value: 'running'
  },
  {
    label: '已暂停',
    value: 'paused'
  },
  {
    label: '已完成',
    value: 'completed'
  },
  {
    label: '已取消',
    value: 'cancelled'
  }
]);
const timeRange = ref<string[]>();
const keyword = ref('');

const dialogVisible = ref(false);
const roleOptions = ref<{ label: string; value: string }[]>([]);
const form = ref({
  title: '',
  role: '',
  tasksText: ''
});
const rules = ref<FormRules>({
  title: {
    required: true,
    message: '请填写标题'
  },
  tasksText: {
    required: true,
    message: '请输入至少一个任务',
    validator(_rule, value, cb) {
      const tasks = value.split('\n').filter((t: string) => t.trim());
      if (tasks.length === 0) {
        cb(new Error('没有任何有效任务'));
        return;
      } else {
        cb();
      }
    }
  }
});
const formRef = useTemplateRef<FormContext>('form');

const batchQueueDialogVisible = ref(false);
const batchQueueId = ref('');

const store = ChatStore();

const getRoles = async () => {
  const res = await fetch('/api/roles');
  if (res.ok) {
    const response = await res.json();
    roleOptions.value = response.map((r: any) => {
      return {
        label: r.name,
        value: r.name
      };
    });
  } else {
    ElMessage.error('获取角色列表失败');
  }
};

const fetchQueues = async (reset: boolean = false) => {
  if (reset) {
    pageNum.value = 1;
  }
  let query = '';
  query += `status=${status.value || ''}`;
  query += `&keyword=${keyword.value}`;
  query += `&page=${pageNum.value}`;
  query += `&size=${pageSize.value}`;
  if (timeRange.value) {
    query += `&createdFrom=${timeRange.value[0]}`;
    query += `&createdTo=${timeRange.value[1]}`;
  }

  const res = await fetch(`/api/batch-tasks?${query}`);
  if (res.ok) {
    const queueStatusMap: Record<string, Record<'label' | 'elType', string>> = store.queueStatusMap;
    const response = await res.json();
    queues.value = response.data;
    total.value = response.total;
    queues.value.forEach(q => {
      const { label, elType } = queueStatusMap[q.status] || {};
      q.statusLabel = label;
      q.statusElType = elType;
      q.createdAt = q.createdAt ? dayjs(q.createdAt).format('YYYY-MM-DD HH:mm:ss') : '';
      q.startedAt = q.startedAt ? dayjs(q.startedAt).format('YYYY-MM-DD HH:mm:ss') : '';
      q.completedAt = q.completedAt ? dayjs(q.completedAt).format('YYYY-MM-DD HH:mm:ss') : '';
      let total = 0, pending = 0, running = 0, completed = 0, error = 0, cancelled = 0;
      q.tasks.forEach(t => {
        total++;
        if (t.status === 'pending') {
          pending++;
        } else if (t.status === 'running') {
          running++;
        } else if (t.status === 'completed') {
          completed++;
        } else if (t.status === 'error') {
          error++;
        } else if (t.status === 'cancelled') {
          cancelled++;
        }
      });
      q.batchStats = {
        total,
        pending,
        running,
        completed,
        error,
        cancelled,
        progress: total !== 0 ? Math.floor((completed + error + cancelled) / total * 100) : 0
      };
    });
  } else {
    ElMessage.error('获取队列列表失败');
  }
};

const handleCreate = () => {
  formRef.value?.resetFields();
  form.value = { title: '', role: '', tasksText: '' };
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validateField();
  if (valid) {
    const res = await fetch('/api/batch-tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: form.value.title,
        role: form.value.role,
        tasks: form.value.tasksText.split('\n').filter((t: string) => t.trim())
      })
    });

    if (res.ok) {
      ElMessage.success('创建成功');
      dialogVisible.value = false;
      const response = await res.json();
      batchQueueId.value = response.id;
      batchQueueDialogVisible.value = true;
      fetchQueues();
    } else {
      ElMessage.error('创建失败');
    }
  }
};

const selectQueue = async (queueId: string) => {
  batchQueueId.value = queueId;
  batchQueueDialogVisible.value = true;
};

const confirmDeleteQueue = (id: string) => {
  ElMessageBox.confirm('确定删除该队列吗', '删除队列', {
    type: 'error'
  }).then(() => {
    deleteQueue(id);
  });
};

const deleteQueue = async (id: string) => {
  const { ok } = await fetch(`/api/batch-tasks/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-type': 'application/json'
    }
  });
  if (ok) {
    fetchQueues();
    ElMessage.success('删除任务队列成功');
  } else {
    ElMessage.error('删除任务队列失败');
  }
};

onMounted(() => {
  getRoles();
  fetchQueues(true);
});
</script>

<template>
  <div class="batch-view">
    <div class="task-actions">
      <el-button type="primary" @click="handleCreate">
        <el-icon>
          <Plus />
        </el-icon>
        <span>新建任务</span>
      </el-button>
    </div>
    <!-- 查询条件 -->
    <div class="task-filters">
      <el-form label-width="80px" label-position="top" inline>
        <el-form-item label="状态筛选">
          <el-select v-model="status" placeholder="请选择" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
       <el-form-item label="创建时间">
         <el-date-picker v-model="timeRange" type="datetimerange" start-placeholder="请选择创建时间开始时间"
            end-placeholder="请选择创建时间结束时间" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="搜索队列ID、标题">
          <el-input v-model="keyword" placeholder="输入关键字搜索..." />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchQueues(true)">筛选</el-button>
        </el-form-item>
      </el-form>
    </div>
    <!-- 任务列表 -->
   <div class="task-list">
      <template v-if="queues.length">
        <div class="batch-queue-item" v-for="queue in queues" :key="queue.id" @click="selectQueue(queue.id)">
          <div class="batch-queue-header">
            <div class="batch-queue-info">
              <div class="batch-queue-title">{{ queue.title }}</div>
              <div v-if="queue.role" class="batch-queue-role">{{ queue.role }}</div>
              <div class="batch-queue-status">
                <el-tag :type="queue.statusElType">{{ queue.statusLabel }}</el-tag>
              </div>
              <div class="batch-queue-id">队列ID: {{ queue.id }}</div>
              <div class="batch-queue-time">创建时间: {{ queue.createdAt }}</div>
            </div>
            <div class="batch-queue-progress">
              <el-progress :percentage="queue.batchStats.progress" />
            </div>
            <div>
              <el-button v-if="['pending', 'completed', 'cancelled'].includes(queue.status)" type="danger" @click.stop="confirmDeleteQueue(queue.id)">删除</el-button>
            </div>
          </div>
          <div class="batch-queue-stats">
            <span>总计: {{ queue.batchStats.total }}</span>
            <span>待执行: {{ queue.batchStats.pending }}</span>
            <span>执行中: {{ queue.batchStats.running }}</span>
            <span>已完成: {{ queue.batchStats.completed }}</span>
            <span>失败: {{ queue.batchStats.error }}</span>
          </div>
        </div>
        <el-pagination v-model:current-page="pageNum" :page-size="pageSize" background
          layout="->, prev, pager, next, total" :total="total" @change="fetchQueues(false)" />
      </template>
      <div v-else class="empty-state">
        <el-empty description="暂无数据" />
      </div>
    </div>

    <!-- 创建对话框 -->
    <el-dialog v-model="dialogVisible" title="创建批量任务" width="600px">
      <el-form ref="form" :model="form" :rules="rules" label-width="80px" label-position="top">
        <el-form-item label="任务标题" prop="title">
          <el-input v-model.trim="form.title" placeholder="例如: 批量扫描主机..." />
        </el-form-item>
        <el-form-item label="指定角色" prop="role">
          <el-select v-model="form.role" placeholder="可选">
            <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务列表" prop="tasksText">
          <el-input v-model.trim="form.tasksText" type="textarea" :rows="10" placeholder="每行一个任务指令..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>
    <batch-queue-dialog v-model:visible="batchQueueDialogVisible" :batch-queue-id="batchQueueId" @deleteQueue="deleteQueue" />
  </div>
</template>

<style lang="scss" scoped>
.batch-view {
  padding: 16px 20px;
  height: 100%;
  overflow: auto;
}

.task-actions {
  display: flex;
  flex-direction: row-reverse;
}

.task-filters {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 16px;

  .el-form {
    align-items: end;
  }

  .el-select {
    width: 150px;
  }

  .el-input {
    width: 300px;
  }
}

.task-list {
  .batch-queue-item {
    margin-bottom: 12px;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 20px;
    box-shadow: var(--shadow-sm);
    transition: all 0.2s ease;
    cursor: pointer;

    &:hover {
      box-shadow: var(--shadow-md);
      transform: translateY(-2px);
      border-color: var(--accent-color);
    }
  }

  .batch-queue-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    margin-bottom: 12px;
    flex-wrap: wrap;

    .batch-queue-info {
      display: flex;
      align-items: center;
      gap: 12px;
      flex: 1;
      flex-wrap: wrap;

      .batch-queue-title {
        font-weight: 600;
        color: var(--text-primary);
      }

      .batch-queue-status {
        display: inline-flex;
        align-items: center;
        border-radius: 12px;
        font-size: 0.8125rem;
        font-weight: 500;
        white-space: nowrap;
      }

      .batch-queue-id {
        font-size: 0.8125rem;
        color: var(--text-secondary);
      }

      .batch-queue-time {
        font-size: 0.8125rem;
        color: var(--text-secondary);
      }
    }

    .batch-queue-progress {
      min-width: 200px;
    }
  }

  .batch-queue-stats {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    font-size: 0.8125rem;
    color: var(--text-secondary);

    >span {
      &:nth-last-child(1) {
        color: var(--error-color);
      }

      &:nth-last-child(2) {
        color: var(--success-color);
      }
    }
  }

  .el-pagination {
    margin-top: 12px;
  }
}
</style>
