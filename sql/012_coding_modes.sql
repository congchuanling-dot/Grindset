-- ============================================================
-- 编程题双模式:ACM(标准输入输出) / LEETCODE(函数签名)
-- question_bank 增加 code_mode / function_name / param_types / return_type
-- 并补充 6 道函数题 + 4 道 ACM 题,含例题与隐藏用例
-- 脚本可重复执行
-- ============================================================

USE grindset;

-- ---------- question_bank 扩展列 ----------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'code_mode');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN code_mode VARCHAR(16) NOT NULL DEFAULT ''ACM'' COMMENT ''ACM 标准输入输出 / LEETCODE 函数签名'' AFTER judge_mode',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'function_name');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN function_name VARCHAR(64) NULL COMMENT ''LEETCODE 模式下待实现的函数名'' AFTER code_mode',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'param_types');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN param_types JSON NULL COMMENT ''函数参数类型,如 ["int[]","int"]'' AFTER function_name',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'return_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN return_type VARCHAR(32) NULL COMMENT ''返回值类型,如 int[]'' AFTER param_types',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 函数题(力扣模式) ----------
INSERT IGNORE INTO question_bank
    (id, title, content, type, judge_mode, code_mode, function_name, param_types, return_type,
     difficulty, time_limit_ms, memory_limit_mb, domain, reference_answer, starter_code,
     key_points, kp_ids, kp_names, source, source_url, content_hash, status)
VALUES
(900000000000003001, '两数之和（函数题）',
 '给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。

你可以假设每种输入只会对应一个答案，同一元素不能重复使用。返回的下标按从小到大排列。

函数签名：int[] twoSum(int[] nums, int target)',
 'CODE', 'CODING', 'LEETCODE', 'twoSum', JSON_ARRAY('int[]', 'int'), 'int[]',
 1, 2000, 256, '算法',
 '遍历数组，用 HashMap 记录「值 -> 下标」。对当前元素 x，先查 target - x 是否已出现：出现则直接返回两个下标；否则把 x 及其下标存表。时间 O(n)，空间 O(n)。',
 JSON_OBJECT(
   'JAVA', 'class Solution {
    public int[] twoSum(int[] nums, int target) {
        // TODO
        return new int[0];
    }
}',
   'PYTHON', 'class Solution:
    def twoSum(self, nums, target):
        # TODO
        return []'
 ),
 '["哈希表","一次遍历"]', '[]', '["哈希表","数组"]',
 'MANUAL', NULL, SHA2('coding-leetcode-two-sum', 256), 1),

(900000000000003002, '有效的括号（函数题）',
 '给定一个只包括 ''(''、'')''、''{''、''}''、''[''、'']'' 的字符串 s，判断字符串是否有效。

有效字符串需满足：左括号必须用相同类型的右括号闭合；左括号必须以正确的顺序闭合。

函数签名：boolean isValid(String s)',
 'CODE', 'CODING', 'LEETCODE', 'isValid', JSON_ARRAY('String'), 'boolean',
 2, 2000, 256, '算法',
 '用栈：左括号入栈；遇到右括号时，栈顶必须是对应左括号，否则返回 false；遍历结束后栈必须为空。时间 O(n)，空间 O(n)。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

class Solution {
    public boolean isValid(String s) {
        // TODO
        return false;
    }
}',
   'PYTHON', 'class Solution:
    def isValid(self, s):
        # TODO
        return False'
 ),
 '["栈","字符串遍历"]', '[]', '["栈","字符串"]',
 'MANUAL', NULL, SHA2('coding-leetcode-valid-parentheses', 256), 1),

(900000000000003003, '最大子数组和（函数题）',
 '给你一个整数数组 nums，请找出一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。

函数签名：int maxSubArray(int[] nums)',
 'CODE', 'CODING', 'LEETCODE', 'maxSubArray', JSON_ARRAY('int[]'), 'int',
 2, 2000, 256, '算法',
 '动态规划（Kadane 算法）：cur = max(x, cur + x)，ans = max(ans, cur)。cur 表示以当前元素结尾的最大子数组和。时间 O(n)，空间 O(1)。',
 JSON_OBJECT(
   'JAVA', 'class Solution {
    public int maxSubArray(int[] nums) {
        // TODO
        return 0;
    }
}',
   'PYTHON', 'class Solution:
    def maxSubArray(self, nums):
        # TODO
        return 0'
 ),
 '["动态规划","Kadane"]', '[]', '["动态规划","数组"]',
 'MANUAL', NULL, SHA2('coding-leetcode-max-subarray', 256), 1),

(900000000000003004, '爬楼梯（函数题）',
 '假设你正在爬楼梯，需要 n 阶你才能到达楼顶。每次你可以爬 1 或 2 个台阶，问有多少种不同的方法可以爬到楼顶。

函数签名：int climbStairs(int n)

提示：1 <= n <= 45',
 'CODE', 'CODING', 'LEETCODE', 'climbStairs', JSON_ARRAY('int'), 'int',
 1, 2000, 256, '算法',
 '本质是斐波那契数列：f(n) = f(n-1) + f(n-2)，f(1)=1，f(2)=2。只需保存前两项，时间 O(n)，空间 O(1)。',
 JSON_OBJECT(
   'JAVA', 'class Solution {
    public int climbStairs(int n) {
        // TODO
        return 0;
    }
}',
   'PYTHON', 'class Solution:
    def climbStairs(self, n):
        # TODO
        return 0'
 ),
 '["动态规划","斐波那契"]', '[]', '["动态规划"]',
 'MANUAL', NULL, SHA2('coding-leetcode-climb-stairs', 256), 1),

