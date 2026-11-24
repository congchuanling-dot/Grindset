-- ============================================================
-- 编程题双模式改造:每道题同时支持 ACM 与 LEETCODE
--   question_bank             增加 starter_code_function(力扣模式初始代码)
--   question_test_cases       增加 case_mode(ACM / LEETCODE)
-- 同时合并重复题目:保留带函数题的版本,删除旧的两道 ACM 重复题
-- 脚本可重复执行
-- ============================================================

USE grindset;

-- ---------- 结构 ----------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'starter_code_function');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN starter_code_function JSON NULL COMMENT ''力扣模式初始代码 {"JAVA":"...","PYTHON":"..."}'' AFTER starter_code',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_test_cases' AND COLUMN_NAME = 'case_mode');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_test_cases ADD COLUMN case_mode VARCHAR(16) NOT NULL DEFAULT ''ACM'' COMMENT ''ACM 标准输入输出 / LEETCODE JSON 参数'' AFTER question_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 合并重复题:删除旧的两道 ACM 题 ----------
DELETE FROM question_submissions WHERE question_id IN (900000000000002001, 900000000000002002);
DELETE FROM question_test_cases WHERE question_id IN (900000000000002001, 900000000000002002);
DELETE FROM user_question_progress WHERE question_id IN (900000000000002001, 900000000000002002);
DELETE FROM question_bank WHERE id IN (900000000000002001, 900000000000002002);

-- ---------- 公共 ACM 初始代码 ----------
SET @acm_java = 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // TODO 按题目说明读入数据并输出答案\n    }\n}';
SET @acm_py = 'import sys\n\ndef main():\n    data = sys.stdin.read()\n    # TODO 按题目说明读入数据并输出答案\n\nif __name__ == "__main__":\n    main()';

-- ---------- 逐题配置:签名 + 两种模式的初始代码 ----------
UPDATE question_bank SET
    title = '两数之和',
    code_mode = 'ACM',
    function_name = 'twoSum',
    param_types = JSON_ARRAY('int[]', 'int'),
    return_type = 'int[]',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'import java.util.*;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // TODO\n        return new int[0];\n    }\n}',
        'PYTHON', 'class Solution:\n    def twoSum(self, nums, target):\n        # TODO\n        return []')
WHERE id = 900000000000003001;

UPDATE question_bank SET
    title = '有效的括号',
    code_mode = 'ACM',
    function_name = 'isValid',
    param_types = JSON_ARRAY('String'),
    return_type = 'boolean',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'import java.util.*;\n\nclass Solution {\n    public boolean isValid(String s) {\n        // TODO\n        return false;\n    }\n}',
        'PYTHON', 'class Solution:\n    def isValid(self, s):\n        # TODO\n        return False')
WHERE id = 900000000000003002;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'maxSubArray',
    param_types = JSON_ARRAY('int[]'),
    return_type = 'int',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public int maxSubArray(int[] nums) {\n        // TODO\n        return 0;\n    }\n}',
        'PYTHON', 'class Solution:\n    def maxSubArray(self, nums):\n        # TODO\n        return 0')
WHERE id = 900000000000003003;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'climbStairs',
    param_types = JSON_ARRAY('int'),
    return_type = 'int',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public int climbStairs(int n) {\n        // TODO\n        return 0;\n    }\n}',
        'PYTHON', 'class Solution:\n    def climbStairs(self, n):\n        # TODO\n        return 0')
WHERE id = 900000000000003004;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'longestCommonPrefix',
    param_types = JSON_ARRAY('String[]'),
    return_type = 'String',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public String longestCommonPrefix(String[] strs) {\n        // TODO\n        return "";\n    }\n}',
        'PYTHON', 'class Solution:\n    def longestCommonPrefix(self, strs):\n        # TODO\n        return ""')
WHERE id = 900000000000003005;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'numIslands',
    param_types = JSON_ARRAY('char[][]'),
    return_type = 'int',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public int numIslands(char[][] grid) {\n        // TODO\n        return 0;\n    }\n}',
        'PYTHON', 'class Solution:\n    def numIslands(self, grid):\n        # TODO\n        return 0')
WHERE id = 900000000000003006;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'add',
    param_types = JSON_ARRAY('int', 'int'),
    return_type = 'int',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public int add(int a, int b) {\n        // TODO\n        return 0;\n    }\n}',
        'PYTHON', 'class Solution:\n    def add(self, a, b):\n        # TODO\n        return 0')
