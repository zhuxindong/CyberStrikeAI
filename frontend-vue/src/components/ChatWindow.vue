<script setup lang="ts">
import { ref, watch, onMounted, reactive, useTemplateRef } from 'vue';
import { getTitleByType, streamChat, scrollToBottom } from '../utils/chatService';
import { escapeHtml } from '../utils/escape';
import MarkdownIt from 'markdown-it';
import { dayjs, ElMessage } from 'element-plus';
import 'element-plus/theme-chalk/display.css';
import { Monitor, Loading, User, ArrowDown, Cpu, MagicStick, Box, Aim, ZoomIn, View, Cloudy, Check } from '@element-plus/icons-vue';
import AttackChainView from './AttackChainView.vue';
import McpCallDialog from "./McpCallDialog.vue";

import { storeToRefs } from 'pinia';
import ConversationStore from "@/store/Conversation";
import ChatStore from "@/store/Chat";
import { useRoute } from 'vue-router';

const md = new MarkdownIt();

// 调用序列项
export interface TimelineItem {
  id?: string;
  type?: string;
  mcpExecutionIds?: string;
  functionName?: string;
  iteration?: string;
  title?: string;
  content?: string;
  createdAt?: string;
  args?: string;
  resultStatus?: string;
}

export interface Message {
  id?: string;
  role: 'user' | 'assistant' | 'system';
  content?: string;
  type?: string; 
  toolName?: string;
  toolArgs?: string;
  toolResult?: string;
  timestamp: number;
  timelineItems?: TimelineItem[]; // 调用序列
  expanded?: boolean; // 时间轴是否展开
  mcpCalls?: any[];
}

interface ActiveTaskMessage {
  id: string;
  taskId: string;
  status: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  lastReactInput: string;
  lastReactOutput: string;
  pinned: boolean;
}

const input = ref('');
const messages = reactive<Message[]>([]);
const activeTasks = ref<ActiveTaskMessage[]>([]);
const loading = ref(false);
const currentTaskId = ref<string | undefined>(undefined);
const progressTitle = ref<string>('');
const messageContainer = useTemplateRef<HTMLElement>('messageContainer');

const showAttackChain = ref(false);
const mcpCallDialogVisible = ref<boolean>(false);
const mcpCallDetail = ref<unknown>({});

const conversionStore = ConversationStore();
const chatStore = ChatStore();
const { conversationId: currentConversationId } = storeToRefs(conversionStore);

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
      const rawMessages = data.messages;
      if (rawMessages && Array.isArray(rawMessages)) {
        messages.splice(0);
        rawMessages.forEach((msg: any) => {
          const item: Message = {
            id: msg.id,
            role: msg.role,
            content: msg.content || '',
            timestamp: new Date(msg.createdAt).getTime()
          };
          if (msg.role === 'assistant') {
            item.timelineItems = msg.messageList.map((item: any) => {
              item.title = getTitleByType(item.type, item);
              item.createdAt = dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss');
              item.args = item.dataJson ? JSON.stringify(JSON.parse(item.dataJson).arguments, null, 2) : '';
              return item;
            });
            generateMCPCalls(item);
            item.expanded = false;
          }
          messages.push(item);
        });
        scroll();
      }
    } else if (response.status === 404) {
      ElMessage.error('对话不存在');
    } else {
      ElMessage.error('加载对话历史失败');
    }
  } finally {
    loading.value = false;
  }
};

let activeTaskInterval: NodeJS.Timeout | undefined;
// 加载活跃任务
const loadActiveTasks = async () => {
  const res = await fetch('/api/conversations/tasks');
  if (res.ok) {
    const queueStatusMap: Record<string, Record<'label' | 'elType', string>> = chatStore.queueStatusMap;
    const data = await res.json();
    activeTasks.value = data.map((item: ActiveTaskMessage) => {
      item.createdAt = dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss');
      item.updatedAt = dayjs(item.updatedAt).format('YYYY-MM-DD HH:mm:ss');
      item.status = queueStatusMap[item.status].label;
      return item;
    });
    if (data.length === 0) {
      stopLoadActiveTasks();
    } else if (!activeTaskInterval) {
      activeTaskInterval = setInterval(() => {
        loadActiveTasks();
      }, 3000);
    }
  }
};

