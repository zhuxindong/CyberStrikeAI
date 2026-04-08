<template>
  <div class="webshell-ai-sidebar">
    <el-button type="primary" @click="createConversation">新对话</el-button>
    <div class="webshell-ai-conv-list">
      <div v-for="{ id, title, updatedAt } in conversations" :class="{ 'active': currentConversationId == id }"
        @click="selectConversation(id)">
        <span class="webshell-ai-conv-item-title">{{ title }}</span>
        <span class="webshell-ai-conv-item-date">{{ updatedAt }}</span>
        <el-button size="small" type="danger" text @click.stop="deleteConversation(id)">
          <el-icon>
            <Close />
          </el-icon>
        </el-button>
      </div>
    </div>
  </div>
  <div class="webshell-ai-main">
    <div ref="messageContainer" class="webshell-ai-messages">
      <template v-for="message in messages">
        <div v-if="message.content" class="webshell-ai-message-item"
          :class="{ 'user': message.role === 'user', 'assistant': message.role !== 'user' }">
          <p>{{ message.content }}</p>
        </div>
        <div v-if="message.timelineItems?.length" class="process-details-container">
          <div class="webshell-ai-process-toggle" @click="toggleTimeline(message)">
            渗透测试详情
            <span>{{ message.expanded ? '▼' : '▶' }}</span>
          </div>
          <div class="process-details-content">
            <div v-show="message.timelineItems.length"
              :class="['webshell-ai-timeline', { 'expanded': message.expanded }]">
              <div v-for="item in message.timelineItems">
                <span class="webshell-ai-timeline-title">{{ item.title }}</span>
                <span class="webshell-ai-timeline-msg">{{ item.content }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
    <div class="webshell-ai-input-row">
      <el-input v-model.trim="prompt" :autosize="{ minRows: 2, maxRows: 6 }" :disabled="loading"
        placeholder="例如：列出当前目录下的文件" @keydown.enter.exact.prevent="sendMessage" />
      <el-button :disabled="loading" type="primary" @click="sendMessage">发送</el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref, useTemplateRef, watch } from 'vue';
import { Conversation } from '../Sidebar.vue';
import { TimelineItem, Message } from '../ChatWindow.vue';
import { dayjs, ElMessage, ElMessageBox } from 'element-plus';
import { scrollToBottom, getTitleByType, streamChat } from '@/utils/chatService';
import { Connection } from './Index.vue';

const { connection } = defineProps<{
  connection: Connection
}>();

watch(() => connection, () => {
  messages.splice(0);
});

const conversations = ref<Conversation[]>([]);
const messages = reactive<Message[]>([]);
const prompt = ref('');
// const currentTaskId = ref('');
const currentConversationId = ref('');
const loading = ref(false);

onMounted(async () => {
  await getConversations();
  const lastest = conversations.value[0];
  if (lastest) {
    selectConversation(lastest.id);
  }
});


const getConversations = async () => {
  const res = await fetch(`/api/webshell/connections/${connection.id}/conversations`);
  if (res.ok) {
    const data = await res.json();
    conversations.value = data.map((item: Conversation) => {
      item.updatedAt = dayjs(item.updatedAt).format('HH:mm');
      return item;
    });
  }
};

const selectConversation = async (id: string) => {
  currentConversationId.value = id;
  const res = await fetch(`/api/conversations/${id}`);
  if (res.ok) {
    const data = await res.json();
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
          item.expanded = false;
        }
        messages.push(item);
      });
      scroll();
    }
  }
}

const createConversation = async () => {
  messages.splice(0);
  messages.push({
    role: 'system',
    content: '系统已就绪。请输入您的测试需求，系统将自动执行相应的安全测试。',
    timestamp: Date.now()
  });
  currentConversationId.value = '';
};

const deleteConversation = async (id: string) => {
  const data = await ElMessageBox.confirm('确定删除此对话？', '删除对话', {
    type: 'warning',
  });
  if (data === 'confirm') {
    const response = await fetch(`/api/conversations/${id}`, { method: 'DELETE' });
    if (response.ok) {
      if (currentConversationId.value === id) {
        messages.splice(0);
        currentConversationId.value = '';
      }
      getConversations();
      ElMessage.success('删除成功');
    } else {
      ElMessage.error('删除失败');
    }
  }
};

const messageContainer = useTemplateRef('messageContainer');

const scroll = () => {
  scrollToBottom(messageContainer.value);
};

const toggleTimeline = (message: Message) => {
  message.expanded = !message.expanded;
  scroll();
};

