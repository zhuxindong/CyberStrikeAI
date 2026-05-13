<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh, Close, FullScreen, Warning, CircleCheck, Aim } from '@element-plus/icons-vue';
import request from '@/utils/request';

interface AttackChainNode {
  id: string;
  type: 'target' | 'action' | 'vulnerability';
  label: string;
  riskScore: number;
  metadata: Record<string, any>;
}

interface AttackChainEdge {
  id: string;
  source: string;
  target: string;
  type: string;
  weight: number;
}

const props = defineProps<{
  conversationId: string;
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const nodes = ref<AttackChainNode[]>([]);
const edges = ref<AttackChainEdge[]>([]);
const loading = ref(false);
const generating = ref(false);
const error = ref('');
const selectedNode = ref<AttackChainNode | null>(null);

// 节点位置（用于简单布局）
const nodePositions = computed(() => {
  const positions: Record<string, { x: number; y: number }> = {};
  const levels: Record<string, number> = {};
  
  // 使用拓扑排序确定层级
  const inDegree: Record<string, number> = {};
  const children: Record<string, string[]> = {};
  
  nodes.value.forEach(n => {
    inDegree[n.id] = 0;
    children[n.id] = [];
  });
  
  edges.value.forEach(e => {
    if (inDegree[e.target] !== undefined) {
      inDegree[e.target]++;
    }
    if (children[e.source]) {
      children[e.source].push(e.target);
    }
  });

  // BFS 确定层级
  const queue: string[] = [];
  nodes.value.forEach(n => {
    if (inDegree[n.id] === 0) {
      levels[n.id] = 0;
      queue.push(n.id);
    }
  });

  while (queue.length > 0) {
    const current = queue.shift()!;
    const level = levels[current];
    children[current].forEach(child => {
      if (levels[child] === undefined) {
        levels[child] = level + 1;
      }
      inDegree[child]--;
      if (inDegree[child] === 0) {
        queue.push(child);
      }
    });
  }

  // 按层级分组并计算位置
  const levelNodes: Record<number, string[]> = {};
  nodes.value.forEach(n => {
    const level = levels[n.id] ?? 0;
    if (!levelNodes[level]) levelNodes[level] = [];
    levelNodes[level].push(n.id);
  });

  const nodeWidth = 200;
  const nodeHeight = 60;
  const levelGap = 120;
  const nodeGap = 40;

  Object.entries(levelNodes).forEach(([levelStr, nodeIds]) => {
    const level = parseInt(levelStr);
    const totalWidth = nodeIds.length * nodeWidth + (nodeIds.length - 1) * nodeGap;
    const startX = (800 - totalWidth) / 2;
    
    nodeIds.forEach((nodeId, index) => {
      positions[nodeId] = {
        x: startX + index * (nodeWidth + nodeGap),
        y: 40 + level * (nodeHeight + levelGap)
      };
    });
  });

  return positions;
});

// 加载攻击链
const loadAttackChain = async () => {
  if (!props.conversationId) return;
  
  loading.value = true;
  error.value = '';
  
  try {
    const response = await request(`/api/attack-chain/${props.conversationId}`);
    if (response.status === 200) {
      const data = response.data;
      nodes.value = data.nodes || [];
      edges.value = data.edges || [];
      
      if (nodes.value.length === 0) {
        error.value = '暂无攻击链数据，点击"重新生成"按钮生成攻击链';
      }
    } else if (response.status === 409) {
      error.value = '攻击链正在生成中，请稍后再试';
    } else {
      error.value = '加载攻击链失败';
    }
  } catch (e) {
    error.value = '加载攻击链失败';
    console.error(e);
  } finally {
    loading.value = false;
  }
};

// 重新生成攻击链
const regenerateChain = async () => {
  if (!props.conversationId) return;
  
  generating.value = true;
  error.value = '';
  
  try {
    const response = await request(`/api/attack-chain/${props.conversationId}/regenerate`, {
      method: 'POST'
    });
    
    const data = response.data;
    if (response.status === 200) {
      nodes.value = data.nodes || [];
      edges.value = data.edges || [];
      ElMessage.success('攻击链生成完成');
      
      if (nodes.value.length === 0) {
        error.value = '对话中没有工具执行记录，无法生成攻击链';
      }
    } else {
      error.value = data.error || '生成失败';
      ElMessage.error(error.value);
    }
  } catch (e) {
    error.value = '生成攻击链失败';
    ElMessage.error('生成攻击链失败');
    console.error(e);
  } finally {
    generating.value = false;
  }
};

// 获取节点样式
const getNodeStyle = (node: AttackChainNode) => {
  const pos = nodePositions.value[node.id] || { x: 0, y: 0 };
  const colors = {
    target: { bg: '#e6f7ff', border: '#1890ff', color: '#1890ff' },
    action: { bg: '#f6ffed', border: '#52c41a', color: '#52c41a' },
    vulnerability: { bg: '#fff2f0', border: '#ff4d4f', color: '#ff4d4f' }
  };
  const c = colors[node.type] || colors.action;
  
  return {
    left: `${pos.x}px`,
    top: `${pos.y}px`,
    background: c.bg,
    borderColor: c.border,
    color: c.color
  };
};

// 获取边的 SVG 路径
const getEdgePath = (edge: AttackChainEdge) => {
  const source = nodePositions.value[edge.source];
  const target = nodePositions.value[edge.target];
  if (!source || !target) return '';
  
  const startX = source.x + 100;
  const startY = source.y + 60;
  const endX = target.x + 100;
  const endY = target.y;
  
  const midY = (startY + endY) / 2;
  
  return `M ${startX} ${startY} C ${startX} ${midY}, ${endX} ${midY}, ${endX} ${endY}`;
};

// 获取节点图标
const getNodeIcon = (type: string) => {
  switch (type) {
    case 'target': return Aim;
    case 'vulnerability': return Warning;
    default: return CircleCheck;
  }
};

// 选择节点
const selectNode = (node: AttackChainNode) => {
  selectedNode.value = node;
};

// 关闭详情
const closeDetails = () => {
  selectedNode.value = null;
};

watch(() => props.visible, (visible) => {
  if (visible && props.conversationId) {
    loadAttackChain();
  }
});

onMounted(() => {
  if (props.visible && props.conversationId) {
    loadAttackChain();
  }
});
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="攻击链可视化"
    size="80%"
    direction="rtl"
    @close="emit('close')"
  >
    <template #header>
      <div class="drawer-header">
        <span>攻击链可视化</span>
        <div class="header-actions">
          <el-button 
            :icon="Refresh" 
            :loading="generating"
            @click="regenerateChain"
          >
            {{ generating ? '生成中...' : '重新生成' }}
          </el-button>
        </div>
      </div>
    </template>

    <div class="attack-chain-container" v-loading="loading">
      <!-- 错误/空状态 -->
      <div v-if="error && nodes.length === 0" class="empty-state">
        <el-icon :size="48"><Warning /></el-icon>
        <p>{{ error }}</p>
        <el-button type="primary" @click="regenerateChain" :loading="generating">
          生成攻击链
        </el-button>
      </div>

      <!-- 攻击链图 -->
      <div v-else class="chain-graph">
        <!-- SVG 连线层 -->
        <svg class="edges-layer" width="800" :height="Object.keys(nodePositions).length * 180 + 100">
          <defs>
            <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
              <polygon points="0 0, 10 3.5, 0 7" fill="#999" />
            </marker>
          </defs>
          <path
            v-for="edge in edges"
            :key="edge.id"
            :d="getEdgePath(edge)"
            class="edge-path"
            :stroke-width="edge.weight * 0.5 + 1"
            marker-end="url(#arrowhead)"
          />
        </svg>

        <!-- 节点层 -->
        <div class="nodes-layer">
          <div
            v-for="node in nodes"
            :key="node.id"
            class="chain-node"
            :class="{ selected: selectedNode?.id === node.id }"
            :style="getNodeStyle(node)"
            @click="selectNode(node)"
          >
            <div class="node-icon">
              <el-icon><component :is="getNodeIcon(node.type)" /></el-icon>
            </div>
            <div class="node-content">
              <div class="node-type">{{ node.type === 'target' ? '目标' : node.type === 'vulnerability' ? '漏洞' : '行动' }}</div>
              <div class="node-label">{{ node.label }}</div>
            </div>
            <div v-if="node.riskScore > 0" class="node-score" :class="getRiskClass(node.riskScore)">
              {{ node.riskScore }}
            </div>
          </div>
        </div>
      </div>

      <!-- 节点详情面板 -->
      <transition name="slide">
        <div v-if="selectedNode" class="node-details-panel">
          <div class="details-header">
            <span>节点详情</span>
            <el-button :icon="Close" circle size="small" @click="closeDetails" />
          </div>
          <div class="details-content">
            <div class="detail-item">
              <label>类型</label>
              <span>{{ selectedNode.type }}</span>
            </div>
            <div class="detail-item">
              <label>标签</label>
              <span>{{ selectedNode.label }}</span>
            </div>
            <div v-if="selectedNode.riskScore > 0" class="detail-item">
              <label>风险评分</label>
              <span :class="getRiskClass(selectedNode.riskScore)">{{ selectedNode.riskScore }}</span>
            </div>
            <div v-if="selectedNode.metadata" class="detail-item metadata">
              <label>元数据</label>
              <pre>{{ JSON.stringify(selectedNode.metadata, null, 2) }}</pre>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </el-drawer>
</template>

<script lang="ts">
function getRiskClass(score: number): string {
  if (score >= 90) return 'critical';
  if (score >= 80) return 'high';
  if (score >= 60) return 'medium';
  return 'low';
}
</script>

<style scoped>
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.attack-chain-container {
  height: 100%;
  position: relative;
  overflow: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: var(--el-text-color-secondary);
}

.empty-state p {
  margin: 16px 0;
}

.chain-graph {
  position: relative;
  min-height: 500px;
  padding: 20px;
}

.edges-layer {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.edge-path {
  fill: none;
  stroke: #999;
  stroke-opacity: 0.6;
}

.nodes-layer {
  position: relative;
}

.chain-node {
  position: absolute;
  width: 200px;
  min-height: 60px;
  padding: 12px;
  border: 2px solid;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.chain-node:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.chain-node.selected {
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.3);
}

.node-icon {
  font-size: 24px;
}

.node-content {
  flex: 1;
  overflow: hidden;
}

.node-type {
  font-size: 10px;
  text-transform: uppercase;
  opacity: 0.7;
}

.node-label {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.node-score {
  font-size: 14px;
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(0,0,0,0.1);
}

.node-score.critical { color: #722ed1; background: #f9f0ff; }
.node-score.high { color: #cf1322; background: #fff1f0; }
.node-score.medium { color: #d46b08; background: #fff7e6; }
.node-score.low { color: #389e0d; background: #f6ffed; }

.node-details-panel {
  position: fixed;
  right: 20px;
  top: 100px;
  width: 300px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  z-index: 100;
}

.details-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color);
  font-weight: 600;
}

.details-content {
  padding: 16px;
}

.detail-item {
  margin-bottom: 12px;
}

.detail-item label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.detail-item.metadata pre {
  font-size: 11px;
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 4px;
  overflow: auto;
  max-height: 200px;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s, opacity 0.2s;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
</style>