// 停止加载活跃任务
const stopLoadActiveTasks = () => {
  activeTasks.value = [];
  clearInterval(activeTaskInterval);
  activeTaskInterval = undefined;
};

// 监听 conversationId 变化，加载历史消息
watch(() => currentConversationId.value, async (newId) => {
  messages.splice(0);
  if(newId) {
    await loadConversationHistory(newId);
  }
});

const route = useRoute();
onMounted(() => {
  const { conversationId } = route.query || {};
  if (conversationId) {
    if (conversationId == currentConversationId.value) {
      loadConversationHistory(conversationId);
    } else {
      currentConversationId.value = conversationId as string;
    }
  }
  fetchRoles();
});

const scroll = () => {
  scrollToBottom(messageContainer.value);
};

// 生成调用工具信息
const generateMCPCalls = (message: Message) => {
  const timelineItems: TimelineItem[] = message.timelineItems || [];
  message.mcpCalls = timelineItems.filter(item => item.type === 'tool_call').map(item => ({
    id: item.id,
    toolName: item.functionName
  }));
};

const sendMessage = async () => {
  if (!input.value.trim() || loading.value) return;

  const userMsg = input.value;
  input.value = '';
  loading.value = true;

  // Add user message
  messages.push({
    role: 'user',
    content: userMsg,
    timestamp: Date.now()
  });
  
  messages.push({
    id: Date.now().toString(),
    role: 'assistant',
    timestamp: Date.now(),
    content: '',
    timelineItems: []
  });

  scroll();

  let streamingConversatinoId: string | undefined = currentConversationId.value;
  // Initial assistant placeholder tracking
  streamChat(userMsg, {
    onMessage: (id, content, type, data) => {
      // 流式输出的对话Id和当前对话Id不一致时，不会输出对话
      if (currentConversationId.value !== streamingConversatinoId) {
        loadActiveTasks();
        return;
      }
      // 最近的一条消息
      const lastMessage: Message = messages[messages.length - 1];
      lastMessage.expanded = true;
      lastMessage.timelineItems = lastMessage.timelineItems || [];
      const title = getTitleByType(type, data, content);
      const createdAt = dayjs(new Date()).format('YYYY-MM-DD HH:mm:ss');
      // 保存任务ID
      if (type === 'conversation') {
        if (data && data.taskId && data.conversationId) {
          currentTaskId.value = data.taskId;
          streamingConversatinoId = data.conversationId;
          progressTitle.value = '🔍 渗透测试进行中...';
          loadActiveTasks();
        }
      } else if (['iteration', 'thinking', 'tool_calls_detected'].includes(type)) {
        lastMessage.timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
      } else if (type === 'cancelled') {
        lastMessage.timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
        progressTitle.value = '⛔ 任务已取消';
      } else if (type === 'progress') {
        progressTitle.value = content;
      } else if (type === 'tool_call') {
        lastMessage.timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content,
          functionName: data.toolName,
          args: JSON.stringify(data.arguments, null, 2)
        });
      } else if (type === 'tool_result') {
        lastMessage.timelineItems.push({
          id: data.executionId,
          type,
          createdAt,
          title,
          content,
          resultStatus: data.resultStatus
        });
      } else if (type === 'error') {
        lastMessage.timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
        progressTitle.value = '❌ 执行失败';
      }
      scroll();
    },
    onCancel: () => {
      stopLoadActiveTasks();
      if (currentConversationId.value !== streamingConversatinoId) {
        return;
      }
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1].content;
      generateMCPCalls(lastMessage);
      toggleTimeline(lastMessage);
      
      loading.value = false;
      currentTaskId.value = undefined;
      progressTitle.value = '⛔ 任务已取消';
      scroll();
    },
    onError: () => {
      stopLoadActiveTasks();
      if (currentConversationId.value !== streamingConversatinoId) {
        return;
      }
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1]?.content;
      loading.value = false;
      progressTitle.value = '❌ 执行失败';
      scroll();
    },
    onDone: () => {
      stopLoadActiveTasks();
      if (currentConversationId.value !== streamingConversatinoId) {
        return;
      }
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1].content;
      generateMCPCalls(lastMessage);
      toggleTimeline(lastMessage);
      
      loading.value = false;
      currentTaskId.value = undefined;
      progressTitle.value = '✅ 渗透测试完成';
      scroll();
    }
  }, currentConversationId.value, selectedRole.value?.name);
};

