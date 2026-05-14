<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import RoleDialog from "./RoleDialog.vue";
import request from '@/utils/request';

export interface Role {
  id: number;
  icon: string;
  name: string;
  description: string;
  userPrompt: string;
  tools: string[];
  toolText: string;
  isDefault: boolean;
  enabled: boolean;
}

const keyword = ref('');
const roles = ref<Role[]>([]);
const filteredRoles = computed(() => {
  return roles.value.filter(role => {
    const roleName = role.name.toLocaleLowerCase();
    const keywordValue = keyword.value.toLocaleLowerCase();
    return roleName.includes(keywordValue);
  });
});
const dialogVisible = ref(false);
const roleData = ref<Role>({
  id: 0,
  icon: '',
  name: '',
  description: '',
  userPrompt: '',
  tools: [],
  toolText: '',
  isDefault: false,
  enabled: false
});

const fetchRoles = async () => {
  try {
    const res = await request('/api/roles');
    if (res.status === 200) {
      const data = res.data;
      roles.value = data.roles.map((role: Role) => {
        role.isDefault = role.id === 13;
        role.tools = role.tools || [];
        if (role.tools.length >= 5) {
          role.toolText = `${role.tools.slice(0, 5).join(',')}等工具`;
        } else {
          role.toolText = role.tools.join(',');
        }
        return role;
      });
    }
  } catch (error) {
    ElMessage.error('获取角色列表失败');
  }
};

const handleAdd = () => {
  dialogVisible.value = true;
  roleData.value = {
    id: 0,
    icon: '',
    name: '',
    description: '',
    userPrompt: '',
    tools: [],
    toolText: '',
    isDefault: false,
    enabled: false
  };
};

const handleEdit = async (role: Role) => {
  dialogVisible.value = true;
  await nextTick();
  roleData.value = { ...role };
};

const handleDelete = async (role: Role) => {
  if (role.isDefault) return;
  try {
    await ElMessageBox.confirm('确定要删除删除该角色吗?', '提示', {
      type: 'warning'
    });
    const res = await request(`/api/roles/${role.name}`, { method: 'DELETE' });
    if (res.status === 200) {
      ElMessage.success('删除成功');
      fetchRoles();
    }
  } catch (e) {
    // cancelled or error
  }
};

onMounted(fetchRoles);
</script>

<template>
  <div class="roles-view">
    <div class="page-header">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新建角色</el-button>
      <el-input v-model="keyword" placeholder="搜索角色" />
    </div>

    <div class="roles-grid">
      <div v-for="role in filteredRoles" class="role-card">
        <div class="role-card-header">
          <h3 class="role-card-title">
            <span class="role-card-icon">{{ role.icon }}</span>
            {{ role.name }}
          </h3>
          <span :class="['role-card-badge', { 'enabled': role.enabled, 'disabled': !role.enabled }]">
            {{ role.enabled ? '已启用' : '已禁用' }}
          </span>
        </div>
        <div class="role-card-description">{{ role.userPrompt }}</div>
        <div class="role-card-tools">
          <span class="role-card-tools-label">工具：</span>
          <span class="role-card-tools-value">{{ role.toolText }}</span>
        </div>
        <div class="role-card-actions">
          <el-button @click="handleEdit(role)">编辑</el-button>
          <el-button v-if="!role.isDefault" type="danger" @click="handleDelete(role)">删除</el-button>
        </div>
      </div>
    </div>

    <role-dialog v-model:visible="dialogVisible" :role-data="roleData" @fetchRoles="fetchRoles" />
  </div>
</template>

<style lang="scss" scoped>
.roles-view {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: #303133;
}

.roles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  overflow-y: auto;
  padding-bottom: 20px;

  .role-card {
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    transition: all 0.2s;
    cursor: default;

    .role-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .role-card-title {
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--text-primary);
        margin: 0;
        flex: 1;
        line-height: 1.4;
        display: flex;
        align-items: center;
        gap: 8px;

        .role-card-icon {
          font-size: 1.25rem;
          line-height: 1;
          flex-shrink: 0;
        }
      }

      .role-card-badge {
        padding: 4px 10px;
        border-radius: 12px;
        font-size: 0.75rem;
        font-weight: 600;
        white-space: nowrap;
        flex-shrink: 0;

        &.disabled {
          background: rgba(220, 53, 69, 0.12);
          color: var(--error-color);
        }

        &.enabled {
          background: rgba(40, 167, 69, 0.12);
          color: var(--success-color);
        }
      }
    }

    .role-card-description {
      font-size: 0.875rem;
      color: var(--text-secondary);
      line-height: 1.5;
      flex: 1;
      min-height: 40px;
    }

    .role-card-tools {
      display: flex;
      gap: 8px;
      font-size: 0.8125rem;
      color: var(--text-secondary);
      padding-top: 8px;
      border-top: 1px solid var(--border-color);
    }

    .role-card-tools-label {
      font-weight: 500;
      white-space: nowrap;
    }

    .role-card-tools-value {
      color: var(--text-primary);
      flex: 1;
      word-break: break-word;
    }

    .role-card-actions {
      display: flex;
      gap: 8px;
      margin-top: 4px;
    }
  }
}
</style>
