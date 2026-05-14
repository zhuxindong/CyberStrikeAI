import { Attachment } from "@/components/ChatWindow.vue";
import { escapeHtml } from "./escape";
import UserStore from "@/store/User";

const userStore = UserStore();
export interface StreamCallbacks {
  onMessage: (id: string, content: string, type: string, extraData?: any) => void;
  onCancel: () => void;
  onError: () => void;
  onDone: () => void;
}

interface StreamParams {
  message: string;
  conversationId?: string;
  role?: string;
  webshellConnectionId?: string;
  attachments?: Attachment[];
}

// 滚动到底部
export const scrollToBottom = (container: HTMLElement | null, target?: HTMLElement) => {
  if (!container) {
    return;
  }
  setTimeout(() => {
    if (target) {
      target.scrollIntoView({
        behavior: 'smooth',  // 平滑滚动
        block: 'end',        // 垂直对齐到底部
        inline: 'nearest'    // 水平对齐方式
      });
    } else {
      container.scrollTo({
        top: container.scrollHeight,
        behavior: 'smooth'
      });
    }
  }, 300);
};

// 根据消息类型获取标题
export const getTitleByType = (type: string, params: any, content?: string) => {
  let title = '';
  if (type === 'tool_calls_detected' || type === 'progress') {
    title = content || params.content || '';
  } else if (type === 'tool_call') {
    const toolName = params.mcpExecutionIds || params.toolName || '未知工具';
    title = `🔧 调用工具: ${escapeHtml(toolName)}`
  } else if (type === 'tool_result') {
    const resultToolName = params.mcpExecutionIds || params.toolName || '未知工具';
    const success = params.resultStatus === 'success';
    const statusIcon = success ? '✅' : '❌';
    title = `${statusIcon} 工具 ${escapeHtml(resultToolName)} 执行${success ? '完成' : '失败'}`
  } else if (type === 'iteration') {
    title = `正在进行第${params.iteration}轮迭代`;
  } else if (type === 'cancelled') {
    title = '⛔ 任务已取消';
  } else if (type === 'thinking') {
    title = '🤔 AI思考';
  } else if (type === 'error') {
    title = '❌ 错误';
  }
  return title;
};

// 流式输出
export async function streamChat(
  params: StreamParams,
  callbacks: StreamCallbacks,
) {
  const { message, conversationId, role, webshellConnectionId, attachments } = params;
  const response = await fetch("/api/agent-loop/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${userStore.token}`,
    },
    body: JSON.stringify({
      message,
      conversationId,
      role,
      webshellConnectionId,
      attachments
    }),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  if (!response.body) {
    throw new Error("Response body is null");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n\n");
    // Keep the last part if it's incomplete
    buffer = lines.pop() || "";

    for (const line of lines) {
      if (line.startsWith("data:")) {
        let jsonStr = line.substring(5).trim();
        if (jsonStr) {
          try {
            const event = JSON.parse(jsonStr);
            // Backend format: { type, message, data }
            // data is a JSON object itself or string
            callbacks.onMessage(event.id, event.message, event.type, event.data);
            if (event.type === "done") {
              callbacks.onDone();
            } else if (event.type === 'cancelled') {
              callbacks.onCancel();
            } else if (event.type === 'error') {
              callbacks.onError();
            }
          } catch (e) {
            console.error("Error parsing SSE JSON", e, jsonStr);
          }
        }
      }
    }
  }
}
