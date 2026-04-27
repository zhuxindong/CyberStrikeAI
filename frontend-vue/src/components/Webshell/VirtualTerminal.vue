<template>
  <div class="webshell-terminal-toolbar">
    <el-button text size="small" type="primary" round @click="termInstace?.clear()">清屏</el-button>
    <span class="webshell-quick-label">快捷命令：</span>
    <el-button v-for="shorcut in shortcuts" text size="small" type="primary" round @click="runQuickCommand(shorcut)">{{
      shorcut }}</el-button>
  </div>
  <div ref="term" class="webshell-terminal-container"></div>
</template>

<script lang="ts" setup>
import { Terminal } from '@xterm/xterm';
import { onMounted, ref, useTemplateRef, watch } from 'vue';
import { Connection } from './Index.vue';
import { FitAddon } from '@xterm/addon-fit';

const { connection } = defineProps<{
  connection: Connection
}>();

watch(() => connection, () => {
  termInstace.value?.clear();
});

const shortcuts = ref([
  'whoami',
  'id',
  'pwd',
  'ls -la',
  'uname -a',
  'ifconfig',
  'ip a',
  'env',
  'hostname',
  'ps aux',
  'netstat'
]);
const webshellHistoryByConn = ref<Record<string, string[]>>({});
const termInstace = ref<Terminal>();

const WEBSHELL_PROMPT = 'shell>';
const WEBSHELL_HISTORY_MAX = 100;
let webshellLineBuffer = '', webshellHistoryIndex = -1, webshellRunning = false;
const termRef = useTemplateRef<HTMLElement>('term');

// 获取历史项
const getWebshellHistory = (connId: string) => {
  if (!connId) return [];
  if (!webshellHistoryByConn.value[connId]) webshellHistoryByConn.value[connId] = [];
  return webshellHistoryByConn.value[connId];
}

// 添加历史项
const pushWebshellHistory = (connId: string, cmd: string) => {
  if (!connId || !cmd) return;
  if (!webshellHistoryByConn.value[connId]) webshellHistoryByConn.value[connId] = [];
  const h = webshellHistoryByConn.value[connId];
  if (h[h.length - 1] === cmd) return;
  h.push(cmd);
  if (h.length > WEBSHELL_HISTORY_MAX) h.shift();
}

// 输出
const writeWebshellOutput = (term: Terminal, text: string, isError: boolean) => {
  if (!term || !text) return;
  const s = String(text).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const lines = s.split('\n');
  const prefix = isError ? '\x1b[31m' : '';
  const suffix = isError ? '\x1b[0m' : '';
  term.write(prefix);
  for (let i = 0; i < lines.length; i++) {
    term.writeln(lines[i].replace(/\r/g, ''));
  }
  term.write(suffix);
}

// 执行快捷命令并将输出写入当前终端
const runQuickCommand = async (cmd: string) => {
  if (webshellRunning) return;
  const term = termInstace.value as Terminal;
  term.writeln('');
  pushWebshellHistory(connection.id, cmd);
  webshellRunning = true;
  try {
    const out = await execWebshellCommand(cmd);
    var s = String(out || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    s.split('\n').forEach(function (line) { term.writeln(line.replace(/\r/g, '')); });
    term.write(WEBSHELL_PROMPT);
  } catch (err: any) {
    term.writeln('\x1b[31m' + (err && err.message ? err.message : '执行失败' + '\x1b[0m'));
    term.write(WEBSHELL_PROMPT);
  } finally {
    webshellRunning = false;
  }
}

// 调用后端执行命令
const execWebshellCommand = async (command: string) => {
  const conn = connection;
  const res = await fetch('/api/webshell/exec', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      url: conn.url,
      password: conn.password || '',
      type: conn.type || 'php',
      method: (conn.method || 'post').toLowerCase(),
      cmd_param: conn.cmdParam || '',
      command: command
    })
  });
  if (res.ok) {
    const data = await res.json();
    if (data.ok) {
      return data.output || '';
    }
    else {
      throw new Error(data.error);
    }
  }
}

