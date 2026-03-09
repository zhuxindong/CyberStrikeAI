<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete } from '@element-plus/icons-vue';

interface Role {
  id: string;
  name: string;
  systemPrompt: string;
  isDefault: boolean;
}

const roles = ref<Role[]>([]);
const dialogVisible = ref(false);
const isEditing = ref(false);
const form = ref<Partial<Role>>({
  name: '',
  systemPrompt: ''
});

const fetchRoles = async () => {
  try {
    const res = await fetch('/api/roles');
    if (res.ok) {
      roles.value = await res.json();
    }
  } catch (error) {
    ElMessage.error('获取角色列表失败');
  }
};

const handleAdd = () => {
  isEditing.value = false;
  form.value = { name: '', systemPrompt: '' };
  dialogVisible.value = true;
};

const handleEdit = (role: Role) => {
  isEditing.value = true;
  form.value = { ...role };
  dialogVisible.value = true;
};

const handleDelete = async (role: Role) => {
  if (role.isDefault) return;
  try {
    await ElMessageBox.confirm('确定要删除删除该角色吗?', '提示', {
      type: 'warning'
    });
    const res = await fetch(`/api/roles/${role.id}`, { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('删除成功');
      fetchRoles();
    }
  } catch (e) {
    // cancelled or error
  }
};

const handleSubmit = async () => {
  try {
    const url = isEditing.value ? `/api/roles/${form.value.id}` : '/api/roles';
    const method = isEditing.value ? 'PUT' : 'POST';
    
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value)
    });
    
    if (res.ok) {
      ElMessage.success(isEditing.value ? '更新成功' : '创建成功');
      dialogVisible.value = false;
      fetchRoles();
    } else {
      ElMessage.error('操作失败');
    }
  } catch (error) {
    ElMessage.error('网络错误');
  }
};

onMounted(fetchRoles);
</script>

<template>
  <div class="roles-view">
    <div class="page-header">
      <el-button type="primary" :icon="Plus" @click="handleAdd">新建角色</el-button>
    </div>

    <div class="roles-grid">
      <el-card v-for="role in roles" :key="role.id" class="role-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span class="role-name">{{ role.name }}</span>
            <div class="actions">
              <el-tag v-if="role.isDefault" size="small" type="info">默认</el-tag>
              <template v-else>
                <el-button link :icon="Edit" @click="handleEdit(role)"></el-button>
                <el-button link type="danger" :icon="Delete" @click="handleDelete(role)"></el-button>
              </template>
            </div>
          </div>
        </template>
        <div class="role-prompt">
          {{ role.systemPrompt }}
        </div>
      </el-card>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑角色' : '新建角色'"
      width="500px"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="form.name" placeholder="例如: 代码审计专家" />
        </el-form-item>
        <el-form-item label="系统提示">
          <el-input 
            v-model="form.systemPrompt" 
            type="textarea" 
            :rows="6"
            placeholder="定义该角色的行为、专长和限制..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.roles-view {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  flex-direction: row-reverse;
  align-items: center;
  margin-bottom: 20px;
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
}

.role-card {
  height: 200px;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-name {
  font-weight: bold;
  font-size: 1.1rem;
}

.role-prompt {
  color: #606266;
  font-size: 0.9rem;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>
