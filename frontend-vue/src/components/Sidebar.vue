<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, ChatDotRound, Delete, Star, StarFilled } from '@element-plus/icons-vue';

interface Conversation {
  id: string;
  title: string;
  updatedAt: string;
  pinned: boolean;
}

const props = defineProps<{
  currentId?: string;
}>();

const emit = defineEmits<{
  (e: 'select', id: string): void;
  (e: 'create'): void;
}>();

const conversations = ref<Conversation[]>([]);
const loading = ref(false);

const fetchConversations = async () => {
  loading.value = true;
  try {
    const response = await fetch('/api/conversations');
    if (response.ok) {
      conversations.value = await response.json();
    }
  } catch (error) {
    console.error('Failed to fetch conversations:', error);
  } finally {
    loading.value = false;
  }
};

const createConversation = async () => {
  try {
    const response = await fetch('/api/conversations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: '新对话' })
    });
    if (response.ok) {
      const newConv = await response.json();
      conversations.value.unshift(newConv);
      emit('select', newConv.id);
    }
  } catch (error) {
    console.error('Failed to create conversation:', error);
  }
};

const deleteConversation = async (id: string, event: Event) => {
  event.stopPropagation();
  if (!confirm('确定删除此对话？')) return;
  
  try {
    const response = await fetch(`/api/conversations/${id}`, { method: 'DELETE' });
    if (response.ok) {
      conversations.value = conversations.value.filter(c => c.id !== id);
    }
  } catch (error) {
    console.error('Failed to delete conversation:', error);
  }
};

const togglePin = async (conv: Conversation, event: Event) => {
  event.stopPropagation();
  try {
    const response = await fetch(`/api/conversations/${conv.id}/pinned`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pinned: !conv.pinned })
    });
    if (response.ok) {
      conv.pinned = !conv.pinned;
    }
  } catch (error) {
    console.error('Failed to toggle pin:', error);
  }
};

const formatTime = (dateStr: string) => {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
  return date.toLocaleDateString();
};

onMounted(() => {
  fetchConversations();
});
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <h3>对话列表</h3>
      <el-button type="primary" :icon="Plus" circle size="small" @click="createConversation" />
    </div>
    
    <el-scrollbar class="conversation-list">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      
      <div v-else-if="conversations.length === 0" class="empty-state">
        <p>暂无对话</p>
        <el-button type="primary" size="small" @click="createConversation">创建对话</el-button>
      </div>
      
      <div v-else>
        <!-- 置顶对话 -->
        <template v-for="conv in conversations.filter(c => c.pinned)" :key="conv.id">
          <div 
            class="conversation-item pinned" 
            :class="{ active: conv.id === currentId }"
            @click="emit('select', conv.id)"
          >
            <el-icon class="pin-icon"><StarFilled /></el-icon>
            <div class="conv-content">
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
            </div>
            <div class="conv-actions">
              <el-icon @click="togglePin(conv, $event)"><StarFilled /></el-icon>
              <el-icon @click="deleteConversation(conv.id, $event)"><Delete /></el-icon>
            </div>
          </div>
        </template>
        
        <!-- 普通对话 -->
        <template v-for="conv in conversations.filter(c => !c.pinned)" :key="conv.id">
          <div 
            class="conversation-item" 
            :class="{ active: conv.id === currentId }"
            @click="emit('select', conv.id)"
          >
            <el-icon class="conv-icon"><ChatDotRound /></el-icon>
            <div class="conv-content">
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
            </div>
            <div class="conv-actions">
              <el-icon @click="togglePin(conv, $event)"><Star /></el-icon>
              <el-icon @click="deleteConversation(conv.id, $event)"><Delete /></el-icon>
            </div>
          </div>
        </template>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.sidebar {
  width: 280px;
  height: 100%;
  background-color: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.loading-state, .empty-state {
  padding: 40px 20px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.conversation-item:hover {
  background-color: var(--el-fill-color-light);
}

.conversation-item.active {
  background-color: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
}

.conversation-item.pinned {
  background-color: var(--el-color-warning-light-9);
}

.conv-icon, .pin-icon {
  margin-right: 10px;
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.pin-icon {
  color: var(--el-color-warning);
}

.conv-content {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.conv-actions {
  display: none;
  gap: 8px;
}

.conversation-item:hover .conv-actions {
  display: flex;
}

.conv-actions .el-icon {
  cursor: pointer;
  color: var(--el-text-color-secondary);
}

.conv-actions .el-icon:hover {
  color: var(--el-color-primary);
}
</style>
