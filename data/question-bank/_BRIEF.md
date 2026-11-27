# 编程题数据生成说明（供批量生成使用）

## 目标

把 LeetCode 热题 100、面试经典 150 题、剑指 Offer 去重合并后的编程题，按「解决方法」分类写入
`data/question-bank/*.json`，通过管理端 `POST /api/admin/questions/import` 导入题库。

- 批次与题目清单：`data/question-bank/_plan.json`
- 单题完整格式样例：`data/question-bank/_example.json`
- 校验脚本：`node scripts/validate-question-bank.mjs [文件名关键字]`

## 判题器约束（必须遵守）

题库编程题统一为 **main 自由运行 + AI 点评**，不再区分 ACM / 力扣模式，也不提供任何测试用例。

- Java 固定 `public class Main`，Python 固定 `main.py`，都在 `main` 里自己构造输入、调用解法、打印输出。
- 题面只描述题目本身，给出输入输出示例与提示；**不要写「输入输出格式（ACM 模式）」这类段落**，也不要提力扣函数签名。
- 平台不比对答案。用户点「AI 点评」后，由大模型检查逻辑、边界情况和潜在 bug。

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
  "timeLimitMs": 5000,
  "memoryLimitMb": 256,
  "starterCode": { "JAVA": "...", "PYTHON": "..." }
}
```

## 写作要求

1. 题面用**自己的话重写**，不要整段照抄力扣；结构：
   `## 题目描述` → `## 示例` → `## 提示`。
   末尾保留一句：`> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。`
2. 初始代码只写通用骨架与 TODO 注释，不给出解题代码：

   ```java
   import java.util.*;

   public class Main {
       public static void main(String[] args) {
           // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果
       }
   }
   ```

   ```python
   import sys

   def main():
       # 在这里构造输入、调用你的解法，并用 print 输出结果
       pass

   if __name__ == "__main__":
       main()
   ```
3. 示例里写清输入长什么样、期望输出是什么，用户据此自己构造输入；输出顺序不唯一的题必须写明排序规则。
4. `difficulty`：简单 1、中等 2、困难 3。`source` 固定 `CRAWL`，`sourceUrl` 用
   `https://leetcode.cn/problems/<slug>/`。`kpNames` 1-3 个知识点。
5. 只创建/修改分配给你的那一个 JSON 文件，不要改代码、不要写 README、不要动别的批次。

## 完成标准

运行 `node scripts/validate-question-bank.mjs <文件名>`，输出 0 个问题；题数与 `_plan.json` 一致。
