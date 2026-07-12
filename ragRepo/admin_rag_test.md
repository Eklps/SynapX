# AgentX RAG 知识库测试文档(admin 版)

## 什么是 RAG

RAG(Retrieval-Augmented Generation,检索增强生成)是一种结合信息检索与文本生成的技术方案。

## 检索优化策略

1. **HyDE(Hypothetical Document Embeddings)**:让 LLM 先生成假设性答案,再用答案向量去检索真实文档
2. **查询扩展(Query Expansion)**:基于同义词、相关词扩展原始查询
3. **混合检索**:同时使用向量检索与关键词检索
4. **Rerank 重排**:用更强的模型对候选文档做精排

## SynapX 平台特点

SynapX 是基于 Spring Boot 3.2 与 LangChain4j 构建的企业级 AI Agent 平台,提供 RAG、MCP 工具沙箱、WebSocket 流式对话、高可用 LLM 网关等能力。
