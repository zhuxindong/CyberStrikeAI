<script setup lang="ts">
import { ref, nextTick, watch, onMounted } from 'vue';
import { streamChat } from '../utils/chatService';
import MarkdownIt from 'markdown-it';
import 'element-plus/theme-chalk/display.css';
import { Promotion, Monitor, Loading, ChatLineRound, User, ArrowDown, Connection, Cpu, MagicStick, Box, Aim, ZoomIn, View, Cloudy, Check } from '@element-plus/icons-vue';

const md = new MarkdownIt();

interface Message {
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
  type?: string; 
  toolName?: string;
  toolArgs?: string;
  toolResult?: string;
  timestamp: number;
}

const props = defineProps<{
  conversationId?: string;
}>();

const input = ref('');
const messages = ref<Message[]>([]);
const loading = ref(false);
const currentConversationId = ref<string | undefined>(props.conversationId);
const currentTaskId = ref<string | undefined>(undefined);
const messagesContainer = ref<HTMLElement | null>(null);

// Role Management
const roleIcons: Record<string, any> = {
  default: User,
  binary_analysis: Cpu,
  post_exploitation: MagicStick,
  container_security: Box,
  pen_tester: Aim,
  digital_forensics: ZoomIn,
  info_gathering: View,
  cloud_audit: Cloudy
};

const getRoleIcon = (roleId?: string) => {
  return (roleId && roleIcons[roleId]) || User;
};

const rolePopoverVisible = ref(false);
const roles = ref<any[]>([]);
const selectedRole = ref<any>(null);

const selectRole = (role: any) => {
  selectedRole.value = role;
  rolePopoverVisible.value = false;
};

const fetchRoles = async () => {
  try {
    const response = await fetch('/api/roles');
    if (response.ok) {
      roles.value = await response.json();
      // Select default role if available
      if (roles.value.length > 0 && !selectedRole.value) {
        selectedRole.value = roles.value[0];
      }
    }
  } catch (error) {
    console.error('Failed to fetch roles:', error);
  }
};

// 加载对话历史消息
const loadConversationHistory = async (conversationId: string) => {
  loading.value = true;
  try {
    const response = await fetch(`/api/conversations/${conversationId}`);
    if (response.ok) {
      const data = await response.json();
      if (data.messages && Array.isArray(data.messages)) {
        messages.value = data.messages.map((msg: any) => ({
          role: msg.role as 'user' | 'assistant' | 'system' | 'tool',
          content: msg.content || '',
          timestamp: new Date(msg.createdAt).getTime()
        }));
        await scrollToBottom();
      }
    }
  } catch (error) {
    console.error('Failed to load conversation history:', error);
  } finally {
    loading.value = false;
  }
};

// 监听 prop 变化，加载历史消息
watch(() => props.conversationId, async (newId) => {
  if (newId && newId !== currentConversationId.value) {
    currentConversationId.value = newId;
    messages.value = [];
    await loadConversationHistory(newId);
  } else if (!newId) {
    currentConversationId.value = undefined;
    messages.value = [];
  }
}, { immediate: true });

onMounted(() => {
  fetchRoles();
});

const scrollToBottom = async () => {
  await nextTick();
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  }
};


