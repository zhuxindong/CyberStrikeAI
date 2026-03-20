<script setup lang="ts">
import { ref, onMounted, useTemplateRef } from 'vue';
import { ElMessage, ElMessageBox, FormContext, FormRules, SelectOptionProps } from 'element-plus';
import { Search, Refresh, Plus, Delete, Edit } from '@element-plus/icons-vue';

interface KnowledgeItem {
  id: string;
  category: string;
  title: string;
  content: string;
  createdAt?: string;
  updatedAt?: string;
  embedding?: string;
}

interface KnowledgeCategory {
  title: string;
  items: KnowledgeItem[];
}

const categories = ref<KnowledgeCategory[]>([]);
const currentCategory = ref();
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

const formRef = useTemplateRef<FormContext>('form');

const loadItems = async () => {
  loading.value = true;
  try {
    const res = await fetch('/api/knowledge');
    if (res.ok) {
      categories.value = await res.json();
      // filterItems();
    }
  } catch (e) {
    ElMessage.error('Connection error');
  } finally {
    loading.value = false;
  }
};

// const filterItems = () => {
//   if (!searchQuery.value) {
//     filteredItems.value = items.value;
//   } else {
//     const q = searchQuery.value.toLowerCase();
//     filteredItems.value = items.value.filter(item => 
//       item.title.toLowerCase().includes(q) || 
//       item.content.toLowerCase().includes(q) ||
//       item.category.toLowerCase().includes(q)
//     );
//   }
// };

const handleSearch = () => {
  // filterItems();
};

const handleRebuildIndex = async () => {
  try {
    const res = await fetch('/api/knowledge/index', { method: 'POST' });
    if (res.ok) {
      ElMessage.success('Index rebuild triggered in background');
    } else {
      ElMessage.error('Failed to trigger index rebuild');
    }
  } catch (e) {
    ElMessage.error('Error triggering index rebuild');
  }
};

const handleAdd = () => {
  isEdit.value = false;
  currentItem.value = { id: '', category: 'General', title: '', content: '' };
  dialogVisible.value = true;
};

const handleEdit = (item: KnowledgeItem) => {
  isEdit.value = true;
  currentItem.value = { ...item };
  dialogVisible.value = true;
};

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm('Are you sure you want to delete this item?', 'Warning', {
      type: 'warning'
    });
    
    const res = await fetch(`/api/knowledge/${id}`, { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('Item deleted');
      loadItems();
    } else {
      ElMessage.error('Failed to delete item');
    }
  } catch (e) {
    // cancelled
  }
};

const handleSave = async () => {
  const valid = await formRef.value?.validateField();
  if (!valid) {
    return;
  }
  try {
    const url = isEdit.value ? `/api/knowledge/${currentItem.value.id}` : '/api/knowledge';
    const method = isEdit.value ? 'PUT' : 'POST';
    
    const res = await fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentItem.value)
    });
    
    if (res.ok) {
      ElMessage.success(isEdit.value ? 'Item updated' : 'Item created');
      dialogVisible.value = false;
      loadItems();
    } else {
      ElMessage.error('Failed to save item');
    }
  } catch (e) {
    ElMessage.error('Error saving item');
  }
};

// Also verify search endpoint
const testSearch = async () => {
    if (!searchQuery.value) return;
    try {
        const res = await fetch(`/api/knowledge/search?query=${encodeURIComponent(searchQuery.value)}`);
        if (res.ok) {
            const results = await res.json();
            ElMessage.success(`Found ${results.length} semantic matches`);
            // Could display these specially, but for now just console log
            console.log("Vector Search Results:", results);
        }
    } catch(e) {
        console.error(e);
    }
}

onMounted(() => {
  loadItems();
});
</script>

<template>
  <div class="knowledge-view">
    <div class="knowledge-stats-bar">
      <div>
        <div class="knowledge-stat-label">总知识项</div>
        <div class="knowledge-stat-value">-</div>
      </div>
      <div>
        <div class="knowledge-stat-label">分类数</div>
        <div class="knowledge-stat-value">-</div>
      </div>
      <div>
        <div class="knowledge-stat-label">总内容</div>
        <div class="knowledge-stat-value">-</div>
      </div>
    </div>
    <div class="toolbar">
      <el-form inline label-width="5em" label-position="top">
        <el-form-item label="分类筛选">
          <el-select v-model="currentCategory" placeholder="请选择">
            <el-option v-for="{ label, value } in categoryOptions" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="搜索">
          <el-input v-model="searchQuery" placeholder="搜索知识..." :prefix-icon="Search" clearable
            @input="handleSearch" @keyup.enter="testSearch">
            <template #append>
              <el-button icon="Search">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button @click="handleRebuildIndex" :icon="Refresh">重建索引</el-button>
        <el-button type="primary" @click="handleAdd" :icon="Plus">添加知识</el-button>
      </div>
    </div>

    <!-- <div class="content-list" v-loading="loading">
      <template v-if="items.length === 0">
        <el-empty description="暂无知识" />
      </template>
      <div v-else class="cards-grid">
        <el-card v-for="item in items" :key="item.id" class="item-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="title" :title="item.title">{{ item.title }}</span>
              <el-tag size="small">{{ item.category }}</el-tag>
            </div>
          </template>
          <div class="card-body">
            <div class="preview">{{ item.content.substring(0, 150) }}...</div>
            <div class="meta">
              <span>Updated: {{ item.updatedAt ? new Date(item.updatedAt).toLocaleString() : 'N/A' }}</span>
              <el-tag v-if="!item.embedding" type="warning" size="small">Not Indexed</el-tag>
            </div>
          </div>
          <div class="card-actions">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(item)">Edit</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(item.id)">Delete</el-button>
          </div>
        </el-card>
      </div>
    </div> -->

    <div class="content-list" v-loading="loading">
      <div v-for="{ title, items } in categories" :key="title" class="knowledge-category-section">
        <div class="knowledge-category-header">
          <h3>📁 {{ title }}</h3>
          <el-tag type="primary" size="large">{{ items.length }} 项</el-tag>
        </div>
        <div class="knowledge-items-grid">
          <div v-for="item in items">
            <div class="knowledge-item-card-header">
              <h3>{{ item.title }}</h3>
              <div class="knowledge-item-card-actions">
                <span @click="handleEdit(item)">
                  <el-icon><Edit /></el-icon>
                </span>
                <span @click="handleDelete(item.id)">
                  <el-icon><Delete /></el-icon>
                </span>
              </div>
            </div>
            <div class="knowledge-item-path">📁 {{ item.content }}</div>
            <div class="knowledge-item-card-footer">🕒 {{ item.updatedAt }}</div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识' : '添加知识'" width="600px">
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
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);

  .el-select {
    width: 160px;
  }

  .el-input {
    width: 600px;
  }

  .actions {
    min-width: 200px;
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
      align-items: baseline;

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
