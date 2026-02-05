<script setup lang="ts">
import { computed, ref } from 'vue';
import ChatWindow from './components/ChatWindow.vue';
import Sidebar from './components/Sidebar.vue';
import ToolsPanel from './components/ToolsPanel.vue';
import ConfigView from './components/ConfigView.vue';
import RolesView from './components/RolesView.vue';
import BatchQueueView from './components/BatchQueueView.vue';
import KnowledgeView from './components/KnowledgeView.vue';
import VulnsView from './components/VulnsView.vue';
// import MonitorView from './components/MonitorView.vue';
import McpView from './components/McpView.vue';
import MCPMonitor from './components/MCPMonitor.vue';
import { 
  Setting, 
  // Monitor, 
  User, 
  VideoPlay, 
  ChatDotRound, 
  Warning,
  Collection,
  Connection,
  Platform,
  House
} from '@element-plus/icons-vue';

const currentConversationId = ref<string | undefined>();
const showToolsPanel = ref(false);
const currentView = ref<'chat' | 'config' | 'roles' | 'batch' | 'vulns' | 'knowledge' | 'mcp' | 'mcpMonitor'>('chat');

const currentViewName = computed(() => {
  return {
    chat: 'CyberStrikeAI',
    batch: '批量任务管理',
    knowledge: '知识库管理',
    config: '系统设置',
    mcpMonitor: 'MCP状态监控',
    mcp: 'MCP管理',
    roles: '角色管理',
    vulns: '漏洞管理'
  }[currentView.value];
});

const handleSelectConversation = (id: string) => {
  currentConversationId.value = id;
  showToolsPanel.value = false;
  currentView.value = 'chat';
};

const handleCreateConversation = () => {
  currentView.value = 'chat';
};

// const toggleToolsPanel = () => {
//   showToolsPanel.value = !showToolsPanel.value;
// };

const handleViewConv = (id: string) => {
  currentConversationId.value = id;
  currentView.value = 'chat';
}
</script>

<template>
  <div class="app-layout">
    <!-- Primary Navigation Rail -->
    <nav class="nav-rail">
      <div class="nav-logo">
        <!-- <img src="/logo.svg" alt="CS" v-if="false" /> -->
        <span class="logo-text">CS</span>
      </div>
      
      <el-menu mode="vertical">
        <el-menu-item index="1" @click="currentView = 'chat'">
          <el-icon><ChatDotRound /></el-icon>
          <span class="nav-label">对话</span>
        </el-menu-item>
        <el-menu-item index="2" @click="currentView = 'batch'">
          <el-icon><VideoPlay /></el-icon>
          <span class="nav-label">任务</span>
        </el-menu-item>
        <el-menu-item index="3" @click="currentView = 'knowledge'">
          <el-icon><Collection /></el-icon>
          <span class="nav-label">知识</span>
        </el-menu-item>
        <el-menu-item index="4" @click="currentView = 'roles'">
          <el-icon><User /></el-icon>
          <span class="nav-label">角色</span>
        </el-menu-item>
        <el-menu-item index="5" @click="currentView = 'vulns'">
          <el-icon><Warning /></el-icon>
          <span class="nav-label">漏洞</span>
        </el-menu-item>
        <!-- <el-menu-item index="6" @click="currentView = 'monitor'">
          <el-icon><Monitor /></el-icon>
          <span class="nav-label">监控</span>
        </el-menu-item> -->
        <el-sub-menu index="6">
          <template #title>
            <el-icon><Connection /></el-icon>
            <span class="nav-label">MCP</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="7-1" @click="currentView = 'mcpMonitor'">
              <el-icon><Platform /></el-icon>
              <span class="nav-label">MCP状态监控</span>
            </el-menu-item>
            <el-menu-item index="7-2" @click="currentView = 'mcp'">
              <el-icon><House /></el-icon>
              <span class="nav-label">MCP管理</span>
            </el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>
        <el-menu-item index="7" @click="currentView = 'config'">
          <el-icon><Setting /></el-icon>
          <span class="nav-label">设置</span>
        </el-menu-item>
      </el-menu>
    </nav>

    <!-- Secondary Sidebar (Contextual) -->
    <aside class="app-sidebar" v-if="currentView === 'chat'">
      <Sidebar 
        :current-id="currentConversationId" 
        @select="handleSelectConversation"
        @create="handleCreateConversation"
      />
    </aside>
    
    <!-- Main Content Area -->
    <div class="app-main">
      <!-- Header -->
      <header class="app-header">
        <div class="header-left">
          <h2>{{ currentViewName }}</h2>
        </div>
        <!-- <div class="header-right">
          <el-button 
            v-if="currentView === 'chat'"
            :icon="Tools" 
            @click="toggleToolsPanel" 
            :type="showToolsPanel ? 'primary' : 'default'"
          >
            工具列表
          </el-button>
        </div> -->
      </header>
      
      <!-- Content -->
      <main class="app-content">
        <div class="content-wrapper">
          <template v-if="currentView === 'config'">
            <div class="full-area">
              <ConfigView />
            </div>
          </template>

          <template v-else-if="currentView === 'roles'">
            <div class="full-area">
              <RolesView />
            </div>
          </template>

          <template v-else-if="currentView === 'batch'">
            <div class="full-area">
              <BatchQueueView @view-conv="handleViewConv" />
            </div>
          </template>

          <template v-else-if="currentView === 'knowledge'">
            <div class="full-area">
              <KnowledgeView />
            </div>
          </template>

          <template v-else-if="currentView === 'vulns'">
            <div class="full-area">
              <VulnsView />
            </div>
          </template>

          <!-- <template v-else-if="currentView === 'monitor'">
            <div class="full-area">
              <MonitorView />
            </div>
          </template> -->

          <template v-else-if="currentView === 'mcp'">
            <div class="full-area">
              <McpView />
            </div>
          </template>

          <template v-else-if="currentView === 'mcpMonitor'">
            <div class="full-area">
              <MCPMonitor />
            </div>
          </template>
          
          <template v-else>
            <div class="chat-area" :class="{ 'with-panel': showToolsPanel }">
              <ChatWindow :conversation-id="currentConversationId" />
            </div>
            
            <div v-if="showToolsPanel" class="tools-area">
              <ToolsPanel />
            </div>
          </template>
        </div>
      </main>
    </div>
  </div>
