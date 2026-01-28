export interface StreamCallbacks {
    onMessage: (content: string, type: string, extraData?: any) => void;
    onError: (error: any) => void;
    onDone: () => void;
}

export async function streamChat(
    message: string,
    callbacks: StreamCallbacks,
    conversationId?: string,
    role?: string
) {
    try {
        const response = await fetch("/api/agent-loop/stream", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                message,
                conversationId,
                role
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
                    jsonStr = jsonStr.replace(/\n/g, '\\n');
                    if (jsonStr) {
                        try {
                            const event = JSON.parse(jsonStr);
                            // Backend format: { type, message, data }
                            // data is a JSON object itself or string
                            if (event.type === "done") {
                                callbacks.onDone();
                                return;
                            }
                            callbacks.onMessage(event.message, event.type, event.data);
                        } catch (e) {
                            console.error("Error parsing SSE JSON", e, jsonStr);
                        }
                    }
                }
            }
        }
    } catch (error) {
        callbacks.onError(error);
    }
}