// 发送Prompt
const sendMessage = async () => {
  if (loading.value) return;

  const userMsg = prompt.value;
  prompt.value = '';
  loading.value = true;

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

  streamChat(userMsg, {
    onMessage: (id, content, type, data) => {
      // 最近的一条消息
      const lastMessage: Message = messages[messages.length - 1];
      lastMessage.expanded = true;
      const timelineItems = lastMessage.timelineItems || [];
      const title = getTitleByType(type, data, content);
      const createdAt = dayjs(new Date()).format('YYYY-MM-DD HH:mm:ss');
      // 保存任务ID
      if (type === 'conversation') {
        if (data && data.conversationId) {
          // currentTaskId.value = data.taskId;
          currentConversationId.value = data.conversationId;
          getConversations();
        }
      } else if (['iteration', 'thinking', 'tool_calls_detected'].includes(type)) {
        timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
      } else if (type === 'cancelled') {
        timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
      } else if (type === 'progress') {
        // progressTitle.value = content;
      } else if (type === 'tool_call') {
        timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content,
          functionName: data.toolName,
          args: JSON.stringify(data.arguments, null, 2)
        });
      } else if (type === 'tool_result') {
        timelineItems.push({
          id: data.executionId,
          type,
          createdAt,
          title,
          content,
          resultStatus: data.resultStatus
        });
      } else if (type === 'error') {
        timelineItems.push({
          id,
          type,
          createdAt,
          title,
          content
        });
      }
      lastMessage.timelineItems = timelineItems;
      scroll();
    },
    onCancel: () => {
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1].content;
      toggleTimeline(lastMessage);

      loading.value = false;
      // currentTaskId.value = undefined;
      // progressTitle.value = '⛔ 任务已取消';
      scroll();
    },
    onError: () => {
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1]?.content;
      loading.value = false;
      // progressTitle.value = '❌ 执行失败';
      scroll();
    },
    onDone: () => {
      const lastMessage: Message = messages[messages.length - 1];
      const timelineItems: TimelineItem[] = lastMessage.timelineItems || [];
      lastMessage.content = timelineItems[timelineItems.length - 1].content;
      toggleTimeline(lastMessage);

      loading.value = false;
      // currentTaskId.value = undefined;
      // progressTitle.value = '✅ 渗透测试完成';
      scroll();
    }
  }, currentConversationId.value, '', connection.id);
};
</script>

<style lang="scss" scoped>
.webshell-ai-sidebar {
  padding: 12px;
  flex-shrink: 0;
  width: 280px;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  background: var(--bg-secondary);

  .webshell-ai-conv-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 4px 0;

    >div {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 12px;
      cursor: pointer;
      font-size: 0.9rem;
      border-left: 3px solid transparent;

      &:hover {
        background: var(--border-color);
      }

      &.active {
        background: var(--accent-light, rgba(59, 130, 246, 0.1));
        border-left-color: var(--accent-color);
      }
    }

    .webshell-ai-conv-item-title {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .webshell-ai-conv-item-date {
      flex-shrink: 0;
      font-size: 0.75rem;
      color: var(--text-secondary);
    }
  }
}

.webshell-ai-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;

  .webshell-ai-messages {
    height: calc(100% - 70px);
    overflow-y: auto;
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 12px;

    >.webshell-ai-message-item {
      padding: 10px 14px;
      border-radius: 10px;
      max-width: 90%;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 14px;

      &.user {
        align-self: flex-end;
        background: var(--accent-color);
        color: #fff;
      }

      &.assistant {
        align-self: flex-start;
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);
        white-space: normal;
      }
    }
  }

  .process-details-container {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border-color);
    max-width: 90%;

    .webshell-ai-process-toggle {
      display: block;
      width: 100%;
      padding: 8px 12px;
      text-align: left;
      font-size: 0.9rem;
      color: var(--text-secondary);
      background: var(--bg-secondary);
      border: 1px solid var(--border-color);
      border-radius: 6px;
      cursor: pointer;

      &:hover {
        color: var(--text-primary);
        background: var(--bg-tertiary);
      }
    }

    .process-details-content {
      width: 100%;
    }

    .webshell-ai-timeline {
      width: 100%;
      margin-bottom: 8px;
      padding: 8px 12px;
      border-radius: 8px;
      background: var(--bg-secondary);
      border: 1px solid var(--border-color);
      max-height: 0;
      overflow: hidden;
      transition: max-height 0.3s ease;
      opacity: 0;

      &.expanded {
        height: auto;
        margin-top: 12px;
        max-height: 2000px;
        overflow-y: auto;
        opacity: 1;
      }

      >div {
        font-size: 0.85rem;
        color: var(--text-primary);
        padding: 4px 0;
        border-bottom: 1px solid var(--border-color);
      }

      .webshell-ai-timeline-title {
        display: block;
        font-weight: 500;
        color: var(--text-secondary);
      }

      .webshell-ai-timeline-msg {
        margin-top: 4px;
        padding-left: 0;
        font-size: 0.8rem;
        color: var(--text-secondary);
        white-space: pre-wrap;
        word-break: break-word;
        max-height: 120px;
        overflow-y: auto;
      }
    }
  }

  .webshell-ai-input-row {
    flex-shrink: 0;
    display: flex;
    gap: 10px;
    padding: 8px 14px;
    border-top: 1px solid var(--border-color);
    align-items: center;
  }
}
</style>