// 最小验证脚本：模拟 SSE 流边界场景，验证 processSSEChunk 行为正确。
// 用 tsx 直接运行：npx tsx lib/parse-sse.test.ts

import { processSSEChunk } from "./parse-sse";

let pass = 0;
let fail = 0;
const failures: string[] = [];

function expect(name: string, cond: boolean, detail?: string) {
  if (cond) {
    pass++;
    console.log(`  ✓ ${name}`);
  } else {
    fail++;
    failures.push(`${name}${detail ? " — " + detail : ""}`);
    console.log(`  ✗ ${name}${detail ? " — " + detail : ""}`);
  }
}

// === 测试 1：经典 split 场景，多条事件分多个 chunk 到达 ===
{
  console.log("\n[1] 多 chunk 切分：每个 chunk 末尾有完整 \\n\\n 分隔");
  const received: unknown[] = [];
  let buf = "";
  // chunk1: 两条完整事件
  buf = processSSEChunk(buf, 'data: {"a":1}\n\ndata: {"a":2}\n\n', false, (d) => received.push(d));
  // chunk2: 末尾有一半
  buf = processSSEChunk(buf, 'data: {"a":3}\n', false, (d) => received.push(d));
  // chunk3: 补全 + 流结束
  buf = processSSEChunk(buf, '\n\n', true, (d) => received.push(d));
  expect("收到 3 条事件", received.length === 3, `actual=${received.length}`);
  expect("事件顺序正确", JSON.stringify(received) === '[{"a":1},{"a":2},{"a":3}]');
}

// === 测试 2：本次 bug 的核心场景 —— 流末尾只有 \n（没有 trailing \n\n）===
{
  console.log("\n[2] 关键场景：最后一条事件只有单 \\n 收尾（无 trailing \\n\\n）");
  const received: unknown[] = [];
  let buf = "";
  // 模拟：前两条都是完整 \n\n 分隔
  buf = processSSEChunk(buf, 'data: {"c":"hi"}\n\ndata: {"c":"hel"}\n\n', false, (d) => received.push(d));
  // 最后一段只有 \n 收尾（不完整 \\n\\n），done=true
  buf = processSSEChunk(buf, 'data: {"c":"hello","done":true}\n', true, (d) => received.push(d));
  expect("收到 3 条事件（不丢最后一条）", received.length === 3, `actual=${received.length}`);
  expect("最后一条 done=true 仍被解析", JSON.stringify(received[2]) === '{"c":"hello","done":true}');
}

// === 测试 3：极端场景 —— 最后一条没有任何收尾（裸字符串 + done）===
{
  console.log("\n[3] 极端场景：最后一条事件完全没有 \\n 收尾");
  const received: unknown[] = [];
  let buf = "";
  buf = processSSEChunk(buf, 'data: {"x":1}\n\n', false, (d) => received.push(d));
  buf = processSSEChunk(buf, 'data: {"x":2}', true, (d) => received.push(d));
  expect("收到 2 条事件", received.length === 2, `actual=${received.length}`);
  expect("尾部裸字符串也被解析", JSON.stringify(received[1]) === '{"x":2}');
}

// === 测试 4：单 chunk 一次性全到 + done ===
{
  console.log("\n[4] 单 chunk 全量 + done=true");
  const received: unknown[] = [];
  processSSEChunk("", 'data: {"v":1}\n\ndata: {"v":2}\n\ndata: {"v":3,"done":true}', true, (d) => received.push(d));
  expect("3 条全收到", received.length === 3);
  expect("最后一条 done=true 收到", JSON.stringify(received[2]) === '{"v":3,"done":true}');
}

// === 测试 5：双重 data: 前缀（容错）===
{
  console.log("\n[5] 偶发 data:data: 双前缀容错");
  const received: unknown[] = [];
  processSSEChunk("", 'data:data: {"k":"v"}\n\n', true, (d) => received.push(d));
  expect("1 条解析成功", received.length === 1);
  expect("解析出正确 JSON", JSON.stringify(received[0]) === '{"k":"v"}');
}

// === 测试 6：含 \r\n 行尾（Windows 风格）===
{
  console.log("\n[6] \\r\\n 分隔也能切");
  const received: unknown[] = [];
  processSSEChunk("", 'data: {"r":1}\r\n\r\ndata: {"r":2}\r\n\r\n', true, (d) => received.push(d));
  expect("2 条解析成功", received.length === 2);
}

// === 测试 7：坏 JSON 不会阻塞后续事件 ===
{
  console.log("\n[7] 坏 JSON 事件不阻塞");
  const received: unknown[] = [];
  processSSEChunk("", 'data: {not valid json}\n\ndata: {"ok":1}\n\n', true, (d) => received.push(d));
  expect("坏事件被跳过，好事件正常收", received.length === 1);
  expect("好事件内容正确", JSON.stringify(received[0]) === '{"ok":1}');
}

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail > 0) {
  console.log("\n失败项:");
  failures.forEach((f) => console.log("  - " + f));
  process.exit(1);
}