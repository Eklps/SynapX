// 最小验证脚本：模拟 <think> 标签跨 chunk 流式到达的多种场景
// 用 tsx 直接运行：npx tsx lib/think-tag-filter.test.ts

import {
  filterThinkTag,
  INITIAL_THINK_FILTER_STATE,
  type ThinkFilterState,
} from "./think-tag-filter";

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

function runFilter(chunks: string[]): string {
  const state: ThinkFilterState = { ...INITIAL_THINK_FILTER_STATE };
  let out = "";
  for (const c of chunks) {
    out += filterThinkTag(c, state);
  }
  return out;
}

// === 测试 1：单 chunk 内完整 <think> 内容 ===
{
  console.log("\n[1] 单 chunk 完整 <think>...</p><think>");
  const out = runFilter(["<think>hidden</think>visible"]);
  expect("只输出 visible", out === "visible", `actual=${JSON.stringify(out)}`);
}

// === 测试 2：跨 chunk 边界 — <think> 在 chunk1 末尾，</think> 在 chunk2 开头 ===
{
  console.log("\n[2] <think> 跨 chunk 边界");
  const out = runFilter(["<think>hidden ", "stuff</think>visible"]);
  expect("跨边界也能正确剥离", out === "visible", `actual=${JSON.stringify(out)}`);
}

// === 测试 3：跨更多 chunk（用户截图的真实场景）===
{
  console.log("\n[3] <think> 跨多个 chunk（流式逐步到达）");
  const state: ThinkFilterState = { ...INITIAL_THINK_FILTER_STATE };
  let out = "";
  // 模拟：think 标签分散到 5 个 chunk 里
  out += filterThinkTag("<think>", state);
  out += filterThinkTag("The user said ", state);
  out += filterThinkTag("你好 again. ", state);
  out += filterThinkTag("I'll respond briefly.", state);
  out += filterThinkTag("</think>", state);
  out += filterThinkTag("你好！很高兴", state);
  out += filterThinkTag("见到你 😊", state);
  expect("跨 7 个 chunk 完整剥离", out === "你好！很高兴见到你 😊", `actual=${JSON.stringify(out)}`);
}

// === 测试 4：流截断 — <think> 没配对 ===
{
  console.log("\n[4] <think> 未闭合（流被截断）");
  const out = runFilter(["<think>never closed", "still hidden", "more hidden"]);
  expect("未闭合 think 内全部丢弃", out === "", `actual=${JSON.stringify(out)}`);
}

// === 测试 5：think 标签在中间 ===
{
  console.log("\n[5] <think> 在文本中间");
  const out = runFilter(["before<think>hidden</think>after"]);
  expect("中间 think 被剥", out === "beforeafter", `actual=${JSON.stringify(out)}`);
}

// === 测试 6：多个 think 块 ===
{
  console.log("\n[6] 多个 <think> 块");
  const out = runFilter(["<think>h1</think>a<think>h2</think>b"]);
  expect("多块都剥", out === "ab", `actual=${JSON.stringify(out)}`);
}

// === 测试 7：没有 think 标签 — 普通文本不变 ===
{
  console.log("\n[7] 无 think 标签");
  const out = runFilter(["hello world", " 你好", " 很高兴"]);
  expect("无标签原样输出", out === "hello world 你好 很高兴", `actual=${JSON.stringify(out)}`);
}

// === 测试 8：状态机在跨 chunk 时不会被重置 ===
{
  console.log("\n[8] 状态机跨 chunk 持续");
  const state: ThinkFilterState = { ...INITIAL_THINK_FILTER_STATE };
  let out = "";
  out += filterThinkTag("<think>hidden", state);
  // 此时 state.inThinkTag = true
  expect("中间状态正确", state.inThinkTag === true);
  out += filterThinkTag("more hidden</think>visible", state);
  expect("可见部分输出", out === "visible");
  expect("状态机恢复", state.inThinkTag === false);
}

// === 测试 9：用户截图的真实文本（一次到达完整）===
{
  console.log("\n[9] 用户截图里的完整文本");
  const text =
    "<think>The user said \"你好\" (hello) again. I'll respond briefly and friendly.</think>你好！很高兴见到你 😊";
  const out = runFilter([text]);
  expect("用户截图场景剥干净", out === "你好！很高兴见到你 😊", `actual=${JSON.stringify(out)}`);
}

// === 测试 10：空字符串 / null 等边界 ===
{
  console.log("\n[10] 边界输入");
  const state: ThinkFilterState = { ...INITIAL_THINK_FILTER_STATE };
  expect("空字符串", filterThinkTag("", state) === "");
  expect("null 字符串（TS 类型上不允许，但 JS 上能进来）", filterThinkTag(null as any, state) === "");
}

console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if (fail > 0) {
  console.log("\n失败项:");
  failures.forEach((f) => console.log("  - " + f));
  process.exit(1);
}