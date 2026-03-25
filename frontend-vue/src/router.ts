import { createRouter, createWebHashHistory } from "vue-router";

const router = createRouter({
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      name: '仪表盘',
      path: '/dashboard',
      component: () => import('./components/Dashboard.vue')
    },
    {
      name: 'CyberStrikeAI',
      path: '/chat',
      component: () => import('./components/ChatWindow.vue')
    },
    {
      name: '任务管理',
      path: '/task',
      component: () => import('./components/BatchQueueView.vue')
    },
    {
      name: '检索历史',
      path: '/knowledge-retrieval-logs',
      component: () => import('./components/KnowledgeRetrieval.vue')
    },
    {
      name: '知识库管理',
      path: '/knowledge-management',
      component: () => import('./components/KnowledgeView.vue')
    },
    {
      name: 'Skills状态监控',
      path: '/skill-monitor',
      component: () => import('./components/SkillMonitor.vue')
    },
    {
      name: 'Skills管理',
      path: '/skill-management',
      component: () => import('./components/SkillManage.vue')
    },
    {
      name: '角色管理',
      path: '/role',
      component: () => import('./components/RolesView.vue')
    },
    {
      name: '漏洞管理',
      path: '/vuln',
      component: () => import('./components/VulnsView.vue')
    },
    {
      name: 'MCP监控',
      path: '/mcp-monitor',
      component: () => import('./components/MCPMonitor.vue')
    },
    {
      name: 'MCP管理',
      path: '/mcp-manage',
      component: () => import('./components/McpView.vue')
    },
    {
      name: '系统设置',
      path: '/config',
      component: () => import('./components/ConfigView.vue')
    },
  ],
  history: createWebHashHistory()
});

export default router;