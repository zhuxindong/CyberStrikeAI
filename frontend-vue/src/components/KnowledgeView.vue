<script setup lang="ts">
import { ref, onMounted, useTemplateRef } from 'vue';
import { dayjs, ElMessage, ElMessageBox, FormContext, FormRules, SelectOptionProps } from 'element-plus';
import { Refresh, Plus, Delete, Edit } from '@element-plus/icons-vue';
import { debounce } from '@/utils/debounce';

interface KnowledgeStats {
  catogoryCount: number;
  catogoryCountInCurrentPage: number;
  itemCountInCurrentPage: number;
}

interface KnowledgeItem {
  id: string;
  category: string;
  title: string;
  content: string;
  createdAt?: string;
  updatedAt?: string;
  filePath?: string;
}

interface KnowledgeCategory {
  category: string;
  items: KnowledgeItem[];
}

const knowledgeStats = ref<KnowledgeStats>({
  catogoryCount: 0,
  catogoryCountInCurrentPage: 0,
  itemCountInCurrentPage: 0
});
const categories = ref<KnowledgeCategory[]>([]);
const currentCategory = ref('');
const categoryOptions = ref<SelectOptionProps[]>([]);
const searchQuery = ref('');
const loading = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const rules = ref<FormRules>({
  category: {
    required: true,
    message: '分类不能为空'
  },
  title: {
    required: true,
    message: '标题不能为空'
  },
  content: {
    required: true,
    message: '内容不能为空'
  },
});
const currentItem = ref<KnowledgeItem>({
  id: '',
  category: '',
  title: '',
  content: ''
});

const loadItems = async () => {
  loading.value = true;
  let query = '?';
  query += `category=${currentCategory.value || ''}`;
  query += `&search=${searchQuery.value}`;
  const res = await fetch(`/api/knowledge/items${query}`);
  loading.value = false;
  if (res.ok) {
    const data = await res.json();
    if (data.categories instanceof Array) {
      categories.value = data.categories;
    } else {
      categories.value = [];
    }
    let itemCount = 0;
    categoryOptions.value = categories.value.map(cat => {
      itemCount += cat.items.length;
      cat.items.forEach(item => {
        item.updatedAt = dayjs(item.updatedAt).format('YYYY-MM-DD HH:mm:ss');
      });
      return {
        label: cat.category,
        value: cat.category
      };
    });
    knowledgeStats.value = {
      catogoryCount: data.total,
      catogoryCountInCurrentPage: categories.value.length,
      itemCountInCurrentPage: itemCount
    };
  }
};
const debouncedSearch = debounce(loadItems, 300);
const loadItemDetail = async (id: string) => {
  const res = await fetch(`/api/knowledge/items/${id}`);
  if (res.ok) {
    const data = await res.json();
    currentItem.value = data;
  }
};

const handleRebuildIndex = async () => {
  const res = await fetch('/api/knowledge/index', { method: 'POST' });
  if (res.ok) {
    const data = await res.json();
    ElMessage.success(data.message);
  } else {
    ElMessage.error('重建索引失败');
  }
};

const formRef = useTemplateRef<FormContext>('form');
const onClose = () => {
  formRef.value?.resetFields();
};
const handleAdd = () => {
  isEdit.value = false;
  dialogVisible.value = true;
};

const handleEdit = (id: string) => {
  isEdit.value = true;
  dialogVisible.value = true;
  loadItemDetail(id);
};

const handleDelete = async (id: string) => {
  await ElMessageBox.confirm('确定要删除这个知识项吗？', '删除知识项', {
    type: 'warning'
  });
  
  const res = await fetch(`/api/knowledge/items/${id}`, { method: 'DELETE' });
  if (res.ok) {
    ElMessage.success('删除成功');
    loadItems();
  } else {
    ElMessage.error('删除失败');
  }
};

const handleSave = async () => {
  const valid = await formRef.value?.validateField();
  if (!valid) {
    return;
  }
  const url = isEdit.value ? `/api/knowledge/items/${currentItem.value.id}` : '/api/knowledge/items';
  const method = isEdit.value ? 'PUT' : 'POST';
  
  const res = await fetch(url, {
    method: method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(currentItem.value)
  });
  
  if (res.ok) {
    ElMessage.success(isEdit.value ? '修改成功' : '添加成功');
    dialogVisible.value = false;
    loadItems();
  } else {
    ElMessage.error(isEdit.value ? '修改失败' : '添加失败');
  }
};

onMounted(() => {
  loadItems();
});
</script>

