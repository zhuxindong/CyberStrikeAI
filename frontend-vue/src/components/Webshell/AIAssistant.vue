<template>
  <div class="webshell-ai-sidebar">
    <el-button type="primary">新对话</el-button>
    <div class="webshell-ai-conv-list">
      <div v-for="{ title, updatedAt } in conversations">
        <span class="webshell-ai-conv-item-title">{{ title }}</span>
        <span class="webshell-ai-conv-item-date">{{ updatedAt }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { Conversation } from '../Sidebar.vue';

const conversations = ref<Conversation[]>([]);
</script>

<style lang="scss" scoped>
.webshell-ai-sidebar {
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
}
</style>