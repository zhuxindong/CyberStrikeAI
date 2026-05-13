<template>
  <el-dialog v-model="visible" width="60%" @open="onOpen" @close="onClose">
    <template #header>
      <div class="batch-queue-header">
        <h4>批量任务队列-{{ batchQueueInfo.title }}</h4>
        <div>
          <template v-if="batchQueueInfo.status === 'pending'">
            <el-button @click="showTask()">添加任务</el-button>
            <el-button @click="startQueue(batchQueueInfo.id)">开始执行</el-button>
          </template>
          <el-button v-else-if="batchQueueInfo.status === 'paused'"
            @click="startQueue(batchQueueInfo.id)">继续执行</el-button>
          <template v-else-if="batchQueueInfo.status === 'running'">
            <el-button @click="puaseQueue(batchQueueInfo.id)">暂停队列</el-button>
          </template>
          <el-button v-if="['pending', 'completed', 'cancelled'].includes(batchQueueInfo.status)" type="danger"
            @click="deleteQueue(batchQueueInfo.id)">删除队列</el-button>
        </div>
      </div>
    </template>
    <div class="batch-queue-detail-info">
      <div class="detail-item">
        <span class="detail-label">任务标题</span>
        <span class="detail-value">{{ batchQueueInfo.title }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">角色</span>
        <span class="detail-value">{{ batchQueueInfo.role }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">队列ID</span>
        <span class="detail-value">
          <code>{{ batchQueueInfo.id }}</code>
        </span>
      </div>
      <div class="detail-item">
        <span class="detail-label">状态</span>
        <span class="detail-value">
          <el-tag :type="batchQueueInfo.statusElType">{{ batchQueueInfo.statusLabel }}</el-tag>
        </span>
      </div>
      <div class="detail-item">
        <span class="detail-label">创建时间</span>
        <span class="detail-value">{{ batchQueueInfo.createdAt }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">开始时间</span>
        <span class="detail-value">{{ batchQueueInfo.startedAt }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">完成时间</span>
        <span class="detail-value">{{ batchQueueInfo.completedAt }}</span>
      </div>
      <div class="detail-item">
        <span class="detail-label">任务总数</span>
        <span class="detail-value">{{ batchQueueInfo.tasks.length }}</span>
      </div>
    </div>
    <div class="batch-queue-tasks-list">
      <h4>任务列表</h4>
      <div class="batch-task-item" v-for="(task, index) in batchQueueInfo.tasks" :key="task.id">
        <div class="batch-task-header">
          <span>
            <span class="batch-task-index">#{{ index + 1 }}</span>
            <el-tag :type="task.statusElType">{{ task.statusLabel }}</el-tag>
            <span class="batch-task-message">{{ task.message }}</span>
          </span>
          <span>
            <template v-if="batchQueueInfo.status === 'pending'">
              <el-button @click="showTask(task)">编辑</el-button>
              <el-button type="danger" @click="deleteTask(task.id)">删除</el-button>
            </template>
            <el-button v-if="task.conversationId" @click="goChat(task.conversationId)">查看对话</el-button>
          </span>
        </div>
        <div v-if="task.startedAt" class="batch-task-time">开始时间: {{ task.startedAt }}</div>
        <div v-if="task.completedAt" class="batch-task-time">结束时间: {{ task.completedAt }}</div>
        <div v-if="task.error" class="batch-task-error">{{ task.error }}</div>
      </div>
    </div>
  </el-dialog>
  <el-dialog v-model="taskVisible" :title="!currentTask?.id ? '添加任务' : '修改任务'" width="50%"
    @close="formRef?.resetFields()">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" label-width="80px">
      <el-form-item label="任务消息" prop="taskMessage">
        <el-input v-model.trim="form.taskMessage" type="textarea" :rows="5" placeholder="请输入" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="taskVisible = false">取消</el-button>
      <el-button v-if="!currentTask?.id" type="primary" @click="addTask">添加</el-button>
      <el-button v-else type="primary" @click="editTask">修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, useTemplateRef, watch } from 'vue';
import { BatchTask, BatchQueue } from './BatchQueueView.vue';
import { dayjs, ElMessage, ElMessageBox, FormContext, FormRules } from 'element-plus';
import { useRouter } from 'vue-router';
import ChatStore from "@/store/Chat";
import request from '@/utils/request';


const props = defineProps<{
  visible: boolean;
  batchQueueId: string;
}>();
const router = useRouter();
const emits = defineEmits(['update:visible', 'deleteQueue'])

const visible = ref(false);
const taskVisible = ref(false);
const batchQueueInfo = ref<BatchQueue>({
  id: '',
  title: '',
  status: 'pending',
  role: '',
  createdAt: '',
  startedAt: '',
  completedAt: '',
  currentIndex: 0,
  tasks: [],
  batchStats: {
    total: 0,
    pending: 0,
    running: 0,
    completed: 0,
    error: 0,
    cancelled: 0,
    progress: 0
  }
});
const currentTask = ref<BatchTask>();

const formRef = useTemplateRef<FormContext>('formRef');
const form = ref({
  taskMessage: ''
});
const rules = ref<FormRules>({
  taskMessage: {
    required: true,
    message: '任务消息不能为空'
  }
});

const store = ChatStore();

watch(() => props, (val) => {
  visible.value = val.visible;
}, {
  deep: true
});

const onOpen = async () => {
  batchQueueInfo.value = {
    id: '',
    title: '',
    status: '',
    role: '',
    createdAt: '',
    startedAt: '',
    completedAt: '',
    currentIndex: 0,
    tasks: [],
    batchStats: {
      total: 0,
      pending: 0,
      running: 0,
      completed: 0,
      error: 0,
      cancelled: 0,
      progress: 0
    }
  };
  await getBatchQueueInfo();
  startIntervalRefresh();
};

const onClose = () => {
  stopIntervalRefresh();
  emits('update:visible', false);
};

const getBatchQueueInfo = async () => {
  const res = await request(`/api/batch-tasks/${props.batchQueueId}`);
  if (res.status == 200) {
    const response: BatchQueue = res.data;
    const queueStatusMap: Record<string, Record<'label' | 'elType', string>> = store.queueStatusMap;
    const { label, elType } = queueStatusMap[response.status] || {};
    response.statusLabel = label;
    response.statusElType = elType;
    response.createdAt = response.createdAt ? dayjs(response.createdAt).format('YYYY-MM-DD HH:mm:ss') : '';
    response.startedAt = response.startedAt ? dayjs(response.startedAt).format('YYYY-MM-DD HH:mm:ss') : '';
    response.completedAt = response.completedAt ? dayjs(response.completedAt).format('YYYY-MM-DD HH:mm:ss') : '';
    response.tasks.forEach((t: BatchTask) => {
      const { label, elType } = queueStatusMap[t.status] || {};
      t.statusLabel = label;
      t.statusElType = elType;
      t.startedAt = t.startedAt ? dayjs(t.startedAt).format('YYYY-MM-DD HH:mm:ss') : '';
      t.completedAt = t.completedAt ? dayjs(t.completedAt).format('YYYY-MM-DD HH:mm:ss') : '';
    });
    batchQueueInfo.value = response;
  } else {
    ElMessage.error('获取队列信息失败');
  }
};

let timer: NodeJS.Timeout | undefined;
const startIntervalRefresh = () => {
  if (batchQueueInfo.value.status === 'running' && !timer) {
    timer = setInterval(() => {
      if (batchQueueInfo.value.status === 'running') {
        getBatchQueueInfo();
      } else {
        stopIntervalRefresh();
      }
    }, 3000);
  }
};

const stopIntervalRefresh = () => {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
};

onBeforeUnmount(() => {
  stopIntervalRefresh();
});

// 执行队列
const startQueue = async (id: string) => {
  const res = await request({
    url: `/api/batch-tasks/${id}/start`,
    method: 'POST'
  });
  if (res.status == 200) {
    ElMessage.success('已开始执行');
    await getBatchQueueInfo();
    startIntervalRefresh();
  } else {
    ElMessage.error('启动队列失败');
  }
};

// 暂停队列
const puaseQueue = async (id: string) => {
  const res = await request({
    url: `/api/batch-tasks/${id}/cancel`, 
    method: 'POST'
  });
  if (res.status === 200) {
    getBatchQueueInfo();
    ElMessage.success('已请求暂停');
  } else {
    ElMessage.error('暂停队列失败');
  }
};

// 删除队列
const deleteQueue = (id: string) => {
  ElMessageBox.confirm('确定要删除这个批量任务队列吗？此操作不可恢复。', '删除队列', {
    type: 'error'
  }).then(() => {
    emits('deleteQueue', id);
    emits('update:visible', false);
  });
};

// 添加任务
const addTask = async () => {
  const result = await formRef.value?.validateField();
  if (result) {
    const res = await request({
      url: '/api/batch-tasks/task',
      method: 'POST',
      data: {
        message: form.value.taskMessage,
        queue: {
          id: batchQueueInfo.value.id
        }
      }
    });
    if (res.status === 200) {
      const task = res.data;
      batchQueueInfo.value.tasks.push(task);
      taskVisible.value = false;
      getBatchQueueInfo();
      ElMessage.success('添加任务成功');
    } else {
      ElMessage.error('添加任务失败');
    }
  }
};

const showTask = (task?: BatchTask) => {
  taskVisible.value = true;
  currentTask.value = task;
  if (task) {
    form.value.taskMessage = task.message;
  }
};

// 修改任务
const editTask = async () => {
  const result = await formRef.value?.validateField();
  if (result) {
    const { status } = await request({
      url: `/api/batch-tasks/task/${currentTask.value?.id}`,
      method: 'PUT',
      data: {
        id: currentTask.value?.id,
        message: form.value.taskMessage
      }
    });
    if (status === 200) {
      taskVisible.value = false;
      getBatchQueueInfo();
      ElMessage.success('修改任务成功');
    } else {
      ElMessage.error('修改任务失败');
    }
  }
};

// 删除任务
const deleteTask = async (id: string) => {
  await ElMessageBox.confirm('确定要删除这个任务吗', '删除任务', {
    type: 'error'
  });
  const { status } = await request({
    url: `/api/batch-tasks/task/${id}`,
    method: 'DELETE'
  });
  if (status === 200) {
    getBatchQueueInfo();
    ElMessage.success('删除任务成功');
  } else {
    ElMessage.error('删除任务失败');
  }
};

// 查看对话
const goChat = (conversationId: string) => {
  router.push({
    path: '/chat',
    query: {
      conversationId
    }
  })
};
</script>

<style lang="scss" scoped>
.batch-queue-header {
  display: flex;
  justify-content: space-between;

  >h4 {
    margin: 0;
  }
}

.batch-queue-detail-info {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--bg-secondary);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;

  .detail-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px;
    background: var(--bg-primary);
    border-radius: 8px;
    border: 1px solid var(--border-color);
    transition: all 0.2s ease;

    &:hover {
      border-color: var(--accent-color);
      box-shadow: 0 2px 8px rgba(0, 102, 255, 0.08);
    }

    .detail-label {
      font-size: 0.75rem;
      color: var(--text-secondary);
      font-weight: 500;
      letter-spacing: 0.3px;
      text-transform: uppercase;
    }

    .detail-value {
      font-size: 0.9375rem;
      color: var(--text-primary);
      font-weight: 500;
      word-break: break-word;

      >code {
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 0.875rem;
        background: var(--bg-secondary);
        padding: 2px 6px;
        border-radius: 4px;
        color: var(--accent-color);
      }
    }
  }
}

.batch-queue-tasks-list {
  max-height: 500px;
  overflow-y: auto;

  >h4 {
    margin: 0 0 16px 0;
    font-size: 1rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .batch-task-item {
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 12px;
    transition: all 0.2s ease;

    .batch-task-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      >span {
        display: inline-flex;
        align-items: center;
      }
    }

    .batch-task-index {
      margin-right: 12px;
      font-weight: 600;
      color: var(--text-secondary);
      min-width: 30px;

      +.el-tag {
        margin-right: 12px;
      }
    }

    .batch-task-message {
      margin-right: 12px;
      font-size: 0.875rem;
      color: var(--text-primary);
      word-break: break-word;
    }

    .batch-task-time {
      font-size: 0.75rem;
      color: var(--text-secondary);
      margin-top: 4px;
    }

    .batch-task-error {
      font-size: 0.8125rem;
      color: var(--error-color);
      margin-top: 8px;
      padding: 8px;
      background: rgba(220, 53, 69, 0.05);
      border-radius: 4px;
      border-left: 3px solid var(--error-color);
    }
  }
}
</style>