const sendMessage = async () => {
  if (!input.value.trim() || loading.value) return;

  const userMsg = input.value;
  input.value = '';
  loading.value = true;

  // Add user message
  messages.value.push({
    role: 'user',
    content: userMsg,
    timestamp: Date.now()
  });
  await scrollToBottom();

  // Initial assistant placeholder tracking
  streamChat(userMsg, {
    onMessage: (content, type, data) => {
      // 保存任务ID
      if (type === 'task_started' && data?.taskId) {
        currentTaskId.value = data.taskId;
        if (data.conversationId) {
          currentConversationId.value = data.conversationId;
        }
      } else if (type === 'conversation') {
        if (data && data.conversationId) {
          currentConversationId.value = data.conversationId;
        }
      } else if (type === 'cancelled') {
        messages.value.push({
          role: 'system',
          content: '任务已取消',
          timestamp: Date.now()
        });
        loading.value = false;
        currentTaskId.value = undefined;
        scrollToBottom();
      } else if (type === 'thinking' || type === 'response') {
        const lastMsg = messages.value[messages.value.length - 1];
        if (lastMsg && lastMsg.role === 'assistant' && lastMsg.type === type) {
            // Append if same type and role
            messages.value[messages.value.length - 1].content = content;
        } else {
            messages.value.push({
              role: 'assistant',
              content: content,
              type: type,
              timestamp: Date.now()
            });
        }
        scrollToBottom();
      } else if (type === 'tool_call') {
        messages.value.push({
          role: 'tool',
          content: `Calling Tool: ${data?.toolName}`,
          toolName: data?.toolName,
          toolArgs: data?.arguments ? JSON.stringify(data.arguments, null, 2) : '',
          timestamp: Date.now()
        });
        scrollToBottom();
      } else if (type === 'tool_result') {
         messages.value.push({
            role: 'tool',
            content: content,
            toolResult: content,
            timestamp: Date.now()
         });
         scrollToBottom();
      } else if (type === 'error') {
        messages.value.push({
          role: 'system',
          content: `Error: ${content}`,
          timestamp: Date.now()
        });
      }
    },
    onError: (err) => {
      console.error(err);
      messages.value.push({
        role: 'system',
        content: "Connection error: " + err.message,
        timestamp: Date.now()
      });
      loading.value = false;
      scrollToBottom();
    },
    onDone: () => {
      loading.value = false;
      currentTaskId.value = undefined;
      scrollToBottom();
    }
  }, currentConversationId.value, selectedRole.value?.name);
};

// 停止当前任务
const stopTask = async () => {
  if (!currentTaskId.value) return;
  try {
    await fetch('/api/agent-loop/cancel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ task_id: currentTaskId.value })
    });
  } catch (error) {
    console.error('Failed to cancel task:', error);
  }
};

const renderMarkdown = (text: string) => {
  return md.render(text || '');
};
</script>

<template>
  <div class="chat-container">
    <div class="messages-area" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <el-icon :size="64" class="icon"><Monitor /></el-icon>
        <h3>CyberStrike AI Ready</h3>
        <p>Enter a target or command to start the investigation.</p>
      </div>
      
      <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
        <div class="message-bubble">
          <div class="message-header">
            <span class="role-badge">
              {{ msg.role.toUpperCase() }} {{ msg.type ? `(${msg.type})` : '' }}
            </span>
            <span class="time">{{ new Date(msg.timestamp).toLocaleTimeString() }}</span>
          </div>
          
          <div v-if="msg.role === 'tool'" class="tool-content">
             <div v-if="msg.toolName"><strong>Tool:</strong> {{ msg.toolName }}</div>
             <pre v-if="msg.toolArgs" class="code-block">{{ msg.toolArgs }}</pre>
             <pre v-if="msg.toolResult" class="result-block">{{ msg.toolResult }}</pre>
             <div v-if="!msg.toolName && !msg.toolResult">{{ msg.content }}</div>
          </div>
          
          <div v-else class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
        </div>
      </div>
      
      <div v-if="loading" class="typing-indicator">
        <el-icon class="is-loading"><Loading /></el-icon> Processing...
      </div>
    </div>

    <div class="input-area">
      <!-- Role Selector -->
      <div class="role-selector-wrapper" v-if="!loading">
        <el-popover
          :visible="rolePopoverVisible"
          @update:visible="val => rolePopoverVisible = val"
          placement="top-start"
          :width="320"
          trigger="click"
          popper-class="role-selector-popover"
        >
          <template #reference>
            <span class="role-selector-btn" :title="selectedRole?.description || '选择角色'">
              <el-icon class="role-icon" :size="16">
                <component :is="getRoleIcon(selectedRole?.id)" />
              </el-icon>
              <span class="role-text">{{ selectedRole?.name || '默认' }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
          </template>

          <div class="role-list-container">
            <div class="role-list-header">选择角色</div>
            <div class="role-list">
              <div 
                v-for="role in roles" 
                :key="role.id" 
                class="role-item"
                :class="{ active: selectedRole?.id === role.id }"
                @click="selectRole(role)"
              >
                <div class="role-item-icon">
                  <el-icon :size="20">
                     <component :is="getRoleIcon(role.id)" />
                  </el-icon>
                </div>
                <div class="role-item-content">
                  <div class="role-item-title">{{ role.name }}</div>
                  <div class="role-item-desc" :title="role.systemPrompt">
                    {{ role.systemPrompt.substring(0, 30) }}...
                  </div>
                </div>
                <div class="role-item-check" v-if="selectedRole?.id === role.id">
                  <el-icon><Check /></el-icon>
                </div>
              </div>
            </div>
          </div>
        </el-popover>
      </div>

      <el-input
        v-model="input"
        :autosize="{ minRows: 2, maxRows: 6 }"
        type="textarea"
        placeholder="输入命令 (例如: 扫描 localhost)"
        @keydown.enter.exact.prevent="sendMessage"
        :disabled="loading"
        class="chat-input"
      />
      <div class="button-group">
        <el-button type="primary" :loading="loading" @click="sendMessage" :disabled="loading">发送</el-button>
        <el-button v-if="loading" type="danger" @click="stopTask">停止</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  max-width: 1200px;
  margin: 0 auto;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  scroll-behavior: smooth;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--el-text-color-secondary);
}