(900000000000003005, '最长公共前缀（函数题）',
 '编写一个函数来查找字符串数组中的最长公共前缀。如果不存在公共前缀，返回空字符串 ""。

函数签名：String longestCommonPrefix(String[] strs)',
 'CODE', 'CODING', 'LEETCODE', 'longestCommonPrefix', JSON_ARRAY('String[]'), 'String',
 1, 2000, 256, '算法',
 '以第一个字符串为基准，逐位与其他字符串比较；某一位不匹配或某个字符串已到末尾就截断。时间 O(n·m)，n 为字符串数量，m 为最短长度。',
 JSON_OBJECT(
   'JAVA', 'class Solution {
    public String longestCommonPrefix(String[] strs) {
        // TODO
        return "";
    }
}',
   'PYTHON', 'class Solution:
    def longestCommonPrefix(self, strs):
        # TODO
        return ""'
 ),
 '["字符串","纵向扫描"]', '[]', '["字符串"]',
 'MANUAL', NULL, SHA2('coding-leetcode-common-prefix', 256), 1),

(900000000000003006, '岛屿数量（函数题）',
 '给你一个由 ''1''（陆地）和 ''0''（水）组成的二维网格 grid，请你计算网格中岛屿的数量。

岛屿总是被水包围，并且每座岛屿只能由水平方向和/或竖直方向上相邻的陆地连接形成。

函数签名：int numIslands(char[][] grid)',
 'CODE', 'CODING', 'LEETCODE', 'numIslands', JSON_ARRAY('char[][]'), 'int',
 3, 3000, 256, '算法',
 '遍历网格，遇到 ''1'' 就把计数加一并从该格开始 DFS/BFS，把相连的陆地全部标记为 ''0''（已访问）。每个格子最多访问一次，时间 O(m·n)。',
 JSON_OBJECT(
   'JAVA', 'class Solution {
    public int numIslands(char[][] grid) {
        // TODO
        return 0;
    }
}',
   'PYTHON', 'class Solution:
    def numIslands(self, grid):
        # TODO
        return 0'
 ),
 '["DFS","BFS","网格遍历"]', '[]', '["搜索","图"]',
 'MANUAL', NULL, SHA2('coding-leetcode-num-islands', 256), 1),

-- ---------- ACM 题(标准输入输出) ----------
(900000000000004001, 'A + B Problem',
 '输入两个整数 a 和 b，输出它们的和。

输入格式：一行两个整数 a 和 b（-10^9 <= a, b <= 10^9）。
输出格式：一行一个整数，表示 a + b。',
 'CODE', 'CODING', 'ACM', NULL, NULL, NULL,
 1, 2000, 128, '算法',
 '直接读入两个整数并输出和。注意使用 long 避免大数溢出。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 读入 a、b，输出 a + b
    }
}',
   'PYTHON', 'import sys

def main():
    data = sys.stdin.read().split()
    # TODO 解析并输出 a + b

if __name__ == "__main__":
    main()'
 ),
 '["输入输出","大数"]', '[]', '["基础语法"]',
 'MANUAL', NULL, SHA2('coding-acm-a-plus-b', 256), 1),

(900000000000004002, '字符串反转',
 '给定一行字符串（可能包含空格），请把它倒序输出。

输入格式：一行字符串 s（长度不超过 1000，可能含空格）。
输出格式：一行，s 的逆序。',
 'CODE', 'CODING', 'ACM', NULL, NULL, NULL,
 1, 2000, 128, '算法',
 '读入整行（不要用 next() 以保留空格），从后往前拼接输出。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 读入整行并倒序输出
    }
}',
   'PYTHON', 'import sys

def main():
    line = sys.stdin.readline().rstrip("\n")
    # TODO 输出 line 的逆序

if __name__ == "__main__":
    main()'
 ),
 '["字符串","整行读取"]', '[]', '["字符串"]',
 'MANUAL', NULL, SHA2('coding-acm-reverse-line', 256), 1),

(900000000000004003, '数组最大值与下标',
 '给定 n 个整数，输出其中的最大值以及它第一次出现的下标（下标从 0 开始）。

输入格式：
第一行一个整数 n（1 <= n <= 1000）；
第二行 n 个整数。
输出格式：一行两个整数，最大值和它的下标，用空格分隔。',
 'CODE', 'CODING', 'ACM', NULL, NULL, NULL,
 1, 2000, 128, '算法',
 '顺序遍历，用 `>` 而不是 `>=` 比较，保证取到第一次出现的下标。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 读入 n 和数组，输出最大值与首次出现的下标
    }
}',
   'PYTHON', 'import sys

