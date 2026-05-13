<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { Plus, ChatDotRound, Delete, Star, StarFilled, Search } from '@element-plus/icons-vue';
import ConversationStore from "@/store/Conversation";
import { storeToRefs } from 'pinia';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

export interface Conversation {
  id: string;
  title: string;
  updatedAt: string;
  pinned: boolean;
}

const store = ConversationStore();
const { conversationId } = storeToRefs(store);
const conversations = ref<Conversation[]>([]);
const loading = ref(false);
const searchQuery = ref('');

const fetchConversations = async () => {
  loading.value = true;
  try {
    const response = await request('/api/conversations');
    if (response.status === 200) {
      conversations.value = response.data;
    }
  } catch (error) {
    console.error('Failed to fetch conversations:', error);
  } finally {
    loading.value = false;
  }
};

const createConversation = async () => {
  try {
    const response = await request('/api/conversations', {
      method: 'POST',
      data: { title: '新对话' }
    });
    if (response.status === 200) {
      const newConv = response.data;
      conversationId.value = newConv.id;
      fetchConversations();
    }
  } catch (error) {
    console.error('Failed to create conversation:', error);
  }
};

const deleteConversation = async (id: string) => {
  const action: any = await ElMessageBox.confirm('确定删除该对话吗？', '删除对话', {
    type: 'warning'
  });
  if (action === 'confirm') {
    try {
      const response = await request(`/api/conversations/${id}`, { method: 'DELETE' });
      if (response.status === 200) {
        conversations.value = conversations.value.filter(c => c.id !== id);
        ElMessage.success('删除成功');
      }
    } catch (error) {
      console.error('Failed to delete conversation:', error);
      ElMessage.success('删除失败');
    }
  }
};

const togglePin = async (conv: Conversation) => {
  try {
    const response = await request(`/api/conversations/${conv.id}/pinned`, {
      method: 'PUT',
      data: { pinned: !conv.pinned }
    });
    if (response.status === 200) {
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

const filteredConversations = () => {
  if (!searchQuery.value) return conversations.value;
  return conversations.value.filter(c => c.title.toLowerCase().includes(searchQuery.value.toLowerCase()));
};

onMounted(() => {
  fetchConversations();
});

onUnmounted(() => {
  conversationId.value = '';
});
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <el-button class="new-chat-btn" type="primary" @click="createConversation">
        <el-icon><Plus /></el-icon> 新对话
      </el-button>
    </div>
    
    <div class="sidebar-content">
      <div class="conversation-search-box">
        <el-input 
          v-model="searchQuery" 
          placeholder="搜索历史记录..." 
          :prefix-icon="Search"
          class="search-input"
        />
      </div>

      <el-scrollbar class="conversation-list">
        <div v-if="conversations.length === 0" class="empty-state">
          <p>暂无对话</p>
        </div>
        
        <div v-else v-loading="loading">
          <!-- 置顶对话 -->
          <div class="section-title" v-if="filteredConversations().some(c => c.pinned)">置顶对话</div>
          <template v-for="conv in filteredConversations().filter(c => c.pinned)" :key="conv.id">
            <div 
              class="conversation-item pinned" 
              :class="{ active: conv.id === conversationId }"
              @click="conversationId = conv.id"
            >
              <el-icon class="pin-icon"><StarFilled /></el-icon>
              <div class="conv-content">
                <div class="conv-title">{{ conv.title }}</div>
                <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
              </div>
              <div class="conv-actions">
                <el-icon @click.stop="togglePin(conv)"><StarFilled /></el-icon>
                <el-icon @click.stop="deleteConversation(conv.id)"><Delete /></el-icon>
              </div>
            </div>
          </template>
          
          <div class="section-title">最近对话</div>
          <template v-for="conv in filteredConversations().filter(c => !c.pinned)" :key="conv.id">
            <div 
              class="conversation-item" 
              :class="{ active: conv.id === conversationId }"
              @click="conversationId = conv.id"
            >
              <el-icon class="conv-icon"><ChatDotRound /></el-icon>
              <div class="conv-content">
                <div class="conv-title">{{ conv.title }}</div>
                <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
              </div>
              <div class="conv-actions">
                <el-icon @click.stop="togglePin(conv)"><Star /></el-icon>
                <el-icon @click.stop="deleteConversation(conv.id)"><Delete /></el-icon>
              </div>
            </div>
          </template>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: 100%;
  height: 100%;
  background: linear-gradient(180deg, #ffffff 0%, #fafbfc 100%);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  flex-shrink: 0;
}

.new-chat-btn {
  width: 100%;
  padding: 10px 16px;
  font-size: 14px;
}

.new-chat-btn:hover {
  background-color: var(--el-color-primary-light-3);
  transform: translateY(-1px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.sidebar-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 16px 16px 16px;
}

.conversation-search-box {
  margin-bottom: 12px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 12px 0 8px 0;
  padding: 0 4px;
}

.conversation-list {
  flex: 1;
}

.loading-state, .empty-state {
  padding: 20px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 6px;
  margin-bottom: 2px;
}

.conversation-item:hover {
  background-color: var(--el-fill-color-light);
}

.conversation-item.active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.conversation-item.active .conv-title {
  font-weight: 500;
}

.conv-icon, .pin-icon {
  margin-right: 10px;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.conversation-item.active .conv-icon {
    color: var(--el-color-primary);
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
  color: var(--el-text-color-primary);
}

.conversation-item.active .conv-title {
    color: var(--el-color-primary);
}

.conv-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.conv-actions {
  display: none;
  gap: 6px;
}

.conversation-item:hover .conv-actions {
  display: flex;
}

.conv-actions .el-icon {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  padding: 2px;
}

.conv-actions .el-icon:hover {
  color: var(--el-color-primary);
  background: rgba(0,0,0,0.05);
  border-radius: 4px;
}
</style>