<template>
  <div class="knowledge-view">
    <div class="knowledge-stats-bar">
      <div>
        <div class="knowledge-stat-label">总分类数</div>
        <div class="knowledge-stat-value">{{ knowledgeStats.catogoryCount }}</div>
      </div>
      <div>
        <div class="knowledge-stat-label">当前页分类</div>
        <div class="knowledge-stat-value">{{ knowledgeStats.catogoryCountInCurrentPage }} 个</div>
      </div>
      <div>
        <div class="knowledge-stat-label">当前页知识项</div>
        <div class="knowledge-stat-value">{{ knowledgeStats.itemCountInCurrentPage }} 项</div>
      </div>
    </div>
    <el-form class="toolbar" inline label-width="5em" label-position="top">
      <el-form-item label="分类筛选">
        <el-select v-model="currentCategory" clearable placeholder="请选择" @change="loadItems">
          <el-option v-for="{ label, value } in categoryOptions" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="searchQuery" placeholder="搜索知识..." clearable @input="debouncedSearch" @keyup.enter="loadItems">
          <template #append>
            <el-button icon="Search" @click="loadItems">搜索</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleRebuildIndex" :icon="Refresh">重建索引</el-button>
        <el-button type="primary" @click="handleAdd" :icon="Plus">添加知识</el-button>
      </el-form-item>
    </el-form>

    <div v-if="categories.length" class="content-list" v-loading="loading">
      <div v-for="{ category, items } in categories" class="knowledge-category-section">
        <div class="knowledge-category-header">
          <h3>📁 {{ category }}</h3>
          <el-tag type="primary" size="large">{{ items.length }} 项</el-tag>
        </div>
        <div class="knowledge-items-grid">
          <div v-for="item in items">
            <div class="knowledge-item-card-header">
              <h3>{{ item.title }}</h3>
              <div class="knowledge-item-card-actions">
                <span @click="handleEdit(item.id)">
                  <el-icon><Edit /></el-icon>
                </span>
                <span @click="handleDelete(item.id)">
                  <el-icon><Delete /></el-icon>
                </span>
              </div>
            </div>
            <div class="knowledge-item-path">📁 {{ item.filePath }}</div>
            <div class="knowledge-item-card-footer">🕒 {{ item.updatedAt }}</div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无知识" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识' : '添加知识'" width="600px" @close="onClose">
      <el-form ref="form" :model="currentItem" :rules="rules" label-position="top">
        <el-form-item label="分类（风险类型）" prop="category">
          <el-input v-model="currentItem.category" placeholder="例如: SQL 注入" />
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="currentItem.title" placeholder="知识项标题" />
        </el-form-item>

        <el-form-item label="内容 (MarkDown 格式)" prop="content">
          <el-input v-model="currentItem.content" type="textarea" :rows="10" placeholder="输入知识内容, 支持Markdown格式" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.knowledge-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
  overflow: auto;

  .knowledge-stats-bar {
    display: flex;
    gap: 24px;
    padding: 16px 20px;
    background: linear-gradient(135deg, rgba(0, 102, 255, 0.05) 0%, rgba(0, 102, 255, 0.02) 100%);
    border: 1px solid rgba(0, 102, 255, 0.15);
    border-radius: 12px;
    box-shadow: var(--shadow-sm);

    .knowledge-stat-label {
      font-size: 0.8125rem;
      color: var(--text-secondary);
      font-weight: 500;
    }

    .knowledge-stat-value {
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--accent-color);
    }
  }
}

.toolbar {
  display: flex;
  flex-wrap: nowrap;
  justify-content: space-between;
  align-items: end;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);

  .el-form-item {
    &:nth-child(1) {
      width: 20%;
    }
    &:nth-child(2) {
      width: 60%;
    }
    &:last-child {
      width: 20%;
    }
  }
}

.content-list {
  display: flex;
  flex-direction: column;
  gap: 32px;

  .knowledge-category-section {
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    padding: 24px;
    box-shadow: var(--shadow-sm);
    transition: all 0.2s ease;

    .knowledge-category-header {
      margin-bottom: 20px;
      padding-bottom: 16px;
      border-bottom: 2px solid var(--border-color);
      display: flex;
      align-items: center;

      >h3 {
        margin-right: 16px;
        font-size: 1.25rem;
        color: var(--text-primary);
      }
    }

    .knowledge-items-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 16px;

      >div {
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);
        border-radius: 12px;
        padding: 16px;
        transition: all 0.2s ease;
        display: flex;
        flex-direction: column;
        gap: 12px;
        cursor: pointer;
        position: relative;
        overflow: hidden;

        &:hover {
          background: var(--bg-primary);
          border-color: var(--accent-color);
          box-shadow: var(--shadow-md);
          transform: translateY(-2px);

          .knowledge-item-card-actions {
            opacity: 1;
          }
        }
      }

      .knowledge-item-card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }

      .knowledge-item-card-actions {
        transition: opacity 0.2s;
        opacity: 0;

        >span {
          width: 32px;
          height: 32px;
          margin-left: 8px;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          border: 1px solid var(--border-color);
          border-radius: 6px;
          background: var(--bg-primary);
          color: var(--text-secondary);
          cursor: pointer;
          transition: all 0.2s ease;

          &:hover {
            background: var(--bg-tertiary);
            border-color: var(--accent-color);
            color: var(--accent-color);
            transform: scale(1.05);
          }
          &:last-child {
            &:hover {
              background: var(--bg-tertiary);
              border-color: var(--error-color);
              color: var(--error-color);
              transform: scale(1.05);
            }
          }
        }
      }

      .knowledge-item-path {
        font-size: 0.75rem;
        color: var(--text-muted);
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .knowledge-item-card-footer {
        padding-top: 12px;
        border-top: 1px solid var(--border-color);
        font-size: 0.75rem;
        color: var(--text-muted);
        white-space: nowrap;
      }
    }
  }
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  padding-bottom: 20px;
}

.item-card {
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.card-body {
  height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  display: -webkit-box;
  line-clamp: 4;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #EBEEF5;
  margin-top: 12px;
}
</style>
