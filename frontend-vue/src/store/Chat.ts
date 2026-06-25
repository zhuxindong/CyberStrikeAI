import { defineStore } from "pinia";

export interface HitlSetting {
  expanded: boolean;
  mode: string;
  sensitiveTools: string;
}

export default defineStore('Chat', {
  state() {
    let hitlSettings: HitlSetting = { expanded: false, mode: '', sensitiveTools: '' };
    const stored = localStorage.getItem('hitlSetting');
    if (stored) {
      try {
        hitlSettings = JSON.parse(stored);
      }
      catch (e) {
        console.log(e);
      }
    }
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
      },
      reasoningSettings: {
        expanded: false,
        mode: 'default',
        effort: '-'
      },
      hitlSetting: hitlSettings
    };
  },
  actions: {
    saveHitlSetting() {
      localStorage.setItem('hitlSetting', JSON.stringify(this.hitlSetting));
    }
  }
});