WHERE id = 900000000000004001;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'reverseString',
    param_types = JSON_ARRAY('String'),
    return_type = 'String',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public String reverseString(String s) {\n        // TODO\n        return "";\n    }\n}',
        'PYTHON', 'class Solution:\n    def reverseString(self, s):\n        # TODO\n        return ""')
WHERE id = 900000000000004002;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'maxAndIndex',
    param_types = JSON_ARRAY('int[]'),
    return_type = 'int[]',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public int[] maxAndIndex(int[] nums) {\n        // TODO\n        return new int[0];\n    }\n}',
        'PYTHON', 'class Solution:\n    def maxAndIndex(self, nums):\n        # TODO\n        return []')
WHERE id = 900000000000004003;

UPDATE question_bank SET
    code_mode = 'ACM',
    function_name = 'isPalindrome',
    param_types = JSON_ARRAY('String'),
    return_type = 'boolean',
    starter_code = JSON_OBJECT('JAVA', @acm_java, 'PYTHON', @acm_py),
    starter_code_function = JSON_OBJECT(
        'JAVA', 'class Solution {\n    public boolean isPalindrome(String s) {\n        // TODO\n        return false;\n    }\n}',
        'PYTHON', 'class Solution:\n    def isPalindrome(self, s):\n        # TODO\n        return False')
WHERE id = 900000000000004004;

-- ---------- 重刷用例:每题 2 例题 + 1 隐藏,两种模式各一份 ----------
DELETE FROM question_test_cases WHERE question_id IN (
    900000000000003001, 900000000000003002, 900000000000003003,
    900000000000003004, 900000000000003005, 900000000000003006,
    900000000000004001, 900000000000004002, 900000000000004003,
    900000000000004004);

