# 编程题数据生成说明（供批量生成使用）

## 目标

把 LeetCode 热题 100、面试经典 150 题、剑指 Offer 去重合并后的编程题，按「解决方法」分类写入
`data/question-bank/*.json`，通过管理端 `POST /api/admin/questions/import` 导入题库。

- 批次与题目清单：`data/question-bank/_plan.json`
- 单题完整格式样例：`data/question-bank/_example.json`
- 校验脚本：`node scripts/validate-question-bank.mjs [文件名关键字]`

## 判题器约束（必须遵守）

判题器实现见 `backend/src/main/java/com/grindset/backend/service/JudgeService.java`。

- 力扣模式：只写 `class Solution`（Java）或 `class Solution`（Python），由驱动读 JSON 参数、调用方法、按 JSON 打印返回值。
- 力扣模式参数类型只支持：
  `int` `long` `double` `boolean` `char` `String`、对应一维数组、`int[][]` `char[][]` `String[][]`、
  `List<Integer>` `List<String>` `List<List<Integer>>`。
- 返回值统一按 JSON 比较：`true/false`、数字、字符串带双引号、数组 `[1,2]`、嵌套数组 `[[1],[2]]`。
- 以下题型**不能用力扣模式**，只能 ACM：
  - 参数/返回是 `ListNode` / `TreeNode` / 图节点；
  - `void` 原地修改（排序、旋转、置零等）；
  - 设计类（LRU、Trie、最小栈、数据流中位数等）；
  - 返回浮点数且需要控制精度的题。
- ACM 模式：Java 固定 `public class Main`，Python 固定 `main.py`，从标准输入读、向标准输出写。
- 输出顺序不唯一的题（子集、全排列、组合总和、三数之和、字母异位词分组等），必须在题面写明唯一排序规则
  （例如“结果按字典序升序输出”），否则判题会误判。

## 单题字段

```json
{
  "title": "两数之和",
  "content": "Markdown 题面",
  "type": "CODE",
  "judgeMode": "CODING",
  "difficulty": 1,
  "domain": "哈希",
  "referenceAnswer": "思路 + 复杂度，100-200 字",
  "keyPoints": ["哈希表", "一次遍历"],
  "kpNames": ["哈希表", "数组"],
  "source": "CRAWL",
  "sourceUrl": "https://leetcode.cn/problems/two-sum/",
  "codeMode": "LEETCODE",
  "timeLimitMs": 2000,
  "memoryLimitMb": 256,
  "functionName": "twoSum",
  "paramTypes": ["int[]", "int"],
  "returnType": "int[]",
  "starterCode": { "JAVA": "...", "PYTHON": "..." },
  "starterCodeFunction": { "JAVA": "...", "PYTHON": "..." },
  "testCases": [
    { "caseMode": "LEETCODE", "input": "[[2,7,11,15],9]", "expectedOutput": "[0,1]", "isSample": 1, "sort": 1 },
    { "caseMode": "ACM", "input": "4 9\n2 7 11 15", "expectedOutput": "0 1", "isSample": 1, "sort": 1 }
  ]
}
```

## 写作要求

1. 题面用**自己的话重写**，不要整段照抄力扣；结构：
   `## 题目描述` → `## 示例` → `## 输入输出格式（ACM 模式）` → `## 提示`。
   力扣模式可用的题，在题面末尾加一句“力扣模式下只需实现 xxx 函数，不用读写标准输入输出”。
2. 力扣模式初始代码：
   - Java：`class Solution { ... }`，方法签名与 `functionName`/`paramTypes`/`returnType` 一致；
   - Python：需要时 `from typing import List`，`class Solution` 方法签名一致。
3. ACM 初始代码：只写读写框架与 TODO 注释，注释里写清输入输出格式；不需要给出解题代码。
4. `modes=acm` 的题：
   - 不要填 `functionName` / `paramTypes` / `returnType` / `starterCodeFunction`；
   - 必须自定义简单明确的输入输出协议并写进题面；
   - 树用层序数组（`null` 表示空节点），链表用节点值数组，设计类用逐行操作序列。
5. `testCases`：每题 4-6 条，至少 2 条 `isSample=1`、至少 1 条 `isSample=0`，`sort` 从 1 递增。
   - 力扣用例 `input` 是参数按签名顺序组成的 JSON 数组，`expectedOutput` 是合法 JSON；
   - ACM 用例 `input` 是标准输入文本，`expectedOutput` 是标准输出文本；
   - **只写你能确定、规模很小的用例，期望输出必须自己核算**，不要编造。
6. `difficulty`：简单 1、中等 2、困难 3。`source` 固定 `CRAWL`，`sourceUrl` 用
   `https://leetcode.cn/problems/<slug>/`。`kpNames` 1-3 个知识点。
7. 只创建/修改分配给你的那一个 JSON 文件，不要改代码、不要写 README、不要动别的批次。

## 完成标准

运行 `node scripts/validate-question-bank.mjs <文件名>`，输出 0 个问题；题数与 `_plan.json` 一致。
