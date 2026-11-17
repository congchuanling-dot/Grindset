-- ============================================================
-- 编程题种子数据（第一版 2 道）
-- question_bank.judge_mode = CODING，starter_code 用 JSON_OBJECT 生成
-- 例题 is_sample = 1（前端可见，用于"运行样例"），其余为隐藏用例
-- 可重复执行：题目按 content_hash 唯一键忽略，用例按主键忽略
-- ============================================================

USE grindset;

INSERT IGNORE INTO question_bank
    (id, title, content, type, judge_mode, difficulty, time_limit_ms, memory_limit_mb,
     domain, reference_answer, starter_code, key_points, kp_ids, kp_names,
     source, source_url, content_hash, status)
VALUES
(900000000000002001, '两数之和',
 '给定一个整数数组 nums 和一个目标值 target，请找出数组中和为 target 的两个数，返回它们的下标。

输入格式：
第一行两个整数 n 和 target，分别表示数组长度和目标值；
第二行 n 个整数，表示数组 nums。

输出格式：
一行两个整数 i j（i < j，从 0 开始计数），表示 nums[i] + nums[j] = target。题目保证有且仅有一组答案。

代码要求：
Java 请在 Main.java 中实现 public class Main；
Python 请写在 main.py 中；
从标准输入读取、向标准输出打印，不要输出多余内容。',
 'CODE', 'CODING', 1, 2000, 256, '算法',
 '用哈希表把"找另一个数"从 O(n) 降到 O(1)：遍历数组时先查 target - nums[i] 是否出现过，出现过就返回两个下标，否则把当前值及其下标放进哈希表。时间复杂度 O(n)，空间复杂度 O(n)。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 读入 n、target 和数组，输出两个下标（升序）
    }
}',
   'PYTHON', 'import sys

def main():
    data = sys.stdin.read().split()
    # TODO 解析输入并输出两个下标（升序）

if __name__ == "__main__":
    main()'
 ),
 '["哈希表","数组遍历","时间复杂度分析"]', '[]', '["哈希表","数组"]',
 'MANUAL', NULL, SHA2('coding-seed-two-sum', 256), 1),

(900000000000002002, '有效的括号',
 '给定一个只包含 ''(''、'')''、''{''、''}''、''[''、'']'' 的字符串 s，判断括号是否有效。

有效条件：
1. 左括号必须用相同类型的右括号闭合；
2. 左括号必须以正确的顺序闭合。

输入格式：
一行字符串 s（长度 1 ~ 10000），只包含上述六种字符。

输出格式：
有效输出 true，无效输出 false（小写）。

代码要求：
Java 请在 Main.java 中实现 public class Main；
Python 请写在 main.py 中；
从标准输入读取、向标准输出打印，不要输出多余内容。',
 'CODE', 'CODING', 2, 2000, 256, '算法',
 '用栈模拟：遇到左括号入栈；遇到右括号时栈顶必须是对应的左括号，否则无效；遍历结束后栈必须为空。时间复杂度 O(n)，空间复杂度 O(n)。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 读入字符串，输出 true 或 false
    }
}',
   'PYTHON', 'import sys

def main():
    s = sys.stdin.read().strip()
    # TODO 判断括号是否有效，输出 true 或 false

if __name__ == "__main__":
    main()'
 ),
 '["栈","字符串处理","边界条件"]', '[]', '["栈","字符串"]',
 'MANUAL', NULL, SHA2('coding-seed-valid-parentheses', 256), 1);

-- ---------- 两数之和用例 ----------
INSERT IGNORE INTO question_test_cases (id, question_id, input, expected_output, is_sample, sort) VALUES
(910000000000002101, 900000000000002001, '4 9
2 7 11 15', '0 1', 1, 1),
(910000000000002102, 900000000000002001, '3 6
3 2 4', '1 2', 1, 2),
(910000000000002103, 900000000000002001, '2 6
3 3', '0 1', 0, 3),
(910000000000002104, 900000000000002001, '5 10
-3 4 3 90 7', '2 4', 0, 4),
(910000000000002105, 900000000000002001, '6 0
-1 0 1 2 -2 4', '0 2', 0, 5);

-- ---------- 有效的括号用例 ----------
INSERT IGNORE INTO question_test_cases (id, question_id, input, expected_output, is_sample, sort) VALUES
(910000000000002201, 900000000000002002, '()', 'true', 1, 1),
(910000000000002202, 900000000000002002, '()[]{}', 'true', 1, 2),
(910000000000002203, 900000000000002002, '(]', 'false', 0, 3),
(910000000000002204, 900000000000002002, '([)]', 'false', 0, 4),
(910000000000002205, 900000000000002002, '{[]}', 'true', 0, 5),
(910000000000002206, 900000000000002002, '(', 'false', 0, 6);
