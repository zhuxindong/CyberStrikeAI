-- Database Initialization Script for CyberStrikeAI (MySQL)
-- Database: cbai

CREATE DATABASE IF NOT EXISTS cbai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cbai;

-- 1. Conversations Table
CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    last_react_input TEXT,
    last_react_output TEXT,
    pinned TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_conversations_updated_at ON conversations(updated_at);
CREATE INDEX idx_conversations_pinned ON conversations(pinned);

-- 2. Messages Table
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content LONGTEXT NOT NULL,
    mcp_execution_ids TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);

-- 3. Process Details Table (Thinking process/events)
CREATE TABLE IF NOT EXISTS process_details (
    id VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message TEXT,
    data LONGTEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_process_details_message_id ON process_details(message_id);
CREATE INDEX idx_process_details_conversation_id ON process_details(conversation_id);

-- 4. Tool Executions Table
CREATE TABLE IF NOT EXISTS tool_executions (
    id VARCHAR(36) PRIMARY KEY,
    tool_name VARCHAR(100) NOT NULL,
    arguments LONGTEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    result LONGTEXT,
    error TEXT,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    duration_ms BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tool_executions_tool_name ON tool_executions(tool_name);
CREATE INDEX idx_tool_executions_start_time ON tool_executions(start_time);
CREATE INDEX idx_tool_executions_status ON tool_executions(status);

-- 5. Tool Stats Table
CREATE TABLE IF NOT EXISTS tool_stats (
    tool_name VARCHAR(100) PRIMARY KEY,
    total_calls INT NOT NULL DEFAULT 0,
    success_calls INT NOT NULL DEFAULT 0,
    failed_calls INT NOT NULL DEFAULT 0,
    last_call_time DATETIME,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Attack Chain Nodes Table
CREATE TABLE IF NOT EXISTS attack_chain_nodes (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    tool_execution_id VARCHAR(36),
    metadata LONGTEXT,
    risk_score INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (tool_execution_id) REFERENCES tool_executions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chain_nodes_conversation ON attack_chain_nodes(conversation_id);

-- 7. Attack Chain Edges Table
CREATE TABLE IF NOT EXISTS attack_chain_edges (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    source_node_id VARCHAR(36) NOT NULL,
    target_node_id VARCHAR(36) NOT NULL,
    edge_type VARCHAR(50) NOT NULL,
    weight INT DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (source_node_id) REFERENCES attack_chain_nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES attack_chain_nodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chain_edges_conversation ON attack_chain_edges(conversation_id);
CREATE INDEX idx_chain_edges_source ON attack_chain_edges(source_node_id);
CREATE INDEX idx_chain_edges_target ON attack_chain_edges(target_node_id);

-- 8. Knowledge Retrieval Logs
CREATE TABLE IF NOT EXISTS knowledge_retrieval_logs (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    message_id VARCHAR(36),
    query TEXT NOT NULL,
    risk_type VARCHAR(100),
    retrieved_items LONGTEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_knowledge_logs_conversation ON knowledge_retrieval_logs(conversation_id);
CREATE INDEX idx_knowledge_logs_message ON knowledge_retrieval_logs(message_id);
CREATE INDEX idx_knowledge_logs_created_at ON knowledge_retrieval_logs(created_at);

-- 9. Conversation Groups
CREATE TABLE IF NOT EXISTS conversation_groups (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(50),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    pinned TINYINT(1) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. Conversation Group Mappings
CREATE TABLE IF NOT EXISTS conversation_group_mappings (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL,
    pinned TINYINT(1) DEFAULT 0,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES conversation_groups(id) ON DELETE CASCADE,
    UNIQUE KEY uk_conv_group (conversation_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_group_mappings_conversation ON conversation_group_mappings(conversation_id);
CREATE INDEX idx_group_mappings_group ON conversation_group_mappings(group_id);

-- 11. Vulnerabilities Table
CREATE TABLE IF NOT EXISTS vulnerabilities (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    vulnerability_type VARCHAR(100),
    target VARCHAR(255),
    proof TEXT,
    impact TEXT,
    recommendation TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_vulnerabilities_conversation ON vulnerabilities(conversation_id);
CREATE INDEX idx_vulnerabilities_severity ON vulnerabilities(severity);
CREATE INDEX idx_vulnerabilities_status ON vulnerabilities(status);

-- 12. Batch Task Queues
CREATE TABLE IF NOT EXISTS batch_task_queues (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    role VARCHAR(50),
    created_at DATETIME NOT NULL,
    started_at DATETIME,
    completed_at DATETIME,
    current_index INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_batch_queues_created_at ON batch_task_queues(created_at);

-- 13. Batch Tasks
CREATE TABLE IF NOT EXISTS batch_tasks (
    id VARCHAR(36) PRIMARY KEY,
    queue_id VARCHAR(36) NOT NULL,
    message TEXT NOT NULL,
    conversation_id VARCHAR(36),
    status VARCHAR(50) NOT NULL,
    started_at DATETIME,
    completed_at DATETIME,
    error TEXT,
    result LONGTEXT,
    FOREIGN KEY (queue_id) REFERENCES batch_task_queues(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_batch_tasks_queue_id ON batch_tasks(queue_id);

-- 14. Knowledge Base Items (Originally in separate DB, merged here)
CREATE TABLE IF NOT EXISTS knowledge_base_items (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content LONGTEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_knowledge_items_category ON knowledge_base_items(category);

-- 15. Knowledge Embeddings
CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id VARCHAR(36) PRIMARY KEY,
    item_id VARCHAR(36) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text LONGTEXT NOT NULL,
    embedding JSON NOT NULL, -- Storing vector as JSON array
    created_at DATETIME NOT NULL,
    FOREIGN KEY (item_id) REFERENCES knowledge_base_items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_knowledge_embeddings_item_id ON knowledge_embeddings(item_id);
