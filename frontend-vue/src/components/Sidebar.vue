<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { Plus, ChatDotRound, Delete, Star, StarFilled, Search } from '@element-plus/icons-vue';
import ConversationStore from "@/store/Conversation";
import ChatStore from "@/store/Chat";
import { storeToRefs } from 'pinia';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

export interface Conversation {
  id: string;
  title: string;
  updatedAt: string;
  pinned: boolean;
}

const convStore = ConversationStore();
const chatStore = ChatStore();
const { conversationId } = storeToRefs(convStore);
const { reasoningSettings, hitlSetting } = storeToRefs(chatStore);
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

const apply = () => {
  chatStore.saveHitlSetting();
  ElMessage.success('人机协同配置已保存');
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
        <el-icon>
          <Plus />
        </el-icon> 新对话
      </el-button>
    </div>

    <div class="sidebar-content">
      <div class="conversation-search-box">
        <el-input v-model="searchQuery" placeholder="搜索历史记录..." :prefix-icon="Search" class="search-input" />
      </div>

      <el-scrollbar class="conversation-list">
        <div v-if="conversations.length === 0" class="empty-state">
          <p>暂无对话</p>
        </div>

        <div v-else v-loading="loading">
          <!-- 置顶对话 -->
          <div class="section-title" v-if="filteredConversations().some(c => c.pinned)">置顶对话</div>
          <template v-for="conv in filteredConversations().filter(c => c.pinned)" :key="conv.id">
            <div class="conversation-item pinned" :class="{ active: conv.id === conversationId }"
              @click="conversationId = conv.id">
              <el-icon class="pin-icon">
                <StarFilled />
              </el-icon>
              <div class="conv-content">
                <div class="conv-title">{{ conv.title }}</div>
                <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
              </div>
              <div class="conv-actions">
                <el-icon @click.stop="togglePin(conv)">
                  <StarFilled />
                </el-icon>
                <el-icon @click.stop="deleteConversation(conv.id)">
                  <Delete />
                </el-icon>
              </div>
            </div>
          </template>

          <div class="section-title">最近对话</div>
          <template v-for="conv in filteredConversations().filter(c => !c.pinned)" :key="conv.id">
            <div class="conversation-item" :class="{ active: conv.id === conversationId }"
              @click="conversationId = conv.id">
              <el-icon class="conv-icon">
                <ChatDotRound />
              </el-icon>
              <div class="conv-content">
                <div class="conv-title">{{ conv.title }}</div>
                <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
              </div>
              <div class="conv-actions">
                <el-icon @click.stop="togglePin(conv)">
                  <Star />
                </el-icon>
                <el-icon @click.stop="deleteConversation(conv.id)">
                  <Delete />
                </el-icon>
              </div>
            </div>
          </template>
        </div>
      </el-scrollbar>

      <div class="sidebar-setting">
        <div>
          <div class="sidebar-setting-toggle" @click="reasoningSettings.expanded = !reasoningSettings.expanded">
            <div class="sidebar-setting-icon">
              <el-icon>
                <Search />
              </el-icon>
            </div>
            <div class="sidebar-setting-heading">
              <p>模型推理</p>
              <p>{{ reasoningSettings.mode }} / {{ reasoningSettings.effort }}</p>
            </div>
          </div>
          <div :class="['sidebar-setting-body', { expanded: reasoningSettings.expanded }]">
            <p class="sidebar-setting-hint">仅 Eino 单代理与多代理请求会带上这些参数；与系统设置中的默认值合并。</p>
            <el-form-item label-position="top" label="模型推理">
              <el-select v-model="reasoningSettings.mode">
                <el-option label="跟随系统" value="default" />
                <el-option label="关闭" value="off" />
                <el-option label="开启" value="on" />
                <el-option label="自动" value="auto" />
              </el-select>
            </el-form-item>
            <el-form-item label-position="top" label="推理强度">
              <el-select v-model="reasoningSettings.effort">
                <el-option label="不指定" value="-" />
                <el-option label="low" value="low" />
                <el-option label="medium" value="medium" />
                <el-option label="high" value="high" />
                <el-option label="max" value="max" />
              </el-select>
            </el-form-item>
          </div>
        </div>
        <div>
          <div class="sidebar-setting-toggle" @click="hitlSetting.expanded = !hitlSetting.expanded">
            <div class="sidebar-setting-icon">
              <el-icon>
                <Avatar />
              </el-icon>
            </div>
            <div class="sidebar-setting-heading">
              <p>人机协同</p>
              <p>审批与白名单</p>
            </div>
          </div>
          <div :class="['sidebar-setting-body', { expanded: hitlSetting.expanded }]">
            <el-form-item label-position="top" label="模式">
              <el-select v-model="hitlSetting.mode">
                <el-option label="关闭" value="off" />
                <el-option label="审批模式" value="approval" />
                <el-option label="审查模式" value="review_edit" />
              </el-select>
            </el-form-item>
            <el-form-item label-position="top" label="白名单工具">
              <el-input v-model="hitlSetting.sensitiveTools" type="textarea" row="3" />
              <p class="sidebar-setting-hint">
                每行一个或逗号分隔；与 config 中全局白名单合并展示。
              </p>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" @click="apply">应用</el-button>
            </el-form-item>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
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
  position: relative;
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

.loading-state,
.empty-state {
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

.conv-icon,
.pin-icon {
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
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
}

.sidebar-setting {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;

  >div {
    border-top: 1px solid var(--border-color);
    background: linear-gradient(165deg, #f8fafc 0%, #f1f5f9 55%, #eef2f7 100%);
    padding: 11px 12px;
    flex-shrink: 0;
  }

  .sidebar-setting-toggle {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    cursor: pointer;
    gap: 8px;

    .sidebar-setting-icon {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 9px;
      background: linear-gradient(145deg, rgba(0, 102, 255, 0.12), rgba(0, 102, 255, 0.06));
      color: var(--accent-color);
      border: 1px solid rgba(0, 102, 255, 0.18);
    }

    .sidebar-setting-heading {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 1px;

      >p {
        &:nth-child(1) {
          font-size: 14px;
          font-weight: 700;
          letter-spacing: -0.02em;
          color: var(--text-primary);
          line-height: 1.2;
        }

        &:nth-child(2) {
          font-size: 11px;
          font-weight: 500;
          color: var(--text-secondary);
          line-height: 1.25;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }

  .sidebar-setting-body {
    overflow: hidden;
    max-height: 0;
    opacity: 1;
    margin-top: 8px;
    padding-bottom: 0;
    transition: max-height 0.3s ease, opacity 0.2s ease, margin-top 0.3s ease;

    &.expanded {
      max-height: 280px;
    }

    ::v-deep .el-form-item__label {
      font-size: 14px;
    }

    .sidebar-setting-hint {
      font-size: 0.75rem;
      color: var(--text-muted, #718096);
      margin: 0;
      line-height: 1.45;
    }
  }
}
</style>