const initTerm = () => {
  if (termRef.value && !termInstace.value) {
    const term = new Terminal({
      cursorBlink: true,
      cursorStyle: 'underline',
      fontSize: 13,
      fontFamily: 'Menlo, Monaco, "Courier New", monospace',
      lineHeight: 1.2,
      scrollback: 2000,
      theme: {
        background: '#0d1117',
        foreground: '#e6edf3',
        cursor: '#58a6ff',
        cursorAccent: '#0d1117',
        selectionBackground: 'rgba(88, 166, 255, 0.3)'
      }
    });
    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);

    term.open(termRef.value);
    fitAddon.fit();

    term.write(WEBSHELL_PROMPT);
    term.onKey(async ({ key }) => {
      // Ctrl+L 清屏
      if (key === '\x0c') {
        term.clear();
        webshellLineBuffer = '';
        webshellHistoryIndex = -1;
      }
      // Ctrl+V 粘贴
      if (key === '\x16') {
        const text = await navigator.clipboard.readText();
        term.paste(text);
      }
    });
    term.onData(async (data: string) => {
      // 上/下键：命令历史
      if (data === '\x1b[A' || data === '\x1bOA') {
        const hist = getWebshellHistory(connection.id);
        if (hist.length === 0) return;
        webshellHistoryIndex = webshellHistoryIndex < 0 ? hist.length : Math.max(0, webshellHistoryIndex - 1);
        webshellLineBuffer = hist[webshellHistoryIndex] || '';
        term.write('\x1b[2K\r' + WEBSHELL_PROMPT + webshellLineBuffer);
        return;
      }
      if (data === '\x1b[B' || data === '\x1bOB') {
        const hist2 = getWebshellHistory(connection.id);
        if (hist2.length === 0) return;
        webshellHistoryIndex = webshellHistoryIndex < 0 ? -1 : Math.min(hist2.length - 1, webshellHistoryIndex + 1);
        if (webshellHistoryIndex < 0) webshellLineBuffer = '';
        else webshellLineBuffer = hist2[webshellHistoryIndex] || '';
        term.write('\x1b[2K\r' + WEBSHELL_PROMPT + webshellLineBuffer);
        return;
      }
      // 回车：发送当前行到后端执行
      if (data === '\r' || data === '\n') {
        term.writeln('');
        const cmd = webshellLineBuffer.trim();
        webshellLineBuffer = '';
        webshellHistoryIndex = -1;
        if (cmd) {
          if (webshellRunning) {
            writeWebshellOutput(term, '请等待当前命令执行完成', true);
            term.write(WEBSHELL_PROMPT);
            return;
          }
          pushWebshellHistory(connection.id, cmd);
          webshellRunning = true;
          try {
            const out = await execWebshellCommand(cmd);
            webshellRunning = false;
            if (out && out.length) writeWebshellOutput(term, out, false);
            term.write(WEBSHELL_PROMPT);
          } catch (err: any) {
            webshellRunning = false;
            writeWebshellOutput(term, err && err.message ? err.message : '执行失败', true);
            term.write(WEBSHELL_PROMPT);
          }
        } else {
          term.write(WEBSHELL_PROMPT);
        }
        return;
      }
      // 多行粘贴：按行依次执行
      if (data.indexOf('\n') !== -1 || data.indexOf('\r') !== -1) {
        const full = (webshellLineBuffer + data).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        const lines = full.split('\n');
        webshellLineBuffer = lines.pop() || '';
        if (lines.length > 0 && !webshellRunning && connection) {
          const runNext = async (i: number) => {
            if (i >= lines.length) {
              term.write(WEBSHELL_PROMPT + webshellLineBuffer);
              return;
            }
            const line = lines[i].trim();
            if (!line) { runNext(i + 1); return; }
            pushWebshellHistory(connection.id, line);
            webshellRunning = true;
            try {
              const out = await execWebshellCommand(line);
              if (out && out.length) writeWebshellOutput(term, out, false);
              webshellRunning = false;
              runNext(i + 1);
            } catch (err: any) {
              writeWebshellOutput(term, err && err.message ? err.message : '执行失败', true);
              webshellRunning = false;
              runNext(i + 1);
            }
          };
          runNext(0);
        } else {
          term.write(data);
        }
        return;
      }
      // 退格
      if (data === '\x7f' || data === '\b') {
        if (webshellLineBuffer.length > 0) {
          webshellLineBuffer = webshellLineBuffer.slice(0, -1);
          term.write('\b \b');
        }
        return;
      }
      webshellLineBuffer += data;
      term.write(data);
    });
    termInstace.value = term;
  } else {
    console.log('容器DOM不存在');
  }
};

onMounted(() => {
  initTerm();
});
</script>

<style lang="scss" scoped>
.webshell-terminal-container {
  flex: 1;
  min-height: 360px;
  padding: 12px;
  background: #0d1117;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  transform: translateZ(0);
  backface-visibility: hidden;
}
</style>