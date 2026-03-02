import { defineStore } from "pinia";

export default defineStore('conversation', {
  state() {
    return {
      conversationId: ''
    };
  }
});