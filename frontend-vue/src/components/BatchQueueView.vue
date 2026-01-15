<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, VideoPlay, VideoPause, Refresh, List } from '@element-plus/icons-vue';

interface BatchTask {
  id: string;
  message: string;
  conversationId: string;
  status: string;
  startedAt: string;
  completedAt: string;
  result: string;
  error: string;
}

interface BatchQueue {
  id: string;
  title: string;
  status: string;
  role: string;
  createdAt: string;
  startedAt: string;
  completedAt: string;
  currentIndex: number;
  tasks: BatchTask[];
}

const queues = ref<BatchQueue[]>([]);
const currentQueue = ref<BatchQueue | null>(null);
const dialogVisible = ref(false);
const form = ref({
  title: '',
  role: '',
  tasksText: ''
});

const fetchQueues = async () => {
  try {
    const res = await fetch('/api/batch-tasks');
    if (res.ok) {
      queues.value = await res.json();
    }
  } catch (error) {
    ElMessage.error('获取任务队列失败');
  }
};

const handleCreate = () => {
  form.value = { title: '', role: '', tasksText: '' };
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!form.value.title || !form.value.tasksText) {
    ElMessage.warning('请填写标题和任务列表');
    return;
  }
  
  const tasks = form.value.tasksText.split('\n').filter(t => t.trim());
  if (tasks.length === 0) {
    ElMessage.warning('没有任何有效任务');
    return;
  }

  try {
    const res = await fetch('/api/batch-tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: form.value.title,
        role: form.value.role,
        tasks: tasks
      })
    });
    
    if (res.ok) {
      ElMessage.success('创建成功');
      dialogVisible.value = false;
      fetchQueues();
    }
  } catch (e) {
    ElMessage.error('创建失败');
  }
};

const selectQueue = async (queue: BatchQueue) => {
  try {
    const res = await fetch(`/api/batch-tasks/${queue.id}`);
    if (res.ok) {
      currentQueue.value = await res.json();
    }
  } catch (e) {
    ElMessage.error('获取详情失败');
  }
};

const startQueue = async (id: string) => {
  try {
    await fetch(`/api/batch-tasks/${id}/start`, { method: 'POST' });
    ElMessage.success('已开始执行');
    refreshCurrent();
  } catch (e) {
    ElMessage.error('启动失败');
  }
};

const cancelQueue = async (id: string) => {
  try {
    await fetch(`/api/batch-tasks/${id}/cancel`, { method: 'POST' });
    ElMessage.success('已请求取消');
    refreshCurrent();
  } catch (e) {
     ElMessage.error('取消失败');
  }
};

const refreshCurrent = () => {
  if (currentQueue.value) {
    selectQueue(currentQueue.value);
  }
  fetchQueues();
};

// Auto refresh if running
let timer: number | undefined;
onMounted(() => {
  fetchQueues();
  timer = setInterval(() => {
    if (currentQueue.value && currentQueue.value.status === 'running') {
      refreshCurrent();
    }
  }, 3000);
});
</script>

<template>
  <div class="batch-view">
    <!-- 左侧列表 -->
    <div class="queue-list">
      <div class="list-header">
        <h3>批量任务队列</h3>
        <el-button :icon="Plus" circle size="small" @click="handleCreate"></el-button>
      </div>
      <div class="list-content">
        <div 
          v-for="q in queues" 
          :key="q.id" 
          class="queue-item"
          :class="{ active: currentQueue?.id === q.id }"
          @click="selectQueue(q)"
        >
          <div class="q-title">{{ q.title }}</div>
          <div class="q-meta">
            <el-tag size="small" :type="q.status === 'completed' ? 'success' : q.status === 'running' ? 'primary' : 'info'">
              {{ q.status }}
            </el-tag>
            <span class="q-time">{{ new Date(q.createdAt).toLocaleTimeString() }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧详情 -->
    <div class="queue-detail" v-if="currentQueue">
      <div class="detail-header">
        <div class="dh-left">
          <h2>{{ currentQueue.title }}</h2>
          <el-tag class="ml-2">{{ currentQueue.status }}</el-tag>
        </div>
        <div class="dh-right">
          <el-button :icon="Refresh" @click="refreshCurrent">刷新</el-button>
          <el-button 
            type="success" 
            :icon="VideoPlay" 
            v-if="currentQueue.status === 'pending'"
            @click="startQueue(currentQueue.id)"
          >
            开始执行
          </el-button>
          <el-button 
            type="danger" 
            :icon="VideoPause" 
            v-if="currentQueue.status === 'running'"
            @click="cancelQueue(currentQueue.id)"
          >
            停止
          </el-button>
        </div>
      </div>

      <div class="detail-stats" v-if="currentQueue.role">
        <span class="label">指定角色:</span> {{ currentQueue.role }}
      </div>

      <el-table :data="currentQueue.tasks" style="width: 100%" height="calc(100% - 120px)">
        <el-table-column type="index" width="50" label="#" />
        <el-table-column prop="message" label="任务指令" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
             <el-tag 
               size="small" 
               :type="row.status === 'completed' ? 'success' : row.status === 'running' ? 'primary' : row.status === 'error' ? 'danger' : 'info'"
             >
               {{ row.status }}
             </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="200">
          <template #default="{ row }">
            <div class="result-cell" v-if="row.result">
              {{ row.result.substring(0, 100) + (row.result.length > 100 ? '...' : '') }}
            </div>
            <div class="error-text" v-else-if="row.error">{{ row.error }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link v-if="row.conversationId" @click="$emit('view-conv', row.conversationId)">
              查看对话
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <div class="empty-state" v-else>
      <el-empty description="选择左侧队列查看详情" />
    </div>

    <!-- 创建对话框 -->
    <el-dialog v-model="dialogVisible" title="创建批量任务" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="任务标题">
          <el-input v-model="form.title" placeholder="例如: 批量扫描主机..." />
        </el-form-item>
        <el-form-item label="指定角色">
          <el-input v-model="form.role" placeholder="可选，例如: red_team" />
        </el-form-item>
        <el-form-item label="任务列表">
          <el-input 
            v-model="form.tasksText" 
            type="textarea" 
            :rows="10"
            placeholder="每行一个任务指令..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.batch-view {
  display: flex;
  height: 100%;
  gap: 16px;
}

.queue-list {
  width: 250px;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}

.list-content {
  flex: 1;
  overflow-y: auto;
}

.queue-item {
  padding: 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.queue-item:hover {
  background-color: #f5f7fa;
}

.queue-item.active {
  background-color: #ecf5ff;
  border-right: 2px solid var(--el-color-primary);
}

.q-title {
  font-weight: 500;
  margin-bottom: 6px;
}

.q-meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  color: #909399;
}

.queue-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.dh-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dh-left h2 { margin: 0; }

.result-cell {
  font-family: monospace;
  font-size: 0.9em;
  color: #606266;
}

.error-text {
  color: #f56c6c;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