def main():
    data = sys.stdin.read().split()
    # TODO 解析并输出最大值与首次出现的下标

if __name__ == "__main__":
    main()'
 ),
 '["数组遍历","边界"]', '[]', '["数组"]',
 'MANUAL', NULL, SHA2('coding-acm-max-index', 256), 1),

(900000000000004004, '回文字符串判断',
 '给定一个只由小写字母组成的字符串 s，判断它是否是回文串。是则输出 yes，否则输出 no。

输入格式：一行字符串 s（1 <= |s| <= 1000）。
输出格式：yes 或 no。',
 'CODE', 'CODING', 'ACM', NULL, NULL, NULL,
 1, 2000, 128, '算法',
 '双指针从两端向中间比较，任一处不相等即不是回文。',
 JSON_OBJECT(
   'JAVA', 'import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // TODO 判断回文，输出 yes / no
    }
}',
   'PYTHON', 'import sys

def main():
    s = sys.stdin.readline().strip()
    # TODO 判断回文，输出 yes / no

if __name__ == "__main__":
    main()'
 ),
 '["双指针","字符串"]', '[]', '["字符串","双指针"]',
 'MANUAL', NULL, SHA2('coding-acm-palindrome', 256), 1);

-- ---------- 用例(函数题:input 为参数 JSON 数组,expected 为返回值 JSON) ----------
INSERT IGNORE INTO question_test_cases (id, question_id, input, expected_output, is_sample, sort) VALUES
(900000000000003101, 900000000000003001, '[[2,7,11,15],9]', '[0,1]', 1, 1),
(900000000000003102, 900000000000003001, '[[3,2,4],6]', '[1,2]', 1, 2),
(900000000000003103, 900000000000003001, '[[3,3],6]', '[0,1]', 0, 3),
(900000000000003104, 900000000000003001, '[[-3,4,3,90],0]', '[0,2]', 0, 4),

(900000000000003201, 900000000000003002, '["()"]', 'true', 1, 1),
(900000000000003202, 900000000000003002, '["()[]{}"]', 'true', 1, 2),
(900000000000003203, 900000000000003002, '["(]"]', 'false', 0, 3),
(900000000000003204, 900000000000003002, '["([)]"]', 'false', 0, 4),
(900000000000003205, 900000000000003002, '["{[]}"]', 'true', 0, 5),

(900000000000003301, 900000000000003003, '[[-2,1,-3,4,-1,2,1,-5,4]]', '6', 1, 1),
(900000000000003302, 900000000000003003, '[[1]]', '1', 1, 2),
(900000000000003303, 900000000000003003, '[[5,4,-1,7,8]]', '23', 0, 3),
(900000000000003304, 900000000000003003, '[[-3,-1,-2]]', '-1', 0, 4),

(900000000000003401, 900000000000003004, '[2]', '2', 1, 1),
(900000000000003402, 900000000000003004, '[3]', '3', 1, 2),
(900000000000003403, 900000000000003004, '[1]', '1', 0, 3),
(900000000000003404, 900000000000003004, '[10]', '89', 0, 4),
(900000000000003405, 900000000000003004, '[45]', '1836311903', 0, 5),

(900000000000003501, 900000000000003005, '[["flower","flow","flight"]]', '"fl"', 1, 1),
(900000000000003502, 900000000000003005, '[["dog","racecar","car"]]', '""', 1, 2),
(900000000000003503, 900000000000003005, '[["a"]]', '"a"', 0, 3),
(900000000000003504, 900000000000003005, '[["ab","ab"]]', '"ab"', 0, 4),

(900000000000003601, 900000000000003006, '[["11110","11010","11000","00000"]]', '1', 1, 1),
(900000000000003602, 900000000000003006, '[["11000","11000","00100","00011"]]', '3', 1, 2),
(900000000000003603, 900000000000003006, '[["111","010","101"]]', '3', 0, 3),

-- ---------- 用例(ACM:input 为标准输入) ----------
(900000000000004101, 900000000000004001, '1 2', '3', 1, 1),
(900000000000004102, 900000000000004001, '-1000000000 1000000000', '0', 1, 2),
(900000000000004103, 900000000000004001, '1000000000 1000000000', '2000000000', 0, 3),

(900000000000004201, 900000000000004002, 'hello world', 'dlrow olleh', 1, 1),
(900000000000004202, 900000000000004002, 'abc', 'cba', 1, 2),
(900000000000004203, 900000000000004002, 'a', 'a', 0, 3),

(900000000000004301, 900000000000004003, '5
3 1 4 1 5', '5 4', 1, 1),
(900000000000004302, 900000000000004003, '3
7 7 7', '7 0', 1, 2),
(900000000000004303, 900000000000004003, '1
-9', '-9 0', 0, 3),

(900000000000004401, 900000000000004004, 'abcba', 'yes', 1, 1),
(900000000000004402, 900000000000004004, 'abc', 'no', 1, 2),
(900000000000004403, 900000000000004004, 'a', 'yes', 0, 3),
(900000000000004404, 900000000000004004, 'abba', 'yes', 0, 4);
