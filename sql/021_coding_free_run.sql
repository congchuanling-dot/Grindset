-- ============================================================
-- 编程题改为「main 自由运行 + AI 点评」
--   删除测试用例表与提交判题表
--   删除题目上的 ACM / 力扣双模式字段
--   代码点评复用 question_attempts（answer 提升为 MEDIUMTEXT 存源码）
--   种子题题面改为开放式描述，初始代码统一为 main 骨架
-- 脚本可重复执行
-- ============================================================

USE grindset;

-- ---------- 删除不再使用的表 ----------
DROP TABLE IF EXISTS question_test_cases;
DROP TABLE IF EXISTS question_submissions;

-- ---------- 删除不再使用的列 ----------
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DELIMITER $$
CREATE PROCEDURE drop_column_if_exists(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl_sql = CONCAT('ALTER TABLE `', tbl, '` DROP COLUMN `', col, '`');
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL drop_column_if_exists('question_bank', 'code_mode');
CALL drop_column_if_exists('question_bank', 'function_name');
CALL drop_column_if_exists('question_bank', 'param_types');
CALL drop_column_if_exists('question_bank', 'return_type');
CALL drop_column_if_exists('question_bank', 'starter_code_function');

DROP PROCEDURE IF EXISTS drop_column_if_exists;

-- ---------- 作答记录复用为代码点评 ----------
ALTER TABLE question_attempts
    MODIFY answer MEDIUMTEXT NOT NULL COMMENT '本次回答 / 提交的源码';

-- ---------- 初始代码统一为 main 骨架 ----------
UPDATE question_bank SET starter_code = JSON_OBJECT(
    'JAVA', 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\n    }\n}',
    'PYTHON', 'import sys\n\ndef main():\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\n    pass\n\nif __name__ == "__main__":\n    main()'
) WHERE judge_mode = 'CODING';

-- ---------- 种子题:题面去掉 ACM 输入输出格式 ----------
UPDATE question_bank SET content = '## 题目描述

给定一个整数数组 nums 和一个目标值 target，请找出和为 target 的两个数，返回它们的下标（i < j）。

## 示例

输入：nums = [2,7,11,15]，target = 9
输出：[0,1]

输入：nums = [3,2,4]，target = 6
输出：[1,2]

## 提示

- 题目保证有且仅有一组答案
- 同一个元素不能重复使用

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003001;

UPDATE question_bank SET content = '## 题目描述

给定一个只包含括号字符的字符串 s，判断括号是否有效。

有效条件：
1. 左括号必须用相同类型的右括号闭合；
2. 左括号必须以正确顺序闭合。

## 示例

输入：s = "()[]{}"
输出：true

输入：s = "([)]"
输出：false

## 提示

- s 只包含 ()[]{} 六种字符

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003002;

UPDATE question_bank SET content = '## 题目描述

给定一个整数数组 nums，请找出一个具有最大和的连续子数组，返回这个最大和。

## 示例

输入：nums = [-2,1,-3,4,-1,2,1,-5,4]
输出：6

输入：nums = [1]
输出：1

## 提示

- 子数组至少包含一个元素

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003003;

UPDATE question_bank SET content = '## 题目描述

假设你正在爬楼梯，需要 n 阶才能到达楼顶。每次你可以爬 1 或 2 个台阶，请问有多少种不同的方法可以爬到楼顶。

## 示例

输入：n = 3
输出：3

输入：n = 5
输出：8

## 提示

- 1 <= n <= 45

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003004;

UPDATE question_bank SET content = '## 题目描述

给定一个字符串数组 strs，请找出它们的最长公共前缀。如果不存在公共前缀，返回空字符串。

## 示例

输入：strs = ["flower","flow","flight"]
输出："fl"

输入：strs = ["dog","racecar","car"]
输出：""

## 提示

- 1 <= strs.length <= 200

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003005;

UPDATE question_bank SET content = '## 题目描述

给定一个由 1（陆地）和 0（水）组成的二维网格 grid，请计算网格中岛屿的数量。

岛屿由水平或垂直方向相邻的陆地连接而成，网格四周都是水。

## 示例

输入：grid = [
  ["1","1","0","0","0"],
  ["1","1","0","0","0"],
  ["0","0","1","0","0"],
  ["0","0","0","1","1"]
]
输出：3

## 提示

- 只能上下左右四个方向连接

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000003006;

UPDATE question_bank SET content = '## 题目描述

给定两个整数 a 和 b，请输出它们的和。

## 示例

输入：a = 1, b = 2
输出：3

输入：a = -1000000000, b = 1000000000
输出：0

## 提示

- -10^9 <= a, b <= 10^9
- 注意使用 long 避免溢出

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000004001;

UPDATE question_bank SET content = '## 题目描述

给定一行字符串（可能包含空格），请把它倒序输出。

## 示例

输入：hello world
输出：dlrow olleh

输入：abc
输出：cba

## 提示

- 字符串长度不超过 1000

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000004002;

UPDATE question_bank SET content = '## 题目描述

给定 n 个整数，输出其中的最大值以及它第一次出现的下标（下标从 0 开始）。

## 示例

输入：nums = [3,1,4,1,5]
输出：5 4

输入：nums = [7,7,7]
输出：7 0

## 提示

- 1 <= n <= 1000

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000004003;

UPDATE question_bank SET content = '## 题目描述

给定一个只由小写字母组成的字符串 s，判断它是否是回文串。是则输出 yes，否则输出 no。

## 示例

输入：abcba
输出：yes

输入：abc
输出：no

## 提示

- 1 <= |s| <= 1000

> 开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。'
WHERE id = 900000000000004004;

-- 已经通过旧 JSON 导入的编程题，需要重新导入才能拿到新题面与 main 骨架。
-- 导入接口按标题跳过重复题，若要刷新历史数据，请先清理对应的 question_bank 记录再导入。
