import { defineStore } from "pinia";

export default defineStore('Chat', {
  state() {
    return {
      queueStatusMap: {
        pending: {
          label: '待执行',
          elType: 'info'
        },
        running: {
          label: '执行中',
          elType: 'primary'
        },
        completed: {
          label: '已完成',
          elType: 'success'
        },
        cancelled: {
          label: '已取消',
          elType: 'info'
        },
        paused: {
          label: '已暂停',
          elType: 'warning'
        },
        error: {
          label: '失败',
          elType: 'danger'
        },
      }
    };
  }
});