.message-row {
  display: flex;
  margin-bottom: 15px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant, .message-row.tool, .message-row.system {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 85%;
  padding: 10px 15px;
  border-radius: 8px;
  background-color: var(--el-bg-color-overlay);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.message-row.user .message-bubble {
  background-color: var(--el-color-primary);
  color: white;
}

.message-row.tool .message-bubble {
  background-color: #1e1e1e;
  color: #d4d4d4;
  font-family: monospace;
  width: 95%;
}

.message-header {
  font-size: 0.75rem;
  margin-bottom: 5px;
  opacity: 0.7;
  display: flex;
  justify-content: space-between;
}

.code-block {
  background: rgba(255,255,255,0.1);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}

.result-block {
  border-left: 3px solid green;
  padding-left: 10px;
  color: #9cdcfe;
}

.markdown-body :deep(pre) {
  background: #f0f0f0;
  padding: 10px;
  border-radius: 4px;
}

.input-area {
  padding: 15px;
  border-top: 1px solid var(--el-border-color);
  display: flex;
  gap: 10px;
  align-items: flex-end; /* changed to align bottom */
  background-color: #f8f9fa; /* added background */
}

/* Role Selector Styles */
.role-selector-wrapper {
  flex-shrink: 0;
  margin-bottom: 4px; /* Align with textarea bottom */
}

.role-selector-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background-color: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--el-text-color-primary);
  font-size: 14px;
  height: 32px;
  box-sizing: border-box;
}

.role-selector-btn:hover {
  background-color: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.role-icon {
  font-size: 16px;
  color: var(--el-color-primary);
}

.role-text {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.button-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  padding: 10px;
}

.chat-input {
    flex: 1;
}
</style>

<style>
/* Global styles for popover content */
.role-selector-popover {
  padding: 0 !important;
  border-radius: 12px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}

.role-list-container {
  display: flex;
  flex-direction: column;
  max-height: 400px;
}

.role-list-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
}

.role-list {
  padding: 8px;
  overflow-y: auto;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  margin-bottom: 4px;
}

.role-item:hover {
  background-color: var(--el-fill-color-light);
}

.role-item.active {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.role-item-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
  border-radius: 8px;
  color: #606266;
}

.role-item.active .role-item-icon {
  background-color: var(--el-color-primary);
  color: white;
}

.role-item-content {
  flex: 1;
  min-width: 0;
}

.role-item-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}

.role-item-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-item-check {
  color: var(--el-color-primary);
}
</style>