// 停止任务
const stopTask = async (taskId?: string) => {
  taskId = taskId || currentTaskId.value;
  if (!taskId) return;
  try {
    const res = await fetch('/api/agent-loop/cancel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ task_id: taskId })
    });
    if (res.ok) {
      loadActiveTasks();
    } else {
      ElMessage.error('停止任务失败');
    }
  } catch (error) {
    console.error('Failed to cancel task:', error);
  }
};

// 展开/收起调用序列
const toggleTimeline = (message: Message) => {
  message.expanded = !message.expanded;
  scroll();
};

const showMcpCall = (messageId: string | undefined, id: string) => {
  mcpCallDialogVisible.value = true;
  const message = messages.find(mes => mes.id === messageId);
  if (message) {
    const timelineItems = message.timelineItems || [];
    const i = timelineItems.findIndex(item => item.id === id);
    if (i !== -1) {
      const toolCall = timelineItems[i];
      const toolResult = timelineItems[i + 1];
      let parsedContent: string;
      try {
        parsedContent = JSON.stringify(JSON.parse(toolResult.content || ''), null, 2);
      } catch (error) {
        parsedContent = toolResult.content || '';
      }
      mcpCallDetail.value = {
        id: toolCall.id,
        createdAt: toolCall.createdAt,
        args: toolCall.args,
        resultStatus: toolResult.resultStatus,
        mcpExecutionIds: toolCall.functionName,
        content: toolResult.content,
        parsedContent
      };
    } else {
      mcpCallDetail.value = {};
    }
  }
};

const renderMarkdown = (text: string | undefined) => {
  return md.render(text || '');
};
</script>

<template>
  <div class="chat-container">
    <div class="active-tasks">
      <el-scrollbar>
        <div class="task-container">
          <div v-for="task in activeTasks" :key="task.id" class="active-task-item">
            <div class="active-task-info">
              <span class="active-task-status">{{ task.status }}</span>
              <span class="active-task-message">{{ task.title }}</span>
            </div>
            <div class="active-task-actions">
              <span class="active-task-time">{{ task.createdAt }}</span>
              <el-button type="danger" @click="stopTask(task.taskId)">停止任务</el-button>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </div>
    <div class="messages-area" ref="messageContainer">
      <!-- <div v-if="!currentConversationId" class="empty-state">
        <el-icon :size="64" class="icon"><Monitor /></el-icon>
        <h3>CyberStrike AI Ready</h3>
        <p>Enter a target or command to start the investigation.</p>
      </div> -->
      
      <div v-for="(msg, index) in messages" :key="index" :class="['message-row', msg.role]">
        <div class="message-container">
          <div class="message-bubble">
            <div class="message-header">
              <span class="role-badge">
                {{ msg.role.toUpperCase() }} {{ msg.type ? `(${msg.type})` : '' }}
              </span>
              <span class="time">{{ new Date(msg.timestamp).toLocaleTimeString() }}</span>
            </div>
            <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <template v-if="msg.role === 'assistant'">
            <!-- 任务进行中 -->
            <div v-if="loading && index === messages.length - 1" class="progress-header">
              <span class="progress-title">{{ progressTitle }}</span>
              <div class="progress-actions">
                  <el-button type="danger" class="progress-stop" @click="stopTask()">停止任务</el-button>
                  <el-button @click="toggleTimeline(msg)">{{ msg.expanded ? '收起详情' : '展开详情'}}</el-button>
              </div>
            </div>
            <!-- 任务结束 -->
            <div v-else class="mcp-call-section">
              <span class="mcp-call-label">📋 渗透测试详情</span>
              <div class="mcp-call-buttons">
                <el-button size="small" v-for="item in msg.mcpCalls" :key="item.id" @click="showMcpCall(msg.id, item.id)">{{ item.toolName }}</el-button>
                <el-button :type="msg.expanded ? '' : 'primary'" size="small" @click="toggleTimeline(msg)">{{ msg.expanded ? '收起详情' : '展开详情'}}</el-button>
              </div>
            </div>
            <!-- 调用序列 -->
            <div v-show="msg.timelineItems?.length" :class="['progress-timeline', { 'expanded': msg.expanded }]">
              <div v-for="({ id, createdAt, title, type, content, args }) in msg.timelineItems"
                :key="id"
                :class="['timeline-item', `timeline-item-${type}`]">
                <div class="timeline-item-header">
                  <span class="timeline-item-time">{{ createdAt }}</span>
                  <span class="timeline-item-title">{{ title }}</span>
                </div>
                <div class="timeline-item-content">
                  <div v-if="type === 'tool_call'" class="tool-section">
                    <div class="tool-details">
                      <div class="tool-arg-section">
                        <strong>参数:</strong>
                        <pre class="tool-args">{{ escapeHtml(args) }}</pre>
                      </div>
                    </div>
                  </div>
                  <div v-else-if="type === 'tool_result'" class="tool-section">
                    <strong>执行结果:</strong>
                    <pre class="tool-result">{{ content }}</pre>
                    <div v-if="id" class="tool-execution-id">
                      执行ID: <code>{{ escapeHtml(id) }}</code>
                    </div>
                  </div>
                  <span v-else-if="type === 'cancelled'">
                    {{ content || '任务已取消' }}
                  </span>
                  <span v-else-if="type !== 'tool_calls_detected' && type !== 'progress'">{{ content }}</span>
                </div>
              </div>
            </div>
          </template>
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
          @update:visible="(val: boolean) => rolePopoverVisible = val"
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
        <el-button v-if="loading" type="danger" @click="stopTask()">停止</el-button>
        <el-button 
          v-if="currentConversationId && !loading" 
          type="warning" 
          @click="showAttackChain = true"
        >
          攻击链
        </el-button>
      </div>
    </div>

    <!-- 攻击链抽屉 -->
    <AttackChainView
      v-if="currentConversationId"
      :conversation-id="currentConversationId"
      :visible="showAttackChain"
      @close="showAttackChain = false"
    />
    <!-- 工具调用详情 -->
    <McpCallDialog v-model:dialogVisible="mcpCallDialogVisible" :detail="mcpCallDetail" />
  </div>
