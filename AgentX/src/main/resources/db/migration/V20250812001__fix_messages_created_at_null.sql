-- 修复 messages 表中 created_at 为 NULL 的历史脏数据
-- 原因：MessageEntity 重写了 createdAt 字段且未带 FieldFill.INSERT 自动填充注解，
--      多模态消息在临时构造 MessageEntity 时漏设 createdAt，导致 NPE 之前
--      部分数据已被写入 DB 但 created_at 为 NULL。
-- 修复策略：用 updated_at 兜底（若 updated_at 也为 null 则用会话创建时间或当前时间），
--          并为 created_at 加上 NOT NULL 默认值约束，防止后续再次出现空值。

-- 1. 先把 NULL 数据补上
-- 1.1 优先用 updated_at 兜底（同一行的更新时间与创建时间通常一致或非常接近）
UPDATE messages
SET created_at = updated_at
WHERE created_at IS NULL
  AND updated_at IS NOT NULL;

-- 1.2 对 updated_at 也为 NULL 的极少数情况，用其所属会话的 context 创建时间兜底
UPDATE messages m
SET created_at = (
    SELECT MIN(c.created_at)
    FROM contexts c
    WHERE c.session_id = m.session_id
      AND c.created_at IS NOT NULL
)
WHERE m.created_at IS NULL
  AND EXISTS (
    SELECT 1 FROM contexts c
    WHERE c.session_id = m.session_id
      AND c.created_at IS NOT NULL
  );

-- 1.3 兜底：剩余确实查不到上下文的，使用当前时间（保证 NOT NULL 约束可以加上）
UPDATE messages
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

-- 2. 给 created_at 加 NOT NULL 默认值约束，防止后续再出现空值
ALTER TABLE messages
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- 3. 同样给 updated_at 加 NOT NULL 默认值（与 created_at 保持一致，减少未来类似隐患）
ALTER TABLE messages
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

-- 4. 添加索引，加速按 created_at 排序的查询
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages (created_at);
