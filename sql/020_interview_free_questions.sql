-- ============================================================
-- 面试 FREE_RUN 题面
--   题库原题面偏 ACM/力扣,面试现场需要直接给出具体输入和期望输出
--   question_bank.free_content 存面试专用题面
-- 脚本可重复执行
-- ============================================================

USE grindset;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl_sql = ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('question_bank', 'free_content',
    'ALTER TABLE question_bank ADD COLUMN free_content MEDIUMTEXT NULL COMMENT ''面试 FREE_RUN 专用题面'' AFTER content');

DROP PROCEDURE IF EXISTS add_column_if_missing;

UPDATE question_bank SET free_content = '## 题目描述

给定一个单链表的头节点 head，请反转链表，并返回反转后的头节点。

链表节点值用箭头形式给出，请在 main 中自行构造链表节点。

## 示例

输入：head = 1 -> 2 -> 3 -> 4 -> 5
输出：5 -> 4 -> 3 -> 2 -> 1

输入：head = 空链表
输出：空链表

## 提示

- 节点值范围 -5000 ~ 5000
- 请说明时间复杂度和空间复杂度' WHERE title = '反转链表';

UPDATE question_bank SET free_content = '## 题目描述

给定两个升序单链表 list1 和 list2，请把它们合并成一个新的升序链表并返回。

链表节点值用箭头形式给出，请在 main 中自行构造链表节点。

## 示例

输入：list1 = 1 -> 2 -> 4，list2 = 1 -> 3 -> 4
输出：1 -> 1 -> 2 -> 3 -> 4 -> 4

输入：list1 = 空链表，list2 = 0
输出：0

## 提示

- 两个链表都是升序
- 请说明时间复杂度和空间复杂度' WHERE title = '合并两个有序链表';

UPDATE question_bank SET free_content = '## 题目描述

给定一个整数数组 nums 和一个目标值 target，请找出数组中和为 target 的两个数，返回它们的下标。

请在 main 中直接构造数组并调用你的解法。

## 示例

输入：nums = [2,7,11,15]，target = 9
输出：[0,1]
解释：nums[0] + nums[1] = 2 + 7 = 9

输入：nums = [3,2,4]，target = 6
输出：[1,2]

## 提示

- 题目保证有且仅有一组答案
- 同一个元素不能重复使用
- 请说明时间复杂度和空间复杂度' WHERE title = '两数之和';

UPDATE question_bank SET free_content = '## 题目描述

给定一个只包含括号字符的字符串 s，判断括号是否有效。

有效条件：
1. 左括号必须用相同类型的右括号闭合；
2. 左括号必须以正确顺序闭合。

## 示例

输入：s = "()[]{}"
输出：true

输入：s = "([)]"
输出：false

输入：s = "("
输出：false

## 提示

- s 只包含 ()[]{} 六种字符
- 请说明时间复杂度和空间复杂度' WHERE title = '有效的括号';

UPDATE question_bank SET free_content = '## 题目描述

给定一个整数数组 nums，请找出一个具有最大和的连续子数组，返回这个最大和。

## 示例

输入：nums = [-2,1,-3,4,-1,2,1,-5,4]
输出：6
解释：连续子数组 [4,-1,2,1] 的和最大，为 6

输入：nums = [1]
输出：1

输入：nums = [5,4,-1,7,8]
输出：23

## 提示

- 子数组至少包含一个元素
- 请说明时间复杂度和空间复杂度' WHERE title = '最大子数组和';

UPDATE question_bank SET free_content = '## 题目描述

假设你正在爬楼梯，需要 n 阶才能到达楼顶。每次你可以爬 1 或 2 个台阶，请问有多少种不同的方法可以爬到楼顶。

## 示例

输入：n = 3
输出：3
解释：1+1+1、1+2、2+1

输入：n = 5
输出：8

## 提示

- 1 <= n <= 45
- 请说明时间复杂度和空间复杂度' WHERE title = '爬楼梯';

UPDATE question_bank SET free_content = '## 题目描述

给定一个由 "1"（陆地）和 "0"（水）组成的二维网格 grid，请计算岛屿的数量。

岛屿由水平或垂直方向相邻的陆地连接而成，网格四周都是水。

## 示例

输入：grid = [
  ["1","1","0","0","0"],
  ["1","1","0","0","0"],
  ["0","0","1","0","0"],
  ["0","0","0","1","1"]
]
输出：3

输入：grid = [
  ["1","1","1"],
  ["1","1","1"],
  ["1","1","1"]
]
输出：1

## 提示

- 只能上下左右四个方向连接
- 请说明时间复杂度和空间复杂度' WHERE title = '岛屿数量';

UPDATE question_bank SET free_content = '## 题目描述

给定一个字符串 s，请找出其中不含重复字符的最长子串的长度。

## 示例

输入：s = "abcabcbb"
输出：3
解释：最长无重复子串是 "abc"

输入：s = "bbbbb"
输出：1

输入：s = "pwwkew"
输出：3

## 提示

- 子串必须连续
- 请说明时间复杂度和空间复杂度' WHERE title = '无重复字符的最长子串';

UPDATE question_bank SET free_content = '## 题目描述

给定一棵二叉树的根节点 root，请返回它的层序遍历结果，即逐层从左到右访问所有节点。

二叉树按层序数组给出，null 表示空节点。请在 main 中自行构造二叉树。

## 示例

输入：root = [3,9,20,null,null,15,7]
输出：[[3],[9,20],[15,7]]

输入：root = []
输出：[]

## 提示

- 按层返回，每层一个数组
- 请说明时间复杂度和空间复杂度' WHERE title = '二叉树的层序遍历';

UPDATE question_bank SET free_content = '## 题目描述

给定一个整数数组 nums，请找出所有和为 0 且不重复的三元组。

## 示例

输入：nums = [-1,0,1,2,-1,-4]
输出：[[-1,-1,2],[-1,0,1]]

输入：nums = [0,1,1]
输出：[]

输入：nums = [0,0,0]
输出：[[0,0,0]]

## 提示

- 答案中不能包含重复的三元组
- 请说明时间复杂度和空间复杂度' WHERE title = '三数之和';

UPDATE question_bank SET free_content = '## 题目描述

给定一个字符串 s，请找出 s 中最长的回文子串。

## 示例

输入：s = "babad"
输出："bab"（"aba" 也是正确答案）

输入：s = "cbbd"
输出："bb"

## 提示

- 回文串是正着读和反着读都一样的字符串
- 请说明时间复杂度和空间复杂度' WHERE title = '最长回文子串';