</template>

<style lang="scss" scoped>
.chat-container {
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 94px);
  max-width: 1200px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.active-tasks {
  position: absolute;
  width: 100%;
  padding: 8px;
  z-index: 100;

  .task-container {
    display: flex;
  }

  .active-task-item {
    display: inline-flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    background: var(--bg-primary);
    border: 1px solid rgba(0, 102, 255, 0.2);
    border-radius: 8px;
    padding: 8px 12px;
    flex-shrink: 0;
    min-width: 280px;
    box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.03);
    margin-right: 12px;
    margin-bottom: 12px;
  }

  .active-task-info {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .active-task-status {
    background: rgba(0, 102, 255, 0.12);
    color: var(--accent-color);
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 0.75rem;
    font-weight: 600;
    flex-shrink: 0;
  }

  .active-task-message {
    font-size: 0.85rem;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 320px;
  }

  .active-task-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .active-task-time {
    font-size: 0.75rem;
    color: var(--text-muted);
  }
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  scroll-behavior: smooth;
  background-color: #f5f7fa;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--el-text-color-secondary);
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 8px;
  word-wrap: break-word;
  word-break: break-word;
  line-height: 1.6;
  box-shadow: var(--shadow-sm);
  overflow-x: auto;
  overflow-y: visible;
  max-width: 100%;
  -webkit-overflow-scrolling: touch;
  position: relative;
}

.message-row {
  display: flex;
  justify-content: flex-start;

  >.message-container {
    margin-bottom: 15px;
    max-width: 100%;
  }

  &.user {
    justify-content: flex-end;

    .message-bubble {
      background-color: var(--el-color-primary);
      color: white;
    }
  }

  &.assistant {
    .message-bubble {
      min-width: 500px;
      background: var(--bg-primary);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-bottom-left-radius: 8px;
      border-top-left-radius: 2px;
    }
  }

  &.tool {
    .message-bubble  {
      background-color: #1e1e1e;
      color: #d4d4d4;
      font-family: monospace;
      width: 95%;
    }
  }
}

