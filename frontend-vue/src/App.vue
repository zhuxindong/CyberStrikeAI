<script setup lang="ts">
import { ref } from 'vue';
import ChatWindow from './components/ChatWindow.vue';
import Sidebar from './components/Sidebar.vue';
import ToolsPanel from './components/ToolsPanel.vue';
import ConfigView from './components/ConfigView.vue';
import { Setting, Tools, Monitor } from '@element-plus/icons-vue';

const currentConversationId = ref<string | undefined>();
const showToolsPanel = ref(false);
const currentView = ref<'chat' | 'config' | 'monitor'>('chat');

const handleSelectConversation = (id: string) => {
  currentConversationId.value = id;
  showToolsPanel.value = false;
  currentView.value = 'chat';
};

const handleCreateConversation = () => {
  currentView.value = 'chat';
};

const toggleToolsPanel = () => {
  showToolsPanel.value = !showToolsPanel.value;
};

const showConfigView = () => {
  currentView.value = 'config';
  showToolsPanel.value = false;
};

const showChatView = () => {
  currentView.value = 'chat';
};
</script>

<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <aside class="app-sidebar">
      <Sidebar 
        :current-id="currentConversationId" 
        @select="handleSelectConversation"
        @create="handleCreateConversation"
      />
    </aside>
    
    <!-- 主内容区 -->
    <div class="app-main">
      <!-- 顶部导航 -->
      <header class="app-header">
        <div class="header-left">
          <h1 @click="showChatView" style="cursor: pointer;">🛡️ CyberStrikeAI</h1>
        </div>
        <div class="header-right">
          <el-button :icon="Tools" @click="toggleToolsPanel" :type="showToolsPanel ? 'primary' : 'default'">
            工具列表
          </el-button>
          <el-button :icon="Monitor">监控</el-button>
          <el-button :icon="Setting" @click="showConfigView" :type="currentView === 'config' ? 'primary' : 'default'">
            设置
          </el-button>
        </div>
      </header>
      
      <!-- 内容区 -->
      <main class="app-content">
        <div class="content-wrapper">
          <!-- 配置页面 -->
          <template v-if="currentView === 'config'">
            <div class="full-area">
              <ConfigView />
            </div>
          </template>
          
          <!-- 聊天界面 -->
          <template v-else>
            <div class="chat-area" :class="{ 'with-panel': showToolsPanel }">
              <ChatWindow :conversation-id="currentConversationId" />
            </div>
            
            <!-- 工具面板 -->
            <div v-if="showToolsPanel" class="tools-area">
              <ToolsPanel />
            </div>
          </template>
        </div>
      </main>
    </div>
  </div>
</template>


<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: #f5f7fa;
}

.app-sidebar {
  width: 280px;
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.header-left h1 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  gap: 10px;
}

.app-content {
  flex: 1;
  overflow: hidden;
  padding: 16px;
}

.content-wrapper {
  display: flex;
  height: 100%;
  gap: 16px;
}

.chat-area {
  flex: 1;
  min-width: 0;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.chat-area.with-panel {
  flex: 2;
}

.tools-area {
  width: 360px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 响应式 */
@media (max-width: 1200px) {
  .app-sidebar {
    width: 240px;
  }
  .tools-area {
    width: 300px;
  }
}

@media (max-width: 768px) {
  .app-sidebar {
    display: none;
  }
  .tools-area {
    display: none;
  }
}

.full-area {
  flex: 1;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: auto;
}
</style>