</template>


<style lang="scss" scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: #f5f7fa;
}

/* Navigation Rail */
.nav-rail {
  /* width: 64px; */
  // background: linear-gradient(180deg, #fafbfc 0%, #f5f7fa 100%);
  background-color: #fff;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  z-index: 100;
  flex-shrink: 0;

  .el-menu {
    width: 150px;
    border: none;
  }
}

.nav-logo {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  color: white;
  font-weight: bold;
  font-size: 18px;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.4);
}

.nav-items {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #606266; /* var(--el-text-color-regular) */
  cursor: pointer;
  padding: 8px 0;
  transition: all 0.2s;
  gap: 4px;
  position: relative;
}

.nav-item:hover {
  color: var(--el-color-primary);
  background-color: rgba(64, 158, 255, 0.1);
}

.nav-item.active {
  color: var(--el-color-primary);
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.12) 0%, rgba(64, 158, 255, 0.06) 100%);
  border-left: 3px solid var(--el-color-primary);
}

.nav-item .el-icon {
  font-size: 24px;
}

.nav-label {
  font-size: 12px;
  transform: scale(0.9);
  font-weight: 500;
}

.nav-spacer {
  flex: 1;
}

/* Secondary Sidebar */
.app-sidebar {
  width: 280px;
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
  background-color: #fff;
  border-right: 1px solid var(--el-border-color-light);
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f0f2f5;
}

.app-header {
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.header-left h2 {
  margin: 0;
  font-size: 1.1rem;
  color: #303133;
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
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.chat-area.with-panel {
  flex: 2;
}

.tools-area {
  width: 320px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.full-area {
  flex: 1;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.empty-placeholder {
  align-items: center;
  justify-content: center;
}

/* Responsive */
@media (max-width: 1200px) {
  .app-sidebar {
    width: 220px;
  }
  .tools-area {
    width: 280px;
  }
}

@media (max-width: 768px) {
  .app-sidebar {
    position: absolute;
    z-index: 90;
    height: 100%;
    transform: translateX(-100%);
    transition: transform 0.3s;
  }
  .app-sidebar.show {
    transform: translateX(0);
  }
  /* Need a toggle button for mobile if we support it */
}
</style>