.message-header {
  font-size: 0.75rem;
  margin-bottom: 5px;
  opacity: 0.7;
  display: flex;
  justify-content: space-between;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);

  .progress-title {
    font-weight: 600;
    color: var(--text-primary);
    font-size: 0.9375rem;
  }

  .progress-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .progress-stop {
    padding: 4px 12px;
    background: rgba(220, 53, 69, 0.1);
    border: 1px solid rgba(220, 53, 69, 0.4);
    border-radius: 4px;
    font-size: 0.8125rem;
    color: var(--error-color);
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: rgba(220, 53, 69, 0.15);
      border-color: var(--error-color);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}

.mcp-call-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
  width: 100%;

  .mcp-call-label {
    font-size: 0.75rem;
    color: var(--text-secondary);
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 500;

    &::before {
      content: '';
      width: 4px;
      height: 4px;
      background: var(--accent-color);
      border-radius: 50%;
      display: inline-block;
      flex-shrink: 0;
    }
  }

  .mcp-call-buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .el-button {
      +.el-button {
        margin-left: 0;
      }
    }
  }
}

.progress-timeline {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  opacity: 0;

  &.expanded {
    max-height: 2000px;
    overflow-y: auto;
    opacity: 1;
    margin-top: 12px;
  }

  .timeline-item-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }

  .timeline-item-time {
    font-size: 0.75rem;
    color: var(--text-muted);
    font-family: monospace;
    min-width: 70px;
  }

  .timeline-item-title {
    font-weight: 500;
    color: var(--text-primary);
    font-size: 0.875rem;
    flex: 1;
  }

  .timeline-item-content {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--border-color);
    font-size: 0.875rem;
    color: var(--text-secondary);
    line-height: 1.6;
  }

  .timeline-item {
    padding: 12px;
    margin-bottom: 8px;
    border-left: 3px solid var(--border-color);
    padding-left: 16px;
    background: var(--bg-secondary);
    border-radius: 4px;
    transition: all 0.2s;
    &:hover {
      background: var(--bg-tertiary);
    }
  }

  .timeline-item-iteration {
    border-left-color: var(--accent-color);
    background: rgba(0, 102, 255, 0.05);
  }

  .timeline-item-thinking {
    border-left-color: #9c27b0;
    background: rgba(156, 39, 176, 0.05);
  }

  .timeline-item-tool_call {
    border-left-color: #ff9800;
    background: rgba(255, 152, 0, 0.05);
  }

  .timeline-item-tool_result {
    border-left-color: var(--success-color);
    background: rgba(40, 167, 69, 0.05);
    &.error {
      border-left-color: var(--error-color);
      background: rgba(220, 53, 69, 0.05);
    }
  }

  .timeline-item-error {
    border-left-color: var(--error-color);
    background: rgba(220, 53, 69, 0.1);
  }

  .timeline-item-cancelled {
    border-left-color: #ff7043;
    background: rgba(255, 112, 67, 0.12);
  }

  .tool-details {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .tool-section {
    display: flex;
    flex-direction: column;
    gap: 8px;

    strong {
      color: var(--text-primary);
      font-size: 0.8125rem;
    }

    &.error {
      .tool-result {
        background: rgba(220, 53, 69, 0.1);
        border-color: var(--error-color);
        color: var(--error-color);
      }
    }

    &.success {
      .tool-result {
        background: rgba(40, 167, 69, 0.1);
        border-color: var(--success-color);
      }
    }
  }

  .tool-args {
    background: var(--bg-tertiary);
    border: 1px solid var(--border-color);
    border-radius: 4px;
    padding: 12px;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 0.8125rem;
    line-height: 1.5;
    overflow-x: auto;
    margin: 0;
    color: var(--text-primary);
  }

  .tool-result {
    background: var(--bg-tertiary);
    border: 1px solid var(--border-color);
    border-radius: 4px;
    padding: 12px;
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    font-size: 0.8125rem;
    line-height: 1.5;
    overflow-x: auto;
    max-height: 400px;
    overflow-y: auto;
    margin: 0;
    white-space: pre-wrap;
    word-wrap: break-word;
    color: var(--text-primary);
  }

  .tool-execution-id {
    margin-top: 8px;
    font-size: 0.75rem;
    color: var(--text-muted);
  }
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