INSERT INTO question_test_cases (id, question_id, case_mode, input, expected_output, is_sample, sort) VALUES
-- 两数之和
(900000000000003111, 900000000000003001, 'ACM', '4 9\n2 7 11 15', '0 1', 1, 1),
(900000000000003112, 900000000000003001, 'ACM', '3 6\n3 2 4', '1 2', 1, 2),
(900000000000003113, 900000000000003001, 'ACM', '2 6\n3 3', '0 1', 0, 3),
(900000000000003121, 900000000000003001, 'LEETCODE', '[[2,7,11,15],9]', '[0,1]', 1, 1),
(900000000000003122, 900000000000003001, 'LEETCODE', '[[3,2,4],6]', '[1,2]', 1, 2),
(900000000000003123, 900000000000003001, 'LEETCODE', '[[3,3],6]', '[0,1]', 0, 3),
-- 有效的括号
(900000000000003211, 900000000000003002, 'ACM', '()', 'true', 1, 1),
(900000000000003212, 900000000000003002, 'ACM', '()[]{}', 'true', 1, 2),
(900000000000003213, 900000000000003002, 'ACM', '(]', 'false', 0, 3),
(900000000000003221, 900000000000003002, 'LEETCODE', '["()"]', 'true', 1, 1),
(900000000000003222, 900000000000003002, 'LEETCODE', '["()[]{}"]', 'true', 1, 2),
(900000000000003223, 900000000000003002, 'LEETCODE', '["(]"]', 'false', 0, 3),
-- 最大子数组和
(900000000000003311, 900000000000003003, 'ACM', '9\n-2 1 -3 4 -1 2 1 -5 4', '6', 1, 1),
(900000000000003312, 900000000000003003, 'ACM', '1\n1', '1', 1, 2),
(900000000000003313, 900000000000003003, 'ACM', '3\n-3 -1 -2', '-1', 0, 3),
(900000000000003321, 900000000000003003, 'LEETCODE', '[[-2,1,-3,4,-1,2,1,-5,4]]', '6', 1, 1),
(900000000000003322, 900000000000003003, 'LEETCODE', '[[1]]', '1', 1, 2),
(900000000000003323, 900000000000003003, 'LEETCODE', '[[-3,-1,-2]]', '-1', 0, 3),
-- 爬楼梯
(900000000000003411, 900000000000003004, 'ACM', '2', '2', 1, 1),
(900000000000003412, 900000000000003004, 'ACM', '3', '3', 1, 2),
(900000000000003413, 900000000000003004, 'ACM', '10', '89', 0, 3),
(900000000000003421, 900000000000003004, 'LEETCODE', '[2]', '2', 1, 1),
(900000000000003422, 900000000000003004, 'LEETCODE', '[3]', '3', 1, 2),
(900000000000003423, 900000000000003004, 'LEETCODE', '[10]', '89', 0, 3),
-- 最长公共前缀
(900000000000003511, 900000000000003005, 'ACM', '3\nflower flow flight', 'fl', 1, 1),
(900000000000003512, 900000000000003005, 'ACM', '3\ndog racecar car', '', 1, 2),
(900000000000003513, 900000000000003005, 'ACM', '2\nab ab', 'ab', 0, 3),
(900000000000003521, 900000000000003005, 'LEETCODE', '[["flower","flow","flight"]]', '"fl"', 1, 1),
(900000000000003522, 900000000000003005, 'LEETCODE', '[["dog","racecar","car"]]', '""', 1, 2),
(900000000000003523, 900000000000003005, 'LEETCODE', '[["ab","ab"]]', '"ab"', 0, 3),
-- 岛屿数量
(900000000000003611, 900000000000003006, 'ACM', '4 5\n11110\n11010\n11000\n00000', '1', 1, 1),
(900000000000003612, 900000000000003006, 'ACM', '4 5\n11000\n11000\n00100\n00011', '3', 1, 2),
(900000000000003613, 900000000000003006, 'ACM', '3 3\n111\n010\n101', '3', 0, 3),
(900000000000003621, 900000000000003006, 'LEETCODE', '[["11110","11010","11000","00000"]]', '1', 1, 1),
(900000000000003622, 900000000000003006, 'LEETCODE', '[["11000","11000","00100","00011"]]', '3', 1, 2),
(900000000000003623, 900000000000003006, 'LEETCODE', '[["111","010","101"]]', '3', 0, 3),
-- A + B
(900000000000004111, 900000000000004001, 'ACM', '1 2', '3', 1, 1),
(900000000000004112, 900000000000004001, 'ACM', '-1000000000 1000000000', '0', 1, 2),
(900000000000004113, 900000000000004001, 'ACM', '1000000000 1000000000', '2000000000', 0, 3),
(900000000000004121, 900000000000004001, 'LEETCODE', '[1,2]', '3', 1, 1),
(900000000000004122, 900000000000004001, 'LEETCODE', '[-1000000000,1000000000]', '0', 1, 2),
(900000000000004123, 900000000000004001, 'LEETCODE', '[1000000000,1000000000]', '2000000000', 0, 3),
-- 字符串反转
(900000000000004211, 900000000000004002, 'ACM', 'hello world', 'dlrow olleh', 1, 1),
(900000000000004212, 900000000000004002, 'ACM', 'abc', 'cba', 1, 2),
(900000000000004213, 900000000000004002, 'ACM', 'a', 'a', 0, 3),
(900000000000004221, 900000000000004002, 'LEETCODE', '["hello world"]', '"dlrow olleh"', 1, 1),
(900000000000004222, 900000000000004002, 'LEETCODE', '["abc"]', '"cba"', 1, 2),
(900000000000004223, 900000000000004002, 'LEETCODE', '["a"]', '"a"', 0, 3),
-- 数组最大值与下标
(900000000000004311, 900000000000004003, 'ACM', '5\n3 1 4 1 5', '5 4', 1, 1),
(900000000000004312, 900000000000004003, 'ACM', '3\n7 7 7', '7 0', 1, 2),
(900000000000004313, 900000000000004003, 'ACM', '1\n-9', '-9 0', 0, 3),
(900000000000004321, 900000000000004003, 'LEETCODE', '[[3,1,4,1,5]]', '[5,4]', 1, 1),
(900000000000004322, 900000000000004003, 'LEETCODE', '[[7,7,7]]', '[7,0]', 1, 2),
(900000000000004323, 900000000000004003, 'LEETCODE', '[[-9]]', '[-9,0]', 0, 3),
-- 回文串判断
(900000000000004411, 900000000000004004, 'ACM', 'abcba', 'yes', 1, 1),
(900000000000004412, 900000000000004004, 'ACM', 'abc', 'no', 1, 2),
(900000000000004413, 900000000000004004, 'ACM', 'abba', 'yes', 0, 3),
(900000000000004421, 900000000000004004, 'LEETCODE', '["abcba"]', 'true', 1, 1),
(900000000000004422, 900000000000004004, 'LEETCODE', '["abc"]', 'false', 1, 2),
(900000000000004423, 900000000000004004, 'LEETCODE', '["abba"]', 'true', 0, 3);

-- 两种模式都支持后,标题里的「（函数题）」后缀没有意义
UPDATE question_bank SET title = REPLACE(title, '（函数题）', '') WHERE judge_mode = 'CODING';
