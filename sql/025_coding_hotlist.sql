-- ============================================================
-- 编程题常考榜单：Hot100 / 剑指 Offer / CodeTop 公司分类
--   数据来源：codetop.cc 公开接口 + 力扣热题 100 清单
--   策略：CodeTop 总频度 Top 300 ∪ Hot100 ∪ 剑指 Offer，按题号去重
--   已有题目按 slug 回填题单/公司/热度，不重复插入
--   脚本可重复执行
-- ============================================================

USE grindset;

-- ---------- 1. 扩展列 ----------
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

CALL add_column_if_missing('question_bank', 'leetcode_no',
    'ALTER TABLE question_bank ADD COLUMN leetcode_no INT NULL COMMENT ''力扣题号（剑指 Offer 题为空）'' AFTER source_url');
CALL add_column_if_missing('question_bank', 'leetcode_slug',
    'ALTER TABLE question_bank ADD COLUMN leetcode_slug VARCHAR(160) NULL COMMENT ''力扣题目 slug，用于拼原题链接'' AFTER leetcode_no');
CALL add_column_if_missing('question_bank', 'tags',
    'ALTER TABLE question_bank ADD COLUMN tags JSON NULL COMMENT ''题单标签：HOT100/JIANZHI/CODETOP/HIGH_FREQ'' AFTER leetcode_slug');
CALL add_column_if_missing('question_bank', 'companies',
    'ALTER TABLE question_bank ADD COLUMN companies JSON NULL COMMENT ''CodeTop 公司分类'' AFTER tags');
CALL add_column_if_missing('question_bank', 'hot_score',
    'ALTER TABLE question_bank ADD COLUMN hot_score INT NOT NULL DEFAULT 0 COMMENT ''CodeTop 总频度，越大越常考'' AFTER companies');
CALL add_column_if_missing('question_bank', 'last_asked_at',
    'ALTER TABLE question_bank ADD COLUMN last_asked_at DATE NULL COMMENT ''CodeTop 最近考察日期'' AFTER hot_score');

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- leetcode_slug 唯一索引：INSERT ... ON DUPLICATE KEY UPDATE 按 slug 合并，避免重复题
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl_sql = ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('question_bank', 'uk_leetcode_slug',
    'ALTER TABLE question_bank ADD UNIQUE KEY uk_leetcode_slug (leetcode_slug)');

DROP PROCEDURE IF EXISTS add_index_if_missing;

-- ---------- 2. 合并历史重复题（009 与 012 各有一份 两数之和 / 有效的括号） ----------
-- 作答记录直接迁移到保留题
UPDATE question_attempts SET question_id = 900000000000003001 WHERE question_id = 900000000000002001;
UPDATE question_attempts SET question_id = 900000000000003002 WHERE question_id = 900000000000002002;
-- 做题进度合并：次数相加、最高分取大、状态取更严重、最近时间取更晚
-- 同一用户两条进度都存在时，先把旧进度合并进保留题
UPDATE user_question_progress k
JOIN user_question_progress o ON o.user_id = k.user_id AND o.question_id = 900000000000002001
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status)
WHERE k.question_id = 900000000000003001;
-- 已合并的旧行删除
DELETE o FROM user_question_progress o
JOIN user_question_progress k ON k.user_id = o.user_id AND k.question_id = 900000000000003001
WHERE o.question_id = 900000000000002001;
-- 没有冲突的旧行直接换成保留题 ID
UPDATE user_question_progress SET question_id = 900000000000003001 WHERE question_id = 900000000000002001;
-- 同一用户两条进度都存在时，先把旧进度合并进保留题
UPDATE user_question_progress k
JOIN user_question_progress o ON o.user_id = k.user_id AND o.question_id = 900000000000002002
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status)
WHERE k.question_id = 900000000000003002;
-- 已合并的旧行删除
DELETE o FROM user_question_progress o
JOIN user_question_progress k ON k.user_id = o.user_id AND k.question_id = 900000000000003002
WHERE o.question_id = 900000000000002002;
-- 没有冲突的旧行直接换成保留题 ID
UPDATE user_question_progress SET question_id = 900000000000003002 WHERE question_id = 900000000000002002;
DELETE FROM question_bank WHERE id IN (900000000000002001, 900000000000002002);

-- ---------- 3. 回填已有编程题（按 slug 匹配，不重复插入） ----------
UPDATE question_bank SET
    leetcode_no = 1,
    leetcode_slug = 'two-sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 306,
    last_asked_at = '2026-09-07',
    source_url = 'https://leetcode.cn/problems/two-sum/'
WHERE id = 900000000000003001;

UPDATE question_bank SET
    leetcode_no = 20,
    leetcode_slug = 'valid-parentheses',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","图森","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 303,
    last_asked_at = '2026-11-14',
    source_url = 'https://leetcode.cn/problems/valid-parentheses/'
WHERE id = 900000000000003002;

UPDATE question_bank SET
    leetcode_no = 53,
    leetcode_slug = 'maximum-subarray',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 382,
    last_asked_at = '2026-11-02',
    source_url = 'https://leetcode.cn/problems/maximum-subarray/'
WHERE id = 900000000000003003;

UPDATE question_bank SET
    leetcode_no = 70,
    leetcode_slug = 'climbing-stairs',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 136,
    last_asked_at = '2026-10-22',
    source_url = 'https://leetcode.cn/problems/climbing-stairs/'
WHERE id = 900000000000003004;

UPDATE question_bank SET
    leetcode_no = 14,
    leetcode_slug = 'longest-common-prefix',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","字节跳动","小米","微软","快手","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 85,
    last_asked_at = '2026-09-23',
    source_url = 'https://leetcode.cn/problems/longest-common-prefix/'
WHERE id = 900000000000003005;

UPDATE question_bank SET
    leetcode_no = 200,
    leetcode_slug = 'number-of-islands',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 339,
    last_asked_at = '2026-11-20',
    source_url = 'https://leetcode.cn/problems/number-of-islands/'
WHERE id = 900000000000003006;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'max-submatrix-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","美团","腾讯","阿里巴巴"]',
    hot_score = 20,
    last_asked_at = '2026-09-18',
    source_url = 'https://leetcode.cn/problems/max-submatrix-lcci/'
WHERE id = 910000000000000017;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'smallest-k-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","百度","网易","美团","腾讯"]',
    hot_score = 22,
    last_asked_at = '2026-06-05',
    source_url = 'https://leetcode.cn/problems/smallest-k-lcci/'
WHERE id = 910000000000000042;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'hanota-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","美团","腾讯"]',
    hot_score = 14,
    last_asked_at = '2025-09-15',
    source_url = 'https://leetcode.cn/problems/hanota-lcci/'
WHERE id = 910000000000000048;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'qiu-12n-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["快手","百度"]',
    hot_score = 2,
    last_asked_at = '2021-09-19',
    source_url = 'https://leetcode.cn/problems/qiu-12n-lcof/'
WHERE id = 910000000000000071;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'bu-ke-pai-zhong-de-shun-zi-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","拼多多","旷视","滴滴","百度","美团","阿里巴巴"]',
    hot_score = 22,
    last_asked_at = '2025-03-12',
    source_url = 'https://leetcode.cn/problems/bu-ke-pai-zhong-de-shun-zi-lcof/'
WHERE id = 910000000000000075;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'nge-tou-zi-de-dian-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["bilibili","京东","字节跳动","快手","拼多多","阿里巴巴"]',
    hot_score = 10,
    last_asked_at = '2026-06-23',
    source_url = 'https://leetcode.cn/problems/nge-tou-zi-de-dian-shu-lcof/'
WHERE id = 910000000000000077;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'he-wei-sde-lian-xu-zheng-shu-xu-lie-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["华为","字节跳动","拼多多","美团","腾讯"]',
    hot_score = 11,
    last_asked_at = '2026-05-22',
    source_url = 'https://leetcode.cn/problems/he-wei-sde-lian-xu-zheng-shu-xu-lie-lcof/'
WHERE id = 910000000000000092;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","猿辅导"]',
    hot_score = 2,
    last_asked_at = '2022-04-01',
    source_url = 'https://leetcode.cn/problems/cong-shang-dao-xia-da-yin-er-cha-shu-lcof/'
WHERE id = 910000000000000105;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'diao-zheng-shu-zu-shun-xu-shi-qi-shu-wei-yu-ou-shu-qian-mian-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","百度","美团","阿里巴巴"]',
    hot_score = 30,
    last_asked_at = '2022-09-27',
    source_url = 'https://leetcode.cn/problems/diao-zheng-shu-zu-shun-xu-shi-qi-shu-wei-yu-ou-shu-qian-mian-lcof/'
WHERE id = 910000000000000125;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'search-rotate-array-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","滴滴","百度","美团"]',
    hot_score = 14,
    last_asked_at = '2025-08-28',
    source_url = 'https://leetcode.cn/problems/search-rotate-array-lcci/'
WHERE id = 910000000000000153;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'sum-lists-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","滴滴","百度","美团","腾讯"]',
    hot_score = 24,
    last_asked_at = '2025-12-15',
    source_url = 'https://leetcode.cn/problems/sum-lists-lcci/'
WHERE id = 910000000000000167;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'compress-string-lcci',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 14,
    last_asked_at = '2026-07-21',
    source_url = 'https://leetcode.cn/problems/compress-string-lcci/'
WHERE id = 910000000000000192;

UPDATE question_bank SET
    leetcode_no = 1444,
    leetcode_slug = 'number-of-ways-of-cutting-a-pizza',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","图森","字节跳动","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2025-09-26',
    source_url = 'https://leetcode.cn/problems/number-of-ways-of-cutting-a-pizza/'
WHERE id = 910000000000000239;

UPDATE question_bank SET
    leetcode_no = 1262,
    leetcode_slug = 'greatest-sum-divisible-by-three',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","美团","腾讯"]',
    hot_score = 20,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/greatest-sum-divisible-by-three/'
WHERE id = 910000000000000391;

UPDATE question_bank SET
    leetcode_no = 1095,
    leetcode_slug = 'find-in-mountain-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","百度","美团","腾讯"]',
    hot_score = 14,
    last_asked_at = '2024-07-09',
    source_url = 'https://leetcode.cn/problems/find-in-mountain-array/'
WHERE id = 910000000000000547;

UPDATE question_bank SET
    leetcode_no = 1047,
    leetcode_slug = 'remove-all-adjacent-duplicates-in-string',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","美团","腾讯","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2023-08-04',
    source_url = 'https://leetcode.cn/problems/remove-all-adjacent-duplicates-in-string/'
WHERE id = 910000000000000603;

UPDATE question_bank SET
    leetcode_no = 1044,
    leetcode_slug = 'longest-duplicate-substring',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","快手","美团","腾讯","阿里巴巴"]',
    hot_score = 24,
    last_asked_at = '2026-09-02',
    source_url = 'https://leetcode.cn/problems/longest-duplicate-substring/'
WHERE id = 910000000000000609;

UPDATE question_bank SET
    leetcode_no = 1004,
    leetcode_slug = 'max-consecutive-ones-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","微软","快手","拼多多"]',
    hot_score = 29,
    last_asked_at = '2026-08-18',
    source_url = 'https://leetcode.cn/problems/max-consecutive-ones-iii/'
WHERE id = 910000000000000683;

UPDATE question_bank SET
    leetcode_no = 977,
    leetcode_slug = 'squares-of-a-sorted-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","阿里巴巴"]',
    hot_score = 16,
    last_asked_at = '2026-01-19',
    source_url = 'https://leetcode.cn/problems/squares-of-a-sorted-array/'
WHERE id = 910000000000000710;

UPDATE question_bank SET
    leetcode_no = 974,
    leetcode_slug = 'subarray-sums-divisible-by-k',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","腾讯"]',
    hot_score = 14,
    last_asked_at = '2026-08-27',
    source_url = 'https://leetcode.cn/problems/subarray-sums-divisible-by-k/'
WHERE id = 910000000000000713;

UPDATE question_bank SET
    leetcode_no = 509,
    leetcode_slug = 'fibonacci-number',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","快手","百度","美团","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2025-09-24',
    source_url = 'https://leetcode.cn/problems/fibonacci-number/'
WHERE id = 910000000000000715;

UPDATE question_bank SET
    leetcode_no = 958,
    leetcode_slug = 'check-completeness-of-a-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","快手","猿辅导","百度","美团","腾讯","虾皮"]',
    hot_score = 47,
    last_asked_at = '2025-11-07',
    source_url = 'https://leetcode.cn/problems/check-completeness-of-a-binary-tree/'
WHERE id = 910000000000000730;

UPDATE question_bank SET
    leetcode_no = 946,
    leetcode_slug = 'validate-stack-sequences',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","小米","小红书","微软","快手","旷视","百度","美团"]',
    hot_score = 15,
    last_asked_at = '2026-06-01',
    source_url = 'https://leetcode.cn/problems/validate-stack-sequences/'
WHERE id = 910000000000000742;

UPDATE question_bank SET
    leetcode_no = 902,
    leetcode_slug = 'numbers-at-most-n-given-digit-set',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","百度","美团","阿里巴巴"]',
    hot_score = 20,
    last_asked_at = '2026-10-20',
    source_url = 'https://leetcode.cn/problems/numbers-at-most-n-given-digit-set/'
WHERE id = 910000000000000786;

UPDATE question_bank SET
    leetcode_no = 887,
    leetcode_slug = 'super-egg-drop',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","百度","美团","腾讯"]',
    hot_score = 30,
    last_asked_at = '2026-03-30',
    source_url = 'https://leetcode.cn/problems/super-egg-drop/'
WHERE id = 910000000000000801;

UPDATE question_bank SET
    leetcode_no = 528,
    leetcode_slug = 'random-pick-with-weight',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 24,
    last_asked_at = '2026-09-19',
    source_url = 'https://leetcode.cn/problems/random-pick-with-weight/'
WHERE id = 910000000000000812;

UPDATE question_bank SET
    leetcode_no = 876,
    leetcode_slug = 'middle-of-the-linked-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","小米","百度","美团","腾讯","虾皮"]',
    hot_score = 15,
    last_asked_at = '2024-08-31',
    source_url = 'https://leetcode.cn/problems/middle-of-the-linked-list/'
WHERE id = 910000000000000816;

UPDATE question_bank SET
    leetcode_no = 470,
    leetcode_slug = 'implement-rand10-using-rand7',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 97,
    last_asked_at = '2026-09-20',
    source_url = 'https://leetcode.cn/problems/implement-rand10-using-rand7/'
WHERE id = 910000000000000821;

UPDATE question_bank SET
    leetcode_no = 863,
    leetcode_slug = 'all-nodes-distance-k-in-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","字节跳动","微软","美团","腾讯","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2025-09-18',
    source_url = 'https://leetcode.cn/problems/all-nodes-distance-k-in-binary-tree/'
WHERE id = 910000000000000831;

UPDATE question_bank SET
    leetcode_no = 862,
    leetcode_slug = 'shortest-subarray-with-sum-at-least-k',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","腾讯","阿里巴巴"]',
    hot_score = 19,
    last_asked_at = '2026-09-03',
    source_url = 'https://leetcode.cn/problems/shortest-subarray-with-sum-at-least-k/'
WHERE id = 910000000000000832;

UPDATE question_bank SET
    leetcode_no = 622,
    leetcode_slug = 'design-circular-queue',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","拼多多","滴滴","美团","腾讯","阿里巴巴"]',
    hot_score = 18,
    last_asked_at = '2026-01-19',
    source_url = 'https://leetcode.cn/problems/design-circular-queue/'
WHERE id = 910000000000000864;

UPDATE question_bank SET
    leetcode_no = 706,
    leetcode_slug = 'design-hashmap',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","拼多多","携程","百度","腾讯"]',
    hot_score = 15,
    last_asked_at = '2026-03-18',
    source_url = 'https://leetcode.cn/problems/design-hashmap/'
WHERE id = 910000000000000900;

UPDATE question_bank SET
    leetcode_no = 704,
    leetcode_slug = 'binary-search',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 153,
    last_asked_at = '2026-07-30',
    source_url = 'https://leetcode.cn/problems/binary-search/'
WHERE id = 910000000000000925;

UPDATE question_bank SET
    leetcode_no = 426,
    leetcode_slug = 'convert-binary-search-tree-to-sorted-doubly-linked-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","网易","腾讯"]',
    hot_score = 18,
    last_asked_at = '2025-04-28',
    source_url = 'https://leetcode.cn/problems/convert-binary-search-tree-to-sorted-doubly-linked-list/'
WHERE id = 910000000000000959;

UPDATE question_bank SET
    leetcode_no = 718,
    leetcode_slug = 'maximum-length-of-repeated-subarray',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 71,
    last_asked_at = '2026-09-18',
    source_url = 'https://leetcode.cn/problems/maximum-length-of-repeated-subarray/'
WHERE id = 910000000000000999;

UPDATE question_bank SET
    leetcode_no = 695,
    leetcode_slug = 'max-area-of-island',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 97,
    last_asked_at = '2026-11-03',
    source_url = 'https://leetcode.cn/problems/max-area-of-island/'
WHERE id = 910000000000001011;

UPDATE question_bank SET
    leetcode_no = 692,
    leetcode_slug = 'top-k-frequent-words',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 26,
    last_asked_at = '2026-07-23',
    source_url = 'https://leetcode.cn/problems/top-k-frequent-words/'
WHERE id = 910000000000001014;

UPDATE question_bank SET
    leetcode_no = 680,
    leetcode_slug = 'valid-palindrome-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","百度","虾皮"]',
    hot_score = 14,
    last_asked_at = '2026-04-15',
    source_url = 'https://leetcode.cn/problems/valid-palindrome-ii/'
WHERE id = 910000000000001026;

UPDATE question_bank SET
    leetcode_no = 679,
    leetcode_slug = '24-game',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","拼多多","携程","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 30,
    last_asked_at = '2026-09-09',
    source_url = 'https://leetcode.cn/problems/24-game/'
WHERE id = 910000000000001027;

UPDATE question_bank SET
    leetcode_no = 678,
    leetcode_slug = 'valid-parenthesis-string',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","快手","拼多多","百度","网易","腾讯","虾皮","阿里巴巴"]',
    hot_score = 37,
    last_asked_at = '2026-10-24',
    source_url = 'https://leetcode.cn/problems/valid-parenthesis-string/'
WHERE id = 910000000000001028;

UPDATE question_bank SET
    leetcode_no = 674,
    leetcode_slug = 'longest-continuous-increasing-subsequence',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","拼多多","猿辅导","网易","美团","腾讯"]',
    hot_score = 17,
    last_asked_at = '2025-03-31',
    source_url = 'https://leetcode.cn/problems/longest-continuous-increasing-subsequence/'
WHERE id = 910000000000001032;

UPDATE question_bank SET
    leetcode_no = 673,
    leetcode_slug = 'number-of-longest-increasing-subsequence',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯"]',
    hot_score = 28,
    last_asked_at = '2026-05-13',
    source_url = 'https://leetcode.cn/problems/number-of-longest-increasing-subsequence/'
WHERE id = 910000000000001033;

UPDATE question_bank SET
    leetcode_no = 670,
    leetcode_slug = 'maximum-swap',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","腾讯","虾皮","阿里巴巴"]',
    hot_score = 24,
    last_asked_at = '2026-01-01',
    source_url = 'https://leetcode.cn/problems/maximum-swap/'
WHERE id = 910000000000001036;

UPDATE question_bank SET
    leetcode_no = 662,
    leetcode_slug = 'maximum-width-of-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮"]',
    hot_score = 84,
    last_asked_at = '2026-07-18',
    source_url = 'https://leetcode.cn/problems/maximum-width-of-binary-tree/'
WHERE id = 910000000000001044;

UPDATE question_bank SET
    leetcode_no = 647,
    leetcode_slug = 'palindromic-substrings',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","快手","猿辅导","百度","美团","腾讯","虾皮"]',
    hot_score = 24,
    last_asked_at = '2026-04-20',
    source_url = 'https://leetcode.cn/problems/palindromic-substrings/'
WHERE id = 910000000000001059;

UPDATE question_bank SET
    leetcode_no = 628,
    leetcode_slug = 'maximum-product-of-three-numbers',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 13,
    last_asked_at = '2026-08-31',
    source_url = 'https://leetcode.cn/problems/maximum-product-of-three-numbers/'
WHERE id = 910000000000001077;

UPDATE question_bank SET
    leetcode_no = 617,
    leetcode_slug = 'merge-two-binary-trees',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","网易","美团","阿里巴巴"]',
    hot_score = 14,
    last_asked_at = '2025-10-29',
    source_url = 'https://leetcode.cn/problems/merge-two-binary-trees/'
WHERE id = 910000000000001087;

UPDATE question_bank SET
    leetcode_no = 611,
    leetcode_slug = 'valid-triangle-number',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","字节跳动","微软","快手","拼多多","旷视","百度","腾讯","虾皮"]',
    hot_score = 30,
    last_asked_at = '2026-07-23',
    source_url = 'https://leetcode.cn/problems/valid-triangle-number/'
WHERE id = 910000000000001093;

UPDATE question_bank SET
    leetcode_no = 572,
    leetcode_slug = 'subtree-of-another-tree',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","微软","快手","滴滴","猿辅导","百度","腾讯","阿里巴巴"]',
    hot_score = 42,
    last_asked_at = '2026-04-14',
    source_url = 'https://leetcode.cn/problems/subtree-of-another-tree/'
WHERE id = 910000000000001130;

UPDATE question_bank SET
    leetcode_no = 567,
    leetcode_slug = 'permutation-in-string',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","字节跳动","微软","拼多多","腾讯"]',
    hot_score = 20,
    last_asked_at = '2025-03-11',
    source_url = 'https://leetcode.cn/problems/permutation-in-string/'
WHERE id = 910000000000001135;

UPDATE question_bank SET
    leetcode_no = 560,
    leetcode_slug = 'subarray-sum-equals-k',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 84,
    last_asked_at = '2026-10-27',
    source_url = 'https://leetcode.cn/problems/subarray-sum-equals-k/'
WHERE id = 910000000000001142;

UPDATE question_bank SET
    leetcode_no = 557,
    leetcode_slug = 'reverse-words-in-a-string-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2022-08-01',
    source_url = 'https://leetcode.cn/problems/reverse-words-in-a-string-iii/'
WHERE id = 910000000000001143;

UPDATE question_bank SET
    leetcode_no = 556,
    leetcode_slug = 'next-greater-element-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","猿辅导","美团"]',
    hot_score = 20,
    last_asked_at = '2025-03-03',
    source_url = 'https://leetcode.cn/problems/next-greater-element-iii/'
WHERE id = 910000000000001144;

UPDATE question_bank SET
    leetcode_no = 554,
    leetcode_slug = 'brick-wall',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为"]',
    hot_score = 15,
    last_asked_at = '2023-08-29',
    source_url = 'https://leetcode.cn/problems/brick-wall/'
WHERE id = 910000000000001146;

UPDATE question_bank SET
    leetcode_no = 540,
    leetcode_slug = 'single-element-in-a-sorted-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","拼多多","猿辅导","美团","腾讯","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-04-10',
    source_url = 'https://leetcode.cn/problems/single-element-in-a-sorted-array/'
WHERE id = 910000000000001159;

UPDATE question_bank SET
    leetcode_no = 525,
    leetcode_slug = 'contiguous-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","拼多多","腾讯"]',
    hot_score = 17,
    last_asked_at = '2026-08-02',
    source_url = 'https://leetcode.cn/problems/contiguous-array/'
WHERE id = 910000000000001172;

UPDATE question_bank SET
    leetcode_no = 523,
    leetcode_slug = 'continuous-subarray-sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","拼多多","滴滴","百度","腾讯"]',
    hot_score = 15,
    last_asked_at = '2026-05-08',
    source_url = 'https://leetcode.cn/problems/continuous-subarray-sum/'
WHERE id = 910000000000001174;

UPDATE question_bank SET
    leetcode_no = 518,
    leetcode_slug = 'coin-change-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 48,
    last_asked_at = '2026-04-24',
    source_url = 'https://leetcode.cn/problems/coin-change-ii/'
WHERE id = 910000000000001178;

UPDATE question_bank SET
    leetcode_no = 516,
    leetcode_slug = 'longest-palindromic-subsequence',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","快手","拼多多","携程","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 37,
    last_asked_at = '2026-04-26',
    source_url = 'https://leetcode.cn/problems/longest-palindromic-subsequence/'
WHERE id = 910000000000001180;

UPDATE question_bank SET
    leetcode_no = 503,
    leetcode_slug = 'next-greater-element-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","旷视","滴滴","腾讯","虾皮","阿里巴巴"]',
    hot_score = 22,
    last_asked_at = '2026-10-10',
    source_url = 'https://leetcode.cn/problems/next-greater-element-ii/'
WHERE id = 910000000000001191;

UPDATE question_bank SET
    leetcode_no = 498,
    leetcode_slug = 'diagonal-traverse',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","拼多多","旷视","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 48,
    last_asked_at = '2026-04-21',
    source_url = 'https://leetcode.cn/problems/diagonal-traverse/'
WHERE id = 910000000000001196;

UPDATE question_bank SET
    leetcode_no = 494,
    leetcode_slug = 'target-sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","百度","美团","腾讯","虾皮"]',
    hot_score = 25,
    last_asked_at = '2026-07-31',
    source_url = 'https://leetcode.cn/problems/target-sum/'
WHERE id = 910000000000001199;

UPDATE question_bank SET
    leetcode_no = 468,
    leetcode_slug = 'validate-ip-address',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 62,
    last_asked_at = '2026-09-04',
    source_url = 'https://leetcode.cn/problems/validate-ip-address/'
WHERE id = 910000000000001222;

UPDATE question_bank SET
    leetcode_no = 460,
    leetcode_slug = 'lfu-cache',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","图森","字节跳动","小红书","微软","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 58,
    last_asked_at = '2026-06-20',
    source_url = 'https://leetcode.cn/problems/lfu-cache/'
WHERE id = 910000000000001230;

UPDATE question_bank SET
    leetcode_no = 459,
    leetcode_slug = 'repeated-substring-pattern',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","美团","腾讯","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2025-03-25',
    source_url = 'https://leetcode.cn/problems/repeated-substring-pattern/'
WHERE id = 910000000000001231;

UPDATE question_bank SET
    leetcode_no = 450,
    leetcode_slug = 'delete-node-in-a-bst',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 39,
    last_asked_at = '2026-04-23',
    source_url = 'https://leetcode.cn/problems/delete-node-in-a-bst/'
WHERE id = 910000000000001240;

UPDATE question_bank SET
    leetcode_no = 445,
    leetcode_slug = 'add-two-numbers-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 40,
    last_asked_at = '2026-09-23',
    source_url = 'https://leetcode.cn/problems/add-two-numbers-ii/'
WHERE id = 910000000000001245;

UPDATE question_bank SET
    leetcode_no = 443,
    leetcode_slug = 'string-compression',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","猿辅导","腾讯","虾皮","阿里巴巴"]',
    hot_score = 23,
    last_asked_at = '2025-11-06',
    source_url = 'https://leetcode.cn/problems/string-compression/'
WHERE id = 910000000000001247;

UPDATE question_bank SET
    leetcode_no = 442,
    leetcode_slug = 'find-all-duplicates-in-an-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","拼多多","猿辅导","腾讯","虾皮","阿里巴巴"]',
    hot_score = 48,
    last_asked_at = '2026-08-03',
    source_url = 'https://leetcode.cn/problems/find-all-duplicates-in-an-array/'
WHERE id = 910000000000001248;

UPDATE question_bank SET
    leetcode_no = 440,
    leetcode_slug = 'k-th-smallest-in-lexicographical-order',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手"]',
    hot_score = 42,
    last_asked_at = '2026-10-28',
    source_url = 'https://leetcode.cn/problems/k-th-smallest-in-lexicographical-order/'
WHERE id = 910000000000001250;

UPDATE question_bank SET
    leetcode_no = 435,
    leetcode_slug = 'non-overlapping-intervals',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小红书","微软","滴滴","猿辅导","腾讯"]',
    hot_score = 14,
    last_asked_at = '2026-06-23',
    source_url = 'https://leetcode.cn/problems/non-overlapping-intervals/'
WHERE id = 910000000000001255;

UPDATE question_bank SET
    leetcode_no = 415,
    leetcode_slug = 'add-strings',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 246,
    last_asked_at = '2026-09-24',
    source_url = 'https://leetcode.cn/problems/add-strings/'
WHERE id = 910000000000001269;

UPDATE question_bank SET
    leetcode_no = 410,
    leetcode_slug = 'split-array-largest-sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","携程","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 19,
    last_asked_at = '2025-08-28',
    source_url = 'https://leetcode.cn/problems/split-array-largest-sum/'
WHERE id = 910000000000001274;

UPDATE question_bank SET
    leetcode_no = 402,
    leetcode_slug = 'remove-k-digits',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","微软","猿辅导","百度","腾讯","虾皮"]',
    hot_score = 55,
    last_asked_at = '2026-11-09',
    source_url = 'https://leetcode.cn/problems/remove-k-digits/'
WHERE id = 910000000000001282;

UPDATE question_bank SET
    leetcode_no = 400,
    leetcode_slug = 'nth-digit',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","猿辅导","腾讯","阿里巴巴"]',
    hot_score = 29,
    last_asked_at = '2026-04-30',
    source_url = 'https://leetcode.cn/problems/nth-digit/'
WHERE id = 910000000000001284;

UPDATE question_bank SET
    leetcode_no = 395,
    leetcode_slug = 'longest-substring-with-at-least-k-repeating-characters',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","携程","猿辅导","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 28,
    last_asked_at = '2026-09-08',
    source_url = 'https://leetcode.cn/problems/longest-substring-with-at-least-k-repeating-characters/'
WHERE id = 910000000000001289;

UPDATE question_bank SET
    leetcode_no = 386,
    leetcode_slug = 'lexicographical-numbers',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","拼多多","猿辅导","百度","虾皮","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-05-11',
    source_url = 'https://leetcode.cn/problems/lexicographical-numbers/'
WHERE id = 910000000000001298;

UPDATE question_bank SET
    leetcode_no = 384,
    leetcode_slug = 'shuffle-an-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","滴滴","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 33,
    last_asked_at = '2026-06-20',
    source_url = 'https://leetcode.cn/problems/shuffle-an-array/'
WHERE id = 910000000000001300;

UPDATE question_bank SET
    leetcode_no = 378,
    leetcode_slug = 'kth-smallest-element-in-a-sorted-matrix',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小红书","快手","拼多多","滴滴","美团","腾讯","虾皮"]',
    hot_score = 27,
    last_asked_at = '2026-10-09',
    source_url = 'https://leetcode.cn/problems/kth-smallest-element-in-a-sorted-matrix/'
WHERE id = 910000000000001306;

UPDATE question_bank SET
    leetcode_no = 354,
    leetcode_slug = 'russian-doll-envelopes',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","微软","拼多多","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-09-02',
    source_url = 'https://leetcode.cn/problems/russian-doll-envelopes/'
WHERE id = 910000000000001330;

UPDATE question_bank SET
    leetcode_no = 349,
    leetcode_slug = 'intersection-of-two-arrays',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","滴滴","猿辅导","百度","网易","腾讯","虾皮","阿里巴巴"]',
    hot_score = 24,
    last_asked_at = '2025-09-03',
    source_url = 'https://leetcode.cn/problems/intersection-of-two-arrays/'
WHERE id = 910000000000001335;

UPDATE question_bank SET
    leetcode_no = 344,
    leetcode_slug = 'reverse-string',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","快手","拼多多","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 25,
    last_asked_at = '2026-11-19',
    source_url = 'https://leetcode.cn/problems/reverse-string/'
WHERE id = 910000000000001340;

UPDATE question_bank SET
    leetcode_no = 343,
    leetcode_slug = 'integer-break',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","商汤","字节跳动","滴滴","猿辅导","百度","美团","腾讯"]',
    hot_score = 23,
    last_asked_at = '2026-06-04',
    source_url = 'https://leetcode.cn/problems/integer-break/'
WHERE id = 910000000000001341;

UPDATE question_bank SET
    leetcode_no = 340,
    leetcode_slug = 'longest-substring-with-at-most-k-distinct-characters',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","猿辅导","腾讯"]',
    hot_score = 14,
    last_asked_at = '2026-05-14',
    source_url = 'https://leetcode.cn/problems/longest-substring-with-at-most-k-distinct-characters/'
WHERE id = 910000000000001344;

UPDATE question_bank SET
    leetcode_no = 337,
    leetcode_slug = 'house-robber-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","图森","字节跳动","微软","快手","美团","腾讯"]',
    hot_score = 20,
    last_asked_at = '2026-05-13',
    source_url = 'https://leetcode.cn/problems/house-robber-iii/'
WHERE id = 910000000000001347;

UPDATE question_bank SET
    leetcode_no = 329,
    leetcode_slug = 'longest-increasing-path-in-a-matrix',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","微软","快手","拼多多","猿辅导","百度","腾讯"]',
    hot_score = 49,
    last_asked_at = '2026-11-04',
    source_url = 'https://leetcode.cn/problems/longest-increasing-path-in-a-matrix/'
WHERE id = 910000000000001355;

UPDATE question_bank SET
    leetcode_no = 328,
    leetcode_slug = 'odd-even-linked-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小米","微软","快手","旷视","滴滴","猿辅导","美团","腾讯","阿里巴巴"]',
    hot_score = 35,
    last_asked_at = '2026-04-17',
    source_url = 'https://leetcode.cn/problems/odd-even-linked-list/'
WHERE id = 910000000000001356;

UPDATE question_bank SET
    leetcode_no = 316,
    leetcode_slug = 'remove-duplicate-letters',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 28,
    last_asked_at = '2026-11-03',
    source_url = 'https://leetcode.cn/problems/remove-duplicate-letters/'
WHERE id = 910000000000001368;

UPDATE question_bank SET
    leetcode_no = 306,
    leetcode_slug = 'additive-number',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","猿辅导","腾讯","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-09-03',
    source_url = 'https://leetcode.cn/problems/additive-number/'
WHERE id = 910000000000001378;

UPDATE question_bank SET
    leetcode_no = 301,
    leetcode_slug = 'remove-invalid-parentheses',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","腾讯","阿里巴巴"]',
    hot_score = 18,
    last_asked_at = '2025-05-17',
    source_url = 'https://leetcode.cn/problems/remove-invalid-parentheses/'
WHERE id = 910000000000001383;

UPDATE question_bank SET
    leetcode_no = 297,
    leetcode_slug = 'serialize-and-deserialize-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 59,
    last_asked_at = '2026-05-25',
    source_url = 'https://leetcode.cn/problems/serialize-and-deserialize-binary-tree/'
WHERE id = 910000000000001387;

UPDATE question_bank SET
    leetcode_no = 268,
    leetcode_slug = 'missing-number',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 16,
    last_asked_at = '2023-08-22',
    source_url = 'https://leetcode.cn/problems/missing-number/'
WHERE id = 910000000000001416;

UPDATE question_bank SET
    leetcode_no = 264,
    leetcode_slug = 'ugly-number-ii',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 25,
    last_asked_at = '2025-10-23',
    source_url = 'https://leetcode.cn/problems/ugly-number-ii/'
WHERE id = 910000000000001420;

UPDATE question_bank SET
    leetcode_no = 260,
    leetcode_slug = 'single-number-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","美团","腾讯","虾皮"]',
    hot_score = 20,
    last_asked_at = '2026-05-27',
    source_url = 'https://leetcode.cn/problems/single-number-iii/'
WHERE id = 910000000000001424;

UPDATE question_bank SET
    leetcode_no = 257,
    leetcode_slug = 'binary-tree-paths',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2026-05-25',
    source_url = 'https://leetcode.cn/problems/binary-tree-paths/'
WHERE id = 910000000000001427;

UPDATE question_bank SET
    leetcode_no = 253,
    leetcode_slug = 'meeting-rooms-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","微软","快手","拼多多","腾讯","虾皮"]',
    hot_score = 28,
    last_asked_at = '2026-09-08',
    source_url = 'https://leetcode.cn/problems/meeting-rooms-ii/'
WHERE id = 910000000000001431;

UPDATE question_bank SET
    leetcode_no = 235,
    leetcode_slug = 'lowest-common-ancestor-of-a-binary-search-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","快手","拼多多","滴滴","猿辅导","腾讯","阿里巴巴"]',
    hot_score = 16,
    last_asked_at = '2026-03-19',
    source_url = 'https://leetcode.cn/problems/lowest-common-ancestor-of-a-binary-search-tree/'
WHERE id = 910000000000001449;

UPDATE question_bank SET
    leetcode_no = 233,
    leetcode_slug = 'number-of-digit-one',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","旷视","腾讯","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-08-15',
    source_url = 'https://leetcode.cn/problems/number-of-digit-one/'
WHERE id = 910000000000001451;

UPDATE question_bank SET
    leetcode_no = 232,
    leetcode_slug = 'implement-queue-using-stacks',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小米","小红书","微软","快手","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 143,
    last_asked_at = '2026-03-09',
    source_url = 'https://leetcode.cn/problems/implement-queue-using-stacks/'
WHERE id = 910000000000001452;

UPDATE question_bank SET
    leetcode_no = 227,
    leetcode_slug = 'basic-calculator-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","微软","快手","拼多多","携程","猿辅导","百度","网易","美团","腾讯","虾皮"]',
    hot_score = 70,
    last_asked_at = '2026-11-11',
    source_url = 'https://leetcode.cn/problems/basic-calculator-ii/'
WHERE id = 910000000000001457;

UPDATE question_bank SET
    leetcode_no = 225,
    leetcode_slug = 'implement-stack-using-queues',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 36,
    last_asked_at = '2026-03-26',
    source_url = 'https://leetcode.cn/problems/implement-stack-using-queues/'
WHERE id = 910000000000001459;

UPDATE question_bank SET
    leetcode_no = 213,
    leetcode_slug = 'house-robber-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","商汤","字节跳动","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 42,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/house-robber-ii/'
WHERE id = 910000000000001471;

UPDATE question_bank SET
    leetcode_no = 204,
    leetcode_slug = 'count-primes',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","百度","美团","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-03-11',
    source_url = 'https://leetcode.cn/problems/count-primes/'
WHERE id = 910000000000001480;

UPDATE question_bank SET
    leetcode_no = 179,
    leetcode_slug = 'largest-number',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 85,
    last_asked_at = '2026-09-24',
    source_url = 'https://leetcode.cn/problems/largest-number/'
WHERE id = 910000000000001505;

UPDATE question_bank SET
    leetcode_no = 168,
    leetcode_slug = 'excel-sheet-column-title',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","携程","猿辅导","百度","腾讯","虾皮","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2024-03-19',
    source_url = 'https://leetcode.cn/problems/excel-sheet-column-title/'
WHERE id = 910000000000001516;

UPDATE question_bank SET
    leetcode_no = 165,
    leetcode_slug = 'compare-version-numbers',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 167,
    last_asked_at = '2026-09-06',
    source_url = 'https://leetcode.cn/problems/compare-version-numbers/'
WHERE id = 910000000000001519;

UPDATE question_bank SET
    leetcode_no = 154,
    leetcode_slug = 'find-minimum-in-rotated-sorted-array-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","百度","腾讯","虾皮"]',
    hot_score = 20,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/find-minimum-in-rotated-sorted-array-ii/'
WHERE id = 910000000000001530;

UPDATE question_bank SET
    leetcode_no = 145,
    leetcode_slug = 'binary-tree-postorder-traversal',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 42,
    last_asked_at = '2026-03-31',
    source_url = 'https://leetcode.cn/problems/binary-tree-postorder-traversal/'
WHERE id = 910000000000001539;

UPDATE question_bank SET
    leetcode_no = 144,
    leetcode_slug = 'binary-tree-preorder-traversal',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 84,
    last_asked_at = '2025-04-18',
    source_url = 'https://leetcode.cn/problems/binary-tree-preorder-traversal/'
WHERE id = 910000000000001540;

UPDATE question_bank SET
    leetcode_no = 143,
    leetcode_slug = 'reorder-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","商汤","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 259,
    last_asked_at = '2026-11-04',
    source_url = 'https://leetcode.cn/problems/reorder-list/'
WHERE id = 910000000000001541;

UPDATE question_bank SET
    leetcode_no = 115,
    leetcode_slug = 'distinct-subsequences',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 21,
    last_asked_at = '2026-09-23',
    source_url = 'https://leetcode.cn/problems/distinct-subsequences/'
WHERE id = 910000000000001569;

UPDATE question_bank SET
    leetcode_no = 113,
    leetcode_slug = 'path-sum-ii',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮"]',
    hot_score = 78,
    last_asked_at = '2026-07-24',
    source_url = 'https://leetcode.cn/problems/path-sum-ii/'
WHERE id = 910000000000001571;

UPDATE question_bank SET
    leetcode_no = 111,
    leetcode_slug = 'minimum-depth-of-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","小米","快手","猿辅导","美团","腾讯","阿里巴巴"]',
    hot_score = 23,
    last_asked_at = '2025-12-18',
    source_url = 'https://leetcode.cn/problems/minimum-depth-of-binary-tree/'
WHERE id = 910000000000001573;

UPDATE question_bank SET
    leetcode_no = 110,
    leetcode_slug = 'balanced-binary-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 90,
    last_asked_at = '2026-10-19',
    source_url = 'https://leetcode.cn/problems/balanced-binary-tree/'
WHERE id = 910000000000001574;

UPDATE question_bank SET
    leetcode_no = 109,
    leetcode_slug = 'convert-sorted-list-to-binary-search-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小红书","微软","快手","拼多多","猿辅导","阿里巴巴"]',
    hot_score = 14,
    last_asked_at = '2026-01-25',
    source_url = 'https://leetcode.cn/problems/convert-sorted-list-to-binary-search-tree/'
WHERE id = 910000000000001575;

UPDATE question_bank SET
    leetcode_no = 107,
    leetcode_slug = 'binary-tree-level-order-traversal-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","小米","拼多多","滴滴","百度","美团","腾讯"]',
    hot_score = 16,
    last_asked_at = '2026-08-06',
    source_url = 'https://leetcode.cn/problems/binary-tree-level-order-traversal-ii/'
WHERE id = 910000000000001577;

UPDATE question_bank SET
    leetcode_no = 96,
    leetcode_slug = 'unique-binary-search-trees',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","快手","百度","美团"]',
    hot_score = 32,
    last_asked_at = '2026-04-20',
    source_url = 'https://leetcode.cn/problems/unique-binary-search-trees/'
WHERE id = 910000000000001588;

UPDATE question_bank SET
    leetcode_no = 93,
    leetcode_slug = 'restore-ip-addresses',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 188,
    last_asked_at = '2026-09-15',
    source_url = 'https://leetcode.cn/problems/restore-ip-addresses/'
WHERE id = 910000000000001591;

UPDATE question_bank SET
    leetcode_no = 91,
    leetcode_slug = 'decode-ways',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","华为","商汤","字节跳动","微软","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 43,
    last_asked_at = '2026-09-22',
    source_url = 'https://leetcode.cn/problems/decode-ways/'
WHERE id = 910000000000001593;

UPDATE question_bank SET
    leetcode_no = 85,
    leetcode_slug = 'maximal-rectangle',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","华为","商汤","图森","字节跳动","微软","快手","腾讯","阿里巴巴"]',
    hot_score = 31,
    last_asked_at = '2026-10-15',
    source_url = 'https://leetcode.cn/problems/maximal-rectangle/'
WHERE id = 910000000000001599;

UPDATE question_bank SET
    leetcode_no = 83,
    leetcode_slug = 'remove-duplicates-from-sorted-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","小红书","微软","快手","携程","旷视","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 69,
    last_asked_at = '2026-04-23',
    source_url = 'https://leetcode.cn/problems/remove-duplicates-from-sorted-list/'
WHERE id = 910000000000001601;

UPDATE question_bank SET
    leetcode_no = 59,
    leetcode_slug = 'spiral-matrix-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮"]',
    hot_score = 42,
    last_asked_at = '2026-08-12',
    source_url = 'https://leetcode.cn/problems/spiral-matrix-ii/'
WHERE id = 910000000000001625;

UPDATE question_bank SET
    leetcode_no = 47,
    leetcode_slug = 'permutations-ii',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","华为","图森","字节跳动","小米","微软","快手","滴滴","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 58,
    last_asked_at = '2026-09-16',
    source_url = 'https://leetcode.cn/problems/permutations-ii/'
WHERE id = 910000000000001637;

UPDATE question_bank SET
    leetcode_no = 44,
    leetcode_slug = 'wildcard-matching',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 32,
    last_asked_at = '2026-08-11',
    source_url = 'https://leetcode.cn/problems/wildcard-matching/'
WHERE id = 910000000000001640;

UPDATE question_bank SET
    leetcode_no = 43,
    leetcode_slug = 'multiply-strings',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","图森","字节跳动","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 134,
    last_asked_at = '2026-10-19',
    source_url = 'https://leetcode.cn/problems/multiply-strings/'
WHERE id = 910000000000001641;

UPDATE question_bank SET
    leetcode_no = 40,
    leetcode_slug = 'combination-sum-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮"]',
    hot_score = 55,
    last_asked_at = '2026-01-05',
    source_url = 'https://leetcode.cn/problems/combination-sum-ii/'
WHERE id = 910000000000001644;

UPDATE question_bank SET
    leetcode_no = 37,
    leetcode_slug = 'sudoku-solver',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小米","微软","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 20,
    last_asked_at = '2025-04-23',
    source_url = 'https://leetcode.cn/problems/sudoku-solver/'
WHERE id = 910000000000001647;

UPDATE question_bank SET
    leetcode_no = 29,
    leetcode_slug = 'divide-two-integers',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","百度","美团","腾讯"]',
    hot_score = 13,
    last_asked_at = '2026-10-26',
    source_url = 'https://leetcode.cn/problems/divide-two-integers/'
WHERE id = 910000000000001655;

UPDATE question_bank SET
    leetcode_no = 18,
    leetcode_slug = '4sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","滴滴","美团","腾讯","阿里巴巴"]',
    hot_score = 23,
    last_asked_at = '2026-06-17',
    source_url = 'https://leetcode.cn/problems/4sum/'
WHERE id = 910000000000001666;

UPDATE question_bank SET
    leetcode_no = 16,
    leetcode_slug = '3sum-closest',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","拼多多","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 55,
    last_asked_at = '2026-07-27',
    source_url = 'https://leetcode.cn/problems/3sum-closest/'
WHERE id = 910000000000001668;

UPDATE question_bank SET
    leetcode_no = 10,
    leetcode_slug = 'regular-expression-matching',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","微软","快手","拼多多","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 40,
    last_asked_at = '2026-04-29',
    source_url = 'https://leetcode.cn/problems/regular-expression-matching/'
WHERE id = 910000000000001674;

UPDATE question_bank SET
    leetcode_no = 8,
    leetcode_slug = 'string-to-integer-atoi',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 134,
    last_asked_at = '2026-08-24',
    source_url = 'https://leetcode.cn/problems/string-to-integer-atoi/'
WHERE id = 910000000000001676;

UPDATE question_bank SET
    leetcode_no = 7,
    leetcode_slug = 'reverse-integer',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","快手","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 45,
    last_asked_at = '2026-03-30',
    source_url = 'https://leetcode.cn/problems/reverse-integer/'
WHERE id = 910000000000001677;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'sort-an-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 361,
    last_asked_at = '2026-11-19',
    source_url = 'https://leetcode.cn/problems/sort-an-array/'
WHERE id = 910000000000001906;

UPDATE question_bank SET
    leetcode_no = 49,
    leetcode_slug = 'group-anagrams',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","百度","美团","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2026-06-05',
    source_url = 'https://leetcode.cn/problems/group-anagrams/'
WHERE id = 930000000000000000;

UPDATE question_bank SET
    leetcode_no = 128,
    leetcode_slug = 'longest-consecutive-sequence',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 98,
    last_asked_at = '2026-11-20',
    source_url = 'https://leetcode.cn/problems/longest-consecutive-sequence/'
WHERE id = 930000000000000001;

UPDATE question_bank SET
    leetcode_no = 242,
    leetcode_slug = 'valid-anagram',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小米","微软","猿辅导","网易","美团","腾讯"]',
    hot_score = 18,
    last_asked_at = '2026-04-16',
    source_url = 'https://leetcode.cn/problems/valid-anagram/'
WHERE id = 930000000000000002;

UPDATE question_bank SET
    leetcode_no = 202,
    leetcode_slug = 'happy-number',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","快手","美团","腾讯","阿里巴巴"]',
    hot_score = 12,
    last_asked_at = '2025-05-21',
    source_url = 'https://leetcode.cn/problems/happy-number/'
WHERE id = 930000000000000003;

UPDATE question_bank SET
    leetcode_no = 219,
    leetcode_slug = 'contains-duplicate-ii',
    tags = '["CODETOP"]',
    companies = '["图森","字节跳动"]',
    hot_score = 2,
    last_asked_at = '2026-07-08',
    source_url = 'https://leetcode.cn/problems/contains-duplicate-ii/'
WHERE id = 930000000000000004;

UPDATE question_bank SET
    leetcode_no = 205,
    leetcode_slug = 'isomorphic-strings',
    tags = '["CODETOP"]',
    companies = '["字节跳动","百度","美团","阿里巴巴"]',
    hot_score = 5,
    last_asked_at = '2024-07-10',
    source_url = 'https://leetcode.cn/problems/isomorphic-strings/'
WHERE id = 930000000000000005;

UPDATE question_bank SET
    leetcode_no = 290,
    leetcode_slug = 'word-pattern',
    tags = '["CODETOP"]',
    companies = '["字节跳动","美团","阿里巴巴"]',
    hot_score = 3,
    last_asked_at = '2023-10-18',
    source_url = 'https://leetcode.cn/problems/word-pattern/'
WHERE id = 930000000000000006;

UPDATE question_bank SET
    leetcode_no = 383,
    leetcode_slug = 'ransom-note',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动"]',
    hot_score = 2,
    last_asked_at = '2022-04-16',
    source_url = 'https://leetcode.cn/problems/ransom-note/'
WHERE id = 930000000000000007;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shu-zu-zhong-zhong-fu-de-shu-zi-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","美团","腾讯","虾皮"]',
    hot_score = 21,
    last_asked_at = '2026-06-10',
    source_url = 'https://leetcode.cn/problems/shu-zu-zhong-zhong-fu-de-shu-zi-lcof/'
WHERE id = 930000000000000008;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'di-yi-ge-zhi-chu-xian-yi-ci-de-zi-fu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","滴滴","百度","美团","腾讯"]',
    hot_score = 12,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/di-yi-ge-zhi-chu-xian-yi-ci-de-zi-fu-lcof/'
WHERE id = 930000000000000009;

UPDATE question_bank SET
    leetcode_no = 283,
    leetcode_slug = 'move-zeroes',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 68,
    last_asked_at = '2026-09-09',
    source_url = 'https://leetcode.cn/problems/move-zeroes/'
WHERE id = 930000000000000010;

UPDATE question_bank SET
    leetcode_no = 11,
    leetcode_slug = 'container-with-most-water',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 59,
    last_asked_at = '2026-11-13',
    source_url = 'https://leetcode.cn/problems/container-with-most-water/'
WHERE id = 930000000000000011;

UPDATE question_bank SET
    leetcode_no = 15,
    leetcode_slug = '3sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 498,
    last_asked_at = '2026-10-26',
    source_url = 'https://leetcode.cn/problems/3sum/'
WHERE id = 930000000000000012;

UPDATE question_bank SET
    leetcode_no = 42,
    leetcode_slug = 'trapping-rain-water',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 202,
    last_asked_at = '2026-11-24',
    source_url = 'https://leetcode.cn/problems/trapping-rain-water/'
WHERE id = 930000000000000013;

UPDATE question_bank SET
    leetcode_no = 125,
    leetcode_slug = 'valid-palindrome',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["图森","字节跳动","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 43,
    last_asked_at = '2026-04-25',
    source_url = 'https://leetcode.cn/problems/valid-palindrome/'
WHERE id = 930000000000000014;

UPDATE question_bank SET
    leetcode_no = 392,
    leetcode_slug = 'is-subsequence',
    tags = '["CODETOP"]',
    companies = '["bilibili","拼多多","百度","腾讯","虾皮","阿里巴巴"]',
    hot_score = 10,
    last_asked_at = '2026-01-16',
    source_url = 'https://leetcode.cn/problems/is-subsequence/'
WHERE id = 930000000000000015;

UPDATE question_bank SET
    leetcode_no = 167,
    leetcode_slug = 'two-sum-ii-input-array-is-sorted',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","小米","快手","百度","美团","腾讯","虾皮"]',
    hot_score = 17,
    last_asked_at = '2024-12-05',
    source_url = 'https://leetcode.cn/problems/two-sum-ii-input-array-is-sorted/'
WHERE id = 930000000000000016;

UPDATE question_bank SET
    leetcode_no = 27,
    leetcode_slug = 'remove-element',
    tags = '["CODETOP"]',
    companies = '["字节跳动","美团"]',
    hot_score = 5,
    last_asked_at = '2023-11-20',
    source_url = 'https://leetcode.cn/problems/remove-element/'
WHERE id = 930000000000000017;

UPDATE question_bank SET
    leetcode_no = 26,
    leetcode_slug = 'remove-duplicates-from-sorted-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","商汤","图森","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 47,
    last_asked_at = '2026-03-19',
    source_url = 'https://leetcode.cn/problems/remove-duplicates-from-sorted-array/'
WHERE id = 930000000000000018;

UPDATE question_bank SET
    leetcode_no = 80,
    leetcode_slug = 'remove-duplicates-from-sorted-array-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","猿辅导","百度","美团","虾皮"]',
    hot_score = 21,
    last_asked_at = '2026-10-27',
    source_url = 'https://leetcode.cn/problems/remove-duplicates-from-sorted-array-ii/'
WHERE id = 930000000000000019;

UPDATE question_bank SET
    leetcode_no = 88,
    leetcode_slug = 'merge-sorted-array',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 306,
    last_asked_at = '2026-11-20',
    source_url = 'https://leetcode.cn/problems/merge-sorted-array/'
WHERE id = 930000000000000020;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'he-wei-sde-liang-ge-shu-zi-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["百度","美团"]',
    hot_score = 2,
    last_asked_at = '2021-10-11',
    source_url = 'https://leetcode.cn/problems/he-wei-sde-liang-ge-shu-zi-lcof/'
WHERE id = 930000000000000021;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'zuo-xuan-zhuan-zi-fu-chuan-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","美团"]',
    hot_score = 2,
    last_asked_at = '2020-09-22',
    source_url = 'https://leetcode.cn/problems/zuo-xuan-zhuan-zi-fu-chuan-lcof/'
WHERE id = 930000000000000023;

UPDATE question_bank SET
    leetcode_no = 151,
    leetcode_slug = 'reverse-words-in-a-string',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 107,
    last_asked_at = '2026-09-07',
    source_url = 'https://leetcode.cn/problems/reverse-words-in-a-string/'
WHERE id = 930000000000000024;

UPDATE question_bank SET
    leetcode_no = 3,
    leetcode_slug = 'longest-substring-without-repeating-characters',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 1232,
    last_asked_at = '2026-11-23',
    source_url = 'https://leetcode.cn/problems/longest-substring-without-repeating-characters/'
WHERE id = 930000000000000025;

UPDATE question_bank SET
    leetcode_no = 438,
    leetcode_slug = 'find-all-anagrams-in-a-string',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","百度"]',
    hot_score = 16,
    last_asked_at = '2026-11-18',
    source_url = 'https://leetcode.cn/problems/find-all-anagrams-in-a-string/'
WHERE id = 930000000000000026;

UPDATE question_bank SET
    leetcode_no = 76,
    leetcode_slug = 'minimum-window-substring',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 136,
    last_asked_at = '2026-08-30',
    source_url = 'https://leetcode.cn/problems/minimum-window-substring/'
WHERE id = 930000000000000027;

UPDATE question_bank SET
    leetcode_no = 209,
    leetcode_slug = 'minimum-size-subarray-sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","小红书","微软","快手","滴滴","百度","美团","腾讯"]',
    hot_score = 74,
    last_asked_at = '2026-07-24',
    source_url = 'https://leetcode.cn/problems/minimum-size-subarray-sum/'
WHERE id = 930000000000000028;

UPDATE question_bank SET
    leetcode_no = 30,
    leetcode_slug = 'substring-with-concatenation-of-all-words',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","虾皮","阿里巴巴"]',
    hot_score = 10,
    last_asked_at = '2026-10-24',
    source_url = 'https://leetcode.cn/problems/substring-with-concatenation-of-all-words/'
WHERE id = 930000000000000029;

UPDATE question_bank SET
    leetcode_no = 239,
    leetcode_slug = 'sliding-window-maximum',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 160,
    last_asked_at = '2026-10-29',
    source_url = 'https://leetcode.cn/problems/sliding-window-maximum/'
WHERE id = 930000000000000030;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'ti-huan-kong-ge-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["京东","字节跳动","美团"]',
    hot_score = 3,
    last_asked_at = '2021-09-14',
    source_url = 'https://leetcode.cn/problems/ti-huan-kong-ge-lcof/'
WHERE id = 930000000000000031;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'biao-shi-shu-zhi-de-zi-fu-chuan-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","快手"]',
    hot_score = 2,
    last_asked_at = '2022-04-28',
    source_url = 'https://leetcode.cn/problems/biao-shi-shu-zhi-de-zi-fu-chuan-lcof/'
WHERE id = 930000000000000032;

UPDATE question_bank SET
    leetcode_no = 6,
    leetcode_slug = 'zigzag-conversion',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","百度","美团"]',
    hot_score = 20,
    last_asked_at = '2026-04-03',
    source_url = 'https://leetcode.cn/problems/zigzag-conversion/'
WHERE id = 930000000000000034;

UPDATE question_bank SET
    leetcode_no = 58,
    leetcode_slug = 'length-of-last-word',
    tags = '["CODETOP"]',
    companies = '["bilibili","字节跳动","小米","网易"]',
    hot_score = 4,
    last_asked_at = '2025-04-15',
    source_url = 'https://leetcode.cn/problems/length-of-last-word/'
WHERE id = 930000000000000035;

UPDATE question_bank SET
    leetcode_no = 12,
    leetcode_slug = 'integer-to-roman',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","美团","腾讯"]',
    hot_score = 24,
    last_asked_at = '2026-07-08',
    source_url = 'https://leetcode.cn/problems/integer-to-roman/'
WHERE id = 930000000000000036;

UPDATE question_bank SET
    leetcode_no = 13,
    leetcode_slug = 'roman-to-integer',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","微软","旷视","猿辅导","网易"]',
    hot_score = 15,
    last_asked_at = '2025-09-25',
    source_url = 'https://leetcode.cn/problems/roman-to-integer/'
WHERE id = 930000000000000037;

UPDATE question_bank SET
    leetcode_no = 68,
    leetcode_slug = 'text-justification',
    tags = '["CODETOP"]',
    companies = '["字节跳动","拼多多","美团","腾讯","阿里巴巴"]',
    hot_score = 13,
    last_asked_at = '2026-07-29',
    source_url = 'https://leetcode.cn/problems/text-justification/'
WHERE id = 930000000000000039;

UPDATE question_bank SET
    leetcode_no = 160,
    leetcode_slug = 'intersection-of-two-linked-lists',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 203,
    last_asked_at = '2026-09-18',
    source_url = 'https://leetcode.cn/problems/intersection-of-two-linked-lists/'
WHERE id = 930000000000000040;

UPDATE question_bank SET
    leetcode_no = 206,
    leetcode_slug = 'reverse-linked-list',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 764,
    last_asked_at = '2026-11-27',
    source_url = 'https://leetcode.cn/problems/reverse-linked-list/'
WHERE id = 930000000000000041;

UPDATE question_bank SET
    leetcode_no = 234,
    leetcode_slug = 'palindrome-linked-list',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","图森","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 88,
    last_asked_at = '2026-10-23',
    source_url = 'https://leetcode.cn/problems/palindrome-linked-list/'
WHERE id = 930000000000000042;

UPDATE question_bank SET
    leetcode_no = 141,
    leetcode_slug = 'linked-list-cycle',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 259,
    last_asked_at = '2026-09-28',
    source_url = 'https://leetcode.cn/problems/linked-list-cycle/'
WHERE id = 930000000000000043;

UPDATE question_bank SET
    leetcode_no = 142,
    leetcode_slug = 'linked-list-cycle-ii',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 173,
    last_asked_at = '2026-11-19',
    source_url = 'https://leetcode.cn/problems/linked-list-cycle-ii/'
WHERE id = 930000000000000044;

UPDATE question_bank SET
    leetcode_no = 21,
    leetcode_slug = 'merge-two-sorted-lists',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 337,
    last_asked_at = '2026-11-19',
    source_url = 'https://leetcode.cn/problems/merge-two-sorted-lists/'
WHERE id = 930000000000000045;

UPDATE question_bank SET
    leetcode_no = 2,
    leetcode_slug = 'add-two-numbers',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 133,
    last_asked_at = '2026-04-16',
    source_url = 'https://leetcode.cn/problems/add-two-numbers/'
WHERE id = 930000000000000046;

UPDATE question_bank SET
    leetcode_no = 19,
    leetcode_slug = 'remove-nth-node-from-end-of-list',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 190,
    last_asked_at = '2026-10-28',
    source_url = 'https://leetcode.cn/problems/remove-nth-node-from-end-of-list/'
WHERE id = 930000000000000047;

UPDATE question_bank SET
    leetcode_no = 24,
    leetcode_slug = 'swap-nodes-in-pairs',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 73,
    last_asked_at = '2026-06-11',
    source_url = 'https://leetcode.cn/problems/swap-nodes-in-pairs/'
WHERE id = 930000000000000048;

UPDATE question_bank SET
    leetcode_no = 25,
    leetcode_slug = 'reverse-nodes-in-k-group',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 542,
    last_asked_at = '2026-11-24',
    source_url = 'https://leetcode.cn/problems/reverse-nodes-in-k-group/'
WHERE id = 930000000000000049;

UPDATE question_bank SET
    leetcode_no = 138,
    leetcode_slug = 'copy-list-with-random-pointer',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","微软","快手","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 59,
    last_asked_at = '2026-09-07',
    source_url = 'https://leetcode.cn/problems/copy-list-with-random-pointer/'
WHERE id = 930000000000000050;

UPDATE question_bank SET
    leetcode_no = 148,
    leetcode_slug = 'sort-list',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","商汤","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 149,
    last_asked_at = '2026-09-28',
    source_url = 'https://leetcode.cn/problems/sort-list/'
WHERE id = 930000000000000051;

UPDATE question_bank SET
    leetcode_no = 23,
    leetcode_slug = 'merge-k-sorted-lists',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 264,
    last_asked_at = '2026-10-24',
    source_url = 'https://leetcode.cn/problems/merge-k-sorted-lists/'
WHERE id = 930000000000000052;

UPDATE question_bank SET
    leetcode_no = 92,
    leetcode_slug = 'reverse-linked-list-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 273,
    last_asked_at = '2026-11-18',
    source_url = 'https://leetcode.cn/problems/reverse-linked-list-ii/'
WHERE id = 930000000000000053;

UPDATE question_bank SET
    leetcode_no = 82,
    leetcode_slug = 'remove-duplicates-from-sorted-list-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","商汤","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 188,
    last_asked_at = '2026-11-23',
    source_url = 'https://leetcode.cn/problems/remove-duplicates-from-sorted-list-ii/'
WHERE id = 930000000000000054;

UPDATE question_bank SET
    leetcode_no = 61,
    leetcode_slug = 'rotate-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 51,
    last_asked_at = '2026-04-20',
    source_url = 'https://leetcode.cn/problems/rotate-list/'
WHERE id = 930000000000000055;

UPDATE question_bank SET
    leetcode_no = 86,
    leetcode_slug = 'partition-list',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","微软","猿辅导","百度","美团","腾讯"]',
    hot_score = 22,
    last_asked_at = '2026-03-23',
    source_url = 'https://leetcode.cn/problems/partition-list/'
WHERE id = 930000000000000056;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'cong-wei-dao-tou-da-yin-lian-biao-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","阿里巴巴"]',
    hot_score = 2,
    last_asked_at = '2022-02-20',
    source_url = 'https://leetcode.cn/problems/cong-wei-dao-tou-da-yin-lian-biao-lcof/'
WHERE id = 930000000000000057;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'lian-biao-zhong-dao-shu-di-kge-jie-dian-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","小米","微软","快手","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 103,
    last_asked_at = '2026-08-04',
    source_url = 'https://leetcode.cn/problems/lian-biao-zhong-dao-shu-di-kge-jie-dian-lcof/'
WHERE id = 930000000000000058;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shan-chu-lian-biao-de-jie-dian-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","快手","猿辅导"]',
    hot_score = 4,
    last_asked_at = '2020-08-29',
    source_url = 'https://leetcode.cn/problems/shan-chu-lian-biao-de-jie-dian-lcof/'
WHERE id = 930000000000000059;

UPDATE question_bank SET
    leetcode_no = 94,
    leetcode_slug = 'binary-tree-inorder-traversal',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 144,
    last_asked_at = '2026-05-21',
    source_url = 'https://leetcode.cn/problems/binary-tree-inorder-traversal/'
WHERE id = 930000000000000060;

UPDATE question_bank SET
    leetcode_no = 104,
    leetcode_slug = 'maximum-depth-of-binary-tree',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 92,
    last_asked_at = '2026-09-14',
    source_url = 'https://leetcode.cn/problems/maximum-depth-of-binary-tree/'
WHERE id = 930000000000000061;

UPDATE question_bank SET
    leetcode_no = 226,
    leetcode_slug = 'invert-binary-tree',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 68,
    last_asked_at = '2026-03-30',
    source_url = 'https://leetcode.cn/problems/invert-binary-tree/'
WHERE id = 930000000000000062;

UPDATE question_bank SET
    leetcode_no = 101,
    leetcode_slug = 'symmetric-tree',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","快手","拼多多","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 97,
    last_asked_at = '2026-07-13',
    source_url = 'https://leetcode.cn/problems/symmetric-tree/'
WHERE id = 930000000000000063;

UPDATE question_bank SET
    leetcode_no = 543,
    leetcode_slug = 'diameter-of-binary-tree',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","小米","微软","快手","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 83,
    last_asked_at = '2026-11-20',
    source_url = 'https://leetcode.cn/problems/diameter-of-binary-tree/'
WHERE id = 930000000000000064;

UPDATE question_bank SET
    leetcode_no = 102,
    leetcode_slug = 'binary-tree-level-order-traversal',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 334,
    last_asked_at = '2026-10-23',
    source_url = 'https://leetcode.cn/problems/binary-tree-level-order-traversal/'
WHERE id = 930000000000000065;

UPDATE question_bank SET
    leetcode_no = 108,
    leetcode_slug = 'convert-sorted-array-to-binary-search-tree',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["字节跳动","微软","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 18,
    last_asked_at = '2026-09-09',
    source_url = 'https://leetcode.cn/problems/convert-sorted-array-to-binary-search-tree/'
WHERE id = 930000000000000066;

UPDATE question_bank SET
    leetcode_no = 98,
    leetcode_slug = 'validate-binary-search-tree',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 84,
    last_asked_at = '2026-04-20',
    source_url = 'https://leetcode.cn/problems/validate-binary-search-tree/'
WHERE id = 930000000000000067;

UPDATE question_bank SET
    leetcode_no = 230,
    leetcode_slug = 'kth-smallest-element-in-a-bst',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","猿辅导","百度","美团","腾讯","虾皮"]',
    hot_score = 33,
    last_asked_at = '2024-04-10',
    source_url = 'https://leetcode.cn/problems/kth-smallest-element-in-a-bst/'
WHERE id = 930000000000000068;

UPDATE question_bank SET
    leetcode_no = 199,
    leetcode_slug = 'binary-tree-right-side-view',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 161,
    last_asked_at = '2026-05-28',
    source_url = 'https://leetcode.cn/problems/binary-tree-right-side-view/'
WHERE id = 930000000000000069;

UPDATE question_bank SET
    leetcode_no = 114,
    leetcode_slug = 'flatten-binary-tree-to-linked-list',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","微软","快手","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 47,
    last_asked_at = '2026-06-15',
    source_url = 'https://leetcode.cn/problems/flatten-binary-tree-to-linked-list/'
WHERE id = 930000000000000070;

UPDATE question_bank SET
    leetcode_no = 105,
    leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 118,
    last_asked_at = '2026-10-08',
    source_url = 'https://leetcode.cn/problems/construct-binary-tree-from-preorder-and-inorder-traversal/'
WHERE id = 930000000000000071;

UPDATE question_bank SET
    leetcode_no = 437,
    leetcode_slug = 'path-sum-iii',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","猿辅导","网易","腾讯"]',
    hot_score = 15,
    last_asked_at = '2026-10-08',
    source_url = 'https://leetcode.cn/problems/path-sum-iii/'
WHERE id = 930000000000000072;

UPDATE question_bank SET
    leetcode_no = 236,
    leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 273,
    last_asked_at = '2026-11-22',
    source_url = 'https://leetcode.cn/problems/lowest-common-ancestor-of-a-binary-tree/'
WHERE id = 930000000000000073;

UPDATE question_bank SET
    leetcode_no = 124,
    leetcode_slug = 'binary-tree-maximum-path-sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 187,
    last_asked_at = '2026-10-13',
    source_url = 'https://leetcode.cn/problems/binary-tree-maximum-path-sum/'
WHERE id = 930000000000000074;

UPDATE question_bank SET
    leetcode_no = 100,
    leetcode_slug = 'same-tree',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","快手","滴滴","百度","腾讯","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2024-04-26',
    source_url = 'https://leetcode.cn/problems/same-tree/'
WHERE id = 930000000000000075;

UPDATE question_bank SET
    leetcode_no = 106,
    leetcode_slug = 'construct-binary-tree-from-inorder-and-postorder-traversal',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","旷视","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 34,
    last_asked_at = '2026-03-27',
    source_url = 'https://leetcode.cn/problems/construct-binary-tree-from-inorder-and-postorder-traversal/'
WHERE id = 930000000000000076;

UPDATE question_bank SET
    leetcode_no = 117,
    leetcode_slug = 'populating-next-right-pointers-in-each-node-ii',
    tags = '["CODETOP"]',
    companies = '["字节跳动","微软","拼多多","美团","腾讯","虾皮"]',
    hot_score = 11,
    last_asked_at = '2026-09-01',
    source_url = 'https://leetcode.cn/problems/populating-next-right-pointers-in-each-node-ii/'
WHERE id = 930000000000000077;

UPDATE question_bank SET
    leetcode_no = 112,
    leetcode_slug = 'path-sum',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","快手","携程","滴滴","猿辅导","美团","腾讯","虾皮"]',
    hot_score = 72,
    last_asked_at = '2026-04-14',
    source_url = 'https://leetcode.cn/problems/path-sum/'
WHERE id = 930000000000000078;

UPDATE question_bank SET
    leetcode_no = 129,
    leetcode_slug = 'sum-root-to-leaf-numbers',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","小米","微软","快手","百度","网易","美团","腾讯"]',
    hot_score = 100,
    last_asked_at = '2026-04-22',
    source_url = 'https://leetcode.cn/problems/sum-root-to-leaf-numbers/'
WHERE id = 930000000000000079;

UPDATE question_bank SET
    leetcode_no = 222,
    leetcode_slug = 'count-complete-tree-nodes',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 18,
    last_asked_at = '2024-11-02',
    source_url = 'https://leetcode.cn/problems/count-complete-tree-nodes/'
WHERE id = 930000000000000080;

UPDATE question_bank SET
    leetcode_no = 637,
    leetcode_slug = 'average-of-levels-in-binary-tree',
    tags = '["CODETOP"]',
    companies = '["字节跳动","微软"]',
    hot_score = 2,
    last_asked_at = '2021-04-28',
    source_url = 'https://leetcode.cn/problems/average-of-levels-in-binary-tree/'
WHERE id = 930000000000000081;

UPDATE question_bank SET
    leetcode_no = 103,
    leetcode_slug = 'binary-tree-zigzag-level-order-traversal',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","滴滴","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 272,
    last_asked_at = '2026-09-16',
    source_url = 'https://leetcode.cn/problems/binary-tree-zigzag-level-order-traversal/'
WHERE id = 930000000000000082;

UPDATE question_bank SET
    leetcode_no = 530,
    leetcode_slug = 'minimum-absolute-difference-in-bst',
    tags = '["CODETOP"]',
    companies = '["字节跳动","猿辅导","腾讯","阿里巴巴"]',
    hot_score = 8,
    last_asked_at = '2022-02-23',
    source_url = 'https://leetcode.cn/problems/minimum-absolute-difference-in-bst/'
WHERE id = 930000000000000083;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'ping-heng-er-cha-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动"]',
    hot_score = 2,
    last_asked_at = '2022-03-30',
    source_url = 'https://leetcode.cn/problems/ping-heng-er-cha-shu-lcof/'
WHERE id = 930000000000000085;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'er-cha-sou-suo-shu-yu-shuang-xiang-lian-biao-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","商汤","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 47,
    last_asked_at = '2026-02-06',
    source_url = 'https://leetcode.cn/problems/er-cha-sou-suo-shu-yu-shuang-xiang-lian-biao-lcof/'
WHERE id = 930000000000000087;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'er-cha-sou-suo-shu-de-hou-xu-bian-li-xu-lie-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","拼多多","滴滴","网易","美团","虾皮"]',
    hot_score = 17,
    last_asked_at = '2025-09-23',
    source_url = 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-hou-xu-bian-li-xu-lie-lcof/'
WHERE id = 930000000000000089;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'er-cha-sou-suo-shu-de-zui-jin-gong-gong-zu-xian-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动"]',
    hot_score = 2,
    last_asked_at = '2022-04-19',
    source_url = 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-zui-jin-gong-gong-zu-xian-lcof/'
WHERE id = 930000000000000090;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'er-cha-sou-suo-shu-de-di-kda-jie-dian-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","微软","猿辅导","腾讯","虾皮","阿里巴巴"]',
    hot_score = 39,
    last_asked_at = '2024-03-25',
    source_url = 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-di-kda-jie-dian-lcof/'
WHERE id = 930000000000000091;

UPDATE question_bank SET
    leetcode_no = 994,
    leetcode_slug = 'rotting-oranges',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","小红书","微软","拼多多","滴滴","腾讯","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-04-21',
    source_url = 'https://leetcode.cn/problems/rotting-oranges/'
WHERE id = 930000000000000092;

UPDATE question_bank SET
    leetcode_no = 207,
    leetcode_slug = 'course-schedule',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","美团","腾讯","阿里巴巴"]',
    hot_score = 69,
    last_asked_at = '2026-10-11',
    source_url = 'https://leetcode.cn/problems/course-schedule/'
WHERE id = 930000000000000093;

UPDATE question_bank SET
    leetcode_no = 210,
    leetcode_slug = 'course-schedule-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","微软","快手","滴滴","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 35,
    last_asked_at = '2026-11-17',
    source_url = 'https://leetcode.cn/problems/course-schedule-ii/'
WHERE id = 930000000000000094;

UPDATE question_bank SET
    leetcode_no = 130,
    leetcode_slug = 'surrounded-regions',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小红书","微软","快手","猿辅导","百度","网易","阿里巴巴"]',
    hot_score = 17,
    last_asked_at = '2025-09-28',
    source_url = 'https://leetcode.cn/problems/surrounded-regions/'
WHERE id = 930000000000000095;

UPDATE question_bank SET
    leetcode_no = 133,
    leetcode_slug = 'clone-graph',
    tags = '["CODETOP"]',
    companies = '["字节跳动","微软","快手","百度","网易","阿里巴巴"]',
    hot_score = 11,
    last_asked_at = '2026-07-13',
    source_url = 'https://leetcode.cn/problems/clone-graph/'
WHERE id = 930000000000000096;

UPDATE question_bank SET
    leetcode_no = 399,
    leetcode_slug = 'evaluate-division',
    tags = '["CODETOP"]',
    companies = '["字节跳动","美团","阿里巴巴"]',
    hot_score = 5,
    last_asked_at = '2025-04-22',
    source_url = 'https://leetcode.cn/problems/evaluate-division/'
WHERE id = 930000000000000097;

UPDATE question_bank SET
    leetcode_no = 127,
    leetcode_slug = 'word-ladder',
    tags = '["CODETOP"]',
    companies = '["亚马逊","华为","字节跳动","腾讯","虾皮","阿里巴巴"]',
    hot_score = 12,
    last_asked_at = '2026-08-04',
    source_url = 'https://leetcode.cn/problems/word-ladder/'
WHERE id = 930000000000000100;

UPDATE question_bank SET
    leetcode_no = 212,
    leetcode_slug = 'word-search-ii',
    tags = '["CODETOP"]',
    companies = '["字节跳动","拼多多","腾讯"]',
    hot_score = 6,
    last_asked_at = '2026-04-29',
    source_url = 'https://leetcode.cn/problems/word-search-ii/'
WHERE id = 930000000000000101;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'ji-qi-ren-de-yun-dong-fan-wei-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["微软","快手","百度","美团"]',
    hot_score = 4,
    last_asked_at = '2024-04-02',
    source_url = 'https://leetcode.cn/problems/ji-qi-ren-de-yun-dong-fan-wei-lcof/'
WHERE id = 930000000000000102;

UPDATE question_bank SET
    leetcode_no = 46,
    leetcode_slug = 'permutations',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 306,
    last_asked_at = '2026-10-27',
    source_url = 'https://leetcode.cn/problems/permutations/'
WHERE id = 930000000000000103;

UPDATE question_bank SET
    leetcode_no = 78,
    leetcode_slug = 'subsets',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","华为","商汤","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 108,
    last_asked_at = '2026-08-27',
    source_url = 'https://leetcode.cn/problems/subsets/'
WHERE id = 930000000000000104;

UPDATE question_bank SET
    leetcode_no = 17,
    leetcode_slug = 'letter-combinations-of-a-phone-number',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯"]',
    hot_score = 28,
    last_asked_at = '2026-10-23',
    source_url = 'https://leetcode.cn/problems/letter-combinations-of-a-phone-number/'
WHERE id = 930000000000000105;

UPDATE question_bank SET
    leetcode_no = 39,
    leetcode_slug = 'combination-sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 96,
    last_asked_at = '2026-05-27',
    source_url = 'https://leetcode.cn/problems/combination-sum/'
WHERE id = 930000000000000106;

UPDATE question_bank SET
    leetcode_no = 22,
    leetcode_slug = 'generate-parentheses',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 153,
    last_asked_at = '2026-09-07',
    source_url = 'https://leetcode.cn/problems/generate-parentheses/'
WHERE id = 930000000000000107;

UPDATE question_bank SET
    leetcode_no = 79,
    leetcode_slug = 'word-search',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","商汤","字节跳动","小米","微软","快手","拼多多","携程","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 61,
    last_asked_at = '2026-06-03',
    source_url = 'https://leetcode.cn/problems/word-search/'
WHERE id = 930000000000000108;

UPDATE question_bank SET
    leetcode_no = 131,
    leetcode_slug = 'palindrome-partitioning',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["字节跳动","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2025-07-17',
    source_url = 'https://leetcode.cn/problems/palindrome-partitioning/'
WHERE id = 930000000000000109;

UPDATE question_bank SET
    leetcode_no = 51,
    leetcode_slug = 'n-queens',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","快手","滴滴","美团","腾讯","虾皮"]',
    hot_score = 27,
    last_asked_at = '2026-08-10',
    source_url = 'https://leetcode.cn/problems/n-queens/'
WHERE id = 930000000000000110;

UPDATE question_bank SET
    leetcode_no = 77,
    leetcode_slug = 'combinations',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","商汤","字节跳动","微软","拼多多","腾讯","虾皮"]',
    hot_score = 16,
    last_asked_at = '2025-06-10',
    source_url = 'https://leetcode.cn/problems/combinations/'
WHERE id = 930000000000000111;

UPDATE question_bank SET
    leetcode_no = 52,
    leetcode_slug = 'n-queens-ii',
    tags = '["CODETOP"]',
    companies = '["华为","猿辅导","百度","腾讯","虾皮"]',
    hot_score = 5,
    last_asked_at = '2024-11-19',
    source_url = 'https://leetcode.cn/problems/n-queens-ii/'
WHERE id = 930000000000000112;

UPDATE question_bank SET
    leetcode_no = 35,
    leetcode_slug = 'search-insert-position',
    tags = '["CODETOP","HOT100"]',
    companies = '["字节跳动","微软","快手","腾讯"]',
    hot_score = 8,
    last_asked_at = '2025-07-24',
    source_url = 'https://leetcode.cn/problems/search-insert-position/'
WHERE id = 930000000000000114;

UPDATE question_bank SET
    leetcode_no = 74,
    leetcode_slug = 'search-a-2d-matrix',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 48,
    last_asked_at = '2026-09-16',
    source_url = 'https://leetcode.cn/problems/search-a-2d-matrix/'
WHERE id = 930000000000000115;

UPDATE question_bank SET
    leetcode_no = 162,
    leetcode_slug = 'find-peak-element',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小米","微软","拼多多","旷视","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 80,
    last_asked_at = '2026-04-19',
    source_url = 'https://leetcode.cn/problems/find-peak-element/'
WHERE id = 930000000000000116;

UPDATE question_bank SET
    leetcode_no = 33,
    leetcode_slug = 'search-in-rotated-sorted-array',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","图森","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 324,
    last_asked_at = '2026-11-27',
    source_url = 'https://leetcode.cn/problems/search-in-rotated-sorted-array/'
WHERE id = 930000000000000117;

UPDATE question_bank SET
    leetcode_no = 34,
    leetcode_slug = 'find-first-and-last-position-of-element-in-sorted-array',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 104,
    last_asked_at = '2026-10-12',
    source_url = 'https://leetcode.cn/problems/find-first-and-last-position-of-element-in-sorted-array/'
WHERE id = 930000000000000118;

UPDATE question_bank SET
    leetcode_no = 153,
    leetcode_slug = 'find-minimum-in-rotated-sorted-array',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 59,
    last_asked_at = '2026-10-19',
    source_url = 'https://leetcode.cn/problems/find-minimum-in-rotated-sorted-array/'
WHERE id = 930000000000000119;

UPDATE question_bank SET
    leetcode_no = 4,
    leetcode_slug = 'median-of-two-sorted-arrays',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","商汤","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 175,
    last_asked_at = '2026-09-28',
    source_url = 'https://leetcode.cn/problems/median-of-two-sorted-arrays/'
WHERE id = 930000000000000120;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'zai-pai-xu-shu-zu-zhong-cha-zhao-shu-zi-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","商汤","图森","字节跳动","快手","美团","阿里巴巴"]',
    hot_score = 19,
    last_asked_at = '2026-07-17',
    source_url = 'https://leetcode.cn/problems/zai-pai-xu-shu-zu-zhong-cha-zhao-shu-zi-lcof/'
WHERE id = 930000000000000124;

UPDATE question_bank SET
    leetcode_no = 69,
    leetcode_slug = 'sqrtx',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 150,
    last_asked_at = '2026-09-21',
    source_url = 'https://leetcode.cn/problems/sqrtx/'
WHERE id = 930000000000000125;

UPDATE question_bank SET
    leetcode_no = 50,
    leetcode_slug = 'powx-n',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 43,
    last_asked_at = '2026-03-24',
    source_url = 'https://leetcode.cn/problems/powx-n/'
WHERE id = 930000000000000126;

UPDATE question_bank SET
    leetcode_no = 394,
    leetcode_slug = 'decode-string',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 103,
    last_asked_at = '2026-11-03',
    source_url = 'https://leetcode.cn/problems/decode-string/'
WHERE id = 930000000000000127;

UPDATE question_bank SET
    leetcode_no = 739,
    leetcode_slug = 'daily-temperatures',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","微软","拼多多","滴滴","美团","腾讯","虾皮"]',
    hot_score = 65,
    last_asked_at = '2026-11-12',
    source_url = 'https://leetcode.cn/problems/daily-temperatures/'
WHERE id = 930000000000000128;

UPDATE question_bank SET
    leetcode_no = 84,
    leetcode_slug = 'largest-rectangle-in-histogram',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","微软","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 26,
    last_asked_at = '2026-03-02',
    source_url = 'https://leetcode.cn/problems/largest-rectangle-in-histogram/'
WHERE id = 930000000000000129;

UPDATE question_bank SET
    leetcode_no = 71,
    leetcode_slug = 'simplify-path',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","拼多多","猿辅导","腾讯","阿里巴巴"]',
    hot_score = 24,
    last_asked_at = '2026-03-11',
    source_url = 'https://leetcode.cn/problems/simplify-path/'
WHERE id = 930000000000000130;

UPDATE question_bank SET
    leetcode_no = 150,
    leetcode_slug = 'evaluate-reverse-polish-notation',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","小红书","微软","快手","拼多多","百度","美团","腾讯"]',
    hot_score = 16,
    last_asked_at = '2026-05-19',
    source_url = 'https://leetcode.cn/problems/evaluate-reverse-polish-notation/'
WHERE id = 930000000000000131;

UPDATE question_bank SET
    leetcode_no = 224,
    leetcode_slug = 'basic-calculator',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","字节跳动","微软","快手","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 57,
    last_asked_at = '2025-12-24',
    source_url = 'https://leetcode.cn/problems/basic-calculator/'
WHERE id = 930000000000000132;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'yong-liang-ge-zhan-shi-xian-dui-lie-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","快手","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 48,
    last_asked_at = '2026-10-10',
    source_url = 'https://leetcode.cn/problems/yong-liang-ge-zhan-shi-xian-dui-lie-lcof/'
WHERE id = 930000000000000133;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'zhan-de-ya-ru-dan-chu-xu-lie-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","微软","网易","美团","虾皮","阿里巴巴"]',
    hot_score = 13,
    last_asked_at = '2024-04-29',
    source_url = 'https://leetcode.cn/problems/zhan-de-ya-ru-dan-chu-xu-lie-lcof/'
WHERE id = 930000000000000134;

UPDATE question_bank SET
    leetcode_no = 215,
    leetcode_slug = 'kth-largest-element-in-an-array',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 609,
    last_asked_at = '2026-11-25',
    source_url = 'https://leetcode.cn/problems/kth-largest-element-in-an-array/'
WHERE id = 930000000000000135;

UPDATE question_bank SET
    leetcode_no = 347,
    leetcode_slug = 'top-k-frequent-elements',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小红书","微软","快手","拼多多","旷视","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 47,
    last_asked_at = '2026-09-23',
    source_url = 'https://leetcode.cn/problems/top-k-frequent-elements/'
WHERE id = 930000000000000136;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'zui-xiao-de-kge-shu-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","小米","微软","快手","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 40,
    last_asked_at = '2026-02-11',
    source_url = 'https://leetcode.cn/problems/zui-xiao-de-kge-shu-lcof/'
WHERE id = 930000000000000137;

UPDATE question_bank SET
    leetcode_no = 502,
    leetcode_slug = 'ipo',
    tags = '["CODETOP"]',
    companies = '["字节跳动"]',
    hot_score = 1,
    last_asked_at = '2023-02-04',
    source_url = 'https://leetcode.cn/problems/ipo/'
WHERE id = 930000000000000138;

UPDATE question_bank SET
    leetcode_no = 373,
    leetcode_slug = 'find-k-pairs-with-smallest-sums',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","快手","腾讯"]',
    hot_score = 8,
    last_asked_at = '2026-10-29',
    source_url = 'https://leetcode.cn/problems/find-k-pairs-with-smallest-sums/'
WHERE id = 930000000000000139;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shu-zu-zhong-de-ni-xu-dui-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","微软","快手","百度","美团","腾讯","虾皮"]',
    hot_score = 54,
    last_asked_at = '2026-10-15',
    source_url = 'https://leetcode.cn/problems/shu-zu-zhong-de-ni-xu-dui-lcof/'
WHERE id = 930000000000000140;

UPDATE question_bank SET
    leetcode_no = 121,
    leetcode_slug = 'best-time-to-buy-and-sell-stock',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 282,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock/'
WHERE id = 930000000000000141;

UPDATE question_bank SET
    leetcode_no = 122,
    leetcode_slug = 'best-time-to-buy-and-sell-stock-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","字节跳动","小米","微软","快手","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 91,
    last_asked_at = '2026-06-04',
    source_url = 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-ii/'
WHERE id = 930000000000000142;

UPDATE question_bank SET
    leetcode_no = 55,
    leetcode_slug = 'jump-game',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 57,
    last_asked_at = '2026-09-08',
    source_url = 'https://leetcode.cn/problems/jump-game/'
WHERE id = 930000000000000143;

UPDATE question_bank SET
    leetcode_no = 45,
    leetcode_slug = 'jump-game-ii',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 40,
    last_asked_at = '2026-11-27',
    source_url = 'https://leetcode.cn/problems/jump-game-ii/'
WHERE id = 930000000000000144;

UPDATE question_bank SET
    leetcode_no = 134,
    leetcode_slug = 'gas-station',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","图森","字节跳动","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 27,
    last_asked_at = '2026-04-24',
    source_url = 'https://leetcode.cn/problems/gas-station/'
WHERE id = 930000000000000145;

UPDATE question_bank SET
    leetcode_no = 135,
    leetcode_slug = 'candy',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","微软","快手","拼多多","网易","腾讯","虾皮","阿里巴巴"]',
    hot_score = 45,
    last_asked_at = '2026-10-28',
    source_url = 'https://leetcode.cn/problems/candy/'
WHERE id = 930000000000000146;

UPDATE question_bank SET
    leetcode_no = 452,
    leetcode_slug = 'minimum-number-of-arrows-to-burst-balloons',
    tags = '["CODETOP"]',
    companies = '["字节跳动","快手","百度","美团","虾皮"]',
    hot_score = 12,
    last_asked_at = '2026-09-02',
    source_url = 'https://leetcode.cn/problems/minimum-number-of-arrows-to-burst-balloons/'
WHERE id = 930000000000000147;

UPDATE question_bank SET
    leetcode_no = 763,
    leetcode_slug = 'partition-labels',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","美团","阿里巴巴"]',
    hot_score = 22,
    last_asked_at = '2026-01-13',
    source_url = 'https://leetcode.cn/problems/partition-labels/'
WHERE id = 930000000000000148;

UPDATE question_bank SET
    leetcode_no = 274,
    leetcode_slug = 'h-index',
    tags = '["CODETOP"]',
    companies = '["字节跳动","拼多多","美团","虾皮","阿里巴巴"]',
    hot_score = 5,
    last_asked_at = '2026-06-03',
    source_url = 'https://leetcode.cn/problems/h-index/'
WHERE id = 930000000000000149;

UPDATE question_bank SET
    leetcode_no = 56,
    leetcode_slug = 'merge-intervals',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 251,
    last_asked_at = '2026-11-13',
    source_url = 'https://leetcode.cn/problems/merge-intervals/'
WHERE id = 930000000000000150;

UPDATE question_bank SET
    leetcode_no = 189,
    leetcode_slug = 'rotate-array',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["字节跳动","小米","微软","快手","滴滴","猿辅导","美团","腾讯","阿里巴巴"]',
    hot_score = 34,
    last_asked_at = '2026-05-16',
    source_url = 'https://leetcode.cn/problems/rotate-array/'
WHERE id = 930000000000000151;

UPDATE question_bank SET
    leetcode_no = 238,
    leetcode_slug = 'product-of-array-except-self',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","拼多多","旷视","百度","腾讯","虾皮","阿里巴巴"]',
    hot_score = 15,
    last_asked_at = '2026-03-31',
    source_url = 'https://leetcode.cn/problems/product-of-array-except-self/'
WHERE id = 930000000000000152;

UPDATE question_bank SET
    leetcode_no = 41,
    leetcode_slug = 'first-missing-positive',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 116,
    last_asked_at = '2026-10-23',
    source_url = 'https://leetcode.cn/problems/first-missing-positive/'
WHERE id = 930000000000000153;

UPDATE question_bank SET
    leetcode_no = 169,
    leetcode_slug = 'majority-element',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","小米","快手","拼多多","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 68,
    last_asked_at = '2026-03-19',
    source_url = 'https://leetcode.cn/problems/majority-element/'
WHERE id = 930000000000000154;

UPDATE question_bank SET
    leetcode_no = 228,
    leetcode_slug = 'summary-ranges',
    tags = '["CODETOP"]',
    companies = '["腾讯"]',
    hot_score = 1,
    last_asked_at = '2021-12-23',
    source_url = 'https://leetcode.cn/problems/summary-ranges/'
WHERE id = 930000000000000155;

UPDATE question_bank SET
    leetcode_no = 57,
    leetcode_slug = 'insert-interval',
    tags = '["CODETOP"]',
    companies = '["亚马逊","华为","字节跳动","快手","滴滴","猿辅导","腾讯"]',
    hot_score = 10,
    last_asked_at = '2026-01-26',
    source_url = 'https://leetcode.cn/problems/insert-interval/'
WHERE id = 930000000000000156;

UPDATE question_bank SET
    leetcode_no = 75,
    leetcode_slug = 'sort-colors',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","华为","字节跳动","小米","小红书","微软","快手","滴滴","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 49,
    last_asked_at = '2026-04-20',
    source_url = 'https://leetcode.cn/problems/sort-colors/'
WHERE id = 930000000000000157;

UPDATE question_bank SET
    leetcode_no = 31,
    leetcode_slug = 'next-permutation',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 137,
    last_asked_at = '2026-08-05',
    source_url = 'https://leetcode.cn/problems/next-permutation/'
WHERE id = 930000000000000158;

UPDATE question_bank SET
    leetcode_no = 287,
    leetcode_slug = 'find-the-duplicate-number',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["商汤","字节跳动","微软","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 39,
    last_asked_at = '2026-10-30',
    source_url = 'https://leetcode.cn/problems/find-the-duplicate-number/'
WHERE id = 930000000000000159;

UPDATE question_bank SET
    leetcode_no = 32,
    leetcode_slug = 'longest-valid-parentheses',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","华为","图森","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 151,
    last_asked_at = '2026-10-28',
    source_url = 'https://leetcode.cn/problems/longest-valid-parentheses/'
WHERE id = 930000000000000160;

UPDATE question_bank SET
    leetcode_no = 118,
    leetcode_slug = 'pascals-triangle',
    tags = '["CODETOP","HOT100"]',
    companies = '["亚马逊","字节跳动","快手","百度","美团","腾讯","虾皮"]',
    hot_score = 11,
    last_asked_at = '2025-09-03',
    source_url = 'https://leetcode.cn/problems/pascals-triangle/'
WHERE id = 930000000000000161;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'ba-shu-zu-pai-cheng-zui-xiao-de-shu-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    hot_score = 20,
    last_asked_at = '2026-04-09',
    source_url = 'https://leetcode.cn/problems/ba-shu-zu-pai-cheng-zui-xiao-de-shu-lcof/'
WHERE id = 930000000000000162;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'da-yin-cong-1dao-zui-da-de-nwei-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","拼多多","美团","腾讯"]',
    hot_score = 7,
    last_asked_at = '2025-08-04',
    source_url = 'https://leetcode.cn/problems/da-yin-cong-1dao-zui-da-de-nwei-shu-lcof/'
WHERE id = 930000000000000164;

UPDATE question_bank SET
    leetcode_no = 73,
    leetcode_slug = 'set-matrix-zeroes',
    tags = '["CODETOP","HOT100"]',
    companies = '["字节跳动","微软","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 11,
    last_asked_at = '2025-03-19',
    source_url = 'https://leetcode.cn/problems/set-matrix-zeroes/'
WHERE id = 930000000000000165;

UPDATE question_bank SET
    leetcode_no = 54,
    leetcode_slug = 'spiral-matrix',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 262,
    last_asked_at = '2026-10-15',
    source_url = 'https://leetcode.cn/problems/spiral-matrix/'
WHERE id = 930000000000000166;

UPDATE question_bank SET
    leetcode_no = 48,
    leetcode_slug = 'rotate-image',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 85,
    last_asked_at = '2026-08-20',
    source_url = 'https://leetcode.cn/problems/rotate-image/'
WHERE id = 930000000000000167;

UPDATE question_bank SET
    leetcode_no = 240,
    leetcode_slug = 'search-a-2d-matrix-ii',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 85,
    last_asked_at = '2026-07-21',
    source_url = 'https://leetcode.cn/problems/search-a-2d-matrix-ii/'
WHERE id = 930000000000000168;

UPDATE question_bank SET
    leetcode_no = 36,
    leetcode_slug = 'valid-sudoku',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","微软","快手","携程","百度","虾皮"]',
    hot_score = 11,
    last_asked_at = '2025-11-10',
    source_url = 'https://leetcode.cn/problems/valid-sudoku/'
WHERE id = 930000000000000169;

UPDATE question_bank SET
    leetcode_no = 289,
    leetcode_slug = 'game-of-life',
    tags = '["CODETOP"]',
    companies = '["华为"]',
    hot_score = 1,
    last_asked_at = '2022-08-17',
    source_url = 'https://leetcode.cn/problems/game-of-life/'
WHERE id = 930000000000000170;

UPDATE question_bank SET
    leetcode_no = 198,
    leetcode_slug = 'house-robber',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","微软","快手","拼多多","携程","滴滴","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 77,
    last_asked_at = '2026-10-17',
    source_url = 'https://leetcode.cn/problems/house-robber/'
WHERE id = 930000000000000171;

UPDATE question_bank SET
    leetcode_no = 139,
    leetcode_slug = 'word-break',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","字节跳动","微软","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 72,
    last_asked_at = '2026-10-15',
    source_url = 'https://leetcode.cn/problems/word-break/'
WHERE id = 930000000000000172;

UPDATE question_bank SET
    leetcode_no = 322,
    leetcode_slug = 'coin-change',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 136,
    last_asked_at = '2026-11-02',
    source_url = 'https://leetcode.cn/problems/coin-change/'
WHERE id = 930000000000000173;

UPDATE question_bank SET
    leetcode_no = 300,
    leetcode_slug = 'longest-increasing-subsequence',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 276,
    last_asked_at = '2026-10-29',
    source_url = 'https://leetcode.cn/problems/longest-increasing-subsequence/'
WHERE id = 930000000000000174;

UPDATE question_bank SET
    leetcode_no = 279,
    leetcode_slug = 'perfect-squares',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","滴滴","美团","腾讯"]',
    hot_score = 24,
    last_asked_at = '2026-03-20',
    source_url = 'https://leetcode.cn/problems/perfect-squares/'
WHERE id = 930000000000000175;

UPDATE question_bank SET
    leetcode_no = 152,
    leetcode_slug = 'maximum-product-subarray',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 91,
    last_asked_at = '2026-10-20',
    source_url = 'https://leetcode.cn/problems/maximum-product-subarray/'
WHERE id = 930000000000000176;

UPDATE question_bank SET
    leetcode_no = 416,
    leetcode_slug = 'partition-equal-subset-sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","快手","拼多多","滴滴","猿辅导","百度","网易","腾讯","阿里巴巴"]',
    hot_score = 31,
    last_asked_at = '2026-07-14',
    source_url = 'https://leetcode.cn/problems/partition-equal-subset-sum/'
WHERE id = 930000000000000177;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'jian-sheng-zi-ii-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动"]',
    hot_score = 2,
    last_asked_at = '2022-06-15',
    source_url = 'https://leetcode.cn/problems/jian-sheng-zi-ii-lcof/'
WHERE id = 930000000000000180;

UPDATE question_bank SET
    leetcode_no = 62,
    leetcode_slug = 'unique-paths',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","商汤","字节跳动","小米","微软","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 78,
    last_asked_at = '2026-06-01',
    source_url = 'https://leetcode.cn/problems/unique-paths/'
WHERE id = 930000000000000183;

UPDATE question_bank SET
    leetcode_no = 63,
    leetcode_slug = 'unique-paths-ii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","商汤","图森","字节跳动","微软","快手","滴滴","网易","美团","腾讯","阿里巴巴"]',
    hot_score = 28,
    last_asked_at = '2026-07-07',
    source_url = 'https://leetcode.cn/problems/unique-paths-ii/'
WHERE id = 930000000000000184;

UPDATE question_bank SET
    leetcode_no = 64,
    leetcode_slug = 'minimum-path-sum',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 97,
    last_asked_at = '2026-09-17',
    source_url = 'https://leetcode.cn/problems/minimum-path-sum/'
WHERE id = 930000000000000185;

UPDATE question_bank SET
    leetcode_no = 120,
    leetcode_slug = 'triangle',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["亚马逊","京东","华为","字节跳动","小米","微软","百度","美团","腾讯"]',
    hot_score = 35,
    last_asked_at = '2026-08-03',
    source_url = 'https://leetcode.cn/problems/triangle/'
WHERE id = 930000000000000186;

UPDATE question_bank SET
    leetcode_no = 5,
    leetcode_slug = 'longest-palindromic-substring',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 367,
    last_asked_at = '2026-11-23',
    source_url = 'https://leetcode.cn/problems/longest-palindromic-substring/'
WHERE id = 930000000000000187;

UPDATE question_bank SET
    leetcode_no = 1143,
    leetcode_slug = 'longest-common-subsequence',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 203,
    last_asked_at = '2026-10-27',
    source_url = 'https://leetcode.cn/problems/longest-common-subsequence/'
WHERE id = 930000000000000188;

UPDATE question_bank SET
    leetcode_no = 72,
    leetcode_slug = 'edit-distance',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 208,
    last_asked_at = '2026-10-09',
    source_url = 'https://leetcode.cn/problems/edit-distance/'
WHERE id = 930000000000000189;

UPDATE question_bank SET
    leetcode_no = 97,
    leetcode_slug = 'interleaving-string',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","字节跳动","微软","拼多多","滴滴","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 34,
    last_asked_at = '2026-10-28',
    source_url = 'https://leetcode.cn/problems/interleaving-string/'
WHERE id = 930000000000000190;

UPDATE question_bank SET
    leetcode_no = 123,
    leetcode_slug = 'best-time-to-buy-and-sell-stock-iii',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["华为","字节跳动","小米","小红书","微软","快手","拼多多","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 56,
    last_asked_at = '2026-10-15',
    source_url = 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-iii/'
WHERE id = 930000000000000191;

UPDATE question_bank SET
    leetcode_no = 188,
    leetcode_slug = 'best-time-to-buy-and-sell-stock-iv',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","小米","百度","腾讯"]',
    hot_score = 17,
    last_asked_at = '2025-10-22',
    source_url = 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-iv/'
WHERE id = 930000000000000192;

UPDATE question_bank SET
    leetcode_no = 221,
    leetcode_slug = 'maximal-square',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["bilibili","华为","字节跳动","小米","微软","猿辅导","百度","美团","腾讯","虾皮"]',
    hot_score = 89,
    last_asked_at = '2026-11-06',
    source_url = 'https://leetcode.cn/problems/maximal-square/'
WHERE id = 930000000000000193;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'li-wu-de-zui-da-jie-zhi-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","阿里巴巴"]',
    hot_score = 3,
    last_asked_at = '2023-01-05',
    source_url = 'https://leetcode.cn/problems/li-wu-de-zui-da-jie-zhi-lcof/'
WHERE id = 930000000000000194;

UPDATE question_bank SET
    leetcode_no = 136,
    leetcode_slug = 'single-number',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 53,
    last_asked_at = '2026-04-22',
    source_url = 'https://leetcode.cn/problems/single-number/'
WHERE id = 930000000000000195;

UPDATE question_bank SET
    leetcode_no = 137,
    leetcode_slug = 'single-number-ii',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","微软","快手","百度","美团","腾讯"]',
    hot_score = 12,
    last_asked_at = '2026-05-14',
    source_url = 'https://leetcode.cn/problems/single-number-ii/'
WHERE id = 930000000000000196;

UPDATE question_bank SET
    leetcode_no = 191,
    leetcode_slug = 'number-of-1-bits',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","旷视","百度","美团","腾讯","虾皮"]',
    hot_score = 20,
    last_asked_at = '2026-05-25',
    source_url = 'https://leetcode.cn/problems/number-of-1-bits/'
WHERE id = 930000000000000197;

UPDATE question_bank SET
    leetcode_no = 190,
    leetcode_slug = 'reverse-bits',
    tags = '["CODETOP"]',
    companies = '["字节跳动","拼多多"]',
    hot_score = 3,
    last_asked_at = '2021-08-20',
    source_url = 'https://leetcode.cn/problems/reverse-bits/'
WHERE id = 930000000000000198;

UPDATE question_bank SET
    leetcode_no = 67,
    leetcode_slug = 'add-binary',
    tags = '["CODETOP"]',
    companies = '["亚马逊","字节跳动","微软","快手","拼多多","腾讯"]',
    hot_score = 10,
    last_asked_at = '2025-02-17',
    source_url = 'https://leetcode.cn/problems/add-binary/'
WHERE id = 930000000000000199;

UPDATE question_bank SET
    leetcode_no = 201,
    leetcode_slug = 'bitwise-and-of-numbers-range',
    tags = '["CODETOP"]',
    companies = '["字节跳动","旷视"]',
    hot_score = 2,
    last_asked_at = '2021-12-18',
    source_url = 'https://leetcode.cn/problems/bitwise-and-of-numbers-range/'
WHERE id = 930000000000000200;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'er-jin-zhi-zhong-1de-ge-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["微软","携程","美团","虾皮"]',
    hot_score = 5,
    last_asked_at = '2022-04-18',
    source_url = 'https://leetcode.cn/problems/er-jin-zhi-zhong-1de-ge-shu-lcof/'
WHERE id = 930000000000000201;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","百度"]',
    hot_score = 2,
    last_asked_at = '2021-03-29',
    source_url = 'https://leetcode.cn/problems/shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-lcof/'
WHERE id = 930000000000000202;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-ii-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","微软","腾讯"]',
    hot_score = 3,
    last_asked_at = '2022-03-23',
    source_url = 'https://leetcode.cn/problems/shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-ii-lcof/'
WHERE id = 930000000000000203;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'bu-yong-jia-jian-cheng-chu-zuo-jia-fa-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","快手","携程","百度","美团","腾讯"]',
    hot_score = 9,
    last_asked_at = '2024-08-14',
    source_url = 'https://leetcode.cn/problems/bu-yong-jia-jian-cheng-chu-zuo-jia-fa-lcof/'
WHERE id = 930000000000000204;

UPDATE question_bank SET
    leetcode_no = 9,
    leetcode_slug = 'palindrome-number',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["京东","华为","商汤","字节跳动","小米","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 33,
    last_asked_at = '2026-05-10',
    source_url = 'https://leetcode.cn/problems/palindrome-number/'
WHERE id = 930000000000000205;

UPDATE question_bank SET
    leetcode_no = 66,
    leetcode_slug = 'plus-one',
    tags = '["CODETOP"]',
    companies = '["字节跳动","虾皮","阿里巴巴"]',
    hot_score = 3,
    last_asked_at = '2022-03-30',
    source_url = 'https://leetcode.cn/problems/plus-one/'
WHERE id = 930000000000000206;

UPDATE question_bank SET
    leetcode_no = 172,
    leetcode_slug = 'factorial-trailing-zeroes',
    tags = '["CODETOP"]',
    companies = '["华为","字节跳动","拼多多","美团","腾讯","阿里巴巴"]',
    hot_score = 13,
    last_asked_at = '2025-08-05',
    source_url = 'https://leetcode.cn/problems/factorial-trailing-zeroes/'
WHERE id = 930000000000000207;

UPDATE question_bank SET
    leetcode_no = 149,
    leetcode_slug = 'max-points-on-a-line',
    tags = '["CODETOP"]',
    companies = '["bilibili","字节跳动","快手","旷视","腾讯","阿里巴巴"]',
    hot_score = 11,
    last_asked_at = '2024-04-16',
    source_url = 'https://leetcode.cn/problems/max-points-on-a-line/'
WHERE id = 930000000000000208;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'shu-zi-xu-lie-zhong-mou-yi-wei-de-shu-zi-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","腾讯"]',
    hot_score = 3,
    last_asked_at = '2025-08-19',
    source_url = 'https://leetcode.cn/problems/shu-zi-xu-lie-zhong-mou-yi-wei-de-shu-zi-lcof/'
WHERE id = 930000000000000209;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'yuan-quan-zhong-zui-hou-sheng-xia-de-shu-zi-lcof',
    tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    companies = '["字节跳动","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 32,
    last_asked_at = '2026-01-19',
    source_url = 'https://leetcode.cn/problems/yuan-quan-zhong-zui-hou-sheng-xia-de-shu-zi-lcof/'
WHERE id = 930000000000000210;

UPDATE question_bank SET
    leetcode_no = 146,
    leetcode_slug = 'lru-cache',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 972,
    last_asked_at = '2026-11-23',
    source_url = 'https://leetcode.cn/problems/lru-cache/'
WHERE id = 930000000000000214;

UPDATE question_bank SET
    leetcode_no = 155,
    leetcode_slug = 'min-stack',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 99,
    last_asked_at = '2026-04-14',
    source_url = 'https://leetcode.cn/problems/min-stack/'
WHERE id = 930000000000000215;

UPDATE question_bank SET
    leetcode_no = 208,
    leetcode_slug = 'implement-trie-prefix-tree',
    tags = '["CODETOP","HOT100","HIGH_FREQ"]',
    companies = '["华为","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 43,
    last_asked_at = '2026-10-12',
    source_url = 'https://leetcode.cn/problems/implement-trie-prefix-tree/'
WHERE id = 930000000000000216;

UPDATE question_bank SET
    leetcode_no = 211,
    leetcode_slug = 'design-add-and-search-words-data-structure',
    tags = '["CODETOP"]',
    companies = '["字节跳动"]',
    hot_score = 2,
    last_asked_at = '2022-09-21',
    source_url = 'https://leetcode.cn/problems/design-add-and-search-words-data-structure/'
WHERE id = 930000000000000217;

UPDATE question_bank SET
    leetcode_no = 295,
    leetcode_slug = 'find-median-from-data-stream',
    tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    companies = '["亚马逊","字节跳动","微软","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]',
    hot_score = 38,
    last_asked_at = '2026-09-27',
    source_url = 'https://leetcode.cn/problems/find-median-from-data-stream/'
WHERE id = 930000000000000218;

UPDATE question_bank SET
    leetcode_no = 380,
    leetcode_slug = 'insert-delete-getrandom-o1',
    tags = '["CODETOP","HIGH_FREQ"]',
    companies = '["字节跳动","快手","拼多多","腾讯"]',
    hot_score = 21,
    last_asked_at = '2026-03-16',
    source_url = 'https://leetcode.cn/problems/insert-delete-getrandom-o1/'
WHERE id = 930000000000000219;

UPDATE question_bank SET
    leetcode_no = NULL,
    leetcode_slug = 'dui-lie-de-zui-da-zhi-lcof',
    tags = '["CODETOP","JIANZHI"]',
    companies = '["字节跳动","快手"]',
    hot_score = 5,
    last_asked_at = '2022-03-30',
    source_url = 'https://leetcode.cn/problems/dui-lie-de-zui-da-zhi-lcof/'
WHERE id = 930000000000000220;

UPDATE question_bank SET
    leetcode_no = 173,
    leetcode_slug = 'binary-search-tree-iterator',
    tags = '["CODETOP"]',
    companies = '["字节跳动","微软","快手","百度"]',
    hot_score = 8,
    last_asked_at = '2023-03-21',
    source_url = 'https://leetcode.cn/problems/binary-search-tree-iterator/'
WHERE id = 930000000000000221;

-- 4 道自定义基础题不参与榜单，只打基础标签
UPDATE question_bank SET tags = JSON_ARRAY('BASE'), companies = NULL, hot_score = 0, last_asked_at = NULL
WHERE id IN (900000000000004001, 900000000000004002, 900000000000004003, 900000000000004004);

-- ---------- 4. 新增常考编程题 ----------
INSERT INTO question_bank
    (id, title, content, type, judge_mode, difficulty, time_limit_ms, memory_limit_mb,
     domain, reference_answer, starter_code, key_points, kp_ids, kp_names,
     source, source_url, content_hash, status,
     leetcode_no, leetcode_slug, tags, companies, hot_score, last_asked_at)
VALUES
(910000000000001681, '无重复字符的最长子串', '给定一个字符串 `s` ，请你找出其中不含有重复字符的 **最长子串**的长度。

**示例 1:**

```
输入: s = "abcabcbb"
输出: 3
解释: 因为无重复字符的最长子串是 "abc"，所以其长度为 3。
```

**示例 2:**

```
输入: s = "bbbbb"
输出: 1
解释: 因为无重复字符的最长子串是 "b"，所以其长度为 1。
```

**示例 3:**

```
输入: s = "pwwkew"
输出: 3
解释: 因为无重复字符的最长子串是 "wke"，所以其长度为 3。
     请注意，你的答案必须是 子串 的长度，"pwke" 是一个子序列，不是子串。
```

**示例 4:**

```
输入: s = ""
输出: 0
```

**提示：**

- `0 <= s.length <= 5 * 10^4`

- `s` 由英文字母、数字、符号和空格组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-substring-without-repeating-characters/', '3a23368db8655df37257c4f26baa91352a2bba767492548eade45e6b11a776e0', 1, 3, 'longest-substring-without-repeating-characters', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 1232, '2026-11-23'),
(910000000000001538, 'LRU缓存机制', '运用你所掌握的数据结构，设计和实现一个  LRU (最近最少使用) 缓存机制 。

实现 `LRUCache` 类：

- `LRUCache(int capacity)` 以正整数作为容量 `capacity` 初始化 LRU 缓存

- `int get(int key)` 如果关键字 `key` 存在于缓存中，则返回关键字的值，否则返回 `-1` 。

- `void put(int key, int value)` 如果关键字已经存在，则变更其数据值；如果关键字不存在，则插入该组「关键字-值」。当缓存容量达到上限时，它应该在写入新数据之前删除最久未使用的数据值，从而为新的数据值留出空间。

**进阶**：你是否可以在 `O(1)` 时间复杂度内完成这两种操作？

**示例：**

```
输入
["LRUCache", "put", "put", "get", "put", "get", "put", "get", "get", "get"]
[[2], [1, 1], [2, 2], [1], [3, 3], [2], [4, 4], [1], [3], [4]]
输出
[null, null, null, 1, null, -1, null, -1, 3, 4]

解释
LRUCache lRUCache = new LRUCache(2);
lRUCache.put(1, 1); // 缓存是 {1=1}
lRUCache.put(2, 2); // 缓存是 {1=1, 2=2}
lRUCache.get(1);    // 返回 1
lRUCache.put(3, 3); // 该操作会使得关键字 2 作废，缓存是 {1=1, 3=3}
lRUCache.get(2);    // 返回 -1 (未找到)
lRUCache.put(4, 4); // 该操作会使得关键字 1 作废，缓存是 {4=4, 3=3}
lRUCache.get(1);    // 返回 -1 (未找到)
lRUCache.get(3);    // 返回 3
lRUCache.get(4);    // 返回 4
```

**提示：**

- `1 <= capacity <= 3000`

- `0 <= key <= 10000`

- `0 <= value <= 10^5`

- 最多调用 `2 * 10^5` 次 `get` 和 `put`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lru-cache/', 'fe62d9222721ae2be5ab08eded97ce2b484dbc079ecdc27cee4e004239ec28e5', 1, 146, 'lru-cache', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 972, '2026-11-23'),
(910000000000001478, '反转链表', '给你单链表的头节点 `head` ，请你反转链表，并返回反转后的链表。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/rev1ex1.jpg)

```
输入：head = [1,2,3,4,5]
输出：[5,4,3,2,1]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/rev1ex2.jpg)

```
输入：head = [1,2]
输出：[2,1]
```

**示例 3：**

```
输入：head = []
输出：[]
```

**提示：**

- 链表中节点的数目范围是 `[0, 5000]`

- `-5000 <= Node.val <= 5000`

**进阶：**链表可以选用迭代或递归方式完成反转。你能否用两种方法解决这道题？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-linked-list/', '9c83a6503a3430d0af9939648b4644af36a6139ea4a7a7fe62c90d52b3692cf3', 1, 206, 'reverse-linked-list', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 764, '2026-11-27'),
(910000000000001469, '数组中的第K个最大元素', '给定整数数组 `nums` 和整数 `k`，请返回数组中第 `k` 个最大的元素。

请注意，你需要找的是数组排序后的第 `k` 个最大的元素，而不是第 `k` 个不同的元素。

**示例 1:**

```
输入: [3,2,1,5,6,4] 和 k = 2
输出: 5
```

**示例 2:**

```
输入: [3,2,3,1,2,4,5,5,6] 和 k = 4
输出: 4
```

**提示：**

- `1 <= k <= nums.length <= 10^4`

- `-10^4 <= nums[i] <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/kth-largest-element-in-an-array/', '3109b2d57ac8ffcda9c97c16fe4bd8be05d88393ce7b5bccd5e0092e401aaea2', 1, 215, 'kth-largest-element-in-an-array', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 609, '2026-11-25'),
(910000000000001659, 'K 个一组翻转链表', '给你一个链表，每 *k*个节点一组进行翻转，请你返回翻转后的链表。

*k*是一个正整数，它的值小于或等于链表的长度。

如果节点总数不是 *k*的整数倍，那么请将最后剩余的节点保持原有顺序。

**进阶：**

- 你可以设计一个只使用常数额外空间的算法来解决此问题吗？

- **你不能只是单纯的改变节点内部的值**，而是需要实际进行节点交换。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/03/reverse_ex1.jpg)

```
输入：head = [1,2,3,4,5], k = 2
输出：[2,1,4,3,5]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/03/reverse_ex2.jpg)

```
输入：head = [1,2,3,4,5], k = 3
输出：[3,2,1,4,5]
```

**示例 3：**

```
输入：head = [1,2,3,4,5], k = 1
输出：[1,2,3,4,5]
```

**示例 4：**

```
输入：head = [1], k = 1
输出：[1]
```

**提示：**

- 列表中节点的数量在范围 `sz` 内

- `1 <= sz <= 5000`

- `0 <= Node.val <= 1000`

- `1 <= k <= sz`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-nodes-in-k-group/', '182cbdb3c9639d04d625a83edaf6844bae5a5fa3b5456f395d75dabe05399795', 1, 25, 'reverse-nodes-in-k-group', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 542, '2026-11-24'),
(910000000000001669, '三数之和', '给你一个包含 `n` 个整数的数组 `nums`，判断 `nums` 中是否存在三个元素 *a，b，c ，*使得 *a + b + c =*0 ？请你找出所有和为 `0` 且不重复的三元组。

**注意：**答案中不可以包含重复的三元组。

**示例 1：**

```
输入：nums = [-1,0,1,2,-1,-4]
输出：[[-1,-1,2],[-1,0,1]]
```

**示例 2：**

```
输入：nums = []
输出：[]
```

**示例 3：**

```
输入：nums = [0]
输出：[]
```

**提示：**

- `0 <= nums.length <= 3000`

- `-10^5 <= nums[i] <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/3sum/', '4741cd2c854568934db9707219b7d420309e40def6ac1d9ec9806d038a8a695f', 1, 15, '3sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 498, '2026-10-26'),
(910000000000001631, '最大子数组和', '给定一个整数数组 `nums` ，找到一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。

**示例 1：**

```
输入：nums = [-2,1,-3,4,-1,2,1,-5,4]输出：6解释：连续子数组 [4,-1,2,1] 的和最大，为 6 。
```
**示例 2：**

```
输入：nums = [1]输出：1
```
**示例 3：**

```
输入：nums = [0]输出：0
```
**示例 4：**

```
输入：nums = [-1]输出：-1
```
**示例 5：**

```
输入：nums = [-100000]输出：-100000
```

**提示：**

- `1 <= nums.length <= 3 * 10^4`
- `-10^5 <= nums[i] <= 10^5`

**进阶：**如果你已经实现复杂度为 `O(n)` 的解法，尝试使用更为精妙的 **分治法** 求解。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-subarray/', '76f3222fa49331fe41bc9c8da4ae6e13a408fa0d5ae286060a73dcaaebb2cce5', 1, 53, 'maximum-subarray', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 382, '2026-11-02'),
(910000000000001679, '最长回文子串', '给你一个字符串 `s`，找到 `s` 中最长的回文子串。

**示例 1：**

```
输入：s = "babad"
输出："bab"
解释："aba" 同样是符合题意的答案。
```

**示例 2：**

```
输入：s = "cbbd"
输出："bb"
```

**示例 3：**

```
输入：s = "a"
输出："a"
```

**示例 4：**

```
输入：s = "ac"
输出："a"
```

**提示：**

- `1 <= s.length <= 1000`

- `s` 仅由数字和英文字母（大写和/或小写）组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-palindromic-substring/', '8fa060b517e090587cd812b09b578b02bca90624852e0301e0fce28c76b9befb', 1, 5, 'longest-palindromic-substring', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 367, '2026-11-23'),
(910000000000001906, '手撕快速排序', '给你一个整数数组 `nums`，请你将该数组升序排列。

**示例 1：**

```
输入：nums = [5,2,3,1]输出：[1,2,3,5]
```
**示例 2：**

```
输入：nums = [5,1,1,2,0,0]输出：[0,0,1,1,2,5]
```

**提示：**

- `1 <= nums.length <= 50000`
- `-50000 <= nums[i] <= 50000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sort-an-array/', '9d3479edcf9780cff4e93214512d9563cf9917785a66dbe5c1e6e06c409c5127', 1, NULL, 'sort-an-array', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 361, '2026-11-19'),
(910000000000001484, '岛屿数量', '给你一个由 `''1''`（陆地）和 `''0''`（水）组成的的二维网格，请你计算网格中岛屿的数量。

岛屿总是被水包围，并且每座岛屿只能由水平方向和/或竖直方向上相邻的陆地连接形成。

此外，你可以假设该网格的四条边均被水包围。

**示例 1：**

```
输入：grid = [
  ["1","1","1","1","0"],
  ["1","1","0","1","0"],
  ["1","1","0","0","0"],
  ["0","0","0","0","0"]
]
输出：1
```

**示例 2：**

```
输入：grid = [
  ["1","1","0","0","0"],
  ["1","1","0","0","0"],
  ["0","0","1","0","0"],
  ["0","0","0","1","1"]
]
输出：3
```

**提示：**

- `m == grid.length`

- `n == grid[i].length`

- `1 <= m, n <= 300`

- `grid[i][j]` 的值为 `''0''` 或 `''1''`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/number-of-islands/', '9d459848590d9fa1d66ce21285f542fd582c841a75471befd371b95984e0a58f', 1, 200, 'number-of-islands', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 339, '2026-11-20'),
(910000000000001663, '合并两个有序链表', '将两个升序链表合并为一个新的 **升序** 链表并返回。新链表是通过拼接给定的两个链表的所有节点组成的。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/03/merge_ex1.jpg)

```
输入：l1 = [1,2,4], l2 = [1,3,4]
输出：[1,1,2,3,4,4]
```

**示例 2：**

```
输入：l1 = [], l2 = []
输出：[]
```

**示例 3：**

```
输入：l1 = [], l2 = [0]
输出：[0]
```

**提示：**

- 两个链表的节点数目范围是 `[0, 50]`

- `-100 <= Node.val <= 100`

- `l1` 和 `l2` 均按 **非递减顺序** 排列', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/merge-two-sorted-lists/', '1a256fdd27b8da05ba764f1d77bf1e6dba8c299e15a79edb1d814c3063664b19', 1, 21, 'merge-two-sorted-lists', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 337, '2026-11-19'),
(910000000000001582, '二叉树的层序遍历', '给你一个二叉树，请你返回其按 **层序遍历** 得到的节点值。 （即逐层地，从左到右访问所有节点）。

**示例：**

二叉树：`[3,9,20,null,null,15,7]`,

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回其层序遍历结果：

```
[
  [3],
  [9,20],
  [15,7]
]
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-level-order-traversal/', '1982e3574f997bdc6e0584f1b1e9851368da8b04d61eed06e7bdabec268e3965', 1, 102, 'binary-tree-level-order-traversal', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 334, '2026-10-23'),
(910000000000001651, '搜索旋转排序数组', '整数数组 `nums` 按升序排列，数组中的值 **互不相同** 。

在传递给函数之前，`nums` 在预先未知的某个下标 `k`（`0 <= k < nums.length`）上进行了 **旋转**，使数组变为 `[nums[k], nums[k+1], ..., nums[n-1], nums[0], nums[1], ..., nums[k-1]]`（下标 **从 0 开始** 计数）。例如， `[0,1,2,4,5,6,7]` 在下标 `3` 处经旋转后可能变为 `[4,5,6,7,0,1,2]` 。

给你 **旋转后** 的数组 `nums` 和一个整数 `target` ，如果 `nums` 中存在这个目标值 `target` ，则返回它的下标，否则返回 `-1` 。

**示例 1：**

```
输入：nums = [4,5,6,7,0,1,2], target = 0
输出：4
```

**示例 2：**

```
输入：nums = [4,5,6,7,0,1,2], target = 3
输出：-1
```

**示例 3：**

```
输入：nums = [1], target = 0
输出：-1
```

**提示：**

- `1 <= nums.length <= 5000`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 中的每个值都 **独一无二**

- 题目数据保证 `nums` 在预先未知的某个下标上进行了旋转

- `-10^4 <= target <= 10^4`

**进阶：**你可以设计一个时间复杂度为 `O(log n)` 的解决方案吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/search-in-rotated-sorted-array/', '3ccd5f61fcb06fc975006adace9985d078bf9eb6c4a08e3b57366d003bc62507', 1, 33, 'search-in-rotated-sorted-array', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","商汤","图森","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 324, '2026-11-27'),
(910000000000001596, '合并两个有序数组', '给你两个按 **非递减顺序** 排列的整数数组 `nums1`**和 `nums2`，另有两个整数 `m` 和 `n` ，分别表示 `nums1` 和 `nums2` 中的元素数目。

请你 **合并** `nums2`**到 `nums1` 中，使合并后的数组同样按 **非递减顺序** 排列。

**注意：**最终，合并后数组不应由函数返回，而是存储在数组 `nums1` 中。为了应对这种情况，`nums1` 的初始长度为 `m + n`，其中前 `m` 个元素表示应合并的元素，后 `n` 个元素为 `0` ，应忽略。`nums2` 的长度为 `n` 。

**示例 1：**

```
输入：nums1 = [1,2,3,0,0,0], m = 3, nums2 = [2,5,6], n = 3
输出：[1,2,2,3,5,6]
解释：需要合并 [1,2,3] 和 [2,5,6] 。
合并结果是 [1,2,2,3,5,6] ，其中斜体加粗标注的为 nums1 中的元素。
```

**示例 2：**

```
输入：nums1 = [1], m = 1, nums2 = [], n = 0
输出：[1]
解释：需要合并 [1] 和 [] 。
合并结果是 [1] 。
```

**示例 3：**

```
输入：nums1 = [0], m = 0, nums2 = [1], n = 1
输出：[1]
解释：需要合并的数组是 [] 和 [1] 。
合并结果是 [1] 。
注意，因为 m = 0 ，所以 nums1 中没有元素。nums1 中仅存的 0 仅仅是为了确保合并结果可以顺利存放到 nums1 中。
```

**提示：**

- `nums1.length == m + n`

- `nums2.length == n`

- `0 <= m, n <= 200`

- `1 <= m + n <= 200`

- `-10^9 <= nums1[i], nums2[j] <= 10^9`

**进阶：**你可以设计实现一个时间复杂度为 `O(m + n)` 的算法解决此问题吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/merge-sorted-array/', '897c80fdb1ae5c40c13afb9b4deaeb4094a1fd24f94d1f2a9d49885856e4f9a2', 1, 88, 'merge-sorted-array', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 306, '2026-11-20'),
(910000000000001638, '全排列', '给定一个不含重复数字的数组 `nums` ，返回其 **所有可能的全排列** 。你可以 **按任意顺序** 返回答案。

**示例 1：**

```
输入：nums = [1,2,3]
输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

**示例 2：**

```
输入：nums = [0,1]
输出：[[0,1],[1,0]]
```

**示例 3：**

```
输入：nums = [1]
输出：[[1]]
```

**提示：**

- `1 <= nums.length <= 6`

- `-10 <= nums[i] <= 10`

- `nums` 中的所有整数 **互不相同**', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/permutations/', 'b24031a35308fde9c1e61c38832db39be6dfe04756d9dc1a1b35a66af0d8cb31', 1, 46, 'permutations', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 306, '2026-10-27'),
(910000000000001683, '两数之和', '给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在该数组中找出 **和为目标值***`target`*  的那 **两个** 整数，并返回它们的数组下标。

你可以假设每种输入只会对应一个答案。但是，数组中同一个元素在答案里不能重复出现。

你可以按任意顺序返回答案。

**示例 1：**

```
输入：nums = [2,7,11,15], target = 9
输出：[0,1]
解释：因为 nums[0] + nums[1] == 9 ，返回 [0, 1] 。
```

**示例 2：**

```
输入：nums = [3,2,4], target = 6
输出：[1,2]
```

**示例 3：**

```
输入：nums = [3,3], target = 6
输出：[0,1]
```

**提示：**

- `2 <= nums.length <= 10^4`

- `-10^9 <= nums[i] <= 10^9`

- `-10^9 <= target <= 10^9`

- **只会存在一个有效答案**

**进阶：**你可以想出一个时间复杂度小于 `O(n^2)` 的算法吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/two-sum/', 'f70e7c70fd07b20bf2c16a60dabe398212ed84826717b99d0573067395f286d7', 1, 1, 'two-sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 306, '2026-09-07'),
(910000000000001664, '有效的括号', '给定一个只包括 `''(''`，`'')''`，`''{''`，`''}''`，`''[''`，`'']''` 的字符串 `s` ，判断字符串是否有效。

有效字符串需满足：

- 左括号必须用相同类型的右括号闭合。

- 左括号必须以正确的顺序闭合。

**示例 1：**

```
输入：s = "()"
输出：true
```

**示例 2：**

```
输入：s = "()[]{}"
输出：true
```

**示例 3：**

```
输入：s = "(]"
输出：false
```

**示例 4：**

```
输入：s = "([)]"
输出：false
```

**示例 5：**

```
输入：s = "{[]}"
输出：true
```

**提示：**

- `1 <= s.length <= 10^4`

- `s` 仅由括号 `''()[]{}''` 组成', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-parentheses/', '56ed65bff10cb165cbf65b1bc31e6c58dff913f68dae1fb96147ac9523d5b295', 1, 20, 'valid-parentheses', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","图森","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 303, '2026-11-14'),
(910000000000001563, '买卖股票的最佳时机', '给定一个数组 `prices` ，它的第 `i` 个元素 `prices[i]` 表示一支给定股票第 `i` 天的价格。

你只能选择 **某一天** 买入这只股票，并选择在 **未来的某一个不同的日子** 卖出该股票。设计一个算法来计算你所能获取的最大利润。

返回你可以从这笔交易中获取的最大利润。如果你不能获取任何利润，返回 `0` 。

**示例 1：**

```
输入：[7,1,5,3,6,4]
输出：5
解释：在第 2 天（股票价格 = 1）的时候买入，在第 5 天（股票价格 = 6）的时候卖出，最大利润 = 6-1 = 5 。
     注意利润不能是 7-1 = 6, 因为卖出价格需要大于买入价格；同时，你不能在买入前卖出股票。
```

**示例 2：**

```
输入：prices = [7,6,4,3,1]
输出：0
解释：在这种情况下, 没有交易完成, 所以最大利润为 0。
```

**提示：**

- `1 <= prices.length <= 10^5`

- `0 <= prices[i] <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock/', 'b40d48fd9a243bafba8a344a4e9275dca748789959447501da9f28519ffa7205', 1, 121, 'best-time-to-buy-and-sell-stock', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 282, '2026-10-30'),
(910000000000001384, '最长上升子序列', '给你一个整数数组 `nums` ，找到其中最长严格递增子序列的长度。

子序列是由数组派生而来的序列，删除（或不删除）数组中的元素而不改变其余元素的顺序。例如，`[3,6,2,7]` 是数组 `[0,3,1,6,2,2,7]` 的子序列。

**示例 1：**

```
输入：nums = [10,9,2,5,3,7,101,18]
输出：4
解释：最长递增子序列是 [2,3,7,101]，因此长度为 4 。
```

**示例 2：**

```
输入：nums = [0,1,0,3,2,3]
输出：4
```

**示例 3：**

```
输入：nums = [7,7,7,7,7,7,7]
输出：1
```

**提示：**

- `1 <= nums.length <= 2500`

- `-10^4 <= nums[i] <= 10^4`

**进阶：**

- 你可以设计时间复杂度为 `O(n^2)` 的解决方案吗？

- 你能将算法的时间复杂度降低到 `O(n log(n))` 吗?', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-increasing-subsequence/', '56bb6ebe1b47c253a03ee7c5ede803f722f4ac5642e629d420d0cf58cf52786c', 1, 300, 'longest-increasing-subsequence', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 276, '2026-10-29'),
(910000000000001448, '二叉树的最近公共祖先', '给定一个二叉树, 找到该树中两个指定节点的最近公共祖先。

百度百科中最近公共祖先的定义为：“对于有根树 T 的两个节点 p、q，最近公共祖先表示为一个节点 x，满足 x 是 p、q 的祖先且 x 的深度尽可能大（**一个节点也可以是它自己的祖先**）。”

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2018/12/14/binarytree.png)

```
输入：root = [3,5,1,6,2,0,8,null,null,7,4], p = 5, q = 1
输出：3
解释：节点 5 和节点 1 的最近公共祖先是节点 3 。
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2018/12/14/binarytree.png)

```
输入：root = [3,5,1,6,2,0,8,null,null,7,4], p = 5, q = 4
输出：5
解释：节点 5 和节点 4 的最近公共祖先是节点 5 。因为根据定义最近公共祖先节点可以为节点本身。
```

**示例 3：**

```
输入：root = [1,2], p = 1, q = 2
输出：1
```

**提示：**

- 树中节点数目在范围 `[2, 10^5]` 内。

- `-10^9 <= Node.val <= 10^9`

- 所有 `Node.val` `互不相同` 。

- `p != q`

- `p` 和 `q` 均存在于给定的二叉树中。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lowest-common-ancestor-of-a-binary-tree/', 'fc7076b7e7a07a157196faa15823067a10766ba7800d3cd459cd9019ae5b6453', 1, 236, 'lowest-common-ancestor-of-a-binary-tree', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 273, '2026-11-22'),
(910000000000001592, '反转链表 II', '给你单链表的头指针 `head` 和两个整数 `left` 和 `right` ，其中 `left <= right` 。请你反转从位置 `left` 到位置 `right` 的链表节点，返回 **反转后的链表** 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/rev2ex2.jpg)

```
输入：head = [1,2,3,4,5], left = 2, right = 4
输出：[1,4,3,2,5]
```

**示例 2：**

```
输入：head = [5], left = 1, right = 1
输出：[5]
```

**提示：**

- 链表中节点数目为 `n`

- `1 <= n <= 500`

- `-500 <= Node.val <= 500`

- `1 <= left <= right <= n`

**进阶：** 你可以使用一趟扫描完成反转吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-linked-list-ii/', '38fde1270e4cd4f14a4e69d750f032a48fcaa3bdfa9c931c810cc6b838e4c546', 1, 92, 'reverse-linked-list-ii', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 273, '2026-11-18'),
(910000000000001581, '二叉树的锯齿形层次遍历', '给定一个二叉树，返回其节点值的锯齿形层序遍历。（即先从左往右，再从右往左进行下一层遍历，以此类推，层与层之间交替进行）。

例如：

给定二叉树 `[3,9,20,null,null,15,7]`,

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回锯齿形层序遍历如下：

```
[
  [3],
  [20,9],
  [15,7]
]
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-zigzag-level-order-traversal/', '7bc9849c7f64742cca07f28fefe25a999624a14dc2458b76eb87c49e986407cc', 1, 103, 'binary-tree-zigzag-level-order-traversal', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","滴滴","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]', 272, '2026-09-16'),
(910000000000001661, '合并K个排序链表', '给你一个链表数组，每个链表都已经按升序排列。

请你将所有链表合并到一个升序链表中，返回合并后的链表。

**示例 1：**

```
输入：lists = [[1,4,5],[1,3,4],[2,6]]
输出：[1,1,2,3,4,4,5,6]
解释：链表数组如下：
[
  1->4->5,
  1->3->4,
  2->6
]
将它们合并到一个有序链表中得到。
1->1->2->3->4->4->5->6
```

**示例 2：**

```
输入：lists = []
输出：[]
```

**示例 3：**

```
输入：lists = [[]]
输出：[]
```

**提示：**

- `k == lists.length`

- `0 <= k <= 10^4`

- `0 <= lists[i].length <= 500`

- `-10^4 <= lists[i][j] <= 10^4`

- `lists[i]` 按 **升序** 排列

- `lists[i].length` 的总和不超过 `10^4`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/merge-k-sorted-lists/', 'f8d408168419d8209cdbb6a71c47f0d711f1c859efd29f94bb8d477ca923826c', 1, 23, 'merge-k-sorted-lists', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 264, '2026-10-24'),
(910000000000001630, '螺旋矩阵', '给你一个 `m` 行 `n` 列的矩阵 `matrix` ，请按照 **顺时针螺旋顺序** ，返回矩阵中的所有元素。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/spiral1.jpg)

```
输入：matrix = [[1,2,3],[4,5,6],[7,8,9]]
输出：[1,2,3,6,9,8,7,4,5]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/spiral.jpg)

```
输入：matrix = [[1,2,3,4],[5,6,7,8],[9,10,11,12]]
输出：[1,2,3,4,8,12,11,10,9,5,6,7]
```

**提示：**

- `m == matrix.length`

- `n == matrix[i].length`

- `1 <= m, n <= 10`

- `-100 <= matrix[i][j] <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/spiral-matrix/', '44aa2d7c00e4379fe854dd5a4209b4cd9cf2ef0fbcceb75841ebbf05d129f911', 1, 54, 'spiral-matrix', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 262, '2026-10-15'),
(910000000000001541, '重排链表', '给定一个单链表 `L`**的头节点 `head` ，单链表 `L` 表示为：

` L_0 → L_1 → … → L_n-1 → L_n `

请将其重新排列后变为：

`L_0 → L_n → L_1 → L_n-1 → L_2 → L_n-2 → …`

不能只是单纯的改变节点内部的值，而是需要实际的进行节点交换。

**示例 1:**

![题目配图](https://pic.leetcode-cn.com/1626420311-PkUiGI-image.png)

```
输入: head = [1,2,3,4]
输出: [1,4,2,3]
```

**示例 2:**

![题目配图](https://pic.leetcode-cn.com/1626420320-YUiulT-image.png)

```
输入: head = [1,2,3,4,5]
输出: [1,5,2,4,3]
```

**提示：**

- 链表的长度范围为 `[1, 5 * 10^4]`

- `1 <= node.val <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reorder-list/', 'db4162a7aab78087019648549621cea145ed7042cef229ab2cadfa5132fbe68c', 1, 143, 'reorder-list', '["CODETOP","HIGH_FREQ"]', '["bilibili","商汤","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 259, '2026-11-04'),
(910000000000001543, '环形链表', '给定一个链表，判断链表中是否有环。

如果链表中有某个节点，可以通过连续跟踪 `next` 指针再次到达，则链表中存在环。 为了表示给定链表中的环，我们使用整数 `pos` 来表示链表尾连接到链表中的位置（索引从 0 开始）。 如果 `pos` 是 `-1`，则在该链表中没有环。**注意：`pos` 不作为参数进行传递**，仅仅是为了标识链表的实际情况。

如果链表中存在环，则返回 `true` 。 否则，返回 `false` 。

**进阶：**

你能用 *O(1)*（即，常量）内存解决此问题吗？

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist.png)

```
输入：head = [3,2,0,-4], pos = 1
输出：true
解释：链表中有一个环，其尾部连接到第二个节点。
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist_test2.png)

```
输入：head = [1,2], pos = 0
输出：true
解释：链表中有一个环，其尾部连接到第一个节点。
```

**示例 3：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist_test3.png)

```
输入：head = [1], pos = -1
输出：false
解释：链表中没有环。
```

**提示：**

- 链表中节点的数目范围是 `[0, 10^4]`

- `-10^5 <= Node.val <= 10^5`

- `pos` 为 `-1` 或者链表中的一个 **有效索引** 。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/linked-list-cycle/', 'bf90909405d11228b8cf51f45469e6a575afe5edf63463c85a7c5d8f0a95cf07', 1, 141, 'linked-list-cycle', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 259, '2026-09-28'),
(910000000000001628, '合并区间', '以数组 `intervals` 表示若干个区间的集合，其中单个区间为 `intervals[i] = [start_i, end_i]` 。请你合并所有重叠的区间，并返回一个不重叠的区间数组，该数组需恰好覆盖输入中的所有区间。

**示例 1：**

```
输入：intervals = [[1,3],[2,6],[8,10],[15,18]]
输出：[[1,6],[8,10],[15,18]]
解释：区间 [1,3] 和 [2,6] 重叠, 将它们合并为 [1,6].
```

**示例 2：**

```
输入：intervals = [[1,4],[4,5]]
输出：[[1,5]]
解释：区间 [1,4] 和 [4,5] 可被视为重叠区间。
```

**提示：**

- `1 <= intervals.length <= 10^4`

- `intervals[i].length == 2`

- `0 <= start_i <= end_i <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/merge-intervals/', '9ec726f4494092bbf7fdcf3df0460c6a43a3f47c4c0193b59c01e53a66cd93da', 1, 56, 'merge-intervals', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 251, '2026-11-13'),
(910000000000001269, '字符串相加', '给定两个字符串形式的非负整数 `num1` 和`num2` ，计算它们的和并同样以字符串形式返回。

你不能使用任何內建的用于处理大整数的库（比如 `BigInteger`）， 也不能直接将输入的字符串转换为整数形式。

**示例 1：**

```
输入：num1 = "11", num2 = "123"
输出："134"
```

**示例 2：**

```
输入：num1 = "456", num2 = "77"
输出："533"
```

**示例 3：**

```
输入：num1 = "0", num2 = "0"
输出："0"
```

**提示：**

- `1 <= num1.length, num2.length <= 10^4`

- `num1` 和`num2` 都只包含数字 `0-9`

- `num1` 和`num2` 都不包含任何前导零', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/add-strings/', '3824129def4ef52ea4952b1440427300082a9d3964b64853eebf5b4cc13ee9ef', 1, 415, 'add-strings', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 246, '2026-09-24'),
(910000000000001612, '编辑距离', '给你两个单词 `word1` 和 `word2`，请你计算出将 `word1` 转换成 `word2`**所使用的最少操作数 。

你可以对一个单词进行如下三种操作：

- 插入一个字符

- 删除一个字符

- 替换一个字符

**示例 1：**

```
输入：word1 = "horse", word2 = "ros"
输出：3
解释：
horse -> rorse (将 ''h'' 替换为 ''r'')
rorse -> rose (删除 ''r'')
rose -> ros (删除 ''e'')
```

**示例 2：**

```
输入：word1 = "intention", word2 = "execution"
输出：5
解释：
intention -> inention (删除 ''t'')
inention -> enention (将 ''i'' 替换为 ''e'')
enention -> exention (将 ''n'' 替换为 ''x'')
exention -> exection (将 ''n'' 替换为 ''c'')
exection -> execution (插入 ''u'')
```

**提示：**

- `0 <= word1.length, word2.length <= 500`

- `word1` 和 `word2` 由小写英文字母组成', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/edit-distance/', '80626efccdeab803660293124cc2923be905d24bf24507fd00708803cb3ffddd', 1, 72, 'edit-distance', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 208, '2026-10-09'),
(910000000000000485, '最长公共子序列', '给定两个字符串 `text1` 和 `text2`，返回这两个字符串的最长 **公共子序列** 的长度。如果不存在 **公共子序列** ，返回 `0` 。

一个字符串的 **子序列****是指这样一个新的字符串：它是由原字符串在不改变字符的相对顺序的情况下删除某些字符（也可以不删除任何字符）后组成的新字符串。

- 例如，`"ace"` 是 `"abcde"` 的子序列，但 `"aec"` 不是 `"abcde"` 的子序列。

两个字符串的 **公共子序列** 是这两个字符串所共同拥有的子序列。

**示例 1：**

```
输入：text1 = "abcde", text2 = "ace"
输出：3
解释：最长公共子序列是 "ace" ，它的长度为 3 。
```

**示例 2：**

```
输入：text1 = "abc", text2 = "abc"
输出：3
解释：最长公共子序列是 "abc" ，它的长度为 3 。
```

**示例 3：**

```
输入：text1 = "abc", text2 = "def"
输出：0
解释：两个字符串没有公共子序列，返回 0 。
```

**提示：**

- `1 <= text1.length, text2.length <= 1000`

- `text1` 和 `text2` 仅由小写英文字符组成。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-common-subsequence/', 'd744f87fdfafbf4da8c54b15f84d3461cf01439f82c34b173c32912a5872e128', 1, 1143, 'longest-common-subsequence', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 203, '2026-10-27'),
(910000000000001524, '相交链表', '给你两个单链表的头节点 `headA` 和 `headB` ，请你找出并返回两个单链表相交的起始节点。如果两个链表没有交点，返回 `null` 。

图示两个链表在节点 `c1` 开始相交**：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/160_statement.png)

题目数据 **保证** 整个链式结构中不存在环。

**注意**，函数返回结果后，链表必须 **保持其原始结构** 。

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/160_example_1.png)

```
输入：intersectVal = 8, listA = [4,1,8,4,5], listB = [5,0,1,8,4,5], skipA = 2, skipB = 3
输出：Intersected at ''8''
解释：相交节点的值为 8 （注意，如果两个链表相交则不能为 0）。
从各自的表头开始算起，链表 A 为 [4,1,8,4,5]，链表 B 为 [5,0,1,8,4,5]。
在 A 中，相交节点前有 2 个节点；在 B 中，相交节点前有 3 个节点。
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/160_example_2.png)

```
输入：intersectVal = 2, listA = [0,9,1,2,4], listB = [3,2,4], skipA = 3, skipB = 1
输出：Intersected at ''2''
解释：相交节点的值为 2 （注意，如果两个链表相交则不能为 0）。
从各自的表头开始算起，链表 A 为 [0,9,1,2,4]，链表 B 为 [3,2,4]。
在 A 中，相交节点前有 3 个节点；在 B 中，相交节点前有 1 个节点。
```

**示例 3：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/160_example_3.png)

```
输入：intersectVal = 0, listA = [2,6,4], listB = [1,5], skipA = 3, skipB = 2
输出：null
解释：从各自的表头开始算起，链表 A 为 [2,6,4]，链表 B 为 [1,5]。
由于这两个链表不相交，所以 intersectVal 必须为 0，而 skipA 和 skipB 可以是任意值。
这两个链表不相交，因此返回 null 。
```

**提示：**

- `listA` 中节点数目为 `m`

- `listB` 中节点数目为 `n`

- `0 <= m, n <= 3 * 10^4`

- `1 <= Node.val <= 10^5`

- `0 <= skipA <= m`

- `0 <= skipB <= n`

- 如果 `listA` 和 `listB` 没有交点，`intersectVal` 为 `0`

- 如果 `listA` 和 `listB` 有交点，`intersectVal == listA[skipA + 1] == listB[skipB + 1]`

**进阶：**你能否设计一个时间复杂度 `O(n)` 、仅用 `O(1)` 内存的解决方案？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/intersection-of-two-linked-lists/', '36e6e64a02101008c9cf57e42a9cc12eb1fb28575486837b7d5d46f405b4c910', 1, 160, 'intersection-of-two-linked-lists', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 203, '2026-09-18'),
(910000000000001642, '接雨水', '给定 *n* 个非负整数表示每个宽度为 1 的柱子的高度图，计算按此排列的柱子，下雨之后能接多少雨水。

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/10/22/rainwatertrap.png)

```
输入：height = [0,1,0,2,1,0,1,3,2,1,2,1]
输出：6
解释：上面是由数组 [0,1,0,2,1,0,1,3,2,1,2,1] 表示的高度图，在这种情况下，可以接 6 个单位的雨水（蓝色部分表示雨水）。
```

**示例 2：**

```
输入：height = [4,2,0,3,2,5]
输出：9
```

**提示：**

- `n == height.length`

- `0 <= n <= 3 * 10^4`

- `0 <= height[i] <= 10^5`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/trapping-rain-water/', 'ebe061d1340493bae6f5e7e17d50916036bd4284af866af92dfb6aa9359c127d', 1, 42, 'trapping-rain-water', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 202, '2026-11-24'),
(910000000000001665, '删除链表的倒数第N个节点', '给你一个链表，删除链表的倒数第 `n`**个结点，并且返回链表的头结点。

**进阶：**你能尝试使用一趟扫描实现吗？

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/03/remove_ex1.jpg)

```
输入：head = [1,2,3,4,5], n = 2
输出：[1,2,3,5]
```

**示例 2：**

```
输入：head = [1], n = 1
输出：[]
```

**示例 3：**

```
输入：head = [1,2], n = 1
输出：[1]
```

**提示：**

- 链表中结点的数目为 `sz`

- `1 <= sz <= 30`

- `0 <= Node.val <= 100`

- `1 <= n <= sz`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-nth-node-from-end-of-list/', '40268e5aaf26abddf6c8f42591da856bd5c15d765fd9ef8fa1afc153cc2e642c', 1, 19, 'remove-nth-node-from-end-of-list', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 190, '2026-10-28'),
(910000000000001602, '删除排序链表中的重复元素 II', '存在一个按升序排列的链表，给你这个链表的头节点 `head` ，请你删除链表中所有存在数字重复情况的节点，只保留原始链表中 **没有重复出现****的数字。

返回同样按升序排列的结果链表。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/linkedlist1.jpg)

```
输入：head = [1,2,3,3,4,4,5]
输出：[1,2,5]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/linkedlist2.jpg)

```
输入：head = [1,1,1,2,3]
输出：[2,3]
```

**提示：**

- 链表中节点数目在范围 `[0, 300]` 内

- `-100 <= Node.val <= 100`

- 题目数据保证链表已经按升序排列', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-duplicates-from-sorted-list-ii/', 'ca825d1f22602f112d42eb68692a209d28a17d3f68e17705c35a1ee033a92674', 1, 82, 'remove-duplicates-from-sorted-list-ii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","商汤","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 188, '2026-11-23'),
(910000000000001591, '复原IP地址', '给定一个只包含数字的字符串，用以表示一个 IP 地址，返回所有可能从 `s` 获得的 **有效 IP 地址**。你可以按任何顺序返回答案。

**有效 IP 地址** 正好由四个整数（每个整数位于 0 到 255 之间组成，且不能含有前导 `0`），整数之间用 `''.''` 分隔。

例如："0.1.2.201" 和 "192.168.1.1" 是 **有效** IP 地址，但是 "0.011.255.245"、"192.168.1.312" 和 "192.168@1.1" 是 **无效** IP 地址。

**示例 1：**

```
输入：s = "25525511135"
输出：["255.255.11.135","255.255.111.35"]
```

**示例 2：**

```
输入：s = "0000"
输出：["0.0.0.0"]
```

**示例 3：**

```
输入：s = "1111"
输出：["1.1.1.1"]
```

**示例 4：**

```
输入：s = "010010"
输出：["0.10.0.10","0.100.1.0"]
```

**示例 5：**

```
输入：s = "101023"
输出：["1.0.10.23","1.0.102.3","10.1.0.23","10.10.2.3","101.0.2.3"]
```

**提示：**

- `0 <= s.length <= 3000`

- `s` 仅由数字组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/restore-ip-addresses/', '352956515255beae72ea2182001446f008db0fc6a229c976e111e66700a453bd', 1, 93, 'restore-ip-addresses', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 188, '2026-09-15'),
(910000000000001560, '二叉树中的最大路径和', '**路径** 被定义为一条从树中任意节点出发，沿父节点-子节点连接，达到任意节点的序列。同一个节点在一条路径序列中 **至多出现一次** 。该路径**至少包含一个**节点，且不一定经过根节点。

**路径和** 是路径中各节点值的总和。

给你一个二叉树的根节点 `root` ，返回其 **最大路径和** 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/13/exx1.jpg)

```
输入：root = [1,2,3]
输出：6
解释：最优路径是 2 -> 1 -> 3 ，路径和为 2 + 1 + 3 = 6
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/13/exx2.jpg)

```
输入：root = [-10,9,20,null,null,15,7]
输出：42
解释：最优路径是 15 -> 20 -> 7 ，路径和为 15 + 20 + 7 = 42
```

**提示：**

- 树中节点数目范围是 `[1, 3 * 10^4]`

- `-1000 <= Node.val <= 1000`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-maximum-path-sum/', '457c071efe1bf6f7c383d5dc702805131ccad46524cd37e22d9597d84cdca1ac', 1, 124, 'binary-tree-maximum-path-sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 187, '2026-10-13'),
(910000000000001680, '寻找两个正序数组的中位数', '给定两个大小分别为 `m` 和 `n` 的正序（从小到大）数组 `nums1` 和 `nums2`。请你找出并返回这两个正序数组的 **中位数** 。

**示例 1：**

```
输入：nums1 = [1,3], nums2 = [2]
输出：2.00000
解释：合并数组 = [1,2,3] ，中位数 2
```

**示例 2：**

```
输入：nums1 = [1,2], nums2 = [3,4]
输出：2.50000
解释：合并数组 = [1,2,3,4] ，中位数 (2 + 3) / 2 = 2.5
```

**示例 3：**

```
输入：nums1 = [0,0], nums2 = [0,0]
输出：0.00000
```

**示例 4：**

```
输入：nums1 = [], nums2 = [1]
输出：1.00000
```

**示例 5：**

```
输入：nums1 = [2], nums2 = []
输出：2.00000
```

**提示：**

- `nums1.length == m`

- `nums2.length == n`

- `0 <= m <= 1000`

- `0 <= n <= 1000`

- `1 <= m + n <= 2000`

- `-10^6 <= nums1[i], nums2[i] <= 10^6`

**进阶：**你能设计一个时间复杂度为 `O(log (m+n))` 的算法解决此问题吗？', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/median-of-two-sorted-arrays/', '598c6e2a11f43aed4b8b8b88bd4546915344cb7d905625e8f9ca9dcbfd31bd02', 1, 4, 'median-of-two-sorted-arrays', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","京东","华为","商汤","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]', 175, '2026-09-28'),
(910000000000001542, '环形链表 II', '给定一个链表，返回链表开始入环的第一个节点。 如果链表无环，则返回 `null`。

为了表示给定链表中的环，我们使用整数 `pos` 来表示链表尾连接到链表中的位置（索引从 0 开始）。 如果 `pos` 是 `-1`，则在该链表中没有环。**注意，`pos` 仅仅是用于标识环的情况，并不会作为参数传递到函数中。**

**说明：**不允许修改给定的链表。

**进阶：**

- 你是否可以使用 `O(1)` 空间解决此题？

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist.png)

```
输入：head = [3,2,0,-4], pos = 1
输出：返回索引为 1 的链表节点
解释：链表中有一个环，其尾部连接到第二个节点。
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist_test2.png)

```
输入：head = [1,2], pos = 0
输出：返回索引为 0 的链表节点
解释：链表中有一个环，其尾部连接到第一个节点。
```

**示例 3：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/07/circularlinkedlist_test3.png)

```
输入：head = [1], pos = -1
输出：返回 null
解释：链表中没有环。
```

**提示：**

- 链表中节点的数目范围在范围 `[0, 10^4]` 内

- `-10^5 <= Node.val <= 10^5`

- `pos` 的值为 `-1` 或者链表中的一个有效索引', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/linked-list-cycle-ii/', '39ae5651fb6e873ff4ca3bd5e456994381a2be37c9037937496bc055379bae17', 1, 142, 'linked-list-cycle-ii', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","商汤","字节跳动","小米","小红书","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 173, '2026-11-19'),
(910000000000001519, '比较版本号', '给你两个版本号 `version1` 和 `version2` ，请你比较它们。

版本号由一个或多个修订号组成，各修订号由一个 `''.''` 连接。每个修订号由 **多位数字** 组成，可能包含 **前导零** 。每个版本号至少包含一个字符。修订号从左到右编号，下标从 0 开始，最左边的修订号下标为 0 ，下一个修订号下标为 1 ，以此类推。例如，`2.5.33` 和 `0.1` 都是有效的版本号。

比较版本号时，请按从左到右的顺序依次比较它们的修订号。比较修订号时，只需比较 **忽略任何前导零后的整数值** 。也就是说，修订号 `1` 和修订号 `001` **相等**。如果版本号没有指定某个下标处的修订号，则该修订号视为 `0` 。例如，版本 `1.0` 小于版本 `1.1` ，因为它们下标为 `0` 的修订号相同，而下标为 `1` 的修订号分别为 `0` 和 `1` ，`0 < 1` 。

返回规则如下：

- 如果 `version1 > version2` 返回 `1`，

- 如果 `version1 < version2` 返回 `-1`，

- 除此之外返回 `0`。

**示例 1：**

```
输入：version1 = "1.01", version2 = "1.001"
输出：0
解释：忽略前导零，"01" 和 "001" 都表示相同的整数 "1"
```

**示例 2：**

```
输入：version1 = "1.0", version2 = "1.0.0"
输出：0
解释：version1 没有指定下标为 2 的修订号，即视为 "0"
```

**示例 3：**

```
输入：version1 = "0.1", version2 = "1.1"
输出：-1
解释：version1 中下标为 0 的修订号是 "0"，version2 中下标为 0 的修订号是 "1" 。0 < 1，所以 version1 < version2
```

**示例 4：**

```
输入：version1 = "1.0.1", version2 = "1"
输出：1
```

**示例 5：**

```
输入：version1 = "7.5.2.4", version2 = "7.5.3"
输出：-1
```

**提示：**

- `1 <= version1.length, version2.length <= 500`

- `version1` 和 `version2` 仅包含数字和 `''.''`

- `version1` 和 `version2` 都是 **有效版本号**

- `version1` 和 `version2` 的所有修订号都可以存储在 **32 位整数** 中', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/compare-version-numbers/', '1a20f47e08d544baace5500fd406de08ed53200d43f555040e7d2a51f977b953', 1, 165, 'compare-version-numbers', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 167, '2026-09-06'),
(910000000000001485, '二叉树的右视图', '给定一个二叉树的 **根节点** `root`，想象自己站在它的右侧，按照从顶部到底部的顺序，返回从右侧所能看到的节点值。

**示例 1:**

![题目配图](https://assets.leetcode.com/uploads/2021/02/14/tree.jpg)

```
输入: [1,2,3,null,5,null,4]
输出: [1,3,4]
```

**示例 2:**

```
输入: [1,null,3]
输出: [1,3]
```

**示例 3:**

```
输入: []
输出: []
```

**提示:**

- 二叉树的节点个数的范围是 `[0,100]`

- `-100 <= Node.val <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-right-side-view/', '5ad08765245314a3a0160470c962a36989706dd9910410ed33d269f1d831d28b', 1, 199, 'binary-tree-right-side-view', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 161, '2026-05-28'),
(910000000000001445, '滑动窗口最大值', '给你一个整数数组 `nums`，有一个大小为 `k`**的滑动窗口从数组的最左侧移动到数组的最右侧。你只可以看到在滑动窗口内的 `k` 个数字。滑动窗口每次只向右移动一位。

返回滑动窗口中的最大值。

**示例 1：**

```
输入：nums = [1,3,-1,-3,5,3,6,7], k = 3
输出：[3,3,5,5,6,7]
解释：
滑动窗口的位置                最大值
---------------               -----
[1  3  -1] -3  5  3  6  7       3
 1 [3  -1  -3] 5  3  6  7       3
 1  3 [-1  -3  5] 3  6  7       5
 1  3  -1 [-3  5  3] 6  7       5
 1  3  -1  -3 [5  3  6] 7       6
 1  3  -1  -3  5 [3  6  7]      7
```

**示例 2：**

```
输入：nums = [1], k = 1
输出：[1]
```

**示例 3：**

```
输入：nums = [1,-1], k = 1
输出：[1,-1]
```

**示例 4：**

```
输入：nums = [9,11], k = 2
输出：[11]
```

**示例 5：**

```
输入：nums = [4,-2], k = 2
输出：[4]
```

**提示：**

- `1 <= nums.length <= 10^5`

- `-10^4 <= nums[i] <= 10^4`

- `1 <= k <= nums.length`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sliding-window-maximum/', 'c10b353aa9fb9221c2b42074e8ac291e26e7cd2b12d3be14689e5621302ea905', 1, 239, 'sliding-window-maximum', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 160, '2026-10-29'),
(910000000000001662, '括号生成', '数字 `n` 代表生成括号的对数，请你设计一个函数，用于能够生成所有可能的并且 **有效的**括号组合。

有效括号组合需满足：左括号必须以正确的顺序闭合。

**示例 1：**

```
输入：n = 3
输出：["((()))","(()())","(())()","()(())","()()()"]
```

**示例 2：**

```
输入：n = 1
输出：["()"]
```

**提示：**

- `1 <= n <= 8`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/generate-parentheses/', '379030da3dee6d70e5cb7f95abd2f940db3d2ce03a93b4cb5ae64dd645a1694a', 1, 22, 'generate-parentheses', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 153, '2026-09-07'),
(910000000000000925, '二分查找', '给定一个 `n` 个元素有序的（升序）整型数组 `nums` 和一个目标值 `target`  ，写一个函数搜索 `nums` 中的 `target`，如果目标值存在返回下标，否则返回 `-1`。

**示例 1:**

```
输入: nums = [-1,0,3,5,9,12], target = 9
输出: 4
解释: 9 出现在 nums 中并且下标为 4
```

**示例 2:**

```
输入: nums = [-1,0,3,5,9,12], target = 2
输出: -1
解释: 2 不存在 nums 中因此返回 -1
```

**提示：**

- 你可以假设 `nums` 中的所有元素是不重复的。

- `n` 将在 `[1, 10000]`之间。

- `nums` 的每个元素都将在 `[-9999, 9999]`之间。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-search/', 'fd91ebab8b0bea43c8061d06e737ef840d08fb54d33fd031dbdbfc1eb3f7405c', 1, 704, 'binary-search', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 153, '2026-07-30'),
(910000000000001652, '最长有效括号', '给你一个只包含 `''(''` 和 `'')''` 的字符串，找出最长有效（格式正确且连续）括号子串的长度。

**示例 1：**

```
输入：s = "(()"
输出：2
解释：最长有效括号子串是 "()"
```

**示例 2：**

```
输入：s = ")()())"
输出：4
解释：最长有效括号子串是 "()()"
```

**示例 3：**

```
输入：s = ""
输出：0
```

**提示：**

- `0 <= s.length <= 3 * 10^4`

- `s[i]` 为 `''(''` 或 `'')''`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-valid-parentheses/', '0ab8a72d5144702bff0db69b6a6aecf5f3a19587d2f09a38d360212992d26710', 1, 32, 'longest-valid-parentheses', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","图森","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 151, '2026-10-28'),
(910000000000001615, 'x 的平方根', '给你一个非负整数 `x` ，计算并返回 `x` 的 **平方根** 。

由于返回类型是整数，结果只保留 **整数部分**，小数部分将被 **舍去 。**

**注意：**不允许使用任何内置指数函数和算符，例如 `pow(x, 0.5)` 或者 `x ** 0.5` 。

**示例 1：**

```
输入：x = 4
输出：2
```

**示例 2：**

```
输入：x = 8
输出：2
解释：8 的平方根是 2.82842..., 由于返回类型是整数，小数部分将被舍去。
```

**提示：**

- `0 <= x <= 2^31 - 1`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sqrtx/', 'c9d0fbeb370c0b1dfcafee526e08d4f84cc1f90910da842cdc46e8945dbf0063', 1, 69, 'sqrtx', '["CODETOP","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 150, '2026-09-21'),
(910000000000001536, '排序链表', '给你链表的头结点 `head` ，请将其按 **升序** 排列并返回 **排序后的链表** 。

**进阶：**

- 你可以在 `O(n log n)` 时间复杂度和常数级空间复杂度下，对链表进行排序吗？

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/14/sort_list_1.jpg)

```
输入：head = [4,2,1,3]
输出：[1,2,3,4]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/14/sort_list_2.jpg)

```
输入：head = [-1,5,3,4,0]
输出：[-1,0,3,4,5]
```

**示例 3：**

```
输入：head = []
输出：[]
```

**提示：**

- 链表中节点的数目在范围 `[0, 5 * 10^4]` 内

- `-10^5 <= Node.val <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sort-list/', '269015e4b6e76c8d8d6464dcdab82d34a6d5ad4eda3c93dacff64dadf5ed0f23', 1, 148, 'sort-list', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","商汤","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 149, '2026-09-28'),
(910000000000001590, '二叉树的中序遍历', '给定一个二叉树的根节点 `root` ，返回它的 **中序** 遍历。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_1.jpg)

```
输入：root = [1,null,2,3]
输出：[1,3,2]
```

**示例 2：**

```
输入：root = []
输出：[]
```

**示例 3：**

```
输入：root = [1]
输出：[1]
```

**示例 4：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_5.jpg)

```
输入：root = [1,2]
输出：[2,1]
```

**示例 5：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_4.jpg)

```
输入：root = [1,null,2]
输出：[1,2]
```

**提示：**

- 树中节点数目在范围 `[0, 100]` 内

- `-100 <= Node.val <= 100`

**进阶:** 递归算法很简单，你可以通过迭代算法完成吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-inorder-traversal/', '77bf847c47177751e2e4b7dc95aa989889cf3116a01dbc8e828e00dae18d09b0', 1, 94, 'binary-tree-inorder-traversal', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 144, '2026-05-21'),
(910000000000001452, '用栈实现队列', '请你仅使用两个栈实现先入先出队列。队列应当支持一般队列支持的所有操作（`push`、`pop`、`peek`、`empty`）：

实现 `MyQueue` 类：

- `void push(int x)` 将元素 x 推到队列的末尾

- `int pop()` 从队列的开头移除并返回元素

- `int peek()` 返回队列开头的元素

- `boolean empty()` 如果队列为空，返回 `true` ；否则，返回 `false`

**说明：**

- 你只能使用标准的栈操作 —— 也就是只有 `push to top`, `peek/pop from top`, `size`, 和 `is empty` 操作是合法的。

- 你所使用的语言也许不支持栈。你可以使用 list 或者 deque（双端队列）来模拟一个栈，只要是标准的栈操作即可。

**进阶：**

- 你能否实现每个操作均摊时间复杂度为 `O(1)` 的队列？换句话说，执行 `n` 个操作的总时间复杂度为 `O(n)` ，即使其中一个操作可能花费较长时间。

**示例：**

```
输入：
["MyQueue", "push", "push", "peek", "pop", "empty"]
[[], [1], [2], [], [], []]
输出：
[null, null, null, 1, 1, false]

解释：
MyQueue myQueue = new MyQueue();
myQueue.push(1); // queue is: [1]
myQueue.push(2); // queue is: [1, 2] (leftmost is front of the queue)
myQueue.peek(); // return 1
myQueue.pop(); // return 1, queue is [2]
myQueue.empty(); // return false
```

**提示：**

- `1 <= x <= 9`

- 最多调用 `100` 次 `push`、`pop`、`peek` 和 `empty`

- 假设所有操作都是有效的 （例如，一个空的队列不会调用 `pop` 或者 `peek` 操作）', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/implement-queue-using-stacks/', '68be2897c0cfc4b838d1465dd2c2ce908c9c28b2885e42f0900087d525a2d52a', 1, 232, 'implement-queue-using-stacks', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","小米","小红书","微软","快手","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 143, '2026-03-09'),
(910000000000001653, '下一个排列', '实现获取 **下一个排列** 的函数，算法需要将给定数字序列重新排列成字典序中下一个更大的排列（即，组合出下一个更大的整数）。

如果不存在下一个更大的排列，则将数字重新排列成最小的排列（即升序排列）。

必须** 原地 **修改，只允许使用额外常数空间。

**示例 1：**

```
输入：nums = [1,2,3]
输出：[1,3,2]
```

**示例 2：**

```
输入：nums = [3,2,1]
输出：[1,2,3]
```

**示例 3：**

```
输入：nums = [1,1,5]
输出：[1,5,1]
```

**示例 4：**

```
输入：nums = [1]
输出：[1]
```

**提示：**

- `1 <= nums.length <= 100`

- `0 <= nums[i] <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/next-permutation/', 'dd990947a0918a3ecbf45f3159b7fbed5908d10baf8d18d06bb076bdeb54dad2', 1, 31, 'next-permutation', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 137, '2026-08-05'),
(910000000000001362, '零钱兑换', '给你一个整数数组 `coins` ，表示不同面额的硬币；以及一个整数 `amount` ，表示总金额。

计算并返回可以凑成总金额所需的 **最少的硬币个数** 。如果没有任何一种硬币组合能组成总金额，返回 `-1` 。

你可以认为每种硬币的数量是无限的。

**示例 1：**

```
输入：coins = [1, 2, 5], amount = 11
输出：3
解释：11 = 5 + 5 + 1
```

**示例 2：**

```
输入：coins = [2], amount = 3
输出：-1
```

**示例 3：**

```
输入：coins = [1], amount = 0
输出：0
```

**示例 4：**

```
输入：coins = [1], amount = 1
输出：1
```

**示例 5：**

```
输入：coins = [1], amount = 2
输出：2
```

**提示：**

- `1 <= coins.length <= 12`

- `1 <= coins[i] <= 2^31 - 1`

- `0 <= amount <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/coin-change/', 'e9d8efa44901db7af1f17bd12555c809ee709906241311c008484f2962809e5a', 1, 322, 'coin-change', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 136, '2026-11-02'),
(910000000000001614, '爬楼梯', '假设你正在爬楼梯。需要 *n* 阶你才能到达楼顶。

每次你可以爬 1 或 2 个台阶。你有多少种不同的方法可以爬到楼顶呢？

**注意：**给定 *n* 是一个正整数。

**示例 1：**

```
输入： 2
输出： 2
解释： 有两种方法可以爬到楼顶。
1.  1 阶 + 1 阶
2.  2 阶
```

**示例 2：**

```
输入： 3
输出： 3
解释： 有三种方法可以爬到楼顶。
1.  1 阶 + 1 阶 + 1 阶
2.  1 阶 + 2 阶
3.  2 阶 + 1 阶
```', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/climbing-stairs/', '13ae5e27a3f4e204fee239609aa153f0af5c596da3e04c8384bb46ca0a174470', 1, 70, 'climbing-stairs', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 136, '2026-10-22'),
(910000000000001608, '最小覆盖子串', '给你一个字符串 `s` 、一个字符串 `t` 。返回 `s` 中涵盖 `t` 所有字符的最小子串。如果 `s` 中不存在涵盖 `t` 所有字符的子串，则返回空字符串 `""` 。

**注意：**

- 对于 `t` 中重复字符，我们寻找的子字符串中该字符数量必须不少于 `t` 中该字符数量。

- 如果 `s` 中存在这样的子串，我们保证它是唯一的答案。

**示例 1：**

```
输入：s = "ADOBECODEBANC", t = "ABC"
输出："BANC"
```

**示例 2：**

```
输入：s = "a", t = "a"
输出："a"
```

**示例 3:**

```
输入: s = "a", t = "aa"
输出: ""
解释: t 中两个字符 ''a'' 均应包含在 s 的子串中，
因此没有符合条件的子字符串，返回空字符串。
```

**提示：**

- `1 <= s.length, t.length <= 10^5`

- `s` 和 `t` 由英文字母组成

**进阶：**你能设计一个在 `o(n)` 时间内解决此问题的算法吗？', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/minimum-window-substring/', '9d2f700060c7f1a2a21cafaf10f813c531fcc197526288a49039aaba9d924ac3', 1, 76, 'minimum-window-substring', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 136, '2026-08-30'),
(910000000000001641, '字符串相乘', '给定两个以字符串形式表示的非负整数 `num1` 和 `num2`，返回 `num1` 和 `num2` 的乘积，它们的乘积也表示为字符串形式。

**示例 1:**

```
输入: num1 = "2", num2 = "3"
输出: "6"
```

**示例 2:**

```
输入: num1 = "123", num2 = "456"
输出: "56088"
```

**说明：**

- `num1` 和 `num2` 的长度小于110。

- `num1` 和 `num2` 只包含数字 `0-9`。

- `num1` 和 `num2` 均不以零开头，除非是数字 0 本身。

- **不能使用任何标准库的大数类型（比如 BigInteger）**或**直接将输入转换为整数来处理**。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/multiply-strings/', 'eacf066c914f87861775b521a420e9f6cb90f3ab44491580bf837f3e4b2fccdf', 1, 43, 'multiply-strings', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","图森","字节跳动","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 134, '2026-10-19'),
(910000000000001676, '字符串转换整数 (atoi)', '请你来实现一个 `myAtoi(string s)` 函数，使其能将字符串转换成一个 32 位有符号整数（类似 C/C++ 中的 `atoi` 函数）。

函数 `myAtoi(string s)` 的算法如下：

- 读入字符串并丢弃无用的前导空格

- 检查下一个字符（假设还未到字符末尾）为正还是负号，读取该字符（如果有）。 确定最终结果是负数还是正数。 如果两者都不存在，则假定结果为正。

- 读入下一个字符，直到到达下一个非数字字符或到达输入的结尾。字符串的其余部分将被忽略。

- 将前面步骤读入的这些数字转换为整数（即，"123" -> 123， "0032" -> 32）。如果没有读入数字，则整数为 `0` 。必要时更改符号（从步骤 2 开始）。

- 如果整数数超过 32 位有符号整数范围 `[−2^31, 2^31 − 1]` ，需要截断这个整数，使其保持在这个范围内。具体来说，小于 `−2^31` 的整数应该被固定为 `−2^31` ，大于 `2^31 − 1` 的整数应该被固定为 `2^31 − 1` 。

- 返回整数作为最终结果。

**注意：**

- 本题中的空白字符只包括空格字符 `'' ''` 。

- 除前导空格或数字后的其余字符串外，**请勿忽略** 任何其他字符。

**示例 1：**

```
输入：s = "42"
输出：42
解释：加粗的字符串为已经读入的字符，插入符号是当前读取的字符。
第 1 步："42"（当前没有读入字符，因为没有前导空格）
         ^
第 2 步："42"（当前没有读入字符，因为这里不存在 ''-'' 或者 ''+''）
         ^
第 3 步："42"（读入 "42"）
           ^
解析得到整数 42 。
由于 "42" 在范围 [-2^31, 2^31 - 1] 内，最终结果为 42 。
```

**示例 2：**

```
输入：s = "   -42"
输出：-42
解释：
第 1 步："   -42"（读入前导空格，但忽视掉）
            ^
第 2 步："   -42"（读入 ''-'' 字符，所以结果应该是负数）
             ^
第 3 步："   -42"（读入 "42"）
               ^
解析得到整数 -42 。
由于 "-42" 在范围 [-2^31, 2^31 - 1] 内，最终结果为 -42 。
```

**示例 3：**

```
输入：s = "4193 with words"
输出：4193
解释：
第 1 步："4193 with words"（当前没有读入字符，因为没有前导空格）
         ^
第 2 步："4193 with words"（当前没有读入字符，因为这里不存在 ''-'' 或者 ''+''）
         ^
第 3 步："4193 with words"（读入 "4193"；由于下一个字符不是一个数字，所以读入停止）
             ^
解析得到整数 4193 。
由于 "4193" 在范围 [-2^31, 2^31 - 1] 内，最终结果为 4193 。
```

**示例 4：**

```
输入：s = "words and 987"
输出：0
解释：
第 1 步："words and 987"（当前没有读入字符，因为没有前导空格）
         ^
第 2 步："words and 987"（当前没有读入字符，因为这里不存在 ''-'' 或者 ''+''）
         ^
第 3 步："words and 987"（由于当前字符 ''w'' 不是一个数字，所以读入停止）
         ^
解析得到整数 0 ，因为没有读入任何数字。
由于 0 在范围 [-2^31, 2^31 - 1] 内，最终结果为 0 。
```

**示例 5：**

```
输入：s = "-91283472332"
输出：-2147483648
解释：
第 1 步："-91283472332"（当前没有读入字符，因为没有前导空格）
         ^
第 2 步："-91283472332"（读入 ''-'' 字符，所以结果应该是负数）
          ^
第 3 步："-91283472332"（读入 "91283472332"）
                     ^
解析得到整数 -91283472332 。
由于 -91283472332 小于范围 [-2^31, 2^31 - 1] 的下界，最终结果被截断为 -2^31 = -2147483648 。
```

**提示：**

- `0 <= s.length <= 200`

- `s` 由英文字母（大写和小写）、数字（`0-9`）、`'' ''`、`''+''`、`''-''` 和 `''.''` 组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/string-to-integer-atoi/', 'e1fa5e6c0404951ad0b63f0a13e273a519fa82b2892f9e13e91d7b2e1350c6bf', 1, 8, 'string-to-integer-atoi', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 134, '2026-08-24'),
(910000000000001682, '两数相加', '给你两个 **非空** 的链表，表示两个非负的整数。它们每位数字都是按照 **逆序** 的方式存储的，并且每个节点只能存储 **一位** 数字。

请你将两个数相加，并以相同形式返回一个表示和的链表。

你可以假设除了数字 0 之外，这两个数都不会以 0 开头。

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2021/01/02/addtwonumber1.jpg)

```
输入：l1 = [2,4,3], l2 = [5,6,4]
输出：[7,0,8]
解释：342 + 465 = 807.
```

**示例 2：**

```
输入：l1 = [0], l2 = [0]
输出：[0]
```

**示例 3：**

```
输入：l1 = [9,9,9,9,9,9,9], l2 = [9,9,9,9]
输出：[8,9,9,9,0,0,0,1]
```

**提示：**

- 每个链表中的节点数在范围 `[1, 100]` 内

- `0 <= Node.val <= 9`

- 题目数据保证列表表示的数字不含前导零', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/add-two-numbers/', 'e3d76be59541de1ab6590316bbbbdbc3794130f430eeb397e463fa16a376cfa2', 1, 2, 'add-two-numbers', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 133, '2026-04-16'),
(910000000000001579, '从前序与中序遍历序列构造二叉树', '给定一棵树的前序遍历 `preorder` 与中序遍历  `inorder`。请构造二叉树并返回其根节点。

**示例 1:**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/tree.jpg)

```
Input: preorder = [3,9,20,15,7], inorder = [9,3,15,20,7]
Output: [3,9,20,null,null,15,7]
```

**示例 2:**

```
Input: preorder = [-1], inorder = [-1]
Output: [-1]
```

**提示:**

- `1 <= preorder.length <= 3000`

- `inorder.length == preorder.length`

- `-3000 <= preorder[i], inorder[i] <= 3000`

- `preorder` 和 `inorder` 均无重复元素

- `inorder` 均出现在 `preorder`

- `preorder` 保证为二叉树的前序遍历序列

- `inorder` 保证为二叉树的中序遍历序列', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/construct-binary-tree-from-preorder-and-inorder-traversal/', '089543aa9a6e5b5425da9d75e8d3fd70f52949be9703eae99309e7a4ff7ba7d1', 1, 105, 'construct-binary-tree-from-preorder-and-inorder-traversal', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 118, '2026-10-08'),
(910000000000001643, '缺失的第一个正数', '给你一个未排序的整数数组 `nums` ，请你找出其中没有出现的最小的正整数。

请你实现时间复杂度为 `O(n)` 并且只使用常数级别额外空间的解决方案。

**示例 1：**

```
输入：nums = [1,2,0]
输出：3
```

**示例 2：**

```
输入：nums = [3,4,-1,1]
输出：2
```

**示例 3：**

```
输入：nums = [7,8,9,11,12]
输出：1
```

**提示：**

- `1 <= nums.length <= 5 * 10^5`

- `-2^31 <= nums[i] <= 2^31 - 1`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/first-missing-positive/', '2c6528ae68adb9df9517640d2f9e6b6d6519916508b16716bac1ab8782de8df6', 1, 41, 'first-missing-positive', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 116, '2026-10-23'),
(910000000000001606, '子集', '给你一个整数数组 `nums` ，数组中的元素 **互不相同** 。返回该数组所有可能的子集（幂集）。

解集 **不能** 包含重复的子集。你可以按 **任意顺序** 返回解集。

**示例 1：**

```
输入：nums = [1,2,3]
输出：[[],[1],[2],[1,2],[3],[1,3],[2,3],[1,2,3]]
```

**示例 2：**

```
输入：nums = [0]
输出：[[],[0]]
```

**提示：**

- `1 <= nums.length <= 10`

- `-10 <= nums[i] <= 10`

- `nums` 中的所有元素 **互不相同**', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/subsets/', 'a717461f3aa1bf950a7ddae7afb499fd4091c9ba8d4b601f3203625ac4031eec', 1, 78, 'subsets', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","华为","商汤","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 108, '2026-08-27'),
(910000000000001533, '翻转字符串里的单词', '给你一个字符串 `s` ，逐个翻转字符串中的所有 **单词** 。

**单词** 是由非空格字符组成的字符串。`s` 中使用至少一个空格将字符串中的 **单词** 分隔开。

请你返回一个翻转 `s` 中单词顺序并用单个空格相连的字符串。

**说明：**

- 输入字符串 `s` 可以在前面、后面或者单词间包含多余的空格。

- 翻转后单词间应当仅用一个空格分隔。

- 翻转后的字符串中不应包含额外的空格。

**示例 1：**

```
输入：s = "the sky is blue"
输出："blue is sky the"
```

**示例 2：**

```
输入：s = "  hello world  "
输出："world hello"
解释：输入字符串可以在前面或者后面包含多余的空格，但是翻转后的字符不能包括。
```

**示例 3：**

```
输入：s = "a good   example"
输出："example good a"
解释：如果两个单词间有多余的空格，将翻转后单词间的空格减少到只含一个。
```

**示例 4：**

```
输入：s = "  Bob    Loves  Alice   "
输出："Alice Loves Bob"
```

**示例 5：**

```
输入：s = "Alice does not even like bob"
输出："bob like even not does Alice"
```

**提示：**

- `1 <= s.length <= 10^4`

- `s` 包含英文大小写字母、数字和空格 `'' ''`

- `s` 中 **至少存在一个** 单词

**进阶：**

- 请尝试使用 `O(1)` 额外空间复杂度的原地解法。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-words-in-a-string/', 'b8278cefeedb2b83a0ac43f4d87cb018cb24f2398ea6661cfbca25c87518ef25', 1, 151, 'reverse-words-in-a-string', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 107, '2026-09-07'),
(910000000000001650, '在排序数组中查找元素的第一个和最后一个位置', '给定一个按照升序排列的整数数组 `nums`，和一个目标值 `target`。找出给定目标值在数组中的开始位置和结束位置。

如果数组中不存在目标值 `target`，返回 `[-1, -1]`。

**进阶：**

- 你可以设计并实现时间复杂度为 `O(log n)` 的算法解决此问题吗？

**示例 1：**

```
输入：nums = [5,7,7,8,8,10], target = 8
输出：[3,4]
```

**示例 2：**

```
输入：nums = [5,7,7,8,8,10], target = 6
输出：[-1,-1]
```

**示例 3：**

```
输入：nums = [], target = 0
输出：[-1,-1]
```

**提示：**

- `0 <= nums.length <= 10^5`

- `-10^9 <= nums[i] <= 10^9`

- `nums` 是一个非递减数组

- `-10^9 <= target <= 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-first-and-last-position-of-element-in-sorted-array/', '0b74cf08888e70273a03aa4fde8475728c14e4c2d7378a0732620589b93ad911', 1, 34, 'find-first-and-last-position-of-element-in-sorted-array', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 104, '2026-10-12'),
(910000000000001290, '字符串解码', '给定一个经过编码的字符串，返回它解码后的字符串。

编码规则为: `k[encoded_string]`，表示其中方括号内部的 *encoded_string* 正好重复 *k* 次。注意 *k* 保证为正整数。

你可以认为输入字符串总是有效的；输入字符串中没有额外的空格，且输入的方括号总是符合格式要求的。

此外，你可以认为原始数据不包含数字，所有的数字只表示重复的次数 *k* ，例如不会出现像 `3a` 或 `2[4]` 的输入。

**示例 1：**

```
输入：s = "3[a]2[bc]"
输出："aaabcbc"
```

**示例 2：**

```
输入：s = "3[a2[c]]"
输出："accaccacc"
```

**示例 3：**

```
输入：s = "2[abc]3[cd]ef"
输出："abcabccdcdcdef"
```

**示例 4：**

```
输入：s = "abc3[cd]xyz"
输出："abccdcdcdxyz"
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/decode-string/', '88f0dd6da061283d5913844f6b08480738f2d5750936e51ab7006612951fab3a', 1, 394, 'decode-string', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 103, '2026-11-03'),
(910000000000000122, '链表中倒数第k个节点', '输入一个链表，输出该链表中倒数第k个节点。为了符合大多数人的习惯，本题从1开始计数，即链表的尾节点是倒数第1个节点。

例如，一个链表有 `6` 个节点，从头节点开始，它们的值依次是 `1、2、3、4、5、6`。这个链表的倒数第 `3` 个节点是值为 `4` 的节点。

**示例：**

```
给定一个链表: 1->2->3->4->5, 和 k = 2.

返回链表 4->5.
```', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lian-biao-zhong-dao-shu-di-kge-jie-dian-lcof/', '204426a145350d90977ea0d973469205eb2765deb87356df987f515ecd9fbdcb', 1, NULL, 'lian-biao-zhong-dao-shu-di-kge-jie-dian-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","小米","微软","快手","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 103, '2026-08-04'),
(910000000000001555, '求根到叶子节点数字之和', '给你一个二叉树的根节点 `root` ，树中每个节点都存放有一个 `0` 到 `9` 之间的数字。

每条从根节点到叶节点的路径都代表一个数字：

- 例如，从根节点到叶节点的路径 `1 -> 2 -> 3` 表示数字 `123` 。

计算从根节点到叶节点生成的 **所有数字之和** 。

**叶节点** 是指没有子节点的节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/num1tree.jpg)

```
输入：root = [1,2,3]
输出：25
解释：
从根到叶子节点路径 1->2 代表数字 12
从根到叶子节点路径 1->3 代表数字 13
因此，数字总和 = 12 + 13 = 25
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/num2tree.jpg)

```
输入：root = [4,9,0,5,1]
输出：1026
解释：
从根到叶子节点路径 4->9->5 代表数字 495
从根到叶子节点路径 4->9->1 代表数字 491
从根到叶子节点路径 4->0 代表数字 40
因此，数字总和 = 495 + 491 + 40 = 1026
```

**提示：**

- 树中节点的数目在范围 `[1, 1000]` 内

- `0 <= Node.val <= 9`

- 树的深度不超过 `10`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sum-root-to-leaf-numbers/', 'a3c4941d0299952acb63686ce52e7b0899c30e8385a53d685a2cecda563638cf', 1, 129, 'sum-root-to-leaf-numbers', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","小米","微软","快手","百度","网易","美团","腾讯"]', 100, '2026-04-22'),
(910000000000001529, '最小栈', '设计一个支持 `push` ，`pop` ，`top` 操作，并能在常数时间内检索到最小元素的栈。

- `push(x)` —— 将元素 x 推入栈中。

- `pop()` —— 删除栈顶的元素。

- `top()` —— 获取栈顶元素。

- `getMin()` —— 检索栈中的最小元素。

**示例:**

```
输入：
["MinStack","push","push","push","getMin","pop","top","getMin"]
[[],[-2],[0],[-3],[],[],[],[]]

输出：
[null,null,null,null,-3,null,0,-2]

解释：
MinStack minStack = new MinStack();
minStack.push(-2);
minStack.push(0);
minStack.push(-3);
minStack.getMin();   --> 返回 -3.
minStack.pop();
minStack.top();      --> 返回 0.
minStack.getMin();   --> 返回 -2.
```

**提示：**

- `pop`、`top` 和 `getMin` 操作总是在 **非空栈** 上调用。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/min-stack/', '19ba06b59e190395fbf856ea88aed5b27a30284dd3fafa7f593dd537aad792f0', 1, 155, 'min-stack', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]', 99, '2026-04-14'),
(910000000000001556, '最长连续序列', '给定一个未排序的整数数组 `nums` ，找出数字连续的最长序列（不要求序列元素在原数组中连续）的长度。

请你设计并实现时间复杂度为 `O(n)`**的算法解决此问题。

**示例 1：**

```
输入：nums = [100,4,200,1,3,2]
输出：4
解释：最长数字连续序列是 [1, 2, 3, 4]。它的长度为 4。
```

**示例 2：**

```
输入：nums = [0,3,7,2,5,8,4,6,0,1]
输出：9
```

**提示：**

- `0 <= nums.length <= 10^5`

- `-10^9 <= nums[i] <= 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-consecutive-sequence/', '56cca5b04c0f48c6716daf281679201b59ce7d8052beaba06329cc5f61226984', 1, 128, 'longest-consecutive-sequence', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 98, '2026-11-20'),
(910000000000001011, '岛屿的最大面积', '给你一个大小为 `m x n` 的二进制矩阵 `grid` 。

**岛屿** 是由一些相邻的 `1` (代表土地) 构成的组合，这里的「相邻」要求两个 `1` 必须在 **水平或者竖直的四个方向上**相邻。你可以假设 `grid` 的四个边缘都被 `0`（代表水）包围着。

岛屿的面积是岛上值为 `1` 的单元格的数目。

计算并返回 `grid` 中最大的岛屿面积。如果没有岛屿，则返回面积为 `0` 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/05/01/maxarea1-grid.jpg)

```
输入：grid = [[0,0,1,0,0,0,0,1,0,0,0,0,0],[0,0,0,0,0,0,0,1,1,1,0,0,0],[0,1,1,0,1,0,0,0,0,0,0,0,0],[0,1,0,0,1,1,0,0,1,0,1,0,0],[0,1,0,0,1,1,0,0,1,1,1,0,0],[0,0,0,0,0,0,0,0,0,0,1,0,0],[0,0,0,0,0,0,0,1,1,1,0,0,0],[0,0,0,0,0,0,0,1,1,0,0,0,0]]
输出：6
解释：答案不应该是 11 ，因为岛屿只能包含水平或垂直这四个方向上的 1 。
```

**示例 2：**

```
输入：grid = [[0,0,0,0,0,0,0,0]]
输出：0
```

**提示：**

- `m == grid.length`

- `n == grid[i].length`

- `1 <= m, n <= 50`

- `grid[i][j]` 为 `0` 或 `1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/max-area-of-island/', '3f6b5a88fc2c2aada3459e76943f662ea688fac1e1d77e93b706e5edf15f491b', 1, 695, 'max-area-of-island', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]', 97, '2026-11-03'),
(910000000000000821, '用 Rand7() 实现 Rand10()', '已有方法 `rand7` 可生成 1 到 7 范围内的均匀随机整数，试写一个方法 `rand10` 生成 1 到 10 范围内的均匀随机整数。

不要使用系统的 `Math.random()` 方法。

**示例 1:**

```
输入: 1
输出: [7]
```

**示例 2:**

```
输入: 2
输出: [8,4]
```

**示例 3:**

```
输入: 3
输出: [8,1,10]
```

**提示:**

- `rand7` 已定义。

- 传入参数: `n` 表示 `rand10` 的调用次数。

**进阶:**

- `rand7()`调用次数的 期望值 是多少 ?

- 你能否尽量少调用 `rand7()` ?', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/implement-rand10-using-rand7/', '85cb15a39be95ca1f341d5eabb7e692ab9597826b095f9c834666a1f1102ced8', 1, 470, 'implement-rand10-using-rand7', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]', 97, '2026-09-20'),
(910000000000001620, '最小路径和', '给定一个包含非负整数的 `m x n` 网格 `grid` ，请找出一条从左上角到右下角的路径，使得路径上的数字总和为最小。

**说明：**每次只能向下或者向右移动一步。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/05/minpath.jpg)

```
输入：grid = [[1,3,1],[1,5,1],[4,2,1]]
输出：7
解释：因为路径 1→3→1→1→1 的总和最小。
```

**示例 2：**

```
输入：grid = [[1,2,3],[4,5,6]]
输出：12
```

**提示：**

- `m == grid.length`

- `n == grid[i].length`

- `1 <= m, n <= 200`

- `0 <= grid[i][j] <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/minimum-path-sum/', 'a1d530edc77e4d456980cfc420ade983b21663d6da14273d9bf9ea0499cc29f6', 1, 64, 'minimum-path-sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 97, '2026-09-17'),
(910000000000001583, '对称二叉树', '给定一个二叉树，检查它是否是镜像对称的。

例如，二叉树 `[1,2,2,3,4,4,3]` 是对称的。

```
1
   / \\
  2   2
 / \\ / \\
3  4 4  3
```

但是下面这个 `[1,2,2,null,3,null,3]` 则不是镜像对称的:

```
1
   / \\
  2   2
   \\   \\
   3    3
```

**进阶：**

你可以运用递归和迭代两种方法解决这个问题吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/symmetric-tree/', '108479be62ed88bdee2c9fd9bbdd5d5d700fa02f21ddf98e8e009e4dc982a856', 1, 101, 'symmetric-tree', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","小米","快手","拼多多","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 97, '2026-07-13'),
(910000000000001645, '组合总和', '给定一个**无重复元素**的正整数数组 `candidates` 和一个正整数 `target` ，找出 `candidates` 中所有可以使数字和为目标数 `target` 的唯一组合。

`candidates` 中的数字可以无限制重复被选取。如果至少一个所选数字数量不同，则两种组合是唯一的。

对于给定的输入，保证和为 `target` 的唯一组合数少于 `150` 个。

**示例 1：**

```
输入: candidates = [2,3,6,7], target = 7
输出: [[7],[2,2,3]]
```

**示例 2：**

```
输入: candidates = [2,3,5], target = 8
输出: [[2,2,2,2],[2,3,3],[3,5]]
```

**示例 3：**

```
输入: candidates = [2], target = 1
输出: []
```

**示例 4：**

```
输入: candidates = [1], target = 1
输出: [[1]]
```

**示例 5：**

```
输入: candidates = [1], target = 2
输出: [[1,1]]
```

**提示：**

- `1 <= candidates.length <= 30`

- `1 <= candidates[i] <= 200`

- `candidate` 中的每个元素都是独一无二的。

- `1 <= target <= 500`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/combination-sum/', 'f0313d17fa5a327440d71c9deb21cb4994f407e5b10e5ae560f677a547d51c44', 1, 39, 'combination-sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]', 96, '2026-05-27'),
(910000000000001580, '二叉树的最大深度', '给定一个二叉树，找出其最大深度。

二叉树的深度为根节点到最远叶子节点的最长路径上的节点数。

**说明:** 叶子节点是指没有子节点的节点。

**示例：**

给定二叉树 `[3,9,20,null,null,15,7]`，

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回它的最大深度 3 。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-depth-of-binary-tree/', 'aa359dfc9c35d704ea622c1195d6d7cbbd6c50cac99e6f2f8b0824be56d6ea0f', 1, 104, 'maximum-depth-of-binary-tree', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 92, '2026-09-14'),
(910000000000001532, '乘积最大子数组', '给你一个整数数组 `nums` ，请你找出数组中乘积最大的连续子数组（该子数组中至少包含一个数字），并返回该子数组所对应的乘积。

**示例 1:**

```
输入: [2,3,-2,4]
输出: 6
解释: 子数组 [2,3] 有最大乘积 6。
```

**示例 2:**

```
输入: [-2,0,-1]
输出: 0
解释: 结果不能为 2, 因为 [-2,-1] 不是子数组。
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-product-subarray/', 'fbc97ae1e85398e0fdfdef355c1fe79fe955b6b11bfa3588a4944dc5b72cfa0e', 1, 152, 'maximum-product-subarray', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 91, '2026-10-20'),
(910000000000001562, '买卖股票的最佳时机 II', '给定一个数组 `prices` ，其中 `prices[i]` 是一支给定股票第 `i` 天的价格。

设计一个算法来计算你所能获取的最大利润。你可以尽可能地完成更多的交易（多次买卖一支股票）。

**注意：**你不能同时参与多笔交易（你必须在再次购买前出售掉之前的股票）。

**示例 1:**

```
输入: prices = [7,1,5,3,6,4]
输出: 7
解释: 在第 2 天（股票价格 = 1）的时候买入，在第 3 天（股票价格 = 5）的时候卖出, 这笔交易所能获得利润 = 5-1 = 4 。
     随后，在第 4 天（股票价格 = 3）的时候买入，在第 5 天（股票价格 = 6）的时候卖出, 这笔交易所能获得利润 = 6-3 = 3 。
```

**示例 2:**

```
输入: prices = [1,2,3,4,5]
输出: 4
解释: 在第 1 天（股票价格 = 1）的时候买入，在第 5 天 （股票价格 = 5）的时候卖出, 这笔交易所能获得利润 = 5-1 = 4 。
     注意你不能在第 1 天和第 2 天接连购买股票，之后再将它们卖出。因为这样属于同时参与了多笔交易，你必须在再次购买前出售掉之前的股票。
```

**示例 3:**

```
输入: prices = [7,6,4,3,1]
输出: 0
解释: 在这种情况下, 没有交易完成, 所以最大利润为 0。
```

**提示：**

- `1 <= prices.length <= 3 * 10^4`

- `0 <= prices[i] <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-ii/', '801131320c0879d9a292990651b92aacfc0cc2b679928554c751bf12811d821c', 1, 122, 'best-time-to-buy-and-sell-stock-ii', '["CODETOP","HIGH_FREQ"]', '["bilibili","亚马逊","华为","字节跳动","小米","微软","快手","滴滴","百度","美团","腾讯","阿里巴巴"]', 91, '2026-06-04'),
(910000000000001574, '平衡二叉树', '给定一个二叉树，判断它是否是高度平衡的二叉树。

本题中，一棵高度平衡二叉树定义为：

一个二叉树*每个节点*的左右两个子树的高度差的绝对值不超过 1 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/06/balance_1.jpg)

```
输入：root = [3,9,20,null,null,15,7]
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/06/balance_2.jpg)

```
输入：root = [1,2,2,3,3,null,null,4,4]
输出：false
```

**示例 3：**

```
输入：root = []
输出：true
```

**提示：**

- 树中的节点数在范围 `[0, 5000]` 内

- `-10^4 <= Node.val <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/balanced-binary-tree/', '75c80a57504ae1ef30c949a63875e5d953a335a380e6bac56751606bb6a05dc6', 1, 110, 'balanced-binary-tree', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 90, '2026-10-19'),
(910000000000001463, '最大正方形', '在一个由 `''0''` 和 `''1''` 组成的二维矩阵内，找到只包含 `''1''` 的最大正方形，并返回其面积。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/26/max1grid.jpg)

```
输入：matrix = [["1","0","1","0","0"],["1","0","1","1","1"],["1","1","1","1","1"],["1","0","0","1","0"]]
输出：4
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/26/max2grid.jpg)

```
输入：matrix = [["0","1"],["1","0"]]
输出：1
```

**示例 3：**

```
输入：matrix = [["0"]]
输出：0
```

**提示：**

- `m == matrix.length`

- `n == matrix[i].length`

- `1 <= m, n <= 300`

- `matrix[i][j]` 为 `''0''` 或 `''1''`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximal-square/', '807dd311968ffb73b3c7c74301a0c633a55a23b53f5b6218d9260d7ec4dbb9e8', 1, 221, 'maximal-square', '["CODETOP","HIGH_FREQ"]', '["bilibili","华为","字节跳动","小米","微软","猿辅导","百度","美团","腾讯","虾皮"]', 89, '2026-11-06'),
(910000000000001450, '回文链表', '给你一个单链表的头节点 `head` ，请你判断该链表是否为回文链表。如果是，返回 `true` ；否则，返回 `false` 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/03/03/pal1linked-list.jpg)

```
输入：head = [1,2,2,1]
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/03/03/pal2linked-list.jpg)

```
输入：head = [1,2]
输出：false
```

**提示：**

- 链表中节点数目在范围`[1, 10^5]` 内

- `0 <= Node.val <= 9`

**进阶：**你能否用 `O(n)` 时间复杂度和 `O(1)` 空间复杂度解决此题？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/palindrome-linked-list/', 'c2412153e7f3f169c04047174065f0d6973ad933d0d68a42f06720d5c8f2f4d9', 1, 234, 'palindrome-linked-list', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","图森","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 88, '2026-10-23'),
(910000000000001505, '最大数', '给定一组非负整数 `nums`，重新排列每个数的顺序（每个数不可拆分）使之组成一个最大的整数。

**注意：**输出结果可能非常大，所以你需要返回一个字符串而不是整数。

**示例 1：**

```
输入：nums = [10,2]
输出："210"
```

**示例 2：**

```
输入：nums = [3,30,34,5,9]
输出："9534330"
```

**示例 3：**

```
输入：nums = [1]
输出："1"
```

**示例 4：**

```
输入：nums = [10]
输出："10"
```

**提示：**

- `1 <= nums.length <= 100`

- `0 <= nums[i] <= 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/largest-number/', '4a7c5481afc968452f1bd82359238336be33f6ed3e4b51bd96cefdf72b7345ba', 1, 179, 'largest-number', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 85, '2026-09-24'),
(910000000000001670, '最长公共前缀', '编写一个函数来查找字符串数组中的最长公共前缀。

如果不存在公共前缀，返回空字符串 `""`。

**示例 1：**

```
输入：strs = ["flower","flow","flight"]
输出："fl"
```

**示例 2：**

```
输入：strs = ["dog","racecar","car"]
输出：""
解释：输入不存在公共前缀。
```

**提示：**

- `1 <= strs.length <= 200`

- `0 <= strs[i].length <= 200`

- `strs[i]` 仅由小写英文字母组成', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-common-prefix/', '56a130cca85fd2954bcb9844503f82f425f6a1182bb29a7f4570deec4a1ebf66', 1, 14, 'longest-common-prefix', '["CODETOP","HIGH_FREQ"]', '["bilibili","亚马逊","京东","字节跳动","小米","微软","快手","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 85, '2026-09-23'),
(910000000000001636, '旋转图像', '给定一个 *n*× *n* 的二维矩阵 `matrix` 表示一个图像。请你将图像顺时针旋转 90 度。

你必须在** 原地** 旋转图像，这意味着你需要直接修改输入的二维矩阵。**请不要**使用另一个矩阵来旋转图像。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/08/28/mat1.jpg)

```
输入：matrix = [[1,2,3],[4,5,6],[7,8,9]]
输出：[[7,4,1],[8,5,2],[9,6,3]]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/08/28/mat2.jpg)

```
输入：matrix = [[5,1,9,11],[2,4,8,10],[13,3,6,7],[15,14,12,16]]
输出：[[15,13,2,5],[14,3,4,1],[12,6,8,9],[16,7,10,11]]
```

**示例 3：**

```
输入：matrix = [[1]]
输出：[[1]]
```

**示例 4：**

```
输入：matrix = [[1,2],[3,4]]
输出：[[3,1],[4,2]]
```

**提示：**

- `matrix.length == n`

- `matrix[i].length == n`

- `1 <= n <= 20`

- `-1000 <= matrix[i][j] <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/rotate-image/', '54c6d366bcbf7510c2a20b95b5ccae379d7020434305c6a0e32bc1a556bf80ff', 1, 48, 'rotate-image', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","商汤","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 85, '2026-08-20'),
(910000000000001444, '搜索二维矩阵 II', '编写一个高效的算法来搜索 `m x n` 矩阵 `matrix` 中的一个目标值 `target` 。该矩阵具有以下特性：

- 每行的元素从左到右升序排列。

- 每列的元素从上到下升序排列。

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/11/25/searchgrid2.jpg)

```
输入：matrix = [[1,4,7,11,15],[2,5,8,12,19],[3,6,9,16,22],[10,13,14,17,24],[18,21,23,26,30]], target = 5
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/11/25/searchgrid.jpg)

```
输入：matrix = [[1,4,7,11,15],[2,5,8,12,19],[3,6,9,16,22],[10,13,14,17,24],[18,21,23,26,30]], target = 20
输出：false
```

**提示：**

- `m == matrix.length`

- `n == matrix[i].length`

- `1 <= n, m <= 300`

- `-10^9 <= matrix[i][j] <= 10^9`

- 每行的所有元素从左到右升序排列

- 每列的所有元素从上到下升序排列

- `-10^9 <= target <= 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/search-a-2d-matrix-ii/', '04bba25ddcb4449abe5410b96c4887254bfe3ef4190c779961de3fca9962d712', 1, 240, 'search-a-2d-matrix-ii', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 85, '2026-07-21'),
(910000000000001142, '和为K的子数组', '给你一个整数数组 `nums` 和一个整数 `k` ，请你统计并返回该数组中和为 `k`** **的连续子数组的个数。

**示例 1：**

```
输入：nums = [1,1,1], k = 2
输出：2
```

**示例 2：**

```
输入：nums = [1,2,3], k = 3
输出：2
```

**提示：**

- `1 <= nums.length <= 2 * 10^4`

- `-1000 <= nums[i] <= 1000`

- `-10^7 <= k <= 10^7`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/subarray-sum-equals-k/', 'e999a7cd970767de42d1c1d7de95833c92342642a145b1a590675c927699904c', 1, 560, 'subarray-sum-equals-k', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 84, '2026-10-27'),
(910000000000001044, '二叉树最大宽度', '给定一个二叉树，编写一个函数来获取这个树的最大宽度。树的宽度是所有层中的最大宽度。这个二叉树与**满二叉树（full binary tree）**结构相同，但一些节点为空。

每一层的宽度被定义为两个端点（该层最左和最右的非空节点，两端点间的`null`节点也计入长度）之间的长度。

**示例 1:**

```
输入:

           1
         /   \\
        3     2
       / \\     \\
      5   3     9

输出: 4
解释: 最大值出现在树的第 3 层，宽度为 4 (5,3,null,9)。
```

**示例 2:**

```
输入:

          1
         /
        3
       / \\
      5   3

输出: 2
解释: 最大值出现在树的第 3 层，宽度为 2 (5,3)。
```

**示例 3:**

```
输入:

          1
         / \\
        3   2
       /
      5

输出: 2
解释: 最大值出现在树的第 2 层，宽度为 2 (3,2)。
```

**示例 4:**

```
输入:

          1
         / \\
        3   2
       /     \\
      5       9
     /         \\
    6           7
输出: 8
解释: 最大值出现在树的第 4 层，宽度为 8 (6,null,null,null,null,null,null,7)。
```

**注意:** 答案在32位有符号整数的表示范围内。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-width-of-binary-tree/', '060dd042ab7db942e7afded008a99eefeb40455c1abbe8f34fdcd28b59a9b3cf', 1, 662, 'maximum-width-of-binary-tree', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮"]', 84, '2026-07-18'),
(910000000000001586, '验证二叉搜索树', '给你一个二叉树的根节点 `root` ，判断其是否是一个有效的二叉搜索树。

**有效** 二叉搜索树定义如下：

- 节点的左子树只包含**小于**当前节点的数。

- 节点的右子树只包含 **大于** 当前节点的数。

- 所有左子树和右子树自身必须也是二叉搜索树。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/12/01/tree1.jpg)

```
输入：root = [2,1,3]
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/12/01/tree2.jpg)

```
输入：root = [5,1,4,null,null,3,6]
输出：false
解释：根节点的值是 5 ，但是右子节点的值是 4 。
```

**提示：**

- 树中节点数目范围在`[1, 10^4]` 内

- `-2^31 <= Node.val <= 2^31 - 1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/validate-binary-search-tree/', '962f640f94a126c4df3aca8ab186f7d1e9bc701e7c6c7fb08bdd6101a1026f00', 1, 98, 'validate-binary-search-tree', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 84, '2026-04-20'),
(910000000000001540, '二叉树的前序遍历', '给你二叉树的根节点 `root` ，返回它节点值的 **前序****遍历。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_1.jpg)

```
输入：root = [1,null,2,3]
输出：[1,2,3]
```

**示例 2：**

```
输入：root = []
输出：[]
```

**示例 3：**

```
输入：root = [1]
输出：[1]
```

**示例 4：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_5.jpg)

```
输入：root = [1,2]
输出：[1,2]
```

**示例 5：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/inorder_4.jpg)

```
输入：root = [1,null,2]
输出：[1,2]
```

**提示：**

- 树中节点数目在范围 `[0, 100]` 内

- `-100 <= Node.val <= 100`

**进阶：**递归算法很简单，你可以通过迭代算法完成吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-preorder-traversal/', '042f6eb9b2a0146b9d91941d89c723db434a85ae52cad4f4048eb80383b8bba4', 1, 144, 'binary-tree-preorder-traversal', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","微软","快手","拼多多","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 84, '2025-04-18'),
(910000000000001156, '二叉树的直径', '给定一棵二叉树，你需要计算它的直径长度。一棵二叉树的直径长度是任意两个结点路径长度中的最大值。这条路径可能穿过也可能不穿过根结点。

**示例 :**

给定二叉树

```
1
         / \\
        2   3
       / \\
      4   5
```

返回 **3**, 它的长度是路径 [4,2,1,3] 或者 [5,2,1,3]。

**注意：**两结点之间的路径长度是以它们之间边的数目表示。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/diameter-of-binary-tree/', '60be2d834c3592d374afb0ac44396500d12f967001cd8158ef8d71a63aa30490', 1, 543, 'diameter-of-binary-tree', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","字节跳动","小米","微软","快手","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]', 83, '2026-11-20'),
(910000000000001522, '寻找峰值', '峰值元素是指其值严格大于左右相邻值的元素。

给你一个整数数组 `nums`，找到峰值元素并返回其索引。数组可能包含多个峰值，在这种情况下，返回 **任何一个峰值** 所在位置即可。

你可以假设 `nums[-1] = nums[n] = -∞` 。

你必须实现时间复杂度为 `O(log n)`**的算法来解决此问题。

**示例 1：**

```
输入：nums = [1,2,3,1]
输出：2
解释：3 是峰值元素，你的函数应该返回其索引 2。
```

**示例 2：**

```
输入：nums = [1,2,1,3,5,6,4]
输出：1 或 5
解释：你的函数可以返回索引 1，其峰值元素为 2；
     或者返回索引 5， 其峰值元素为 6。
```

**提示：**

- `1 <= nums.length <= 1000`

- `-2^31 <= nums[i] <= 2^31 - 1`

- 对于所有有效的 `i` 都有 `nums[i] != nums[i + 1]`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-peak-element/', 'b9160690da604514c9025718f3a6105b75ec70b6bc8520be4bce0e47dc14ec58', 1, 162, 'find-peak-element', '["CODETOP","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小米","微软","拼多多","旷视","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 80, '2026-04-19'),
(910000000000001571, '路径总和 II', '给你二叉树的根节点 `root` 和一个整数目标和 `targetSum` ，找出所有 **从根节点到叶子节点** 路径总和等于给定目标和的路径。

**叶子节点** 是指没有子节点的节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/18/pathsumii1.jpg)

```
输入：root = [5,4,8,11,null,13,4,7,2,null,null,5,1], targetSum = 22
输出：[[5,4,11,2],[5,8,4,5]]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/18/pathsum2.jpg)

```
输入：root = [1,2,3], targetSum = 5
输出：[]
```

**示例 3：**

```
输入：root = [1,2], targetSum = 0
输出：[]
```

**提示：**

- 树中节点总数在范围 `[0, 5000]` 内

- `-1000 <= Node.val <= 1000`

- `-1000 <= targetSum <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/path-sum-ii/', '5d759be87e8274d4788ba24498e4ccc9fd22baca79589799011c2f78605af505', 1, 113, 'path-sum-ii', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮"]', 78, '2026-07-24'),
(910000000000001622, '不同路径', '一个机器人位于一个 `m x n`**网格的左上角 （起始点在下图中标记为 “Start” ）。

机器人每次只能向下或者向右移动一步。机器人试图达到网格的右下角（在下图中标记为 “Finish” ）。

问总共有多少条不同的路径？

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2018/10/22/robot_maze.png)

```
输入：m = 3, n = 7
输出：28
```

**示例 2：**

```
输入：m = 3, n = 2
输出：3
解释：
从左上角开始，总共有 3 条路径可以到达右下角。
1. 向右 -> 向下 -> 向下
2. 向下 -> 向下 -> 向右
3. 向下 -> 向右 -> 向下
```

**示例 3：**

```
输入：m = 7, n = 3
输出：28
```

**示例 4：**

```
输入：m = 3, n = 3
输出：6
```

**提示：**

- `1 <= m, n <= 100`

- 题目数据保证答案小于等于 `2 * 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/unique-paths/', 'cd9a71f0e09d7dff3ac310864b16e628d7f7e3c73a1d9d44cf0cf0f941a6ba9d', 1, 62, 'unique-paths', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","商汤","字节跳动","小米","微软","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 78, '2026-06-01'),
(910000000000001486, '打家劫舍', '你是一个专业的小偷，计划偷窃沿街的房屋。每间房内都藏有一定的现金，影响你偷窃的唯一制约因素就是相邻的房屋装有相互连通的防盗系统，**如果两间相邻的房屋在同一晚上被小偷闯入，系统会自动报警**。

给定一个代表每个房屋存放金额的非负整数数组，计算你**不触动警报装置的情况下**，一夜之内能够偷窃到的最高金额。

**示例 1：**

```
输入：[1,2,3,1]
输出：4
解释：偷窃 1 号房屋 (金额 = 1) ，然后偷窃 3 号房屋 (金额 = 3)。
     偷窃到的最高金额 = 1 + 3 = 4 。
```

**示例 2：**

```
输入：[2,7,9,3,1]
输出：12
解释：偷窃 1 号房屋 (金额 = 2), 偷窃 3 号房屋 (金额 = 9)，接着偷窃 5 号房屋 (金额 = 1)。
     偷窃到的最高金额 = 2 + 9 + 1 = 12 。
```

**提示：**

- `1 <= nums.length <= 100`

- `0 <= nums[i] <= 400`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/house-robber/', '815782767d8af85ef5fd5c7b92cc3b67f38f2add1a2ebef0929796d3e852ed6e', 1, 198, 'house-robber', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","微软","快手","拼多多","携程","滴滴","网易","美团","腾讯","虾皮","阿里巴巴"]', 77, '2026-10-17'),
(910000000000001475, '长度最小的子数组', '给定一个含有 `n`****个正整数的数组和一个正整数 `target`**。**

找出该数组中满足其和****`≥ target`****的长度最小的 **连续子数组** `[nums_l, nums_l+1, ..., nums_r-1, nums_r]` ，并返回其长度**。**如果不存在符合条件的子数组，返回 `0` 。

**示例 1：**

```
输入：target = 7, nums = [2,3,1,2,4,3]
输出：2
解释：子数组 [4,3] 是该条件下的长度最小的子数组。
```

**示例 2：**

```
输入：target = 4, nums = [1,4,4]
输出：1
```

**示例 3：**

```
输入：target = 11, nums = [1,1,1,1,1,1,1,1]
输出：0
```

**提示：**

- `1 <= target <= 10^9`

- `1 <= nums.length <= 10^5`

- `1 <= nums[i] <= 10^5`

**进阶：**

- 如果你已经实现**`O(n)` 时间复杂度的解法, 请尝试设计一个 `O(n log(n))` 时间复杂度的解法。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/minimum-size-subarray-sum/', '579a221ac4d7f3fddd10bdb379c1a323799e8c615f6d087016bdf3c265212c1b', 1, 209, 'minimum-size-subarray-sum', '["CODETOP","HIGH_FREQ"]', '["bilibili","华为","字节跳动","小红书","微软","快手","滴滴","百度","美团","腾讯"]', 74, '2026-07-24'),
(910000000000001660, '两两交换链表中的节点', '给定一个链表，两两交换其中相邻的节点，并返回交换后的链表。

**你不能只是单纯的改变节点内部的值**，而是需要实际的进行节点交换。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/03/swap_ex1.jpg)

```
输入：head = [1,2,3,4]
输出：[2,1,4,3]
```

**示例 2：**

```
输入：head = []
输出：[]
```

**示例 3：**

```
输入：head = [1]
输出：[1]
```

**提示：**

- 链表中节点的数目在范围 `[0, 100]` 内

- `0 <= Node.val <= 100`

**进阶：**你能在不修改链表节点值的情况下解决这个问题吗?（也就是说，仅修改节点本身。）', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/swap-nodes-in-pairs/', 'bfd3ef00dcf278d2832cf11d08ea3ec9f3def9814a6f7901bc75a3007920e4f3', 1, 24, 'swap-nodes-in-pairs', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","微软","快手","拼多多","旷视","滴滴","猿辅导","网易","美团","腾讯","阿里巴巴"]', 73, '2026-06-11'),
(910000000000001545, '单词拆分', '给定一个**非空**字符串 *s* 和一个包含**非空**单词的列表 *wordDict*，判定 *s* 是否可以被空格拆分为一个或多个在字典中出现的单词。

**说明：**

- 拆分时可以重复使用字典中的单词。

- 你可以假设字典中没有重复的单词。

**示例 1：**

```
输入: s = "leetcode", wordDict = ["leet", "code"]
输出: true
解释: 返回 true 因为 "leetcode" 可以被拆分成 "leet code"。
```

**示例 2：**

```
输入: s = "applepenapple", wordDict = ["apple", "pen"]
输出: true
解释: 返回 true 因为 "applepenapple" 可以被拆分成 "apple pen apple"。
     注意你可以重复使用字典中的单词。
```

**示例 3：**

```
输入: s = "catsandog", wordDict = ["cats", "dog", "sand", "and", "cat"]
输出: false
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/word-break/', 'a526a0a3d2335aca7a4c0c8caa91ff5e61d000e4f9f8c4e40474f11d2f362fef', 1, 139, 'word-break', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","微软","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 72, '2026-10-15'),
(910000000000001572, '路径总和', '给你二叉树的根节点 `root` 和一个表示目标和的整数 `targetSum` ，判断该树中是否存在 **根节点到叶子节点** 的路径，这条路径上所有节点值相加等于目标和 `targetSum` 。

**叶子节点** 是指没有子节点的节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/18/pathsum1.jpg)

```
输入：root = [5,4,8,11,null,13,4,7,2,null,null,null,1], targetSum = 22
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/18/pathsum2.jpg)

```
输入：root = [1,2,3], targetSum = 5
输出：false
```

**示例 3：**

```
输入：root = [1,2], targetSum = 0
输出：false
```

**提示：**

- 树中节点的数目在范围 `[0, 5000]` 内

- `-1000 <= Node.val <= 1000`

- `-1000 <= targetSum <= 1000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/path-sum/', '8eecb55acc56dfd552c19bc33cdb1e0b4e00f1e69d3a4d2c845b10d55d5c6b6b', 1, 112, 'path-sum', '["CODETOP","HIGH_FREQ"]', '["商汤","字节跳动","快手","携程","滴滴","猿辅导","美团","腾讯","虾皮"]', 72, '2026-04-14'),
(910000000000000999, '最长重复子数组', '给两个整数数组 `A` 和 `B` ，返回两个数组中公共的、长度最长的子数组的长度。

**示例：**

```
输入：
A: [1,2,3,2,1]
B: [3,2,1,4,7]
输出：3
解释：
长度最长的公共子数组是 [3, 2, 1] 。
```

**提示：**

- `1 <= len(A), len(B) <= 1000`

- `0 <= A[i], B[i] < 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-length-of-repeated-subarray/', '45f7c08555a5eee1c57ff03262bde9d7df372689d918640384d06ac39862151a', 1, 718, 'maximum-length-of-repeated-subarray', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","百度","美团","腾讯","虾皮","阿里巴巴"]', 71, '2026-09-18'),
(910000000000001457, '基本计算器 II', '给你一个字符串表达式 `s` ，请你实现一个基本计算器来计算并返回它的值。

整数除法仅保留整数部分。

**示例 1：**

```
输入：s = "3+2*2"
输出：7
```

**示例 2：**

```
输入：s = " 3/2 "
输出：1
```

**示例 3：**

```
输入：s = " 3+5 / 2 "
输出：5
```

**提示：**

- `1 <= s.length <= 3 * 10^5`

- `s` 由整数和算符 `(''+'', ''-'', ''*'', ''/'')` 组成，中间由一些空格隔开

- `s` 表示一个 **有效表达式**

- 表达式中的所有整数都是非负整数，且在范围 `[0, 2^31 - 1]` 内

- 题目数据保证答案是一个 **32-bit 整数**', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/basic-calculator-ii/', 'b8b249b34896ca22f9ebe913122bcfbd263bdb1dfd0109afd26bc296f8e15495', 1, 227, 'basic-calculator-ii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","微软","快手","拼多多","携程","猿辅导","百度","网易","美团","腾讯","虾皮"]', 70, '2026-11-11'),
(910000000000001477, '课程表', '你这个学期必须选修 `numCourses` 门课程，记为 `0` 到 `numCourses - 1` 。

在选修某些课程之前需要一些先修课程。 先修课程按数组 `prerequisites` 给出，其中 `prerequisites[i] = [a_i, b_i]` ，表示如果要学习课程 `a_i` 则 **必须** 先学习课程  `b_i`_ 。

- 例如，先修课程对 `[0, 1]` 表示：想要学习课程 `0` ，你需要先完成课程 `1` 。

请你判断是否可能完成所有课程的学习？如果可以，返回 `true` ；否则，返回 `false` 。

**示例 1：**

```
输入：numCourses = 2, prerequisites = [[1,0]]
输出：true
解释：总共有 2 门课程。学习课程 1 之前，你需要完成课程 0 。这是可能的。
```

**示例 2：**

```
输入：numCourses = 2, prerequisites = [[1,0],[0,1]]
输出：false
解释：总共有 2 门课程。学习课程 1 之前，你需要先完成​课程 0 ；并且学习课程 0 之前，你还应先完成课程 1 。这是不可能的。
```

**提示：**

- `1 <= numCourses <= 10^5`

- `0 <= prerequisites.length <= 5000`

- `prerequisites[i].length == 2`

- `0 <= a_i, b_i < numCourses`

- `prerequisites[i]` 中的所有课程对 **互不相同**', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/course-schedule/', 'a337d1079e317c17fab3d04a73dfc8439deb97d52afa4187b5bbfda530f8c41b', 1, 207, 'course-schedule', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","商汤","字节跳动","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","美团","腾讯","阿里巴巴"]', 69, '2026-10-11'),
(910000000000001601, '删除排序链表中的重复元素', '存在一个按升序排列的链表，给你这个链表的头节点 `head` ，请你删除所有重复的元素，使每个元素 **只出现一次** 。

返回同样按升序排列的结果链表。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/list1.jpg)

```
输入：head = [1,1,2]
输出：[1,2]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/list2.jpg)

```
输入：head = [1,1,2,3,3]
输出：[1,2,3]
```

**提示：**

- 链表中节点数目在范围 `[0, 300]` 内

- `-100 <= Node.val <= 100`

- 题目数据保证链表已经按升序排列', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-duplicates-from-sorted-list/', 'c85522befada6b5c9376418b1aa9b948687c01e7db4fc3d2b16c0bcb195be7fb', 1, 83, 'remove-duplicates-from-sorted-list', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","小红书","微软","快手","携程","旷视","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 69, '2026-04-23'),
(910000000000001401, '移动零', '给定一个数组 `nums`，编写一个函数将所有 `0` 移动到数组的末尾，同时保持非零元素的相对顺序。

**示例:**

```
输入: [0,1,0,3,12]
输出: [1,3,12,0,0]
```

**说明**:

- 必须在原数组上操作，不能拷贝额外的数组。

- 尽量减少操作次数。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/move-zeroes/', 'c0312c9e5cf2287d8d7c0ef3157f15ba043b9cb5e35b87fd5502aff65783ba49', 1, 283, 'move-zeroes', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 68, '2026-09-09'),
(910000000000001458, '翻转二叉树', '翻转一棵二叉树。

**示例：**

输入：

```
4
   /   \\
  2     7
 / \\   / \\
1   3 6   9
```

输出：

```
4
   /   \\
  7     2
 / \\   / \\
9   6 3   1
```

**备注:**

这个问题是受到 Max Howell 的 原问题 启发的 ：

谷歌：我们90％的工程师使用您编写的软件(Homebrew)，但是您却无法在面试时在白板上写出翻转二叉树这道题，这太糟糕了。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/invert-binary-tree/', '992aea7fe011f6aab5e95a451a1e499429d8b6fdd952f4ed2db9b0dff6bae764', 1, 226, 'invert-binary-tree', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["华为","商汤","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 68, '2026-03-30'),
(910000000000001515, '多数元素', '给定一个大小为 *n*的数组，找到其中的多数元素。多数元素是指在数组中出现次数 **大于** `⌊ n/2 ⌋` 的元素。

你可以假设数组是非空的，并且给定的数组总是存在多数元素。

**示例 1：**

```
输入：[3,2,3]
输出：3
```

**示例 2：**

```
输入：[2,2,1,1,1,2,2]
输出：2
```

**进阶：**

- 尝试设计时间复杂度为 O(n)、空间复杂度为 O(1) 的算法解决此问题。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/majority-element/', '3421b75aff6c76de4f155f54bdf6f3c7a08bab34b979efe08cc36767589b04aa', 1, 169, 'majority-element', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","小米","快手","拼多多","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 68, '2026-03-19'),
(910000000000000978, '每日温度', '请根据每日 `气温` 列表 `temperatures` ，请计算在每一天需要等几天才会有更高的温度。如果气温在这之后都不会升高，请在该位置用 `0` 来代替。

**示例 1:**

```
输入: temperatures = [73,74,75,71,69,72,76,73]
输出: [1,1,4,2,1,1,0,0]
```

**示例 2:**

```
输入: temperatures = [30,40,50,60]
输出: [1,1,1,0]
```

**示例 3:**

```
输入: temperatures = [30,60,90]
输出: [1,1,0]
```

**提示：**

- `1 <= temperatures.length <= 10^5`

- `30 <= temperatures[i] <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/daily-temperatures/', 'ce5e2ed473e6ce71b0c142d2e3065bfe47d4a74ec60204f8366f5e5419e4ced8', 1, 739, 'daily-temperatures', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","字节跳动","微软","拼多多","滴滴","美团","腾讯","虾皮"]', 65, '2026-11-12'),
(910000000000001222, '验证IP地址', '编写一个函数来验证输入的字符串是否是有效的 IPv4 或 IPv6 地址。

- 如果是有效的 IPv4 地址，返回 `"IPv4"` ；

- 如果是有效的 IPv6 地址，返回 `"IPv6"` ；

- 如果不是上述类型的 IP 地址，返回 `"Neither"` 。

**IPv4** 地址由十进制数和点来表示，每个地址包含 4 个十进制数，其范围为 0 - 255， 用(".")分割。比如，`172.16.254.1`；

同时，IPv4 地址内的数不会以 0 开头。比如，地址 `172.16.254.01` 是不合法的。

**IPv6** 地址由 8 组 16 进制的数字来表示，每组表示 16 比特。这些组数字通过 (":")分割。比如,  `2001:0db8:85a3:0000:0000:8a2e:0370:7334` 是一个有效的地址。而且，我们可以加入一些以 0 开头的数字，字母可以使用大写，也可以是小写。所以， `2001:db8:85a3:0:0:8A2E:0370:7334` 也是一个有效的 IPv6 address地址 (即，忽略 0 开头，忽略大小写)。

然而，我们不能因为某个组的值为 0，而使用一个空的组，以至于出现 (::) 的情况。 比如， `2001:0db8:85a3::8A2E:0370:7334` 是无效的 IPv6 地址。

同时，在 IPv6 地址中，多余的 0 也是不被允许的。比如， `02001:0db8:85a3:0000:0000:8a2e:0370:7334` 是无效的。

**示例 1：**

```
输入：IP = "172.16.254.1"
输出："IPv4"
解释：有效的 IPv4 地址，返回 "IPv4"
```

**示例 2：**

```
输入：IP = "2001:0db8:85a3:0:0:8A2E:0370:7334"
输出："IPv6"
解释：有效的 IPv6 地址，返回 "IPv6"
```

**示例 3：**

```
输入：IP = "256.256.256.256"
输出："Neither"
解释：既不是 IPv4 地址，又不是 IPv6 地址
```

**示例 4：**

```
输入：IP = "2001:0db8:85a3:0:0:8A2E:0370:7334:"
输出："Neither"
```

**示例 5：**

```
输入：IP = "1e1.4.5.6"
输出："Neither"
```

**提示：**

- `IP` 仅由英文字母，数字，字符 `''.''` 和 `'':''` 组成。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/validate-ip-address/', '3ce00a45807adad2fa0e94e4327e5636fbe57b31a1b371c9f609d2a6c1eab805', 1, 468, 'validate-ip-address', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]', 62, '2026-09-04'),
(910000000000001605, '单词搜索', '给定一个 `m x n` 二维字符网格 `board` 和一个字符串单词 `word` 。如果 `word` 存在于网格中，返回 `true` ；否则，返回 `false` 。

单词必须按照字母顺序，通过相邻的单元格内的字母构成，其中“相邻”单元格是那些水平相邻或垂直相邻的单元格。同一个单元格内的字母不允许被重复使用。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/04/word2.jpg)

```
输入：board = [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]], word = "ABCCED"
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/04/word-1.jpg)

```
输入：board = [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]], word = "SEE"
输出：true
```

**示例 3：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/15/word3.jpg)

```
输入：board = [["A","B","C","E"],["S","F","C","S"],["A","D","E","E"]], word = "ABCB"
输出：false
```

**提示：**

- `m == board.length`

- `n = board[i].length`

- `1 <= m, n <= 6`

- `1 <= word.length <= 15`

- `board` 和 `word` 仅由大小写英文字母组成

**进阶：**你可以使用搜索剪枝的技术来优化解决方案，使其在 `board` 更大的情况下可以更快解决问题？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/word-search/', '7017ca40f7cc854b36a73eb68effaad77a4798339480d1c6fbe5425e481e9348', 1, 79, 'word-search', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["bilibili","亚马逊","华为","商汤","字节跳动","小米","微软","快手","拼多多","携程","猿辅导","美团","腾讯","虾皮","阿里巴巴"]', 61, '2026-06-03'),
(910000000000001673, '盛最多水的容器', '给你 `n` 个非负整数 `a_1，a_2，...，a`_`n`，每个数代表坐标中的一个点 `(i, a_i)` 。在坐标内画 `n` 条垂直线，垂直线 `i` 的两个端点分别为 `(i, a_i)` 和 `(i, 0)` 。找出其中的两条线，使得它们与 `x` 轴共同构成的容器可以容纳最多的水。

**说明：**你不能倾斜容器。

**示例 1：**

![题目配图](https://aliyun-lc-upload.oss-cn-hangzhou.aliyuncs.com/aliyun-lc-upload/uploads/2018/07/25/question_11.jpg)

```
输入：[1,8,6,2,5,4,8,3,7]
输出：49
解释：图中垂直线代表输入数组 [1,8,6,2,5,4,8,3,7]。在此情况下，容器能够容纳水（表示为蓝色部分）的最大值为 49。
```

**示例 2：**

```
输入：height = [1,1]
输出：1
```

**示例 3：**

```
输入：height = [4,3,2,1,4]
输出：16
```

**示例 4：**

```
输入：height = [1,2,1]
输出：2
```

**提示：**

- `n == height.length`

- `2 <= n <= 10^5`

- `0 <= height[i] <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/container-with-most-water/', '8a7e37c42da8f73eefdbebbe85c2a28aea966b8eef999a9f8679c9afeb3a0ce5', 1, 11, 'container-with-most-water', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]', 59, '2026-11-13'),
(910000000000001531, '寻找旋转排序数组中的最小值', '已知一个长度为 `n` 的数组，预先按照升序排列，经由 `1` 到 `n` 次 **旋转** 后，得到输入数组。例如，原数组 `nums = [0,1,2,4,5,6,7]` 在变化后可能得到：

- 若旋转 `4` 次，则可以得到 `[4,5,6,7,0,1,2]`

- 若旋转 `7` 次，则可以得到 `[0,1,2,4,5,6,7]`

注意，数组 `[a[0], a[1], a[2], ..., a[n-1]]` **旋转一次** 的结果为数组 `[a[n-1], a[0], a[1], a[2], ..., a[n-2]]` 。

给你一个元素值 **互不相同** 的数组 `nums` ，它原来是一个升序排列的数组，并按上述情形进行了多次旋转。请你找出并返回数组中的 **最小元素** 。

**示例 1：**

```
输入：nums = [3,4,5,1,2]
输出：1
解释：原数组为 [1,2,3,4,5] ，旋转 3 次得到输入数组。
```

**示例 2：**

```
输入：nums = [4,5,6,7,0,1,2]
输出：0
解释：原数组为 [0,1,2,4,5,6,7] ，旋转 4 次得到输入数组。
```

**示例 3：**

```
输入：nums = [11,13,15,17]
输出：11
解释：原数组为 [11,13,15,17] ，旋转 4 次得到输入数组。
```

**提示：**

- `n == nums.length`

- `1 <= n <= 5000`

- `-5000 <= nums[i] <= 5000`

- `nums` 中的所有整数 **互不相同**

- `nums` 原来是一个升序排序的数组，并进行了 `1` 至 `n` 次旋转', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-minimum-in-rotated-sorted-array/', 'be47b97a83d4888573eb665fa6cc497c3bff1327ae0d9ca0a63e88ade5ea3e07', 1, 153, 'find-minimum-in-rotated-sorted-array', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 59, '2026-10-19'),
(910000000000001546, '复制带随机指针的链表', '给你一个长度为 `n` 的链表，每个节点包含一个额外增加的随机指针 `random` ，该指针可以指向链表中的任何节点或空节点。

构造这个链表的 **深拷贝**。 深拷贝应该正好由 `n` 个 **全新** 节点组成，其中每个新节点的值都设为其对应的原节点的值。新节点的 `next` 指针和 `random` 指针也都应指向复制链表中的新节点，并使原链表和复制链表中的这些指针能够表示相同的链表状态。**复制链表中的指针都不应指向原链表中的节点**。

例如，如果原链表中有 `X` 和 `Y` 两个节点，其中 `X.random --> Y` 。那么在复制链表中对应的两个节点 `x` 和 `y` ，同样有 `x.random --> y` 。

返回复制链表的头节点。

用一个由 `n` 个节点组成的链表来表示输入/输出中的链表。每个节点用一个 `[val, random_index]` 表示：

- `val`：一个表示 `Node.val` 的整数。

- `random_index`：随机指针指向的节点索引（范围从 `0` 到 `n-1`）；如果不指向任何节点，则为 `null` 。

你的代码 **只** 接受原链表的头节点 `head` 作为传入参数。

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/01/09/e1.png)

```
输入：head = [[7,null],[13,0],[11,4],[10,2],[1,0]]
输出：[[7,null],[13,0],[11,4],[10,2],[1,0]]
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/01/09/e2.png)

```
输入：head = [[1,1],[2,1]]
输出：[[1,1],[2,1]]
```

**示例 3：**

**![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/01/09/e3.png)**

```
输入：head = [[3,null],[3,0],[3,null]]
输出：[[3,null],[3,0],[3,null]]
```

**示例 4：**

```
输入：head = []
输出：[]
解释：给定的链表为空（空指针），因此返回 null。
```

**提示：**

- `0 <= n <= 1000`

- `-10000 <= Node.val <= 10000`

- `Node.random` 为空（null）或指向链表中的节点。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/copy-list-with-random-pointer/', 'e520d1290f2fa17a1f849d762be39653985b5a9aa21a7855cf400d98dae0aa7f', 1, 138, 'copy-list-with-random-pointer', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","小米","微软","快手","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 59, '2026-09-07'),
(910000000000001387, '二叉树的序列化与反序列化', '序列化是将一个数据结构或者对象转换为连续的比特位的操作，进而可以将转换后的数据存储在一个文件或者内存中，同时也可以通过网络传输到另一个计算机环境，采取相反方式重构得到原数据。

请设计一个算法来实现二叉树的序列化与反序列化。这里不限定你的序列 / 反序列化算法执行逻辑，你只需要保证一个二叉树可以被序列化为一个字符串并且将这个字符串反序列化为原始的树结构。

**提示:**输入输出格式与 LeetCode 目前使用的方式一致，详情请参阅 LeetCode 序列化二叉树的格式。你并非必须采取这种方式，你也可以采用其他的方法解决这个问题。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/15/serdeser.jpg)

```
输入：root = [1,2,3,null,null,4,5]
输出：[1,2,3,null,null,4,5]
```

**示例 2：**

```
输入：root = []
输出：[]
```

**示例 3：**

```
输入：root = [1]
输出：[1]
```

**示例 4：**

```
输入：root = [1,2]
输出：[1,2]
```

**提示：**

- 树中结点数在范围 `[0, 10^4]` 内

- `-1000 <= Node.val <= 1000`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/serialize-and-deserialize-binary-tree/', '4f70be4223eb6f3a69803c501fcf379207e511307d2eef40842f58d844d6a396', 1, 297, 'serialize-and-deserialize-binary-tree', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮","阿里巴巴"]', 59, '2026-05-25'),
(910000000000001637, '全排列 II', '给定一个可包含重复数字的序列 `nums` ，**按任意顺序** 返回所有不重复的全排列。

**示例 1：**

```
输入：nums = [1,1,2]
输出：
[[1,1,2],
 [1,2,1],
 [2,1,1]]
```

**示例 2：**

```
输入：nums = [1,2,3]
输出：[[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

**提示：**

- `1 <= nums.length <= 8`

- `-10 <= nums[i] <= 10`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/permutations-ii/', '7d79f0ba6d15017a7aff130422f42e9d892cfb269b15ccf9b1985805d3f1aed8', 1, 47, 'permutations-ii', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","华为","图森","字节跳动","小米","微软","快手","滴滴","猿辅导","美团","腾讯","虾皮","阿里巴巴"]', 58, '2026-09-16'),
(910000000000001230, 'LFU缓存', '请你为 最不经常使用（LFU）缓存算法设计并实现数据结构。

实现 `LFUCache` 类：

- `LFUCache(int capacity)` - 用数据结构的容量 `capacity` 初始化对象

- `int get(int key)` - 如果键存在于缓存中，则获取键的值，否则返回 -1。

- `void put(int key, int value)` - 如果键已存在，则变更其值；如果键不存在，请插入键值对。当缓存达到其容量时，则应该在插入新项之前，使最不经常使用的项无效。在此问题中，当存在平局（即两个或更多个键具有相同使用频率）时，应该去除 **最近最久未使用** 的键。

**注意**「项的使用次数」就是自插入该项以来对其调用 `get` 和 `put` 函数的次数之和。使用次数会在对应项被移除后置为 0 。

为了确定最不常使用的键，可以为缓存中的每个键维护一个 **使用计数器** 。使用计数最小的键是最久未使用的键。

当一个键首次插入到缓存中时，它的使用计数器被设置为 `1` (由于 put 操作)。对缓存中的键执行 `get` 或 `put` 操作，使用计数器的值将会递增。

**示例：**

```
输入：
["LFUCache", "put", "put", "get", "put", "get", "get", "put", "get", "get", "get"]
[[2], [1, 1], [2, 2], [1], [3, 3], [2], [3], [4, 4], [1], [3], [4]]
输出：
[null, null, null, 1, null, -1, 3, null, -1, 3, 4]

解释：
// cnt(x) = 键 x 的使用计数
// cache=[] 将显示最后一次使用的顺序（最左边的元素是最近的）
LFUCache lFUCache = new LFUCache(2);
lFUCache.put(1, 1);   // cache=[1,_], cnt(1)=1
lFUCache.put(2, 2);   // cache=[2,1], cnt(2)=1, cnt(1)=1
lFUCache.get(1);      // 返回 1
                      // cache=[1,2], cnt(2)=1, cnt(1)=2
lFUCache.put(3, 3);   // 去除键 2 ，因为 cnt(2)=1 ，使用计数最小
                      // cache=[3,1], cnt(3)=1, cnt(1)=2
lFUCache.get(2);      // 返回 -1（未找到）
lFUCache.get(3);      // 返回 3
                      // cache=[3,1], cnt(3)=2, cnt(1)=2
lFUCache.put(4, 4);   // 去除键 1 ，1 和 3 的 cnt 相同，但 1 最久未使用
                      // cache=[4,3], cnt(4)=1, cnt(3)=2
lFUCache.get(1);      // 返回 -1（未找到）
lFUCache.get(3);      // 返回 3
                      // cache=[3,4], cnt(4)=1, cnt(3)=3
lFUCache.get(4);      // 返回 4
                      // cache=[3,4], cnt(4)=2, cnt(3)=3
```

**提示：**

- `0 <= capacity, key, value <= 10^4`

- 最多调用 `10^5` 次 `get` 和 `put` 方法

**进阶：**你可以为这两种操作设计时间复杂度为 `O(1)` 的实现吗？', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lfu-cache/', 'e802a64873b4929de45b2f471c4291efa267092e83110896932a5df0effd87bc', 1, 460, 'lfu-cache', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","图森","字节跳动","小红书","微软","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]', 58, '2026-06-20'),
(910000000000001629, '跳跃游戏', '给定一个非负整数数组 `nums` ，你最初位于数组的 **第一个下标** 。

数组中的每个元素代表你在该位置可以跳跃的最大长度。

判断你是否能够到达最后一个下标。

**示例 1：**

```
输入：nums = [2,3,1,1,4]
输出：true
解释：可以先跳 1 步，从下标 0 到达下标 1, 然后再从下标 1 跳 3 步到达最后一个下标。
```

**示例 2：**

```
输入：nums = [3,2,1,0,4]
输出：false
解释：无论怎样，总会到达下标为 3 的位置。但该下标的最大跳跃长度是 0 ， 所以永远不可能到达最后一个下标。
```

**提示：**

- `1 <= nums.length <= 3 * 10^4`

- `0 <= nums[i] <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/jump-game/', '5798979aeffa91375a0de4916a7f7c12b208939ad5297a5e0f154659fa1d8b8c', 1, 55, 'jump-game', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]', 57, '2026-09-08'),
(910000000000001460, '基本计算器', '给你一个字符串表达式 `s` ，请你实现一个基本计算器来计算并返回它的值。

**示例 1：**

```
输入：s = "1 + 1"
输出：2
```

**示例 2：**

```
输入：s = " 2-1 + 2 "
输出：3
```

**示例 3：**

```
输入：s = "(1+(4+5+2)-3)+(6+8)"
输出：23
```

**提示：**

- `1 <= s.length <= 3 * 10^5`

- `s` 由数字、`''+''`、`''-''`、`''(''`、`'')''`、和 `'' ''` 组成

- `s` 表示一个有效的表达式', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/basic-calculator/', '970968b01116cda891d5603b5f86713d9b6421277391042d34d8510628488188', 1, 224, 'basic-calculator', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","华为","字节跳动","微软","快手","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 57, '2025-12-24'),
(910000000000001561, '买卖股票的最佳时机 III', '给定一个数组，它的第**`i` 个元素是一支给定的股票在第 `i`**天的价格。

设计一个算法来计算你所能获取的最大利润。你最多可以完成 **两笔**交易。

**注意：**你不能同时参与多笔交易（你必须在再次购买前出售掉之前的股票）。

**示例 1:**

```
输入：prices = [3,3,5,0,0,3,1,4]
输出：6
解释：在第 4 天（股票价格 = 0）的时候买入，在第 6 天（股票价格 = 3）的时候卖出，这笔交易所能获得利润 = 3-0 = 3 。
     随后，在第 7 天（股票价格 = 1）的时候买入，在第 8 天 （股票价格 = 4）的时候卖出，这笔交易所能获得利润 = 4-1 = 3 。
```

**示例 2：**

```
输入：prices = [1,2,3,4,5]
输出：4
解释：在第 1 天（股票价格 = 1）的时候买入，在第 5 天 （股票价格 = 5）的时候卖出, 这笔交易所能获得利润 = 5-1 = 4 。
     注意你不能在第 1 天和第 2 天接连购买股票，之后再将它们卖出。
     因为这样属于同时参与了多笔交易，你必须在再次购买前出售掉之前的股票。
```

**示例 3：**

```
输入：prices = [7,6,4,3,1]
输出：0
解释：在这个情况下, 没有交易完成, 所以最大利润为 0。
```

**示例 4：**

```
输入：prices = [1]
输出：0
```

**提示：**

- `1 <= prices.length <= 10^5`

- `0 <= prices[i] <= 10^5`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-iii/', 'cbce44548344e6427a6e5ed198fae8a3ed332624581627cd8a423b2a01c686b5', 1, 123, 'best-time-to-buy-and-sell-stock-iii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","小米","小红书","微软","快手","拼多多","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 56, '2026-10-15'),
(910000000000001282, '移掉K位数字', '给你一个以字符串表示的非负整数 `num` 和一个整数 `k` ，移除这个数中的 `k`**位数字，使得剩下的数字最小。请你以字符串形式返回这个最小的数字。

**示例 1 ：**

```
输入：num = "1432219", k = 3
输出："1219"
解释：移除掉三个数字 4, 3, 和 2 形成一个新的最小的数字 1219 。
```

**示例 2 ：**

```
输入：num = "10200", k = 1
输出："200"
解释：移掉首位的 1 剩下的数字为 200. 注意输出不能有任何前导零。
```

**示例 3 ：**

```
输入：num = "10", k = 2
输出："0"
解释：从原数字移除所有的数字，剩余为空就是 0 。
```

**提示：**

- `1 <= k <= num.length <= 10^5`

- `num` 仅由若干位数字（0 - 9）组成

- 除了 **0** 本身之外，`num` 不含任何前导零', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-k-digits/', 'f0252d66d19f503c7438756232a789e34c8f86d7aaa9be6cf51376918a41f88a', 1, 402, 'remove-k-digits', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","微软","猿辅导","百度","腾讯","虾皮"]', 55, '2026-11-09'),
(910000000000001668, '最接近的三数之和', '给定一个包括 *n* 个整数的数组 `nums`* *和 一个目标值 `target`。找出 `nums`* *中的三个整数，使得它们的和与 `target` 最接近。返回这三个数的和。假定每组输入只存在唯一答案。

**示例：**

```
输入：nums = [-1,2,1,-4], target = 1
输出：2
解释：与 target 最接近的和是 2 (-1 + 2 + 1 = 2) 。
```

**提示：**

- `3 <= nums.length <= 10^3`

- `-10^3 <= nums[i] <= 10^3`

- `-10^4 <= target <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/3sum-closest/', '5be6e63a543291ca910b573cee7ffd8f1fd42c14a5a8e6eef90bf0fb66330fe9', 1, 16, '3sum-closest', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","拼多多","美团","腾讯","虾皮","阿里巴巴"]', 55, '2026-07-27'),
(910000000000001644, '组合总和 II', '给定一个数组 `candidates` 和一个目标数 `target` ，找出 `candidates` 中所有可以使数字和为 `target` 的组合。

`candidates` 中的每个数字在每个组合中只能使用一次。

**注意：**解集不能包含重复的组合。

**示例 1:**

```
输入: candidates = [10,1,2,7,6,1,5], target = 8,
输出:
[
[1,1,6],
[1,2,5],
[1,7],
[2,6]
]
```

**示例 2:**

```
输入: candidates = [2,5,2,1,2], target = 5,
输出:
[
[1,2,2],
[5]
]
```

**提示:**

- `1 <= candidates.length <= 100`

- `1 <= candidates[i] <= 50`

- `1 <= target <= 30`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/combination-sum-ii/', 'b34acd1e79f82c5187622d06c7088391306cb62bbb8c8d45b81fc10ee20f94e6', 1, 40, 'combination-sum-ii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮"]', 55, '2026-01-05'),
(910000000000000098, '数组中的逆序对', '在数组中的两个数字，如果前面一个数字大于后面的数字，则这两个数字组成一个逆序对。输入一个数组，求出这个数组中的逆序对的总数。

**示例 1:**

```
输入: [7,5,6,4]
输出: 5
```

**限制：**

`0 <= 数组长度 <= 50000`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shu-zu-zhong-de-ni-xu-dui-lcof/', '3add05a4fe46f9926310e91e8e55ada30bde3586bb1421b9a2e79b9df2e1c777', 1, NULL, 'shu-zu-zhong-de-ni-xu-dui-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","华为","字节跳动","微软","快手","百度","美团","腾讯","虾皮"]', 54, '2026-10-15'),
(910000000000001548, '只出现一次的数字', '给定一个**非空**整数数组，除了某个元素只出现一次以外，其余每个元素均出现两次。找出那个只出现了一次的元素。

**说明：**

你的算法应该具有线性时间复杂度。 你可以不使用额外空间来实现吗？

**示例 1:**

```
输入: [2,2,1]
输出: 1
```

**示例 2:**

```
输入: [4,1,2,1,2]
输出: 4
```', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/single-number/', '979d0663e522e35bbcf3255fc38b829dc37437393b543aacf454390c19d18a04', 1, 136, 'single-number', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 53, '2026-04-22'),
(910000000000001623, '旋转链表', '给你一个链表的头节点 `head` ，旋转链表，将链表每个节点向右移动 `k`**个位置。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/rotate1.jpg)

```
输入：head = [1,2,3,4,5], k = 2
输出：[4,5,1,2,3]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/roate2.jpg)

```
输入：head = [0,1,2], k = 4
输出：[2,0,1]
```

**提示：**

- 链表中节点的数目在范围 `[0, 500]` 内

- `-100 <= Node.val <= 100`

- `0 <= k <= 2 * 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/rotate-list/', '3458928683a75dd86e7cd255fa26679c3132cc4cbc7076de4f499407e204d572', 1, 61, 'rotate-list', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 51, '2026-04-20'),
(910000000000001355, '矩阵中的最长递增路径', '给定一个 `m x n` 整数矩阵 `matrix` ，找出其中 **最长递增路径** 的长度。

对于每个单元格，你可以往上，下，左，右四个方向移动。 你 **不能** 在 **对角线** 方向上移动或移动到 **边界外**（即不允许环绕）。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/05/grid1.jpg)

```
输入：matrix = [[9,9,4],[6,6,8],[2,1,1]]
输出：4
解释：最长递增路径为 [1, 2, 6, 9]。
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/27/tmp-grid.jpg)

```
输入：matrix = [[3,4,5],[3,2,6],[2,2,1]]
输出：4
解释：最长递增路径是 [3, 4, 5, 6]。注意不允许在对角线方向上移动。
```

**示例 3：**

```
输入：matrix = [[1]]
输出：1
```

**提示：**

- `m == matrix.length`

- `n == matrix[i].length`

- `1 <= m, n <= 200`

- `0 <= matrix[i][j] <= 2^31 - 1`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-increasing-path-in-a-matrix/', '016c62f675501f41f7c783fb0f0c1b475c25881feaaf52964529a1dccec0692f', 1, 329, 'longest-increasing-path-in-a-matrix', '["CODETOP","HIGH_FREQ"]', '["华为","商汤","字节跳动","微软","快手","拼多多","猿辅导","百度","腾讯"]', 49, '2026-11-04'),
(910000000000001609, '颜色分类', '给定一个包含红色、白色和蓝色，一共 `n`**个元素的数组，**原地**对它们进行排序，使得相同颜色的元素相邻，并按照红色、白色、蓝色顺序排列。

此题中，我们使用整数 `0`、 `1` 和 `2` 分别表示红色、白色和蓝色。

**示例 1：**

```
输入：nums = [2,0,2,1,1,0]
输出：[0,0,1,1,2,2]
```

**示例 2：**

```
输入：nums = [2,0,1]
输出：[0,1,2]
```

**示例 3：**

```
输入：nums = [0]
输出：[0]
```

**示例 4：**

```
输入：nums = [1]
输出：[1]
```

**提示：**

- `n == nums.length`

- `1 <= n <= 300`

- `nums[i]` 为 `0`、`1` 或 `2`

**进阶：**

- 你可以不使用代码库中的排序函数来解决这道题吗？

- 你能想出一个仅使用常数空间的一趟扫描算法吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sort-colors/', '766191f5a50372307989dd66451da531967b9651493e969e82c1bc4d3ac3a0d8', 1, 75, 'sort-colors', '["CODETOP","HOT100","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","小米","小红书","微软","快手","滴滴","美团","腾讯","虾皮","阿里巴巴"]', 49, '2026-04-20'),
(910000000000000143, '用两个栈实现队列', '用两个栈实现一个队列。队列的声明如下，请实现它的两个函数 `appendTail` 和 `deleteHead` ，分别完成在队列尾部插入整数和在队列头部删除整数的功能。(若队列中没有元素，`deleteHead` 操作返回 -1 )

**示例 1：**

```
输入：
["CQueue","appendTail","deleteHead","deleteHead"]
[[],[3],[],[]]
输出：[null,null,3,-1]
```

**示例 2：**

```
输入：
["CQueue","deleteHead","appendTail","appendTail","deleteHead","deleteHead"]
[[],[],[5],[2],[],[]]
输出：[null,-1,null,null,5,2]
```

**提示：**

- `1 <= values <= 10000`

- `最多会对 appendTail、deleteHead 进行 10000 次调用`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/yong-liang-ge-zhan-shi-xian-dui-lie-lcof/', '5bef79801c15e68b5b5aa89937db846a9421c84c73945903c7ca6dd41c8b88cc', 1, NULL, 'yong-liang-ge-zhan-shi-xian-dui-lie-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","快手","旷视","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 48, '2026-10-10'),
(910000000000001610, '搜索二维矩阵', '编写一个高效的算法来判断 `m x n` 矩阵中，是否存在一个目标值。该矩阵具有如下特性：

- 每行中的整数从左到右按升序排列。

- 每行的第一个整数大于前一行的最后一个整数。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/05/mat.jpg)

```
输入：matrix = [[1,3,5,7],[10,11,16,20],[23,30,34,60]], target = 3
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/11/25/mat2.jpg)

```
输入：matrix = [[1,3,5,7],[10,11,16,20],[23,30,34,60]], target = 13
输出：false
```

**提示：**

- `m == matrix.length`

- `n == matrix[i].length`

- `1 <= m, n <= 100`

- `-10^4 <= matrix[i][j], target <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/search-a-2d-matrix/', 'f028a364925b058096dc239c9ee15cae1e820f2eeee9de676cfa7fc25c51b356', 1, 74, 'search-a-2d-matrix', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]', 48, '2026-09-16'),
(910000000000001248, '数组中重复的数据', '给定一个整数数组 a，其中1 ≤ a[i] ≤ *n* （*n*为数组长度）, 其中有些元素出现**两次**而其他元素出现**一次**。

找到所有出现**两次**的元素。

你可以不用到任何额外空间并在O(*n*)时间复杂度内解决这个问题吗？

**示例：**

```
输入:
[4,3,2,7,8,2,3,1]

输出:
[2,3]
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-all-duplicates-in-an-array/', '9824a38d6c36128c30067b420cf7c2f2f2c9b21f8565398f211817db9dd5591c', 1, 442, 'find-all-duplicates-in-an-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","拼多多","猿辅导","腾讯","虾皮","阿里巴巴"]', 48, '2026-08-03'),
(910000000000001178, '零钱兑换 II', '给你一个整数数组 `coins` 表示不同面额的硬币，另给一个整数 `amount` 表示总金额。

请你计算并返回可以凑成总金额的硬币组合数。如果任何硬币组合都无法凑出总金额，返回 `0` 。

假设每一种面额的硬币有无限个。

题目数据保证结果符合 32 位带符号整数。

**示例 1：**

```
输入：amount = 5, coins = [1, 2, 5]输出：4解释：有四种方式可以凑成总金额：5=55=2+2+15=2+1+1+15=1+1+1+1+1
```
**示例 2：**

```
输入：amount = 3, coins = [2]输出：0解释：只用面额 2 的硬币不能凑成总金额 3 。
```
**示例 3：**

```
输入：amount = 10, coins = [10] 输出：1
```

**提示：**

- `1 <= coins.length <= 300`
- `1 <= coins[i] <= 5000`
- `coins` 中的所有值 **互不相同**
- `0 <= amount <= 5000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/coin-change-ii/', '3ce4947104b454dc6f54eb923fd013fef579930f18ff559f9ff43ec8c68c9fde', 1, 518, 'coin-change-ii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 48, '2026-04-24'),
(910000000000001196, '对角线遍历', '给你一个大小为 `m x n` 的矩阵 `mat` ，请以对角线遍历的顺序，用一个数组返回这个矩阵中的所有元素。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/04/10/diag1-grid.jpg)

```
输入：mat = [[1,2,3],[4,5,6],[7,8,9]]
输出：[1,2,4,7,5,3,6,8,9]
```

**示例 2：**

```
输入：mat = [[1,2],[3,4]]
输出：[1,2,3,4]
```

**提示：**

- `m == mat.length`

- `n == mat[i].length`

- `1 <= m, n <= 10^4`

- `1 <= m * n <= 10^4`

- `-10^5 <= mat[i][j] <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/diagonal-traverse/', 'c2c5ec5ebdb9d1235c89fe625c615f167699914714d05206ce12a5a8a5751383', 1, 498, 'diagonal-traverse', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","拼多多","旷视","猿辅导","百度","美团","腾讯","阿里巴巴"]', 48, '2026-04-21'),
(910000000000001337, '前 K 个高频元素', '给你一个整数数组 `nums` 和一个整数 `k` ，请你返回其中出现频率前 `k` 高的元素。你可以按 **任意顺序** 返回答案。

**示例 1:**

```
输入: nums = [1,1,1,2,2,3], k = 2
输出: [1,2]
```

**示例 2:**

```
输入: nums = [1], k = 1
输出: [1]
```

**提示：**

- `1 <= nums.length <= 10^5`

- `k` 的取值范围是 `[1, 数组中不相同的元素的个数]`

- 题目数据保证答案唯一，换句话说，数组中前 `k` 个高频元素的集合是唯一的

**进阶：**你所设计算法的时间复杂度 **必须** 优于 `O(n log n)` ，其中 `n`**是数组大小。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/top-k-frequent-elements/', '7d36bd8d722c36c22f09a1c5b1942bf855c5e2ae6f6ef20ff2cbd6264f34091b', 1, 347, 'top-k-frequent-elements', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小红书","微软","快手","拼多多","旷视","滴滴","百度","美团","腾讯","阿里巴巴"]', 47, '2026-09-23'),
(910000000000001570, '二叉树展开为链表', '给你二叉树的根结点 `root` ，请你将它展开为一个单链表：

- 展开后的单链表应该同样使用 `TreeNode` ，其中 `right` 子指针指向链表中下一个结点，而左子指针始终为 `null` 。

- 展开后的单链表应该与二叉树 **先序遍历** 顺序相同。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/14/flaten.jpg)

```
输入：root = [1,2,5,3,4,null,6]
输出：[1,null,2,null,3,null,4,null,5,null,6]
```

**示例 2：**

```
输入：root = []
输出：[]
```

**示例 3：**

```
输入：root = [0]
输出：[0]
```

**提示：**

- 树中结点数在范围 `[0, 2000]` 内

- `-100 <= Node.val <= 100`

**进阶：**你可以使用原地算法（`O(1)` 额外空间）展开这棵树吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/flatten-binary-tree-to-linked-list/', 'a07128719e58d15cd95ecf0ee7480fa47f809605bbb855c4989b95c39fcd8fcd', 1, 114, 'flatten-binary-tree-to-linked-list', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","小米","微软","快手","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 47, '2026-06-15'),
(910000000000001658, '删除排序数组中的重复项', '给你一个有序数组 `nums` ，请你** 原地** 删除重复出现的元素，使每个元素 **只出现一次** ，返回删除后数组的新长度。

不要使用额外的数组空间，你必须在 **原地 修改输入数组**并在使用 O(1) 额外空间的条件下完成。

**说明:**

为什么返回数值是整数，但输出的答案是数组呢?

请注意，输入数组是以**「引用」**方式传递的，这意味着在函数里修改输入数组对于调用者是可见的。

你可以想象内部操作如下:

```
// nums 是以“引用”方式传递的。也就是说，不对实参做任何拷贝
int len = removeDuplicates(nums);

// 在函数里修改输入数组对于调用者是可见的。
// 根据你的函数返回的长度, 它会打印出数组中 该长度范围内 的所有元素。
for (int i = 0; i < len; i++) {
    print(nums[i]);
}
```

**示例 1：**

```
输入：nums = [1,1,2]
输出：2, nums = [1,2]
解释：函数应该返回新的长度 2 ，并且原数组 nums 的前两个元素被修改为 1, 2 。不需要考虑数组中超出新长度后面的元素。
```

**示例 2：**

```
输入：nums = [0,0,1,1,1,2,2,3,3,4]
输出：5, nums = [0,1,2,3,4]
解释：函数应该返回新的长度 5 ， 并且原数组 nums 的前五个元素被修改为 0, 1, 2, 3, 4 。不需要考虑数组中超出新长度后面的元素。
```

**提示：**

- `0 <= nums.length <= 3 * 10^4`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 已按升序排列', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-duplicates-from-sorted-array/', 'e065aec5a6dc65e80e7e058065974e4c247607560e32f3a892934b05507a330b', 1, 26, 'remove-duplicates-from-sorted-array', '["CODETOP","HIGH_FREQ"]', '["京东","商汤","图森","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 47, '2026-03-19'),
(910000000000000111, '二叉搜索树与双向链表', '输入一棵二叉搜索树，将该二叉搜索树转换成一个排序的循环双向链表。要求不能创建任何新的节点，只能调整树中节点指针的指向。

为了让您更好地理解问题，以下面的二叉搜索树为例：

![题目配图](https://assets.leetcode.com/uploads/2018/10/12/bstdlloriginalbst.png)

我们希望将这个二叉搜索树转化为双向循环链表。链表中的每个节点都有一个前驱和后继指针。对于双向循环链表，第一个节点的前驱是最后一个节点，最后一个节点的后继是第一个节点。

下图展示了上面的二叉搜索树转化成的链表。&ldquo;head&rdquo; 表示指向链表中有最小元素的节点。

![题目配图](https://assets.leetcode.com/uploads/2018/10/12/bstdllreturndll.png)

特别地，我们希望可以就地完成转换操作。当转化完成以后，树中节点的左指针需要指向前驱，树中节点的右指针需要指向后继。还需要返回链表中的第一个节点的指针。

**注意：**本题与主站 426 题相同：https://leetcode-cn.com/problems/convert-binary-search-tree-to-sorted-doubly-linked-list/

**注意：**此题对比原题有改动。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/er-cha-sou-suo-shu-yu-shuang-xiang-lian-biao-lcof/', '7ef8f5eb9fda00965ac16e6688eb5725085cf3d843ed8b1fd6c0ffc1a46c66b4', 1, NULL, 'er-cha-sou-suo-shu-yu-shuang-xiang-lian-biao-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["亚马逊","商汤","字节跳动","小米","微软","快手","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]', 47, '2026-02-06'),
(910000000000000730, '二叉树的完全性检验', '给定一个二叉树，确定它是否是一个*完全二叉树*。

**百度百科中对完全二叉树的定义如下：**

若设二叉树的深度为 h，除第 h 层外，其它各层 (1～h-1) 的结点数都达到最大个数，第 h 层所有的结点都连续集中在最左边，这就是完全二叉树。（注：第 h 层可能包含 1~ 2^h 个节点。）

**示例 1：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/15/complete-binary-tree-1.png)

```
输入：[1,2,3,4,5,6]
输出：true
解释：最后一层前的每一层都是满的（即，结点值为 {1} 和 {2,3} 的两层），且最后一层中的所有结点（{4,5,6}）都尽可能地向左。
```

**示例 2：**

**![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/15/complete-binary-tree-2.png)**

```
输入：[1,2,3,4,5,null,7]
输出：false
解释：值为 7 的结点没有尽可能靠向左侧。
```

**提示：**

- 树中将会有 1 到 100 个结点。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/check-completeness-of-a-binary-tree/', 'aae4590dd299a8a2df268643895ec9bae7cbac9402a17bb0109add37c9968c9d', 1, 958, 'check-completeness-of-a-binary-tree', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","快手","猿辅导","百度","美团","腾讯","虾皮"]', 47, '2025-11-07'),
(910000000000001549, '分发糖果', '老师想给孩子们分发糖果，有 *N* 个孩子站成了一条直线，老师会根据每个孩子的表现，预先给他们评分。

你需要按照以下要求，帮助老师给这些孩子分发糖果：

- 每个孩子至少分配到 1 个糖果。

- 评分更高的孩子必须比他两侧的邻位孩子获得更多的糖果。

那么这样下来，老师至少需要准备多少颗糖果呢？

**示例 1：**

```
输入：[1,0,2]
输出：5
解释：你可以分别给这三个孩子分发 2、1、2 颗糖果。
```

**示例 2：**

```
输入：[1,2,2]
输出：4
解释：你可以分别给这三个孩子分发 1、2、1 颗糖果。
     第三个孩子只得到 1 颗糖果，这已满足上述两个条件。
```', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/candy/', '27f155dea66c0dc17a1635706ba2d93bafee83be7966ddf8442ac041127a8144', 1, 135, 'candy', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","微软","快手","拼多多","网易","腾讯","虾皮","阿里巴巴"]', 45, '2026-10-28'),
(910000000000001677, '整数反转', '给你一个 32 位的有符号整数 `x` ，返回将 `x` 中的数字部分反转后的结果。

如果反转后整数超过 32 位的有符号整数的范围 `[−2^31,  2^31 − 1]` ，就返回 0。

**假设环境不允许存储 64 位整数（有符号或无符号）。**

**示例 1：**

```
输入：x = 123
输出：321
```

**示例 2：**

```
输入：x = -123
输出：-321
```

**示例 3：**

```
输入：x = 120
输出：21
```

**示例 4：**

```
输入：x = 0
输出：0
```

**提示：**

- `-2^31 <= x <= 2^31 - 1`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-integer/', '02bd8a361c5b2e64a425c8b177fcd0e1f334943bc39c7e255d4e0a831db328e1', 1, 7, 'reverse-integer', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","快手","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]', 45, '2026-03-30'),
(910000000000001476, '实现 Trie (前缀树)', '**Trie**（发音类似 "try"）或者说 **前缀树** 是一种树形数据结构，用于高效地存储和检索字符串数据集中的键。这一数据结构有相当多的应用情景，例如自动补完和拼写检查。

请你实现 Trie 类：

- `Trie()` 初始化前缀树对象。

- `void insert(String word)` 向前缀树中插入字符串 `word` 。

- `boolean search(String word)` 如果字符串 `word` 在前缀树中，返回 `true`（即，在检索之前已经插入）；否则，返回 `false` 。

- `boolean startsWith(String prefix)` 如果之前已经插入的字符串 `word` 的前缀之一为 `prefix` ，返回 `true` ；否则，返回 `false` 。

**示例：**

```
输入
["Trie", "insert", "search", "search", "startsWith", "insert", "search"]
[[], ["apple"], ["apple"], ["app"], ["app"], ["app"], ["app"]]
输出
[null, null, true, false, true, null, true]

解释
Trie trie = new Trie();
trie.insert("apple");
trie.search("apple");   // 返回 True
trie.search("app");     // 返回 False
trie.startsWith("app"); // 返回 True
trie.insert("app");
trie.search("app");     // 返回 True
```

**提示：**

- `1 <= word.length, prefix.length <= 2000`

- `word` 和 `prefix` 仅由小写英文字母组成

- `insert`、`search` 和 `startsWith` 调用次数 **总计** 不超过 `3 * 10^4` 次', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/implement-trie-prefix-tree/', 'fdd6081195595f842d0d227142e73d3c378f38d4327dd01d63a60473d022701e', 1, 208, 'implement-trie-prefix-tree', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 43, '2026-10-12'),
(910000000000001593, '解码方法', '一条包含字母 `A-Z` 的消息通过以下映射进行了 **编码** ：

```
''A'' -> 1
''B'' -> 2
...
''Z'' -> 26
```

要 **解码** 已编码的消息，所有数字必须基于上述映射的方法，反向映射回字母（可能有多种方法）。例如，`"11106"` 可以映射为：

- `"AAJF"` ，将消息分组为 `(1 1 10 6)`

- `"KJF"` ，将消息分组为 `(11 10 6)`

注意，消息不能分组为  `(1 11 06)` ，因为 `"06"` 不能映射为 `"F"` ，这是由于 `"6"` 和 `"06"` 在映射中并不等价。

给你一个只含数字的 **非空**字符串 `s` ，请计算并返回 **解码** 方法的 **总数** 。

题目数据保证答案肯定是一个 **32 位** 的整数。

**示例 1：**

```
输入：s = "12"
输出：2
解释：它可以解码为 "AB"（1 2）或者 "L"（12）。
```

**示例 2：**

```
输入：s = "226"
输出：3
解释：它可以解码为 "BZ" (2 26), "VF" (22 6), 或者 "BBF" (2 2 6) 。
```

**示例 3：**

```
输入：s = "0"
输出：0
解释：没有字符映射到以 0 开头的数字。
含有 0 的有效映射是 ''J'' -> "10" 和 ''T''-> "20" 。
由于没有字符，因此没有有效的方法对此进行解码，因为所有数字都需要映射。
```

**示例 4：**

```
输入：s = "06"
输出：0
解释："06" 不能映射到 "F" ，因为字符串含有前导 0（"6" 和 "06" 在映射中并不等价）。
```

**提示：**

- `1 <= s.length <= 100`

- `s` 只包含数字，并且可能包含前导零。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/decode-ways/', '0dfe57180feeb992c8b3ab5ce9bd7b498463f7077a1596291fb6a679434ab0a3', 1, 91, 'decode-ways', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["亚马逊","华为","商汤","字节跳动","微软","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]', 43, '2026-09-22'),
(910000000000001559, '验证回文串', '给定一个字符串，验证它是否是回文串，只考虑字母和数字字符，可以忽略字母的大小写。

**说明：**本题中，我们将空字符串定义为有效的回文串。

**示例 1:**

```
输入: "A man, a plan, a canal: Panama"
输出: true
解释："amanaplanacanalpanama" 是回文串
```

**示例 2:**

```
输入: "race a car"
输出: false
解释："raceacar" 不是回文串
```

**提示：**

- `1 <= s.length <= 2 * 10^5`

- 字符串 `s` 由 ASCII 字符组成', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-palindrome/', '951668628cce36bf647f0f64c93dbfc469a91c6336b701b7fd58c47b0cb24979', 1, 125, 'valid-palindrome', '["CODETOP","HIGH_FREQ"]', '["图森","字节跳动","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]', 43, '2026-04-25'),
(910000000000001634, 'Pow(x, n)', '实现 pow(*x*, *n*) ，即计算 x 的 n 次幂函数（即，x^n）。

**示例 1：**

```
输入：x = 2.00000, n = 10
输出：1024.00000
```

**示例 2：**

```
输入：x = 2.10000, n = 3
输出：9.26100
```

**示例 3：**

```
输入：x = 2.00000, n = -2
输出：0.25000
解释：2^-2 = 1/2^2 = 1/4 = 0.25
```

**提示：**

- `-100.0 < x < 100.0`

- `-2^31 <= n <= 2^31-1`

- `-10^4 <= x^n <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/powx-n/', '1f2f05e6f99f78d9c3b9c722be29cdaad7cab03641e59b87fa86c165f9d4eb05', 1, 50, 'powx-n', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["商汤","字节跳动","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 43, '2026-03-24'),
(910000000000001471, '打家劫舍 II', '你是一个专业的小偷，计划偷窃沿街的房屋，每间房内都藏有一定的现金。这个地方所有的房屋都 **围成一圈** ，这意味着第一个房屋和最后一个房屋是紧挨着的。同时，相邻的房屋装有相互连通的防盗系统，**如果两间相邻的房屋在同一晚上被小偷闯入，系统会自动报警** 。

给定一个代表每个房屋存放金额的非负整数数组，计算你 **在不触动警报装置的情况下** ，今晚能够偷窃到的最高金额。

**示例 1：**

```
输入：nums = [2,3,2]
输出：3
解释：你不能先偷窃 1 号房屋（金额 = 2），然后偷窃 3 号房屋（金额 = 2）, 因为他们是相邻的。
```

**示例 2：**

```
输入：nums = [1,2,3,1]
输出：4
解释：你可以先偷窃 1 号房屋（金额 = 1），然后偷窃 3 号房屋（金额 = 3）。
     偷窃到的最高金额 = 1 + 3 = 4 。
```

**示例 3：**

```
输入：nums = [0]
输出：0
```

**提示：**

- `1 <= nums.length <= 100`

- `0 <= nums[i] <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/house-robber-ii/', '5117ff8a22ae66b5a0a202aa9227406e2b17a3df24e3611353b3334a2f306fb5', 1, 213, 'house-robber-ii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","商汤","字节跳动","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]', 42, '2026-10-30'),
(910000000000001250, '字典序的第K小数字', '给定整数 `n` 和 `k`，找到 `1` 到 `n` 中字典序第 `k` 小的数字。

注意：1 ≤ k ≤ n ≤ 10^9。

**示例 :**

```
输入:
n: 13   k: 2

输出:
10

解释:
字典序的排列是 [1, 10, 11, 12, 13, 2, 3, 4, 5, 6, 7, 8, 9]，所以第二小的数字是 10。
```', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/k-th-smallest-in-lexicographical-order/', 'dbe700b51c80e9000805e22afeaaad9c886e753fac5ef2c5233ca45e4a446ee1', 1, 440, 'k-th-smallest-in-lexicographical-order', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手"]', 42, '2026-10-28'),
(910000000000001625, '螺旋矩阵 II', '给你一个正整数 `n` ，生成一个包含 `1` 到 `n^2` 所有元素，且元素按顺时针顺序螺旋排列的 `n x n` 正方形矩阵 `matrix` 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/spiraln.jpg)

```
输入：n = 3
输出：[[1,2,3],[8,9,4],[7,6,5]]
```

**示例 2：**

```
输入：n = 1
输出：[[1]]
```

**提示：**

- `1 <= n <= 20`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/spiral-matrix-ii/', '9aed7c0d0bc1d6b1fc0941ce7ccdff89fcec8b742a13239e450130882b24a36d', 1, 59, 'spiral-matrix-ii', '["CODETOP","HIGH_FREQ"]', '["商汤","字节跳动","微软","快手","拼多多","猿辅导","美团","腾讯","虾皮"]', 42, '2026-08-12'),
(910000000000001130, '另一个树的子树', '给你两棵二叉树 `root` 和 `subRoot` 。检验 `root` 中是否包含和 `subRoot` 具有相同结构和节点值的子树。如果存在，返回 `true` ；否则，返回 `false` 。

二叉树 `tree` 的一棵子树包括 `tree` 的某个节点和这个节点的所有后代节点。`tree` 也可以看做它自身的一棵子树。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/04/28/subtree1-tree.jpg)

```
输入：root = [3,4,5,1,2], subRoot = [4,1,2]
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/04/28/subtree2-tree.jpg)

```
输入：root = [3,4,5,1,2,null,null,null,null,0], subRoot = [4,1,2]
输出：false
```

**提示：**

- `root` 树上的节点数量范围是 `[1, 2000]`

- `subRoot` 树上的节点数量范围是 `[1, 1000]`

- `-10^4 <= root.val <= 10^4`

- `-10^4 <= subRoot.val <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/subtree-of-another-tree/', '676945dfbfc4c85d250e5e07f5b3fa5e924479e5f55cf4c4f8ca5994afb6dd6d', 1, 572, 'subtree-of-another-tree', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["华为","商汤","字节跳动","微软","快手","滴滴","猿辅导","百度","腾讯","阿里巴巴"]', 42, '2026-04-14'),
(910000000000001539, '二叉树的后序遍历', '给定一个二叉树，返回它的 *后序 *遍历。

**示例:**

```
输入: [1,null,2,3]
   1
    \\
     2
    /
   3

输出: [3,2,1]
```

**进阶:** 递归算法很简单，你可以通过迭代算法完成吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-postorder-traversal/', 'f9f01cd219e4cec8e42ae310293ebd51c09e2225c55e1c69b151a29e2edf4403', 1, 145, 'binary-tree-postorder-traversal', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 42, '2026-03-31'),
(910000000000001639, '跳跃游戏 II', '给你一个非负整数数组 `nums` ，你最初位于数组的第一个位置。

数组中的每个元素代表你在该位置可以跳跃的最大长度。

你的目标是使用最少的跳跃次数到达数组的最后一个位置。

假设你总是可以到达数组的最后一个位置。

**示例 1:**

```
输入: nums = [2,3,1,1,4]
输出: 2
解释: 跳到最后一个位置的最小跳跃数是 2。
     从下标为 0 跳到下标为 1 的位置，跳 1 步，然后跳 3 步到达数组的最后一个位置。
```

**示例 2:**

```
输入: nums = [2,3,0,1,4]
输出: 2
```

**提示:**

- `1 <= nums.length <= 10^4`

- `0 <= nums[i] <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/jump-game-ii/', 'dcc3582d6b4ddfdc309ee261860449bd555ef74af6ba3b53a8868a3f64dc76f6', 1, 45, 'jump-game-ii', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 40, '2026-11-27'),
(910000000000001245, '两数相加 II', '给你两个 **非空**链表来代表两个非负整数。数字最高位位于链表开始位置。它们的每个节点只存储一位数字。将这两数相加会返回一个新的链表。

你可以假设除了数字 0 之外，这两个数字都不会以零开头。

**示例1：**

![题目配图](https://pic.leetcode-cn.com/1626420025-fZfzMX-image.png)

```
输入：l1 = [7,2,4,3], l2 = [5,6,4]
输出：[7,8,0,7]
```

**示例2：**

```
输入：l1 = [2,4,3], l2 = [5,6,4]
输出：[8,0,7]
```

**示例3：**

```
输入：l1 = [0], l2 = [0]
输出：[0]
```

**提示：**

- 链表的长度范围为` [1, 100]`

- `0 <= node.val <= 9`

- 输入数据保证链表代表的数字无前导 0

**进阶：**如果输入链表不能修改该如何处理？换句话说，不能对列表中的节点进行翻转。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/add-two-numbers-ii/', 'ff96a5d44db9b2950e682e1cef11aa98b9fb009cba2674f7dbe7df750dee101c', 1, 445, 'add-two-numbers-ii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 40, '2026-09-23'),
(910000000000001674, '正则表达式匹配', '给你一个字符串 `s` 和一个字符规律 `p`，请你来实现一个支持 `''.''` 和 `''*''` 的正则表达式匹配。

- `''.''` 匹配任意单个字符

- `''*''` 匹配零个或多个前面的那一个元素

所谓匹配，是要涵盖 **整个**字符串 `s`的，而不是部分字符串。

**示例 1：**

```
输入：s = "aa" p = "a"
输出：false
解释："a" 无法匹配 "aa" 整个字符串。
```

**示例 2:**

```
输入：s = "aa" p = "a*"
输出：true
解释：因为 ''*'' 代表可以匹配零个或多个前面的那一个元素, 在这里前面的元素就是 ''a''。因此，字符串 "aa" 可被视为 ''a'' 重复了一次。
```

**示例 3：**

```
输入：s = "ab" p = ".*"
输出：true
解释：".*" 表示可匹配零个或多个（''*''）任意字符（''.''）。
```

**示例 4：**

```
输入：s = "aab" p = "c*a*b"
输出：true
解释：因为 ''*'' 表示零个或多个，这里 ''c'' 为 0 个, ''a'' 被重复一次。因此可以匹配字符串 "aab"。
```

**示例 5：**

```
输入：s = "mississippi" p = "mis*is*p*."
输出：false
```

**提示：**

- `0 <= s.length <= 20`

- `0 <= p.length <= 30`

- `s` 可能为空，且只包含从 `a-z` 的小写字母。

- `p` 可能为空，且只包含从 `a-z` 的小写字母，以及字符 `.` 和 `*`。

- 保证每次出现字符 `*` 时，前面都匹配到有效的字符', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/regular-expression-matching/', 'd5c451c204da5ee952bf3c220e5d2c1eb8faf89b41aafa84ff0afee1aa97d768', 1, 10, 'regular-expression-matching', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["亚马逊","字节跳动","微软","快手","拼多多","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 40, '2026-04-29'),
(910000000000000115, '最小的k个数', '输入整数数组 `arr` ，找出其中最小的 `k` 个数。例如，输入4、5、1、6、2、7、3、8这8个数字，则最小的4个数字是1、2、3、4。

**示例 1：**

```
输入：arr = [3,2,1], k = 2
输出：[1,2] 或者 [2,1]
```

**示例 2：**

```
输入：arr = [0,1,2,1], k = 1
输出：[0]
```

**限制：**

- `0 <= k <= arr.length <= 10000`

- `0 <= arr[i] <= 10000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/zui-xiao-de-kge-shu-lcof/', '4655bccda7e456ac52c77dc663516040736e13a48437278d56d2088cabcffbbe', 1, NULL, 'zui-xiao-de-kge-shu-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["商汤","字节跳动","小米","微软","快手","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]', 40, '2026-02-11'),
(910000000000001397, '寻找重复数', '给定一个包含 `n + 1` 个整数的数组 `nums` ，其数字都在 `1` 到 `n`**之间（包括 `1` 和 `n`），可知至少存在一个重复的整数。

假设 `nums` 只有 **一个重复的整数** ，找出 **这个重复的数** 。

你设计的解决方案必须不修改数组 `nums` 且只用常量级 `O(1)` 的额外空间。

**示例 1：**

```
输入：nums = [1,3,4,2,2]
输出：2
```

**示例 2：**

```
输入：nums = [3,1,3,4,2]
输出：3
```

**示例 3：**

```
输入：nums = [1,1]
输出：1
```

**示例 4：**

```
输入：nums = [1,1,2]
输出：1
```

**提示：**

- `1 <= n <= 10^5`

- `nums.length == n + 1`

- `1 <= nums[i] <= n`

- `nums` 中 **只有一个整数** 出现 **两次或多次** ，其余整数均只出现 **一次**

**进阶：**

- 如何证明 `nums` 中至少存在一个重复的数字?

- 你可以设计一个线性级时间复杂度 `O(n)` 的解决方案吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-the-duplicate-number/', '06101b86f57dedae63ac78d34e098e98b66e78d590bad9da70df4aa5fa3aff9e', 1, 287, 'find-the-duplicate-number', '["CODETOP","HOT100","HIGH_FREQ"]', '["商汤","字节跳动","微软","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]', 39, '2026-10-30'),
(910000000000001240, '删除二叉搜索树中的节点', '给定一个二叉搜索树的根节点 **root**和一个值 **key**，删除二叉搜索树中的 **key **对应的节点，并保证二叉搜索树的性质不变。返回二叉搜索树（有可能被更新）的根节点的引用。

一般来说，删除节点可分为两个步骤：

- 首先找到需要删除的节点；

- 如果找到了，删除它。

**说明：** 要求算法时间复杂度为 O(h)，h 为树的高度。

**示例:**

```
root = [5,3,6,2,4,null,7]
key = 3

    5
   / \\
  3   6
 / \\   \\
2   4   7

给定需要删除的节点值是 3，所以我们首先找到 3 这个节点，然后删除它。

一个正确的答案是 [5,4,6,2,null,null,7], 如下图所示。

    5
   / \\
  4   6
 /     \\
2       7

另一个正确答案是 [5,2,6,null,4,null,7]。

    5
   / \\
  2   6
   \\   \\
    4   7
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/delete-node-in-a-bst/', 'b6776c9fee2d1ef0da046ca85107a08ed6975ce5bfa8cf868ad4f925ebcaa87f', 1, 450, 'delete-node-in-a-bst', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 39, '2026-04-23'),
(910000000000000083, '二叉搜索树的第k大节点', '给定一棵二叉搜索树，请找出其中第k大的节点。

**示例 1:**

```
输入: root = [3,1,4,null,2], k = 1
   3
  / \\
 1   4
  \\
   2
输出: 4
```

**示例 2:**

```
输入: root = [5,3,6,2,4,null,null,1], k = 3
       5
      / \\
     3   6
    / \\
   2   4
  /
 1
输出: 4
```

**限制：**

1 ≤ k ≤ 二叉搜索树元素个数', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-di-kda-jie-dian-lcof/', 'd8952283b8af1b169fadfe4befa5c125c33047e7b41cb8cc0fe314c2e14384fa', 1, NULL, 'er-cha-sou-suo-shu-de-di-kda-jie-dian-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","微软","猿辅导","腾讯","虾皮","阿里巴巴"]', 39, '2024-03-25'),
(910000000000001389, '数据流的中位数', '中位数是有序列表中间的数。如果列表长度是偶数，中位数则是中间两个数的平均值。

例如，

[2,3,4] 的中位数是 3

[2,3] 的中位数是 (2 + 3) / 2 = 2.5

设计一个支持以下两种操作的数据结构：

- void addNum(int num) - 从数据流中添加一个整数到数据结构中。

- double findMedian() - 返回目前所有元素的中位数。

**示例：**

```
addNum(1)
addNum(2)
findMedian() -> 1.5
addNum(3)
findMedian() -> 2
```

**进阶:**

- 如果数据流中所有整数都在 0 到 100 范围内，你将如何优化你的算法？

- 如果数据流中 99% 的整数都在 0 到 100 范围内，你将如何优化你的算法？', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-median-from-data-stream/', '384cf521bd5e0ffe305cb620d04db3371232ba3d91f5b0767a10445135365bd8', 1, 295, 'find-median-from-data-stream', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["亚马逊","字节跳动","微软","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]', 38, '2026-09-27'),
(910000000000001028, '有效的括号字符串', '给定一个只包含三种字符的字符串：`（ `，`）` 和 `*`，写一个函数来检验这个字符串是否为有效字符串。有效字符串具有如下规则：

- 任何左括号 `(` 必须有相应的右括号 `)`。

- 任何右括号 `)` 必须有相应的左括号 `(` 。

- 左括号 `(` 必须在对应的右括号之前 `)`。

- `*` 可以被视为单个右括号 `)` ，或单个左括号 `(` ，或一个空字符串。

- 一个空字符串也被视为有效字符串。

**示例 1:**

```
输入: "()"
输出: True
```

**示例 2:**

```
输入: "(*)"
输出: True
```

**示例 3:**

```
输入: "(*))"
输出: True
```

**注意:**

- 字符串大小将在 [1，100] 范围内。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-parenthesis-string/', '533fe09fca5f47dfdf179bb59d5ebe4023827bc4c64c081b3c71fd257f7579d2', 1, 678, 'valid-parenthesis-string', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","小米","快手","拼多多","百度","网易","腾讯","虾皮","阿里巴巴"]', 37, '2026-10-24'),
(910000000000001180, '最长回文子序列', '给你一个字符串 `s` ，找出其中最长的回文子序列，并返回该序列的长度。

子序列定义为：不改变剩余字符顺序的情况下，删除某些字符或者不删除任何字符形成的一个序列。

**示例 1：**

```
输入：s = "bbbab"
输出：4
解释：一个可能的最长回文子序列为 "bbbb" 。
```

**示例 2：**

```
输入：s = "cbbd"
输出：2
解释：一个可能的最长回文子序列为 "bb" 。
```

**提示：**

- `1 <= s.length <= 1000`

- `s` 仅由小写英文字母组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-palindromic-subsequence/', '1292b7d5882be97e3d5b90d659881e9d83b02f2467f0dab86276502312342f03', 1, 516, 'longest-palindromic-subsequence', '["CODETOP","HIGH_FREQ"]', '["华为","商汤","字节跳动","快手","拼多多","携程","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 37, '2026-04-26'),
(910000000000001459, '用队列实现栈', '请你仅使用两个队列实现一个后入先出（LIFO）的栈，并支持普通栈的全部四种操作（`push`、`top`、`pop` 和 `empty`）。

实现 `MyStack` 类：

- `void push(int x)` 将元素 x 压入栈顶。

- `int pop()` 移除并返回栈顶元素。

- `int top()` 返回栈顶元素。

- `boolean empty()` 如果栈是空的，返回 `true` ；否则，返回 `false` 。

**注意：**

- 你只能使用队列的基本操作 —— 也就是 `push to back`、`peek/pop from front`、`size` 和 `is empty` 这些操作。

- 你所使用的语言也许不支持队列。 你可以使用 list （列表）或者 deque（双端队列）来模拟一个队列 , 只要是标准的队列操作即可。

**示例：**

```
输入：
["MyStack", "push", "push", "top", "pop", "empty"]
[[], [1], [2], [], [], []]
输出：
[null, null, null, 2, 2, false]

解释：
MyStack myStack = new MyStack();
myStack.push(1);
myStack.push(2);
myStack.top(); // 返回 2
myStack.pop(); // 返回 2
myStack.empty(); // 返回 False
```

**提示：**

- `1 <= x <= 9`

- 最多调用`100` 次 `push`、`pop`、`top` 和 `empty`

- 每次调用 `pop` 和 `top` 都保证栈不为空

**进阶：**你能否实现每种操作的均摊时间复杂度为 `O(1)` 的栈？换句话说，执行 `n` 个操作的总时间复杂度 `O(n)` ，尽管其中某个操作可能需要比其他操作更长的时间。你可以使用两个以上的队列。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/implement-stack-using-queues/', '8346f791525ee6c3f6fd10f4d4e445d4dd3665252e754958c3fa1221ac27e843', 1, 225, 'implement-stack-using-queues', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","快手","拼多多","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 36, '2026-03-26'),
(910000000000001474, '课程表 II', '现在你总共有 `numCourses` 门课需要选，记为 `0` 到 `numCourses - 1`。给你一个数组 `prerequisites` ，其中 `prerequisites[i] = [a_i, b_i]` ，表示在选修课程 `a_i` 前 **必须** 先选修 `b_i` 。

- 例如，想要学习课程 `0` ，你需要先完成课程 `1` ，我们用一个匹配来表示：`[0,1]` 。

返回你为了学完所有课程所安排的学习顺序。可能会有多个正确的顺序，你只要返回 **任意一种** 就可以了。如果不可能完成所有课程，返回 **一个空数组** 。

**示例 1：**

```
输入：numCourses = 2, prerequisites = [[1,0]]
输出：[0,1]
解释：总共有 2 门课程。要学习课程 1，你需要先完成课程 0。因此，正确的课程顺序为 [0,1] 。
```

**示例 2：**

```
输入：numCourses = 4, prerequisites = [[1,0],[2,0],[3,1],[3,2]]
输出：[0,2,1,3]
解释：总共有 4 门课程。要学习课程 3，你应该先完成课程 1 和课程 2。并且课程 1 和课程 2 都应该排在课程 0 之后。
因此，一个正确的课程顺序是 [0,1,2,3] 。另一个正确的排序是 [0,2,1,3] 。
```

**示例 3：**

```
输入：numCourses = 1, prerequisites = []
输出：[0]
```

**提示：**

- `1 <= numCourses <= 2000`

- `0 <= prerequisites.length <= numCourses * (numCourses - 1)`

- `prerequisites[i].length == 2`

- `0 <= a_i, b_i < numCourses`

- `a_i != b_i`

- 所有`[a_i, b_i]` 匹配 **互不相同**

**拓展：**

- 这个问题相当于查找一个循环是否存在于有向图中。如果存在循环，则不存在拓扑排序，因此不可能选取所有课程进行学习。

- 通过 DFS 进行拓扑排序 - 一个关于Coursera的精彩视频教程（21分钟），介绍拓扑排序的基本概念。

- 拓扑排序也可以通过 BFS 完成。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/course-schedule-ii/', 'd215e9db1a5ffa098a9641ca2d2ecb0dce6b0b8b0dbf9d8f47333e1386705d0a', 1, 210, 'course-schedule-ii', '["CODETOP","HIGH_FREQ"]', '["华为","商汤","字节跳动","微软","快手","滴滴","美团","腾讯","虾皮","阿里巴巴"]', 35, '2026-11-17'),
(910000000000001564, '三角形最小路径和', '给定一个三角形 `triangle` ，找出自顶向下的最小路径和。

每一步只能移动到下一行中相邻的结点上。**相邻的结点**在这里指的是 **下标** 与 **上一层结点下标** 相同或者等于 **上一层结点下标 + 1** 的两个结点。也就是说，如果正位于当前行的下标 `i` ，那么下一步可以移动到下一行的下标 `i` 或 `i + 1` 。

**示例 1：**

```
输入：triangle = [[2],[3,4],[6,5,7],[4,1,8,3]]
输出：11
解释：如下面简图所示：
   2
  3 4
 6 5 7
4 1 8 3
自顶向下的最小路径和为 11（即，2 + 3 + 5 + 1 = 11）。
```

**示例 2：**

```
输入：triangle = [[-10]]
输出：-10
```

**提示：**

- `1 <= triangle.length <= 200`

- `triangle[0].length == 1`

- `triangle[i].length == triangle[i - 1].length + 1`

- `-10^4 <= triangle[i][j] <= 10^4`

**进阶：**

- 你可以只使用 `O(n)` 的额外空间（`n` 为三角形的总行数）来解决这个问题吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/triangle/', 'd2b5a47b5cd73bd34666bc47abb309013ec4b0a905cad33bdd523b94c185e0ab', 1, 120, 'triangle', '["CODETOP","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","小米","微软","百度","美团","腾讯"]', 35, '2026-08-03'),
(910000000000001356, '奇偶链表', '给定一个单链表，把所有的奇数节点和偶数节点分别排在一起。请注意，这里的奇数节点和偶数节点指的是节点编号的奇偶性，而不是节点的值的奇偶性。

请尝试使用原地算法完成。你的算法的空间复杂度应为 O(1)，时间复杂度应为 O(nodes)，nodes 为节点总数。

**示例 1:**

```
输入: 1->2->3->4->5->NULL
输出: 1->3->5->2->4->NULL
```

**示例 2:**

```
输入: 2->1->3->5->6->4->7->NULL
输出: 2->3->6->7->1->5->4->NULL
```

**说明:**

- 应当保持奇数节点和偶数节点的相对顺序。

- 链表的第一个节点视为奇数节点，第二个节点视为偶数节点，以此类推。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/odd-even-linked-list/', '40596fdeabf5753d19fb991fe15e113b31c47ce67a1f6ce7c00a616910639188', 1, 328, 'odd-even-linked-list', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","小米","微软","快手","旷视","滴滴","猿辅导","美团","腾讯","阿里巴巴"]', 35, '2026-04-17'),
(910000000000001587, '交错字符串', '给定三个字符串 `s1`、`s2`、`s3`，请你帮忙验证 `s3` 是否是由 `s1` 和 `s2`****交错**组成的。

两个字符串 `s` 和 `t` **交错** 的定义与过程如下，其中每个字符串都会被分割成若干 **非空** 子字符串：

- `s = s_1 + s_2 + ... + s_n`

- `t = t_1 + t_2 + ... + t_m`

- `|n - m| <= 1`

- **交错** 是 `s_1 + t_1 + s_2 + t_2 + s_3 + t_3 + ...` 或者 `t_1 + s_1 + t_2 + s_2 + t_3 + s_3 + ...`

**提示：**`a + b` 意味着字符串 `a` 和 `b` 连接。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/02/interleave.jpg)

```
输入：s1 = "aabcc", s2 = "dbbca", s3 = "aadbbcbcac"
输出：true
```

**示例 2：**

```
输入：s1 = "aabcc", s2 = "dbbca", s3 = "aadbbbaccc"
输出：false
```

**示例 3：**

```
输入：s1 = "", s2 = "", s3 = ""
输出：true
```

**提示：**

- `0 <= s1.length, s2.length <= 100`

- `0 <= s3.length <= 200`

- `s1`、`s2`、和 `s3` 都由小写英文字母组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/interleaving-string/', '63c4741c8496e08b8e28780d4591868c395587428a22c3c9f0d3c9cf77b0e797', 1, 97, 'interleaving-string', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","拼多多","滴滴","美团","腾讯","虾皮","阿里巴巴"]', 34, '2026-10-28'),
(910000000000001495, '轮转数组', '给定一个数组，将数组中的元素向右移动 `k`**个位置，其中 `k`**是非负数。

**进阶：**

- 尽可能想出更多的解决方案，至少有三种不同的方法可以解决这个问题。
- 你可以使用空间复杂度为 O(1) 的 **原地**算法解决这个问题吗？

**示例 1:**

```
输入: nums = [1,2,3,4,5,6,7], k = 3输出: [5,6,7,1,2,3,4]解释:向右旋转 1 步: [7,1,2,3,4,5,6]向右旋转 2 步: [6,7,1,2,3,4,5]向右旋转 3 步: [5,6,7,1,2,3,4]
```
**示例 2:**

```
输入：nums = [-1,-100,3,99], k = 2输出：[3,99,-1,-100]解释: 向右旋转 1 步: [99,-1,-100,3]向右旋转 2 步: [3,99,-1,-100]
```

**提示：**

- `1 <= nums.length <= 2 * 10^4`
- `-2^31 <= nums[i] <= 2^31 - 1`
- `0 <= k <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/rotate-array/', '29068eb363f95b52d5bc27496eb21ace292f073839145b6fa7b6dc46b76c5a0c', 1, 189, 'rotate-array', '["CODETOP","HOT100","HIGH_FREQ"]', '["字节跳动","小米","微软","快手","滴滴","猿辅导","美团","腾讯","阿里巴巴"]', 34, '2026-05-16'),
(910000000000001578, '从中序与后序遍历序列构造二叉树', '根据一棵树的中序遍历与后序遍历构造二叉树。

**注意:**

你可以假设树中没有重复的元素。

例如，给出

```
中序遍历 inorder = [9,3,15,20,7]
后序遍历 postorder = [9,15,7,20,3]
```

返回如下的二叉树：

```
3
   / \\
  9  20
    /  \\
   15   7
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/construct-binary-tree-from-inorder-and-postorder-traversal/', '582d273e2a49d0ea920601fc87eafa571eeb76b774ca466ff9c08d0724ef0b9e', 1, 106, 'construct-binary-tree-from-inorder-and-postorder-traversal', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","旷视","百度","网易","美团","腾讯","虾皮","阿里巴巴"]', 34, '2026-03-27'),
(910000000000001300, '打乱数组', '给你一个整数数组 nums ，设计算法来打乱一个没有重复元素的数组。

实现 `Solution` class:

- `Solution(int[] nums)` 使用整数数组 `nums` 初始化对象

- `int[] reset()` 重设数组到它的初始状态并返回

- `int[] shuffle()` 返回数组随机打乱后的结果

**示例：**

```
输入
["Solution", "shuffle", "reset", "shuffle"]
[[[1, 2, 3]], [], [], []]
输出
[null, [3, 1, 2], [1, 2, 3], [1, 3, 2]]

解释
Solution solution = new Solution([1, 2, 3]);
solution.shuffle();    // 打乱数组 [1,2,3] 并返回结果。任何 [1,2,3]的排列返回的概率应该相同。例如，返回 [3, 1, 2]
solution.reset();      // 重设数组到它的初始状态 [1, 2, 3] 。返回 [1, 2, 3]
solution.shuffle();    // 随机返回数组 [1, 2, 3] 打乱后的结果。例如，返回 [1, 3, 2]
```

**提示：**

- `1 <= nums.length <= 200`

- `-10^6 <= nums[i] <= 10^6`

- `nums` 中的所有元素都是 **唯一的**

- 最多可以调用 `5 * 10^4` 次 `reset` 和 `shuffle`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shuffle-an-array/', '5caf2fd7b48d851812ae8ca4db2eccec87d6545bfd99b724b23bb6086b416fda', 1, 384, 'shuffle-an-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","滴滴","网易","美团","腾讯","虾皮","阿里巴巴"]', 33, '2026-06-20'),
(910000000000001675, '回文数', '给你一个整数 `x` ，如果 `x` 是一个回文整数，返回 `true` ；否则，返回 `false` 。

回文数是指正序（从左向右）和倒序（从右向左）读都是一样的整数。例如，`121` 是回文，而 `123` 不是。

**示例 1：**

```
输入：x = 121
输出：true
```

**示例 2：**

```
输入：x = -121
输出：false
解释：从左向右读, 为 -121 。 从右向左读, 为 121- 。因此它不是一个回文数。
```

**示例 3：**

```
输入：x = 10
输出：false
解释：从右向左读, 为 01 。因此它不是一个回文数。
```

**示例 4：**

```
输入：x = -101
输出：false
```

**提示：**

- `-2^31 <= x <= 2^31 - 1`

**进阶：**你能不将整数转为字符串来解决这个问题吗？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/palindrome-number/', 'cbff46fabf6b08e85bcfda988ba5ecedb199139141330a28a282954e891274b2', 1, 9, 'palindrome-number', '["CODETOP","HIGH_FREQ"]', '["京东","华为","商汤","字节跳动","小米","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]', 33, '2026-05-10'),
(910000000000001454, '二叉搜索树中第K小的元素', '给定一个二叉搜索树的根节点 `root` ，和一个整数 `k` ，请你设计一个算法查找其中第 `k`****个最小元素（从 1 开始计数）。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/28/kthtree1.jpg)

```
输入：root = [3,1,4,null,2], k = 1
输出：1
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/28/kthtree2.jpg)

```
输入：root = [5,3,6,2,4,null,null,1], k = 3
输出：3
```

**提示：**

- 树中的节点数为 `n` 。

- `1 <= k <= n <= 10^4`

- `0 <= Node.val <= 10^4`

**进阶：**如果二叉搜索树经常被修改（插入/删除操作）并且你需要频繁地查找第 `k` 小的值，你将如何优化算法？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/kth-smallest-element-in-a-bst/', '25bc4e0ada5d9e620053335f630f949ad2e94e30dd134ab5876ff1fbc20cfb3e', 1, 230, 'kth-smallest-element-in-a-bst', '["CODETOP","HOT100","HIGH_FREQ"]', '["字节跳动","微软","快手","猿辅导","百度","美团","腾讯","虾皮"]', 33, '2024-04-10'),
(910000000000001640, '通配符匹配', '给定一个字符串 (`s`) 和一个字符模式 (`p`) ，实现一个支持 `''?''` 和 `''*''` 的通配符匹配。

```
''?'' 可以匹配任何单个字符。
''*'' 可以匹配任意字符串（包括空字符串）。
```

两个字符串**完全匹配**才算匹配成功。

**说明:**

- `s` 可能为空，且只包含从 `a-z` 的小写字母。

- `p` 可能为空，且只包含从 `a-z` 的小写字母，以及字符 `?` 和 `*`。

**示例 1:**

```
输入:
s = "aa"
p = "a"
输出: false
解释: "a" 无法匹配 "aa" 整个字符串。
```

**示例 2:**

```
输入:
s = "aa"
p = "*"
输出: true
解释: ''*'' 可以匹配任意字符串。
```

**示例 3:**

```
输入:
s = "cb"
p = "?a"
输出: false
解释: ''?'' 可以匹配 ''c'', 但第二个 ''a'' 无法匹配 ''b''。
```

**示例 4:**

```
输入:
s = "adceb"
p = "*a*b"
输出: true
解释: 第一个 ''*'' 可以匹配空字符串, 第二个 ''*'' 可以匹配字符串 "dce".
```

**示例 5:**

```
输入:
s = "acdcb"
p = "a*c?b"
输出: false
```', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/wildcard-matching/', '3287537e0466d82a44eb8ed921e4b65df215dea175ac802601c1f3687f6705a9', 1, 44, 'wildcard-matching', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]', 32, '2026-08-11'),
(910000000000001588, '不同的二叉搜索树', '给你一个整数 `n` ，求恰由 `n` 个节点组成且节点值从 `1` 到 `n` 互不相同的 **二叉搜索树** 有多少种？返回满足题意的二叉搜索树的种数。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/18/uniquebstn3.jpg)

```
输入：n = 3
输出：5
```

**示例 2：**

```
输入：n = 1
输出：1
```

**提示：**

- `1 <= n <= 19`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/unique-binary-search-trees/', 'e50178cf821be51ad942f36297a1af08c99f0e2a8025d52774412fa5c014c579', 1, 96, 'unique-binary-search-trees', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","快手","百度","美团"]', 32, '2026-04-20'),
(910000000000000073, '圆圈中最后剩下的数字', '0,1,···,n-1这n个数字排成一个圆圈，从数字0开始，每次从这个圆圈里删除第m个数字（删除后从下一个数字开始计数）。求出这个圆圈里剩下的最后一个数字。

例如，0、1、2、3、4这5个数字组成一个圆圈，从数字0开始每次删除第3个数字，则删除的前4个数字依次是2、0、4、1，因此最后剩下的数字是3。

**示例 1：**

```
输入: n = 5, m = 3
输出: 3
```

**示例 2：**

```
输入: n = 10, m = 17
输出: 2
```

**限制：**

- `1 <= n <= 10^5`

- `1 <= m <= 10^6`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/yuan-quan-zhong-zui-hou-sheng-xia-de-shu-zi-lcof/', 'd5116f22d35785a1440eec3e2f4898c54dc70099af787bdc11227ec60284dfd5', 1, NULL, 'yuan-quan-zhong-zui-hou-sheng-xia-de-shu-zi-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","快手","拼多多","百度","美团","腾讯","虾皮","阿里巴巴"]', 32, '2026-01-19'),
(910000000000001599, '最大矩形', '给定一个仅包含 `0` 和 `1` 、大小为 `rows x cols` 的二维二进制矩阵，找出只包含 `1` 的最大矩形，并返回其面积。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/09/14/maximal.jpg)

```
输入：matrix = [["1","0","1","0","0"],["1","0","1","1","1"],["1","1","1","1","1"],["1","0","0","1","0"]]
输出：6
解释：最大矩形如上图所示。
```

**示例 2：**

```
输入：matrix = []
输出：0
```

**示例 3：**

```
输入：matrix = [["0"]]
输出：0
```

**示例 4：**

```
输入：matrix = [["1"]]
输出：1
```

**示例 5：**

```
输入：matrix = [["0","0"]]
输出：0
```

**提示：**

- `rows == matrix.length`

- `cols == matrix[0].length`

- `0 <= row, cols <= 200`

- `matrix[i][j]` 为 `''0''` 或 `''1''`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximal-rectangle/', 'd993931bc3ed473be74039b42b4d9bc4620fe69da69537ed11657d1266453bae', 1, 85, 'maximal-rectangle', '["CODETOP","HIGH_FREQ"]', '["bilibili","华为","商汤","图森","字节跳动","微软","快手","腾讯","阿里巴巴"]', 31, '2026-10-15'),
(910000000000001268, '分割等和子集', '给你一个 **只包含正整数**的 **非空**数组 `nums` 。请你判断是否可以将这个数组分割成两个子集，使得两个子集的元素和相等。

**示例 1：**

```
输入：nums = [1,5,11,5]
输出：true
解释：数组可以分割成 [1, 5, 5] 和 [11] 。
```

**示例 2：**

```
输入：nums = [1,2,3,5]
输出：false
解释：数组不能分割成两个元素和相等的子集。
```

**提示：**

- `1 <= nums.length <= 200`

- `1 <= nums[i] <= 100`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/partition-equal-subset-sum/', 'a214ca987d3b3a23ebd5373ad0fbd11271ff4982f8a3b085681d5fa59297b45b', 1, 416, 'partition-equal-subset-sum', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","快手","拼多多","滴滴","猿辅导","百度","网易","腾讯","阿里巴巴"]', 31, '2026-07-14'),
(910000000000001027, '24 点游戏', '你有 4 张写有 1 到 9 数字的牌。你需要判断是否能通过 `*`，`/`，`+`，`-`，`(`，`)` 的运算得到 24。

**示例 1:**

```
输入: [4, 1, 8, 7]
输出: True
解释: (8-4) * (7-1) = 24
```

**示例 2:**

```
输入: [1, 2, 1, 2]
输出: False
```

**注意:**

- 除法运算符 `/` 表示实数除法，而不是整数除法。例如 4 / (1 - 2/3) = 12 。

- 每个运算符对两个数进行运算。特别是我们不能用 `-` 作为一元运算符。例如，`[1, 1, 1, 1]` 作为输入时，表达式 `-1 - 1 - 1 - 1` 是不允许的。

- 你不能将数字连接在一起。例如，输入为 `[1, 2, 1, 2]` 时，不能写成 12 + 12 。', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/24-game/', '11ffe8349353f253126e1a2669ae5b2a82c768d6058c4ed0b4df2d53a05c47c7', 1, 679, '24-game', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","拼多多","携程","美团","腾讯","虾皮","阿里巴巴"]', 30, '2026-09-09'),
(910000000000001093, '有效三角形的个数', '给定一个包含非负整数的数组，你的任务是统计其中可以组成三角形三条边的三元组个数。

**示例 1:**

```
输入: [2,2,3,4]
输出: 3
解释:
有效的组合是:
2,3,4 (使用第一个 2)
2,3,4 (使用第二个 2)
2,2,3
```

**注意:**

- 数组长度不超过1000。

- 数组里整数的范围为 [0, 1000]。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-triangle-number/', 'feb646272bbfb998ef46b620a2e36f6920ac0b1d31e97e734db501677f2707d8', 1, 611, 'valid-triangle-number', '["CODETOP","HIGH_FREQ"]', '["bilibili","亚马逊","华为","字节跳动","微软","快手","拼多多","旷视","百度","腾讯","虾皮"]', 30, '2026-07-23'),
(910000000000000801, '鸡蛋掉落', '给你 `k` 枚相同的鸡蛋，并可以使用一栋从第 `1` 层到第 `n` 层共有 `n` 层楼的建筑。

已知存在楼层 `f` ，满足 `0 <= f <= n` ，任何从**高于** `f` 的楼层落下的鸡蛋都会碎，从 `f` 楼层或比它低的楼层落下的鸡蛋都不会破。

每次操作，你可以取一枚没有碎的鸡蛋并把它从任一楼层 `x` 扔下（满足 `1 <= x <= n`）。如果鸡蛋碎了，你就不能再次使用它。如果某枚鸡蛋扔下后没有摔碎，则可以在之后的操作中 **重复使用** 这枚鸡蛋。

请你计算并返回要确定 `f` **确切的值** 的 **最小操作次数** 是多少？

**示例 1：**

```
输入：k = 1, n = 2
输出：2
解释：
鸡蛋从 1 楼掉落。如果它碎了，肯定能得出 f = 0 。
否则，鸡蛋从 2 楼掉落。如果它碎了，肯定能得出 f = 1 。
如果它没碎，那么肯定能得出 f = 2 。
因此，在最坏的情况下我们需要移动 2 次以确定 f 是多少。
```

**示例 2：**

```
输入：k = 2, n = 6
输出：3
```

**示例 3：**

```
输入：k = 3, n = 14
输出：4
```

**提示：**

- `1 <= k <= 100`

- `1 <= n <= 10^4`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/super-egg-drop/', '552bd69efcdbd64da631ad328d9a4b1c60d4e33ee8151b3d925bdcec1a142521', 1, 887, 'super-egg-drop', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","百度","美团","腾讯"]', 30, '2026-03-30'),
(910000000000000125, '调整数组顺序使奇数位于偶数前面', '输入一个整数数组，实现一个函数来调整该数组中数字的顺序，使得所有奇数位于数组的前半部分，所有偶数位于数组的后半部分。

**示例：**

```
输入：nums = [1,2,3,4]
输出：[1,3,2,4]
注：[3,1,2,4] 也是正确的答案之一。
```

**提示：**

- `0 <= nums.length <= 50000`

- `1 <= nums[i] <= 10000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/diao-zheng-shu-zu-shun-xu-shi-qi-shu-wei-yu-ou-shu-qian-mian-lcof/', '9e4ce17ffc70b89a6e69e7cbce1bdaba8563f7286b61ab83298933a21a5e8770', 1, NULL, 'diao-zheng-shu-zu-shun-xu-shi-qi-shu-wei-yu-ou-shu-qian-mian-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","百度","美团","阿里巴巴"]', 30, '2022-09-27'),
(910000000000000683, '最大连续1的个数 III', '给定一个由若干 `0` 和 `1` 组成的数组 `A`，我们最多可以将 `K` 个值从 0 变成 1 。

返回仅包含 1 的最长（连续）子数组的长度。

**示例 1：**

```
输入：A = [1,1,1,0,0,0,1,1,1,1,0], K = 2
输出：6
解释：
[1,1,1,0,0,1,1,1,1,1,1]
粗体数字从 0 翻转到 1，最长的子数组长度为 6。
```

**示例 2：**

```
输入：A = [0,0,1,1,0,0,1,1,1,0,1,1,0,0,0,1,1,1,1], K = 3
输出：10
解释：
[0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1]
粗体数字从 0 翻转到 1，最长的子数组长度为 10。
```

**提示：**

- `1 <= A.length <= 20000`

- `0 <= K <= A.length`

- `A[i]` 为 `0` 或 `1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/max-consecutive-ones-iii/', 'fc3c40326174f35516d9d2743db99349a4b11c9fc9a5a65ac31f78f3fb50add9', 1, 1004, 'max-consecutive-ones-iii', '["CODETOP","HIGH_FREQ"]', '["bilibili","华为","字节跳动","微软","快手","拼多多"]', 29, '2026-08-18'),
(910000000000001284, '第N个数字', '在无限的整数序列 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, ...中找到第 `n`**位数字。

**注意：**`n`**是正数且在 32 位整数范围内（`n < 2^31`）。

**示例 1：**

```
输入：3
输出：3
```

**示例 2：**

```
输入：11
输出：0
解释：第 11 位数字在序列 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, ... 里是 0 ，它是 10 的一部分。
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/nth-digit/', '609766415d85db208122f86400c4d728a7fdf03ce3d1232c411c431acfcd38a6', 1, 400, 'nth-digit', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","猿辅导","腾讯","阿里巴巴"]', 29, '2026-04-30'),
(910000000000001368, '去除重复字母', '给你一个字符串 `s` ，请你去除字符串中重复的字母，使得每个字母只出现一次。需保证 **返回结果的字典序最小**（要求不能打乱其他字符的相对位置）。

**注意：**该题与 1081 https://leetcode-cn.com/problems/smallest-subsequence-of-distinct-characters 相同

**示例 1：**

```
输入：s = "bcabc"
输出："abc"
```

**示例 2：**

```
输入：s = "cbacdcbc"
输出："acdb"
```

**提示：**

- `1 <= s.length <= 10^4`

- `s` 由小写英文字母组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-duplicate-letters/', '10cc1fdc2f027713dab0c14d2f30b9c6d5ccc0af504394f35d7d03b471918c8a', 1, 316, 'remove-duplicate-letters', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]', 28, '2026-11-03'),
(910000000000001667, '电话号码的字母组合', '给定一个仅包含数字 `2-9` 的字符串，返回所有它能表示的字母组合。答案可以按 **任意顺序** 返回。

给出数字到字母的映射如下（与电话按键相同）。注意 1 不对应任何字母。

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/original_images/17_telephone_keypad.png)

**示例 1：**

```
输入：digits = "23"
输出：["ad","ae","af","bd","be","bf","cd","ce","cf"]
```

**示例 2：**

```
输入：digits = ""
输出：[]
```

**示例 3：**

```
输入：digits = "2"
输出：["a","b","c"]
```

**提示：**

- `0 <= digits.length <= 4`

- `digits[i]` 是范围 `[''2'', ''9'']` 的一个数字。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/letter-combinations-of-a-phone-number/', 'a84926adbaa1c1214513907a2509968ad5644962f90532c6a5ef71dfbfa0b8d0', 1, 17, 'letter-combinations-of-a-phone-number', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯"]', 28, '2026-10-23'),
(910000000000001289, '至少有K个重复字符的最长子串', '给你一个字符串 `s` 和一个整数 `k` ，请你找出 `s` 中的最长子串， 要求该子串中的每一字符出现次数都不少于 `k` 。返回这一子串的长度。

**示例 1：**

```
输入：s = "aaabb", k = 3
输出：3
解释：最长子串为 "aaa" ，其中 ''a'' 重复了 3 次。
```

**示例 2：**

```
输入：s = "ababbc", k = 2
输出：5
解释：最长子串为 "ababb" ，其中 ''a'' 重复了 2 次， ''b'' 重复了 3 次。
```

**提示：**

- `1 <= s.length <= 10^4`

- `s` 仅由小写英文字母组成

- `1 <= k <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-substring-with-at-least-k-repeating-characters/', 'a2c9d1a329cef456d1509072d0187ffb3ea630cbba0a482b069e79b9a77a6655', 1, 395, 'longest-substring-with-at-least-k-repeating-characters', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","携程","猿辅导","网易","美团","腾讯","阿里巴巴"]', 28, '2026-09-08'),
(910000000000001431, '会议室 II', '该题是 Leetcode Plus 会员题', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/meeting-rooms-ii/', '859c264519ce31b473cc0595b38a600cf21b8ff181679b86bccb977ae3856723', 1, 253, 'meeting-rooms-ii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","微软","快手","拼多多","腾讯","虾皮"]', 28, '2026-09-08'),
(910000000000001621, '不同路径 II', '一个机器人位于一个 *m x n*网格的左上角 （起始点在下图中标记为“Start” ）。

机器人每次只能向下或者向右移动一步。机器人试图达到网格的右下角（在下图中标记为“Finish”）。

现在考虑网格中有障碍物。那么从左上角到右下角将会有多少条不同的路径？

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/10/22/robot_maze.png)

网格中的障碍物和空位置分别用 `1` 和 `0` 来表示。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/04/robot1.jpg)

```
输入：obstacleGrid = [[0,0,0],[0,1,0],[0,0,0]]
输出：2
解释：
3x3 网格的正中间有一个障碍物。
从左上角到右下角一共有 2 条不同的路径：
1. 向右 -> 向右 -> 向下 -> 向下
2. 向下 -> 向下 -> 向右 -> 向右
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/04/robot2.jpg)

```
输入：obstacleGrid = [[0,1],[0,0]]
输出：1
```

**提示：**

- `m == obstacleGrid.length`

- `n == obstacleGrid[i].length`

- `1 <= m, n <= 100`

- `obstacleGrid[i][j]` 为 `0` 或 `1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/unique-paths-ii/', '42e99c779907516d0b232fd5b813c9eea98904787c77615ea87569d00d3fd4a2', 1, 63, 'unique-paths-ii', '["CODETOP","HIGH_FREQ"]', '["华为","商汤","图森","字节跳动","微软","快手","滴滴","网易","美团","腾讯","阿里巴巴"]', 28, '2026-07-07'),
(910000000000001033, '最长递增子序列的个数', '给定一个未排序的整数数组，找到最长递增子序列的个数。

**示例 1:**

```
输入: [1,3,5,4,7]
输出: 2
解释: 有两个最长递增子序列，分别是 [1, 3, 4, 7] 和[1, 3, 5, 7]。
```

**示例 2:**

```
输入: [2,2,2,2,2]
输出: 5
解释: 最长递增子序列的长度是1，并且存在5个子序列的长度为1，因此输出5。
```

**注意:** 给定的数组长度不超过 2000 并且结果一定是32位有符号整数。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/number-of-longest-increasing-subsequence/', 'b5277fa8ad0d18ee2125c136d8031d07b3fa4b0d608e41f85d0bced7a4489880', 1, 673, 'number-of-longest-increasing-subsequence', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯"]', 28, '2026-05-13'),
(910000000000001306, '有序矩阵中第K小的元素', '给你一个 `n x n`**矩阵 `matrix` ，其中每行和每列元素均按升序排序，找到矩阵中第 `k` 小的元素。

请注意，它是 **排序后** 的第 `k` 小元素，而不是第 `k` 个 **不同** 的元素。

**示例 1：**

```
输入：matrix = [[1,5,9],[10,11,13],[12,13,15]], k = 8
输出：13
解释：矩阵中的元素为 [1,5,9,10,11,12,13,13,15]，第 8 小元素是 13
```

**示例 2：**

```
输入：matrix = [[-5]], k = 1
输出：-5
```

**提示：**

- `n == matrix.length`

- `n == matrix[i].length`

- `1 <= n <= 300`

- `-10^9 <= matrix[i][j] <= 10^9`

- 题目数据 **保证** `matrix` 中的所有行和列都按 **非递减顺序** 排列

- `1 <= k <= n^2`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/kth-smallest-element-in-a-sorted-matrix/', '63ca28fe4dfc7f8a25ae3f685857732e3eca6440490a9679befc02dda9cf1bc1', 1, 378, 'kth-smallest-element-in-a-sorted-matrix', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","小红书","快手","拼多多","滴滴","美团","腾讯","虾皮"]', 27, '2026-10-09'),
(910000000000001633, 'N皇后', '**n 皇后问题** 研究的是如何将 `n` 个皇后放置在 `n×n` 的棋盘上，并且使皇后彼此之间不能相互攻击。

给你一个整数 `n` ，返回所有不同的 **n**皇后问题** 的解决方案。

每一种解法包含一个不同的 **n 皇后问题** 的棋子放置方案，该方案中 `''Q''` 和 `''.''` 分别代表了皇后和空位。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/11/13/queens.jpg)

```
输入：n = 4
输出：[[".Q..","...Q","Q...","..Q."],["..Q.","Q...","...Q",".Q.."]]
解释：如上图所示，4 皇后问题存在两个不同的解法。
```

**示例 2：**

```
输入：n = 1
输出：[["Q"]]
```

**提示：**

- `1 <= n <= 9`

- 皇后彼此不能相互攻击，也就是说：任何两个皇后都不能处于同一条横行、纵行或斜线上。', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/n-queens/', 'eb892d544f32326a86ecef7e5bc0c806a179af7314800c890f1ce2caa1aa8558', 1, 51, 'n-queens', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","快手","滴滴","美团","腾讯","虾皮"]', 27, '2026-08-10'),
(910000000000001550, '加油站', '在一条环路上有 *N* 个加油站，其中第 *i* 个加油站有汽油 `gas[i]`* *升。

你有一辆油箱容量无限的的汽车，从第*i*个加油站开往第*i+1 *个加油站需要消耗汽油 `cost[i]`* *升。你从其中的一个加油站出发，开始时油箱为空。

如果你可以绕环路行驶一周，则返回出发时加油站的编号，否则返回 -1。

**说明:**

- 如果题目有解，该答案即为唯一答案。

- 输入数组均为非空数组，且长度相同。

- 输入数组中的元素均为非负数。

**示例 1:**

```
输入:
gas  = [1,2,3,4,5]
cost = [3,4,5,1,2]

输出: 3

解释:
从 3 号加油站(索引为 3 处)出发，可获得 4 升汽油。此时油箱有 = 0 + 4 = 4 升汽油
开往 4 号加油站，此时油箱有 4 - 1 + 5 = 8 升汽油
开往 0 号加油站，此时油箱有 8 - 2 + 1 = 7 升汽油
开往 1 号加油站，此时油箱有 7 - 3 + 2 = 6 升汽油
开往 2 号加油站，此时油箱有 6 - 4 + 3 = 5 升汽油
开往 3 号加油站，你需要消耗 5 升汽油，正好足够你返回到 3 号加油站。
因此，3 可为起始索引。
```

**示例 2:**

```
输入:
gas  = [2,3,4]
cost = [3,4,3]

输出: -1

解释:
你不能从 0 号或 1 号加油站出发，因为没有足够的汽油可以让你行驶到下一个加油站。
我们从 2 号加油站出发，可以获得 4 升汽油。 此时油箱有 = 0 + 4 = 4 升汽油
开往 0 号加油站，此时油箱有 4 - 3 + 2 = 3 升汽油
开往 1 号加油站，此时油箱有 3 - 3 + 3 = 3 升汽油
你无法返回 2 号加油站，因为返程需要消耗 4 升汽油，但是你的油箱只有 3 升汽油。
因此，无论怎样，你都不可能绕环路行驶一周。
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/gas-station/', '4c9d8db434c71410300f811c28ae5c6292059076b3cf5deb4b62aca67fa140ce', 1, 134, 'gas-station', '["CODETOP","HIGH_FREQ"]', '["华为","图森","字节跳动","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]', 27, '2026-04-24'),
(910000000000001014, '前K个高频单词', '给一非空的单词列表，返回前 *k *个出现次数最多的单词。

返回的答案应该按单词出现频率由高到低排序。如果不同的单词有相同出现频率，按字母顺序排序。

**示例 1：**

```
输入: ["i", "love", "leetcode", "i", "love", "coding"], k = 2
输出: ["i", "love"]
解析: "i" 和 "love" 为出现次数最多的两个单词，均为2次。
    注意，按字母顺序 "i" 在 "love" 之前。
```

**示例 2：**

```
输入: ["the", "day", "is", "sunny", "the", "the", "the", "sunny", "is", "is"], k = 4
输出: ["the", "is", "sunny", "day"]
解析: "the", "is", "sunny" 和 "day" 是出现次数最多的四个单词，
    出现次数依次为 4, 3, 2 和 1 次。
```

**注意：**

- 假定 *k* 总为有效值， 1 ≤ *k* ≤ 集合元素数。

- 输入的单词均由小写字母组成。

**扩展练习：**

- 尝试以 *O*(*n* log *k*) 时间复杂度和 *O*(*n*) 空间复杂度解决。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/top-k-frequent-words/', '7e434ce77e2b43ba114e578b5cfd651c681107cf0194f739d6f773f0ff8cc536', 1, 692, 'top-k-frequent-words', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","拼多多","猿辅导","百度","美团","腾讯","阿里巴巴"]', 26, '2026-07-23'),
(910000000000001600, '柱状图中最大的矩形', '给定 *n* 个非负整数，用来表示柱状图中各个柱子的高度。每个柱子彼此相邻，且宽度为 1 。

求在该柱状图中，能够勾勒出来的矩形的最大面积。

**示例 1:**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/histogram.jpg)

```
输入：heights = [2,1,5,6,2,3]
输出：10
解释：最大的矩形为图中红色区域，面积为 10
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/histogram-1.jpg)

```
输入： heights = [2,4]
输出： 4
```

**提示：**

- `1 <= heights.length <=10^5`

- `0 <= heights[i] <= 10^4`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/largest-rectangle-in-histogram/', '32dcaba5f645450ea539a4510c5d4c0a5793b2ed8a0c7cec604f55954809e294', 1, 84, 'largest-rectangle-in-histogram', '["CODETOP","HOT100","HIGH_FREQ"]', '["bilibili","华为","字节跳动","微软","百度","美团","腾讯","阿里巴巴"]', 26, '2026-03-02'),
(910000000000001340, '反转字符串', '编写一个函数，其作用是将输入的字符串反转过来。输入字符串以字符数组 `char[]` 的形式给出。

不要给另外的数组分配额外的空间，你必须**原地修改输入数组**、使用 O(1) 的额外空间解决这一问题。

你可以假设数组中的所有字符都是 ASCII 码表中的可打印字符。

**示例 1：**

```
输入：["h","e","l","l","o"]
输出：["o","l","l","e","h"]
```

**示例 2：**

```
输入：["H","a","n","n","a","h"]
输出：["h","a","n","n","a","H"]
```', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-string/', '12cb76228c9f08eaea0ec4ccd813a4f6faeb1849073dc56be7cc57469dc3c0e2', 1, 344, 'reverse-string', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","快手","拼多多","百度","美团","腾讯","阿里巴巴"]', 25, '2026-11-19'),
(910000000000001199, '目标和', '给你一个整数数组 `nums` 和一个整数 `target` 。

向数组中的每个整数前添加 `''+''` 或 `''-''` ，然后串联起所有整数，可以构造一个 **表达式** ：

- 例如，`nums = [2, 1]` ，可以在 `2` 之前添加 `''+''` ，在 `1` 之前添加 `''-''` ，然后串联起来得到表达式 `"+2-1"` 。

返回可以通过上述方法构造的、运算结果等于 `target` 的不同 **表达式** 的数目。

**示例 1：**

```
输入：nums = [1,1,1,1,1], target = 3
输出：5
解释：一共有 5 种方法让最终目标和为 3 。
-1 + 1 + 1 + 1 + 1 = 3
+1 - 1 + 1 + 1 + 1 = 3
+1 + 1 - 1 + 1 + 1 = 3
+1 + 1 + 1 - 1 + 1 = 3
+1 + 1 + 1 + 1 - 1 = 3
```

**示例 2：**

```
输入：nums = [1], target = 1
输出：1
```

**提示：**

- `1 <= nums.length <= 20`

- `0 <= nums[i] <= 1000`

- `0 <= sum(nums[i]) <= 1000`

- `-1000 <= target <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/target-sum/', 'eb1fabd8edf94a92368dcb0b0be93842c84a48429261b13981e5683b7f9c325c', 1, 494, 'target-sum', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","百度","美团","腾讯","虾皮"]', 25, '2026-07-31'),
(910000000000001420, '丑数 II', '给你一个整数 `n` ，请你找出并返回第 `n` 个 **丑数** 。

**丑数**就是只包含质因数 `2`、`3` 和/或 `5` 的正整数。

**示例 1：**

```
输入：n = 10
输出：12
解释：[1, 2, 3, 4, 5, 6, 8, 9, 10, 12] 是由前 10 个丑数组成的序列。
```

**示例 2：**

```
输入：n = 1
输出：1
解释：1 通常被视为丑数。
```

**提示：**

- `1 <= n <= 1690`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/ugly-number-ii/', 'bbd4f86cfa6b5ec3f9c756c9d03b76d1524c0b44e99f70d0962cf27b62b26b0a', 1, 264, 'ugly-number-ii', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","小米","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]', 25, '2025-10-23'),
(910000000000000812, '按权重随机选择', '给定一个正整数数组 `w` ，其中 `w[i]` 代表下标 `i` 的权重（下标从 `0` 开始），请写一个函数 `pickIndex` ，它可以随机地获取下标 `i`，选取下标 `i` 的概率与 `w[i]` 成正比。

例如，对于 `w = [1, 3]`，挑选下标 `0` 的概率为 `1 / (1 + 3) = 0.25` （即，25%），而选取下标 `1` 的概率为 `3 / (1 + 3) = 0.75`（即，75%）。

也就是说，选取下标 `i` 的概率为 `w[i] / sum(w)` 。

**示例 1：**

```
输入：
["Solution","pickIndex"]
[[[1]],[]]
输出：
[null,0]
解释：
Solution solution = new Solution([1]);
solution.pickIndex(); // 返回 0，因为数组中只有一个元素，所以唯一的选择是返回下标 0。
```

**示例 2：**

```
输入：
["Solution","pickIndex","pickIndex","pickIndex","pickIndex","pickIndex"]
[[[1,3]],[],[],[],[],[]]
输出：
[null,1,1,1,1,0]
解释：
Solution solution = new Solution([1, 3]);
solution.pickIndex(); // 返回 1，返回下标 1，返回该下标概率为 3/4 。
solution.pickIndex(); // 返回 1
solution.pickIndex(); // 返回 1
solution.pickIndex(); // 返回 1
solution.pickIndex(); // 返回 0，返回下标 0，返回该下标概率为 1/4 。

由于这是一个随机问题，允许多个答案，因此下列输出都可以被认为是正确的:
[null,1,1,1,1,0]
[null,1,1,1,1,1]
[null,1,1,1,0,0]
[null,1,1,1,0,1]
[null,1,0,1,0,0]
......
诸若此类。
```

**提示：**

- `1 <= w.length <= 10000`

- `1 <= w[i] <= 10^5`

- `pickIndex` 将被调用不超过 `10000` 次', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/random-pick-with-weight/', '47dd10df44874508381241574d7919da454c7c6721f16274f175731657332b28', 1, 528, 'random-pick-with-weight', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]', 24, '2026-09-19'),
(910000000000000609, '最长重复子串', '给出一个字符串 `S`，考虑其所有**重复子串**（`S` 的连续子串，出现两次或多次，可能会有重叠）。

返回**任何**具有最长可能长度的重复子串。（如果 `S` 不含重复子串，那么答案为 `""`。）

**示例 1：**

```
输入："banana"
输出："ana"
```

**示例 2：**

```
输入："abcd"
输出：""
```

**提示：**

- `2 <= S.length <= 10^5`

- `S` 由小写英文字母组成。', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-duplicate-substring/', '66d571159ec2e2d1a78a9ad7adbe644c0f57cf87d549c8ffc711d6cbe17f5649', 1, 1044, 'longest-duplicate-substring', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","快手","美团","腾讯","阿里巴巴"]', 24, '2026-09-02'),
(910000000000001672, '整数转罗马数字', '罗马数字包含以下七种字符： `I`， `V`， `X`， `L`，`C`，`D` 和 `M`。

```
字符          数值
I             1
V             5
X             10
L             50
C             100
D             500
M             1000
```

例如， 罗马数字 2 写做 `II` ，即为两个并列的 1。12 写做 `XII` ，即为 `X` + `II` 。 27 写做  `XXVII`, 即为 `XX` + `V` + `II` 。

通常情况下，罗马数字中小的数字在大的数字的右边。但也存在特例，例如 4 不写做 `IIII`，而是 `IV`。数字 1 在数字 5 的左边，所表示的数等于大数 5 减小数 1 得到的数值 4 。同样地，数字 9 表示为 `IX`。这个特殊的规则只适用于以下六种情况：

- `I` 可以放在 `V` (5) 和 `X` (10) 的左边，来表示 4 和 9。

- `X` 可以放在 `L` (50) 和 `C` (100) 的左边，来表示 40 和 90。

- `C` 可以放在 `D` (500) 和 `M` (1000) 的左边，来表示 400 和 900。

给你一个整数，将其转为罗马数字。

**示例 1:**

```
输入: num = 3
输出: "III"
```

**示例 2:**

```
输入: num = 4
输出: "IV"
```

**示例 3:**

```
输入: num = 9
输出: "IX"
```

**示例 4:**

```
输入: num = 58
输出: "LVIII"
解释: L = 50, V = 5, III = 3.
```

**示例 5:**

```
输入: num = 1994
输出: "MCMXCIV"
解释: M = 1000, CM = 900, XC = 90, IV = 4.
```

**提示：**

- `1 <= num <= 3999`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/integer-to-roman/', 'a974001c2540da8dfd7f281bc6329a45cddae5802ebd19771e7d7b9109affe37', 1, 12, 'integer-to-roman', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","美团","腾讯"]', 24, '2026-07-08'),
(910000000000001059, '回文子串', '给你一个字符串 `s` ，请你统计并返回这个字符串中 **回文子串** 的数目。

**回文字符串** 是正着读和倒过来读一样的字符串。

**子字符串** 是字符串中的由连续字符组成的一个序列。

具有不同开始位置或结束位置的子串，即使是由相同的字符组成，也会被视作不同的子串。

**示例 1：**

```
输入：s = "abc"
输出：3
解释：三个回文子串: "a", "b", "c"
```

**示例 2：**

```
输入：s = "aaa"
输出：6
解释：6个回文子串: "a", "a", "a", "aa", "aa", "aaa"
```

**提示：**

- `1 <= s.length <= 1000`

- `s` 由小写英文字母组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/palindromic-substrings/', '8dd5367ff707f2144499874d18a18b0510fbcce963c82622adf3edd736114241', 1, 647, 'palindromic-substrings', '["CODETOP","HIGH_FREQ"]', '["亚马逊","京东","华为","字节跳动","快手","猿辅导","百度","美团","腾讯","虾皮"]', 24, '2026-04-20'),
(910000000000001405, '完全平方数', '给定正整数 *n*，找到若干个完全平方数（比如 `1, 4, 9, 16, ...`）使得它们的和等于*n*。你需要让组成和的完全平方数的个数最少。

给你一个整数 `n` ，返回和为 `n` 的完全平方数的 **最少数量** 。

**完全平方数** 是一个整数，其值等于另一个整数的平方；换句话说，其值等于一个整数自乘的积。例如，`1`、`4`、`9` 和 `16` 都是完全平方数，而 `3` 和 `11` 不是。

**示例 1：**

```
输入：n = 12
输出：3
解释：12 = 4 + 4 + 4
```

**示例 2：**

```
输入：n = 13
输出：2
解释：13 = 4 + 9
```

**提示：**

- `1 <= n <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/perfect-squares/', '743283e262b55c47c6fa3738025d855d134fee2e71f1837ed6763254452510fb', 1, 279, 'perfect-squares', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","商汤","字节跳动","滴滴","美团","腾讯"]', 24, '2026-03-20'),
(910000000000001613, '简化路径', '给你一个字符串 `path` ，表示指向某一文件或目录的 Unix 风格 **绝对路径**（以 `''/''` 开头），请你将其转化为更加简洁的规范路径。

在 Unix 风格的文件系统中，一个点（`.`）表示当前目录本身；此外，两个点 （`..`） 表示将目录切换到上一级（指向父目录）；两者都可以是复杂相对路径的组成部分。任意多个连续的斜杠（即，`''//''`）都被视为单个斜杠 `''/''` 。 对于此问题，任何其他格式的点（例如，`''...''`）均被视为文件/目录名称。

请注意，返回的 **规范路径** 必须遵循下述格式：

- 始终以斜杠 `''/''` 开头。

- 两个目录名之间必须只有一个斜杠 `''/''` 。

- 最后一个目录名（如果存在）**不能**以 `''/''` 结尾。

- 此外，路径仅包含从根目录到目标文件或目录的路径上的目录（即，不含 `''.''` 或 `''..''`）。

返回简化后得到的 **规范路径** 。

**示例 1：**

```
输入：path = "/home/"
输出："/home"
解释：注意，最后一个目录名后面没有斜杠。
```

**示例 2：**

```
输入：path = "/../"
输出："/"
解释：从根目录向上一级是不可行的，因为根目录是你可以到达的最高级。
```

**示例 3：**

```
输入：path = "/home//foo/"
输出："/home/foo"
解释：在规范路径中，多个连续斜杠需要用一个斜杠替换。
```

**示例 4：**

```
输入：path = "/a/./b/../../c/"
输出："/c"
```

**提示：**

- `1 <= path.length <= 3000`

- `path` 由英文字母，数字，`''.''`，`''/''` 或 `''_''` 组成。

- `path` 是一个有效的 Unix 风格绝对路径。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/simplify-path/', 'd22dc3416e373be727907aa94c997e113a54f8c59e355be8a6e2efb3a770f7a7', 1, 71, 'simplify-path', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","拼多多","猿辅导","腾讯","阿里巴巴"]', 24, '2026-03-11'),
(910000000000001036, '最大交换', '给定一个非负整数，你**至多**可以交换一次数字中的任意两位。返回你能得到的最大值。

**示例 1 :**

```
输入: 2736
输出: 7236
解释: 交换数字2和数字7。
```

**示例 2 :**

```
输入: 9973
输出: 9973
解释: 不需要交换。
```

**注意:**

- 给定数字的范围是 [0, 10^8]', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-swap/', '8584fb4fd025eb3aa3cf9f8025af91836522815f10c3c5fde5aa8d2fcf77f2e9', 1, 670, 'maximum-swap', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","腾讯","虾皮","阿里巴巴"]', 24, '2026-01-01'),
(910000000000000167, '链表求和', '给定两个用链表表示的整数，每个节点包含一个数位。

这些数位是反向存放的，也就是个位排在链表首部。

编写函数对这两个整数求和，并用链表形式返回结果。

**示例：**

```
输入：(7 -> 1 -> 6) + (5 -> 9 -> 2)，即617 + 295
输出：2 -> 1 -> 9，即912
```

**进阶：**思考一下，假设这些数位是正向存放的，又该如何解决呢?

**示例：**

```
输入：(6 -> 1 -> 7) + (2 -> 9 -> 5)，即617 + 295
输出：9 -> 1 -> 2，即912
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sum-lists-lcci/', 'ee067fff0aa5de825efe3b94fa3c85876f4857ee862ea7cb266a30cb9a579a8a', 1, NULL, 'sum-lists-lcci', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","滴滴","百度","美团","腾讯"]', 24, '2025-12-15'),
(910000000000001335, '两个数组的交集', '给定两个数组，编写一个函数来计算它们的交集。

**示例 1：**

```
输入：nums1 = [1,2,2,1], nums2 = [2,2]
输出：[2]
```

**示例 2：**

```
输入：nums1 = [4,9,5], nums2 = [9,4,9,8,4]
输出：[9,4]
```

**说明：**

- 输出结果中的每个元素一定是唯一的。

- 我们可以不考虑输出结果的顺序。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/intersection-of-two-arrays/', 'daf27c5df50a2a1b8f161b8203cd146fedd01a4a8b93ceda88dd6274ade3b26b', 1, 349, 'intersection-of-two-arrays', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","滴滴","猿辅导","百度","网易","腾讯","虾皮","阿里巴巴"]', 24, '2025-09-03'),
(910000000000001666, '四数之和', '给你一个由 `n` 个整数组成的数组 `nums` ，和一个目标值 `target` 。请你找出并返回满足下述全部条件且不重复的四元组 `[nums[a], nums[b], nums[c], nums[d]]` ：

- `0 <= a, b, c, d < n`

- `a`、`b`、`c` 和 `d` **互不相同**

- `nums[a] + nums[b] + nums[c] + nums[d] == target`

你可以按 **任意顺序** 返回答案 。

**示例 1：**

```
输入：nums = [1,0,-1,0,-2,2], target = 0
输出：[[-2,-1,1,2],[-2,0,0,2],[-1,0,0,1]]
```

**示例 2：**

```
输入：nums = [2,2,2,2,2], target = 8
输出：[[2,2,2,2]]
```

**提示：**

- `1 <= nums.length <= 200`

- `-10^9 <= nums[i] <= 10^9`

- `-10^9 <= target <= 10^9`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/4sum/', '629d00a85f6852cd1e0ea5ba42b90e936945bbf602f7e1c47eac3f41a2d50bd6', 1, 18, '4sum', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","滴滴","美团","腾讯","阿里巴巴"]', 23, '2026-06-17'),
(910000000000001341, '整数拆分', '给定一个正整数 *n*，将其拆分为**至少**两个正整数的和，并使这些整数的乘积最大化。 返回你可以获得的最大乘积。

**示例 1:**

```
输入: 2
输出: 1
解释: 2 = 1 + 1, 1 × 1 = 1。
```

**示例 2:**

```
输入: 10
输出: 36
解释: 10 = 3 + 3 + 4, 3 × 3 × 4 = 36。
```

**说明:**你可以假设 *n *不小于 2 且不大于 58。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/integer-break/', '680341b72ea322f10cfa4db1207c929a8b2d98d74d2124b956d624194286524f', 1, 343, 'integer-break', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["亚马逊","商汤","字节跳动","滴滴","猿辅导","百度","美团","腾讯"]', 23, '2026-06-04'),
(910000000000001573, '二叉树的最小深度', '给定一个二叉树，找出其最小深度。

最小深度是从根节点到最近叶子节点的最短路径上的节点数量。

**说明：**叶子节点是指没有子节点的节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/10/12/ex_depth.jpg)

```
输入：root = [3,9,20,null,null,15,7]
输出：2
```

**示例 2：**

```
输入：root = [2,null,3,null,4,null,5,null,6]
输出：5
```

**提示：**

- 树中节点数的范围在 `[0, 10^5]` 内

- `-1000 <= Node.val <= 1000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/minimum-depth-of-binary-tree/', '30e3572441e026fabc03517f497e68b3df8e5d68f8452bd8f33f859b808062e6', 1, 111, 'minimum-depth-of-binary-tree', '["CODETOP","HIGH_FREQ"]', '["bilibili","华为","字节跳动","小米","快手","猿辅导","美团","腾讯","阿里巴巴"]', 23, '2025-12-18'),
(910000000000001247, '压缩字符串', '给你一个字符数组 `chars` ，请使用下述算法压缩：

从一个空字符串 `s` 开始。对于 `chars` 中的每组 **连续重复字符** ：

- 如果这一组长度为 `1` ，则将字符追加到 `s` 中。

- 否则，需要向 `s` 追加字符，后跟这一组的长度。

压缩后得到的字符串 `s` **不应该直接返回** ，需要转储到字符数组 `chars` 中。需要注意的是，如果组长度为 `10` 或 `10` 以上，则在 `chars` 数组中会被拆分为多个字符。

请在 **修改完输入数组后** ，返回该数组的新长度。

你必须设计并实现一个只使用常量额外空间的算法来解决此问题。

**示例 1：**

```
输入：chars = ["a","a","b","b","c","c","c"]
输出：返回 6 ，输入数组的前 6 个字符应该是：["a","2","b","2","c","3"]
解释：
"aa" 被 "a2" 替代。"bb" 被 "b2" 替代。"ccc" 被 "c3" 替代。
```

**示例 2：**

```
输入：chars = ["a"]
输出：返回 1 ，输入数组的前 1 个字符应该是：["a"]
解释：
没有任何字符串被替代。
```

**示例 3：**

```
输入：chars = ["a","b","b","b","b","b","b","b","b","b","b","b","b"]
输出：返回 4 ，输入数组的前 4 个字符应该是：["a","b","1","2"]。
解释：
由于字符 "a" 不重复，所以不会被压缩。"bbbbbbbbbbbb" 被 “b12” 替代。
注意每个数字在数组中都有它自己的位置。
```

**提示：**

- `1 <= chars.length <= 2000`

- `chars[i]` 可以是小写英文字母、大写英文字母、数字或符号', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/string-compression/', '91f4fc2b4d87ba75b77ec33c80afdca75cc41b6d9139155436ff476b011411de', 1, 443, 'string-compression', '["CODETOP","HIGH_FREQ"]', '["京东","华为","字节跳动","猿辅导","腾讯","虾皮","阿里巴巴"]', 23, '2025-11-06'),
(910000000000001191, '下一个更大元素 II', '给定一个循环数组（最后一个元素的下一个元素是数组的第一个元素），输出每个元素的下一个更大元素。数字 x 的下一个更大的元素是按数组遍历顺序，这个数字之后的第一个比它更大的数，这意味着你应该循环地搜索它的下一个更大的数。如果不存在，则输出 -1。

**示例 1:**

```
输入: [1,2,1]
输出: [2,-1,2]
解释: 第一个 1 的下一个更大的数是 2；
数字 2 找不到下一个更大的数；
第二个 1 的下一个最大的数需要循环搜索，结果也是 2。
```

**注意:** 输入数组的长度不会超过 10000。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/next-greater-element-ii/', 'fa0a6b8d6af9276bc65ce9de378da52035337a17422c3aec2656fc8e7ea5c8fe', 1, 503, 'next-greater-element-ii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","旷视","滴滴","腾讯","虾皮","阿里巴巴"]', 22, '2026-10-10'),
(910000000000000042, '最小K个数', '设计一个算法，找出数组中最小的k个数。以任意顺序返回这k个数均可。

**示例：**

```
输入： arr = [1,3,5,7,2,4,6,8], k = 4
输出： [1,2,3,4]
```

**提示：**

- `0 <= len(arr) <= 100000`

- `0 <= k <= min(100000, len(arr))`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/smallest-k-lcci/', 'c993718a92880d187dd65b116f2a9e5481a19c5f71fb41e5cb62c9b90a095b80', 1, NULL, 'smallest-k-lcci', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","百度","网易","美团","腾讯"]', 22, '2026-06-05'),
(910000000000001598, '分隔链表', '给你一个链表的头节点 `head` 和一个特定值**`x` ，请你对链表进行分隔，使得所有 **小于** `x` 的节点都出现在 **大于或等于** `x` 的节点之前。

你应当 **保留** 两个分区中每个节点的初始相对位置。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/04/partition.jpg)

```
输入：head = [1,4,3,2,5,2], x = 3
输出：[1,2,2,4,3,5]
```

**示例 2：**

```
输入：head = [2,1], x = 2
输出：[1,2]
```

**提示：**

- 链表中节点的数目在范围 `[0, 200]` 内

- `-100 <= Node.val <= 100`

- `-200 <= x <= 200`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/partition-list/', '07134d3f6c8f4a720c6c95ef764f3cd77075df40db556d7a8487fbc09c666e3b', 1, 86, 'partition-list', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","猿辅导","百度","美团","腾讯"]', 22, '2026-03-23'),
(910000000000000949, '划分字母区间', '字符串 `S` 由小写字母组成。我们要把这个字符串划分为尽可能多的片段，同一字母最多出现在一个片段中。返回一个表示每个字符串片段的长度的列表。

**示例：**

```
输入：S = "ababcbacadefegdehijhklij"
输出：[9,7,8]
解释：
划分结果为 "ababcbaca", "defegde", "hijhklij"。
每个字母最多出现在一个片段中。
像 "ababcbacadefegde", "hijhklij" 的划分是错误的，因为划分的片段数较少。
```

**提示：**

- `S`的长度在`[1, 500]`之间。

- `S`只包含小写字母 `''a''` 到 `''z''` 。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/partition-labels/', '17c9ce67dd26907ac5e2be9f4a3197e818e9fb2a2dfd3732f23dd37a8807fa19', 1, 763, 'partition-labels', '["CODETOP","HOT100","HIGH_FREQ"]', '["京东","华为","字节跳动","美团","阿里巴巴"]', 22, '2026-01-13'),
(910000000000000075, '扑克牌中的顺子', '从**若干副扑克牌**中随机抽 `5` 张牌，判断是不是一个顺子，即这5张牌是不是连续的。2～10为数字本身，A为1，J为11，Q为12，K为13，而大、小王为 0 ，可以看成任意数字。A 不能视为 14。

**示例 1:**

```
输入: [1,2,3,4,5]
输出: True
```

**示例 2:**

```
输入: [0,0,1,2,5]
输出: True
```

**限制：**

数组长度为 5

数组的数取值为 [0, 13] .', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/bu-ke-pai-zhong-de-shun-zi-lcof/', '4fd32d73bcab12c2677a8a50f3acdd18f795b36f0fd98b39857bebecde7685c0', 1, NULL, 'bu-ke-pai-zhong-de-shun-zi-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["华为","字节跳动","小米","拼多多","旷视","滴滴","百度","美团","阿里巴巴"]', 22, '2025-03-12'),
(910000000000001604, '删除排序数组中的重复项 II', '给你一个有序数组 `nums` ，请你** 原地** 删除重复出现的元素，使每个元素 **最多出现两次** ，返回删除后数组的新长度。

不要使用额外的数组空间，你必须在 **原地 修改输入数组**并在使用 O(1) 额外空间的条件下完成。

**说明：**

为什么返回数值是整数，但输出的答案是数组呢？

请注意，输入数组是以**「引用」**方式传递的，这意味着在函数里修改输入数组对于调用者是可见的。

你可以想象内部操作如下:

```
// nums 是以“引用”方式传递的。也就是说，不对实参做任何拷贝
int len = removeDuplicates(nums);

// 在函数里修改输入数组对于调用者是可见的。
// 根据你的函数返回的长度, 它会打印出数组中 该长度范围内 的所有元素。
for (int i = 0; i < len; i++) {
    print(nums[i]);
}
```

**示例 1：**

```
输入：nums = [1,1,1,2,2,3]
输出：5, nums = [1,1,2,2,3]
解释：函数应返回新长度 length = 5, 并且原数组的前五个元素被修改为 1, 1, 2, 2, 3 。 不需要考虑数组中超出新长度后面的元素。
```

**示例 2：**

```
输入：nums = [0,0,1,1,1,1,2,3,3]
输出：7, nums = [0,0,1,1,2,3,3]
解释：函数应返回新长度 length = 7, 并且原数组的前五个元素被修改为 0, 0, 1, 1, 2, 3, 3 。 不需要考虑数组中超出新长度后面的元素。
```

**提示：**

- `1 <= nums.length <= 3 * 10^4`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 已按升序排列', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-duplicates-from-sorted-array-ii/', '19c72a239af56af877ec9674b921c8be689d9d12d5937b9cde96399ee1a7b7b4', 1, 80, 'remove-duplicates-from-sorted-array-ii', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","猿辅导","百度","美团","虾皮"]', 21, '2026-10-27'),
(910000000000001569, '不同的子序列', '给定一个字符串 `s`****和一个字符串 `t` ，计算在 `s` 的子序列中 `t` 出现的个数。

字符串的一个 **子序列** 是指，通过删除一些（也可以不删除）字符且不干扰剩余字符相对位置所组成的新字符串。（例如，`"ACE"` 是 `"ABCDE"` 的一个子序列，而 `"AEC"` 不是）

题目数据保证答案符合 32 位带符号整数范围。

**示例 1：**

```
输入：s = "rabbbit", t = "rabbit"
输出：3
解释：
如下图所示, 有 3 种可以从 s 中得到 "rabbit" 的方案。
rabbbit
rabbbit
rabbbit
```

**示例 2：**

```
输入：s = "babgbag", t = "bag"
输出：5
解释：
如下图所示, 有 5 种可以从 s 中得到 "bag" 的方案。
babgbag
babgbag
babgbag
babgbag
babgbag
```

**提示：**

- `0 <= s.length, t.length <= 1000`

- `s` 和 `t` 由英文字母组成', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/distinct-subsequences/', '1e76394e117fb75bd5549ed1e550e4dbac81658d9ec3bcaf0935e0cf6558834a', 1, 115, 'distinct-subsequences', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","百度","网易","美团","腾讯","阿里巴巴"]', 21, '2026-09-23'),
(910000000000000141, '数组中重复的数字', '找出数组中重复的数字。

在一个长度为 n 的数组 nums 里的所有数字都在 0～n-1 的范围内。数组中某些数字是重复的，但不知道有几个数字重复了，也不知道每个数字重复了几次。请找出数组中任意一个重复的数字。

**示例 1：**

```
输入：
[2, 3, 1, 0, 2, 5, 3]
输出：2 或 3
```

**限制：**

`2 <= n <= 100000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shu-zu-zhong-zhong-fu-de-shu-zi-lcof/', 'd509bf692bb5dd2b9428b85f047cdb02ffd7cad4b2e701b06ef733347f5a79dd', 1, NULL, 'shu-zu-zhong-zhong-fu-de-shu-zi-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","微软","快手","美团","腾讯","虾皮"]', 21, '2026-06-10'),
(910000000000001304, '常数时间插入、删除和获取随机元素', '实现`RandomizedSet` 类：

- `RandomizedSet()` 初始化 `RandomizedSet` 对象

- `bool insert(int val)` 当元素 `val` 不存在时，向集合中插入该项，并返回 `true` ；否则，返回 `false` 。

- `bool remove(int val)` 当元素 `val` 存在时，从集合中移除该项，并返回 `true` ；否则，返回 `false` 。

- `int getRandom()` 随机返回现有集合中的一项（测试用例保证调用此方法时集合中至少存在一个元素）。每个元素应该有 **相同的概率** 被返回。

你必须实现类的所有函数，并满足每个函数的 **平均** 时间复杂度为 `O(1)` 。

**示例：**

```
输入
["RandomizedSet", "insert", "remove", "insert", "getRandom", "remove", "insert", "getRandom"]
[[], [1], [2], [2], [], [1], [2], []]
输出
[null, true, false, true, 2, true, false, 2]

解释
RandomizedSet randomizedSet = new RandomizedSet();
randomizedSet.insert(1); // 向集合中插入 1 。返回 true 表示 1 被成功地插入。
randomizedSet.remove(2); // 返回 false ，表示集合中不存在 2 。
randomizedSet.insert(2); // 向集合中插入 2 。返回 true 。集合现在包含 [1,2] 。
randomizedSet.getRandom(); // getRandom 应随机返回 1 或 2 。
randomizedSet.remove(1); // 从集合中移除 1 ，返回 true 。集合现在包含 [2] 。
randomizedSet.insert(2); // 2 已在集合中，所以返回 false 。
randomizedSet.getRandom(); // 由于 2 是集合中唯一的数字，getRandom 总是返回 2 。
```

**提示：**

- `-2^31 <= val <= 2^31 - 1`

- 最多调用 `insert`、`remove` 和 `getRandom` 函数 `2 * ``10^5` 次

- 在调用 `getRandom` 方法时，数据结构中 **至少存在一个** 元素。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/insert-delete-getrandom-o1/', '54d6c444d699ea812cc3257cc52e2351752e490dce606ffb57be12b3918115ff', 1, 380, 'insert-delete-getrandom-o1', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","拼多多","腾讯"]', 21, '2026-03-16'),
(910000000000000239, '切披萨的方案数', '给你一个 `rows x cols` 大小的矩形披萨和一个整数 `k` ，矩形包含两种字符： `''A''` （表示苹果）和 `''.''` （表示空白格子）。你需要切披萨 `k-1` 次，得到 `k` 块披萨并送给别人。

切披萨的每一刀，先要选择是向垂直还是水平方向切，再在矩形的边界上选一个切的位置，将披萨一分为二。如果垂直地切披萨，那么需要把左边的部分送给一个人，如果水平地切，那么需要把上面的部分送给一个人。在切完最后一刀后，需要把剩下来的一块送给最后一个人。

请你返回确保每一块披萨包含 **至少** 一个苹果的切披萨方案数。由于答案可能是个很大的数字，请你返回它对 10^9 + 7 取余的结果。

**示例 1：**

**![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2020/05/10/ways_to_cut_apple_1.png)**

```
输入：pizza = ["A..","AAA","..."], k = 3
输出：3
解释：上图展示了三种切披萨的方案。注意每一块披萨都至少包含一个苹果。
```

**示例 2：**

```
输入：pizza = ["A..","AA.","..."], k = 3
输出：1
```

**示例 3：**

```
输入：pizza = ["A..","A..","..."], k = 1
输出：1
```

**提示：**

- `1 <= rows, cols <= 50`

- `rows == pizza.length`

- `cols == pizza[i].length`

- `1 <= k <= 10`

- `pizza` 只包含字符 `''A''` 和 `''.''` 。', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/number-of-ways-of-cutting-a-pizza/', '69fec9618603680f03336388c04d9a7ac89af8f7f5dfb016eb38478b5c1c3cee', 1, 1444, 'number-of-ways-of-cutting-a-pizza', '["CODETOP","HIGH_FREQ"]', '["bilibili","图森","字节跳动","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","美团","腾讯","虾皮","阿里巴巴"]', 21, '2025-09-26'),
(910000000000000715, '斐波那契数', '**斐波那契数**，通常用 `F(n)` 表示，形成的序列称为 **斐波那契数列** 。该数列由 `0` 和 `1` 开始，后面的每一项数字都是前面两项数字的和。也就是：

```
F(0) = 0，F(1) = 1
F(n) = F(n - 1) + F(n - 2)，其中 n > 1
```

给你 `n` ，请计算 `F(n)` 。

**示例 1：**

```
输入：2
输出：1
解释：F(2) = F(1) + F(0) = 1 + 0 = 1
```

**示例 2：**

```
输入：3
输出：2
解释：F(3) = F(2) + F(1) = 1 + 1 = 2
```

**示例 3：**

```
输入：4
输出：3
解释：F(4) = F(3) + F(2) = 2 + 1 = 3
```

**提示：**

- `0 <= n <= 30`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/fibonacci-number/', 'eef43fa8f8312b0c1051c8da1542460f9b18f21ad96dd93bb1c9b996792793d5', 1, 509, 'fibonacci-number', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","小米","微软","快手","百度","美团","阿里巴巴"]', 21, '2025-09-24'),
(910000000000001231, '重复的子字符串', '给定一个非空的字符串，判断它是否可以由它的一个子串重复多次构成。给定的字符串只含有小写英文字母，并且长度不超过10000。

**示例 1:**

```
输入: "abab"

输出: True

解释: 可由子字符串 "ab" 重复两次构成。
```

**示例 2:**

```
输入: "aba"

输出: False
```

**示例 3:**

```
输入: "abcabcabcabc"

输出: True

解释: 可由子字符串 "abc" 重复四次构成。 (或者子字符串 "abcabc" 重复两次构成。)
```', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/repeated-substring-pattern/', 'bfa365827627b0b3559df8ce92ab60965f638dbcba31a193a387b2f5f17eb1b6', 1, 459, 'repeated-substring-pattern', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","美团","腾讯","阿里巴巴"]', 21, '2025-03-25'),
(910000000000001516, 'Excel表列名称', '给你一个整数 `columnNumber` ，返回它在 Excel 表中相对应的列名称。

例如：

```
A -> 1
B -> 2
C -> 3
...
Z -> 26
AA -> 27
AB -> 28
...
```

**示例 1：**

```
输入：columnNumber = 1
输出："A"
```

**示例 2：**

```
输入：columnNumber = 28
输出："AB"
```

**示例 3：**

```
输入：columnNumber = 701
输出："ZY"
```

**示例 4：**

```
输入：columnNumber = 2147483647
输出："FXSHRXW"
```

**提示：**

- `1 <= columnNumber <= 2^31 - 1`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/excel-sheet-column-title/', '52a9b2c475a31f958951c8d0000c5e63ada49a303ce64301747631f9e17bbc3b', 1, 168, 'excel-sheet-column-title', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","携程","猿辅导","百度","腾讯","虾皮","阿里巴巴"]', 21, '2024-03-19'),
(910000000000000603, '删除字符串中的所有相邻重复项', '给出由小写字母组成的字符串 `S`，**重复项删除操作**会选择两个相邻且相同的字母，并删除它们。

在 S 上反复执行重复项删除操作，直到无法继续删除。

在完成所有重复项删除操作后返回最终的字符串。答案保证唯一。

**示例：**

```
输入："abbaca"
输出："ca"
解释：
例如，在 "abbaca" 中，我们可以删除 "bb" 由于两字母相邻且相同，这是此时唯一可以执行删除操作的重复项。之后我们得到字符串 "aaca"，其中又只有 "aa" 可以执行重复项删除操作，所以最后的字符串为 "ca"。
```

**提示：**

- `1 <= S.length <= 20000`

- `S` 仅由小写英文字母组成。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-all-adjacent-duplicates-in-string/', 'a50bf435f6bcc67333ddfdbddae9dbece784985397b0643579250f206cee5aa8', 1, 1047, 'remove-all-adjacent-duplicates-in-string', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","美团","腾讯","阿里巴巴"]', 21, '2023-08-04'),
(910000000000001530, '寻找旋转排序数组中的最小值 II', '已知一个长度为 `n` 的数组，预先按照升序排列，经由 `1` 到 `n` 次 **旋转** 后，得到输入数组。例如，原数组 `nums = [0,1,4,4,5,6,7]` 在变化后可能得到：

- 若旋转 `4` 次，则可以得到 `[4,5,6,7,0,1,4]`

- 若旋转 `7` 次，则可以得到 `[0,1,4,4,5,6,7]`

注意，数组 `[a[0], a[1], a[2], ..., a[n-1]]` **旋转一次** 的结果为数组 `[a[n-1], a[0], a[1], a[2], ..., a[n-2]]` 。

给你一个可能存在 **重复** 元素值的数组 `nums` ，它原来是一个升序排列的数组，并按上述情形进行了多次旋转。请你找出并返回数组中的 **最小元素** 。

**示例 1：**

```
输入：nums = [1,3,5]
输出：1
```

**示例 2：**

```
输入：nums = [2,2,2,0,1]
输出：0
```

**提示：**

- `n == nums.length`

- `1 <= n <= 5000`

- `-5000 <= nums[i] <= 5000`

- `nums` 原来是一个升序排序的数组，并进行了 `1` 至 `n` 次旋转

**进阶：**

- 这道题是 寻找旋转排序数组中的最小值 的延伸题目。

- 允许重复会影响算法的时间复杂度吗？会如何影响，为什么？', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-minimum-in-rotated-sorted-array-ii/', '9fdd230c2542c237bc8f0984490f4da58aa38c14eb237e0a0eac20cd55958787', 1, 154, 'find-minimum-in-rotated-sorted-array-ii', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","百度","腾讯","虾皮"]', 20, '2026-10-30'),
(910000000000000391, '可被三整除的最大和', '给你一个整数数组 `nums`，请你找出并返回能被三整除的元素最大和。

**示例 1：**

```
输入：nums = [3,6,5,1,8]
输出：18
解释：选出数字 3, 6, 1 和 8，它们的和是 18（可被 3 整除的最大和）。
```

**示例 2：**

```
输入：nums = [4]
输出：0
解释：4 不能被 3 整除，所以无法选出数字，返回 0。
```

**示例 3：**

```
输入：nums = [1,2,3,4,4]
输出：12
解释：选出数字 1, 3, 4 以及 4，它们的和是 12（可被 3 整除的最大和）。
```

**提示：**

- `1 <= nums.length <= 4 * 10^4`

- `1 <= nums[i] <= 10^4`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/greatest-sum-divisible-by-three/', '8a4d8cf4d4bbe99d081034ef248fcda893fb73bd7c7a7608d77c532ed52c88c4', 1, 1262, 'greatest-sum-divisible-by-three', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","美团","腾讯"]', 20, '2026-10-30'),
(910000000000000786, '最大为 N 的数字组合', '我们有一组**排序的**数字 `D`，它是  `{''1'',''2'',''3'',''4'',''5'',''6'',''7'',''8'',''9''}` 的非空子集。（请注意，`''0''` 不包括在内。）

现在，我们用这些数字进行组合写数字，想用多少次就用多少次。例如 `D = {''1'',''3'',''5''}`，我们可以写出像 `''13'', ''551'', ''1351315''` 这样的数字。

返回可以用 `D` 中的数字写出的小于或等于 `N` 的正整数的数目。

**示例 1：**

```
输入：D = ["1","3","5","7"], N = 100
输出：20
解释：
可写出的 20 个数字是：
1, 3, 5, 7, 11, 13, 15, 17, 31, 33, 35, 37, 51, 53, 55, 57, 71, 73, 75, 77.
```

**示例 2：**

```
输入：D = ["1","4","9"], N = 1000000000
输出：29523
解释：
我们可以写 3 个一位数字，9 个两位数字，27 个三位数字，
81 个四位数字，243 个五位数字，729 个六位数字，
2187 个七位数字，6561 个八位数字和 19683 个九位数字。
总共，可以使用D中的数字写出 29523 个整数。
```

**提示：**

- `D` 是按排序顺序的数字 `''1''-''9''` 的子集。

- `1 <= N <= 10^9`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/numbers-at-most-n-given-digit-set/', '11236ae84e149df8f96361fd7be4f33ad2b4a5d5da36c9bee453e71b091a6fb6', 1, 902, 'numbers-at-most-n-given-digit-set', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","百度","美团","阿里巴巴"]', 20, '2026-10-20'),
(910000000000000017, '最大子矩阵', '给定一个正整数、负整数和 0 组成的 N × M 矩阵，编写代码找出元素总和最大的子矩阵。

返回一个数组 `[r1, c1, r2, c2]`，其中 `r1`, `c1` 分别代表子矩阵左上角的行号和列号，`r2`, `c2` 分别代表右下角的行号和列号。若有多个满足条件的子矩阵，返回任意一个均可。

**注意：**本题相对书上原题稍作改动

**示例：**

```
输入：
[
   [-1,0],
   [0,-1]
]
输出：[0,1,0,1]
解释：输入中标粗的元素即为输出所表示的矩阵
```

**说明：**

- `1 <= matrix.length, matrix[0].length <= 200`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/max-submatrix-lcci/', 'e4cb1b2993054bddc1ef29b2d6c30d7fad77af2c0c923802d064ddd6dba99f39', 1, NULL, 'max-submatrix-lcci', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","拼多多","美团","腾讯","阿里巴巴"]', 20, '2026-09-18'),
(910000000000001424, '只出现一次的数字 III', '给定一个整数数组 `nums`，其中恰好有两个元素只出现一次，其余所有元素均出现两次。 找出只出现一次的那两个元素。你可以按 **任意顺序** 返回答案。

**进阶：**你的算法应该具有线性时间复杂度。你能否仅使用常数空间复杂度来实现？

**示例 1：**

```
输入：nums = [1,2,1,3,2,5]
输出：[3,5]
解释：[5, 3] 也是有效的答案。
```

**示例 2：**

```
输入：nums = [-1,0]
输出：[-1,0]
```

**示例 3：**

```
输入：nums = [0,1]
输出：[1,0]
```

**提示：**

- `2 <= nums.length <= 3 * 10^4`

- `-2^31 <= nums[i] <= 2^31 - 1`

- 除两个只出现一次的整数外，`nums` 中的其他数字都出现两次', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/single-number-iii/', '9133176c1e6f90a9b176e454c87ce9d7bcb41089954d26c9fde5db1251bf44d4', 1, 260, 'single-number-iii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","美团","腾讯","虾皮"]', 20, '2026-05-27'),
(910000000000001493, '位1的个数', '编写一个函数，输入是一个无符号整数（以二进制串的形式），返回其二进制表达式中数字位数为 ''1'' 的个数（也被称为汉明重量）。

**提示：**

- 请注意，在某些语言（如 Java）中，没有无符号整数类型。在这种情况下，输入和输出都将被指定为有符号整数类型，并且不应影响您的实现，因为无论整数是有符号的还是无符号的，其内部的二进制表示形式都是相同的。

- 在 Java 中，编译器使用二进制补码记法来表示有符号整数。因此，在上面的 **示例 3** 中，输入表示有符号整数 `-3`。

**示例 1：**

```
输入：00000000000000000000000000001011
输出：3
解释：输入的二进制串 00000000000000000000000000001011 中，共有三位为 ''1''。
```

**示例 2：**

```
输入：00000000000000000000000010000000
输出：1
解释：输入的二进制串 00000000000000000000000010000000 中，共有一位为 ''1''。
```

**示例 3：**

```
输入：11111111111111111111111111111101
输出：31
解释：输入的二进制串 11111111111111111111111111111101 中，共有 31 位为 ''1''。
```

**提示：**

- 输入必须是长度为 `32` 的 **二进制串** 。

**进阶**：

- 如果多次调用这个函数，你将如何优化你的算法？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/number-of-1-bits/', 'a6c7ea7af95942bf1190ad61eb352fe3f2b72b456350212001e736940e7aa98d', 1, 191, 'number-of-1-bits', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","旷视","百度","美团","腾讯","虾皮"]', 20, '2026-05-25'),
(910000000000001347, '打家劫舍 III', '在上次打劫完一条街道之后和一圈房屋后，小偷又发现了一个新的可行窃的地区。这个地区只有一个入口，我们称之为&ldquo;根&rdquo;。 除了&ldquo;根&rdquo;之外，每栋房子有且只有一个&ldquo;父&ldquo;房子与之相连。一番侦察之后，聪明的小偷意识到&ldquo;这个地方的所有房屋的排列类似于一棵二叉树&rdquo;。 如果两个直接相连的房子在同一天晚上被打劫，房屋将自动报警。

计算在不触动警报的情况下，小偷一晚能够盗取的最高金额。

**示例 1:**

```
输入: [3,2,3,null,3,null,1]

     3
    / \\
   2   3
    \\   \\
     3   1

输出: 7
解释: 小偷一晚能够盗取的最高金额 = 3 + 3 + 1 = 7.
```

**示例 2:**

```
输入: [3,4,5,1,3,null,1]

     3
    / \\
   4   5
  / \\   \\
 1   3   1

输出: 9
解释: 小偷一晚能够盗取的最高金额 = 4 + 5 = 9.
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/house-robber-iii/', '661dd8d318b667de0bfda2bad81512181927a281de332f1522d94cd035c78b6d', 1, 337, 'house-robber-iii', '["CODETOP","HIGH_FREQ"]', '["亚马逊","图森","字节跳动","微软","快手","美团","腾讯"]', 20, '2026-05-13'),
(910000000000000093, '把数组排成最小的数', '输入一个非负整数数组，把数组里所有数字拼接起来排成一个数，打印能拼接出的所有数字中最小的一个。

**示例 1:**

```
输入: [10,2]
输出: "102"
```

**示例 2:**

```
输入: [3,30,34,5,9]
输出: "3033459"
```

**提示:**

- `0 < nums.length <= 100`

**说明:**

- 输出结果可能非常大，所以你需要返回一个字符串而不是整数

- 拼接起来的数字可能会有前导 0，最后结果不需要去掉前导 0', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/ba-shu-zu-pai-cheng-zui-xiao-de-shu-lcof/', '6333a2f68ddaf9d4613bdd790778d5bc1fdb94e617ff19d5e2de09c06416803a', 1, NULL, 'ba-shu-zu-pai-cheng-zui-xiao-de-shu-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]', 20, '2026-04-09'),
(910000000000001678, 'Z 字形变换', '将一个给定字符串 `s` 根据给定的行数 `numRows` ，以从上往下、从左到右进行 Z 字形排列。

比如输入字符串为 `"PAYPALISHIRING"` 行数为 `3` 时，排列如下：

```
P   A   H   N
A P L S I I G
Y   I   R
```

之后，你的输出需要从左往右逐行读取，产生出一个新的字符串，比如：`"PAHNAPLSIIGYIR"`。

请你实现这个将字符串进行指定行数变换的函数：

```
string convert(string s, int numRows);
```

**示例 1：**

```
输入：s = "PAYPALISHIRING", numRows = 3
输出："PAHNAPLSIIGYIR"
```

**示例 2：**

```
输入：s = "PAYPALISHIRING", numRows = 4
输出："PINALSIGYAHRPI"
解释：
P     I    N
A   L S  I G
Y A   H R
P     I
```

**示例 3：**

```
输入：s = "A", numRows = 1
输出："A"
```

**提示：**

- `1 <= s.length <= 1000`

- `s` 由英文字母（小写和大写）、`'',''` 和 `''.''` 组成

- `1 <= numRows <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/zigzag-conversion/', 'fa486f1a8b15b8d3f03b94366ba7c363a49aeb7f58ed9d7588c09d142838ca88', 1, 6, 'zigzag-conversion', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","百度","美团"]', 20, '2026-04-03'),
(910000000000001647, '解数独', '编写一个程序，通过填充空格来解决数独问题。

数独的解法需**遵循如下规则**：

- 数字 `1-9` 在每一行只能出现一次。

- 数字 `1-9` 在每一列只能出现一次。

- 数字 `1-9` 在每一个以粗实线分隔的 `3x3` 宫内只能出现一次。（请参考示例图）

数独部分空格内已填入了数字，空白格用 `''.''` 表示。

**示例：**

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2021/04/12/250px-sudoku-by-l2g-20050714svg.png)

```
输入：board = [["5","3",".",".","7",".",".",".","."],["6",".",".","1","9","5",".",".","."],[".","9","8",".",".",".",".","6","."],["8",".",".",".","6",".",".",".","3"],["4",".",".","8",".","3",".",".","1"],["7",".",".",".","2",".",".",".","6"],[".","6",".",".",".",".","2","8","."],[".",".",".","4","1","9",".",".","5"],[".",".",".",".","8",".",".","7","9"]]
输出：[["5","3","4","6","7","8","9","1","2"],["6","7","2","1","9","5","3","4","8"],["1","9","8","3","4","2","5","6","7"],["8","5","9","7","6","1","4","2","3"],["4","2","6","8","5","3","7","9","1"],["7","1","3","9","2","4","8","5","6"],["9","6","1","5","3","7","2","8","4"],["2","8","7","4","1","9","6","3","5"],["3","4","5","2","8","6","1","7","9"]]
解释：输入的数独如上图所示，唯一有效的解决方案如下所示：
```

**提示：**

- `board.length == 9`

- `board[i].length == 9`

- `board[i][j]` 是一位数字或者 `''.''`

- 题目数据 **保证** 输入数独仅有一个解', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/sudoku-solver/', 'c2c1aaa72d5d39f808a467bd7d9567f8b2cdb08f92595234859fb7c740c1c030', 1, 37, 'sudoku-solver', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","小米","微软","美团","腾讯","虾皮","阿里巴巴"]', 20, '2025-04-23'),
(910000000000001135, '字符串的排列', '给你两个字符串 `s1` 和 `s2` ，写一个函数来判断 `s2` 是否包含 `s1`** **的排列。

换句话说，`s1` 的排列之一是 `s2` 的 **子串** 。

**示例 1：**

```
输入：s1 = "ab" s2 = "eidbaooo"
输出：true
解释：s2 包含 s1 的排列之一 ("ba").
```

**示例 2：**

```
输入：s1= "ab" s2 = "eidboaoo"
输出：false
```

**提示：**

- `1 <= s1.length, s2.length <= 10^4`

- `s1` 和 `s2` 仅包含小写字母', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/permutation-in-string/', 'e2489753449688f841fd89ce38c6b4512293b3562d3fa26afda39b825e9ecbc3', 1, 567, 'permutation-in-string', '["CODETOP","HIGH_FREQ"]', '["bilibili","京东","字节跳动","微软","拼多多","腾讯"]', 20, '2025-03-11'),
(910000000000001144, '下一个更大元素 III', '给你一个正整数 `n` ，请你找出符合条件的最小整数，其由重新排列 `n`****中存在的每位数字组成，并且其值大于 `n` 。如果不存在这样的正整数，则返回 `-1` 。

**注意** ，返回的整数应当是一个 **32 位整数** ，如果存在满足题意的答案，但不是 **32 位整数** ，同样返回 `-1` 。

**示例 1：**

```
输入：n = 12
输出：21
```

**示例 2：**

```
输入：n = 21
输出：-1
```

**提示：**

- `1 <= n <= 2^31 - 1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/next-greater-element-iii/', 'f56140151e2bbe5ff437b2f22cb4f97cc06d84d478d376501b068689723c0c92', 1, 556, 'next-greater-element-iii', '["CODETOP","HIGH_FREQ"]', '["字节跳动","猿辅导","美团"]', 20, '2025-03-03'),
(910000000000000832, '和至少为 K 的最短子数组', '返回 `A` 的最短的非空连续子数组的**长度**，该子数组的和至少为 `K` 。

如果没有和至少为 `K` 的非空子数组，返回 `-1` 。

**示例 1：**

```
输入：A = [1], K = 1
输出：1
```

**示例 2：**

```
输入：A = [1,2], K = 4
输出：-1
```

**示例 3：**

```
输入：A = [2,-1,2], K = 3
输出：3
```

**提示：**

- `1 <= A.length <= 50000`

- `-10 ^ 5 <= A[i] <= 10 ^ 5`

- `1 <= K <= 10 ^ 9`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shortest-subarray-with-sum-at-least-k/', '5a8e6106f76c80316b575a509ad6e48023c8c97b794d6486cf698abc0af0c34e', 1, 862, 'shortest-subarray-with-sum-at-least-k', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","腾讯","阿里巴巴"]', 19, '2026-09-03'),
(910000000000000087, '在排序数组中查找数字 I', '统计一个数字在排序数组中出现的次数。

**示例 1:**

```
输入: nums = [5,7,7,8,8,10], target = 8
输出: 2
```

**示例 2:**

```
输入: nums = [5,7,7,8,8,10], target = 6
输出: 0
```

**提示：**

- `0 <= nums.length <= 10^5`

- `-10^9 <= nums[i] <= 10^9`

- `nums` 是一个非递减数组

- `-10^9 <= target <= 10^9`

**注意：**本题与主站 34 题相同（仅返回值不同）：https://leetcode-cn.com/problems/find-first-and-last-position-of-element-in-sorted-array/', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/zai-pai-xu-shu-zu-zhong-cha-zhao-shu-zi-lcof/', '12c2eefe93d9d33f2648b533376eda3f99c06b151e4327f245041fde0272a936', 1, NULL, 'zai-pai-xu-shu-zu-zhong-cha-zhao-shu-zi-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","商汤","图森","字节跳动","快手","美团","阿里巴巴"]', 19, '2026-07-17'),
(910000000000001274, '分割数组的最大值', '给定一个非负整数数组 `nums` 和一个整数 `m` ，你需要将这个数组分成 `m`**个非空的连续子数组。

设计一个算法使得这 `m`**个子数组各自和的最大值最小。

**示例 1：**

```
输入：nums = [7,2,5,10,8], m = 2
输出：18
解释：
一共有四种方法将 nums 分割为 2 个子数组。 其中最好的方式是将其分为 [7,2,5] 和 [10,8] 。
因为此时这两个子数组各自的和的最大值为18，在所有情况中最小。
```

**示例 2：**

```
输入：nums = [1,2,3,4,5], m = 2
输出：9
```

**示例 3：**

```
输入：nums = [1,4,4], m = 3
输出：4
```

**提示：**

- `1 <= nums.length <= 1000`

- `0 <= nums[i] <= 10^6`

- `1 <= m <= min(50, nums.length)`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/split-array-largest-sum/', 'a966f55135634f8c3003f70a7b6712b929d3a5b7f7f70f8632f6e7550d0627af', 1, 410, 'split-array-largest-sum', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","携程","百度","美团","腾讯","阿里巴巴"]', 19, '2025-08-28'),
(910000000000001576, '将有序数组转换为二叉搜索树', '给你一个整数数组 `nums` ，其中元素已经按 **升序** 排列，请你将其转换为一棵 **高度平衡** 二叉搜索树。

**高度平衡**二叉树是一棵满足「每个节点的左右两个子树的高度差的绝对值不超过 1 」的二叉树。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/18/btree1.jpg)

```
输入：nums = [-10,-3,0,5,9]
输出：[0,-3,9,-10,null,5]
解释：[0,-10,5,null,-3,null,9] 也将被视为正确答案：
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/18/btree.jpg)

```
输入：nums = [1,3]
输出：[3,1]
解释：[1,3] 和 [3,1] 都是高度平衡二叉搜索树。
```

**提示：**

- `1 <= nums.length <= 10^4`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 按 **严格递增** 顺序排列', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/convert-sorted-array-to-binary-search-tree/', '4ce121cb06483846452d4060f75b31a50d0b3387e75827c71d786f4f7ceec13e', 1, 108, 'convert-sorted-array-to-binary-search-tree', '["CODETOP","HOT100","HIGH_FREQ"]', '["字节跳动","微软","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]', 18, '2026-09-09'),
(910000000000001442, '有效的字母异位词', '给定两个字符串 `s` 和 `t` ，编写一个函数来判断 `t` 是否是 `s` 的字母异位词。

**注意：**若 `s` 和 `t`**中每个字符出现的次数都相同，则称 `s` 和 `t`**互为字母异位词。

**示例 1:**

```
输入: s = "anagram", t = "nagaram"
输出: true
```

**示例 2:**

```
输入: s = "rat", t = "car"
输出: false
```

**提示:**

- `1 <= s.length, t.length <= 5 * 10^4`

- `s` 和 `t` 仅包含小写字母

**进阶:**如果输入字符串包含 unicode 字符怎么办？你能否调整你的解法来应对这种情况？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-anagram/', '9fe40be551e3255cdb3428ee97492fd19e0f0a1c4c91a3e8c9ca08f4631abea5', 1, 242, 'valid-anagram', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","小米","微软","猿辅导","网易","美团","腾讯"]', 18, '2026-04-16'),
(910000000000000864, '设计循环队列', '设计你的循环队列实现。 循环队列是一种线性数据结构，其操作表现基于 FIFO（先进先出）原则并且队尾被连接在队首之后以形成一个循环。它也被称为&ldquo;环形缓冲器&rdquo;。

循环队列的一个好处是我们可以利用这个队列之前用过的空间。在一个普通队列里，一旦一个队列满了，我们就不能插入下一个元素，即使在队列前面仍有空间。但是使用循环队列，我们能使用这些空间去存储新的值。

你的实现应该支持如下操作：

- `MyCircularQueue(k)`: 构造器，设置队列长度为 k 。

- `Front`: 从队首获取元素。如果队列为空，返回 -1 。

- `Rear`: 获取队尾元素。如果队列为空，返回 -1 。

- `enQueue(value)`: 向循环队列插入一个元素。如果成功插入则返回真。

- `deQueue()`: 从循环队列中删除一个元素。如果成功删除则返回真。

- `isEmpty()`: 检查循环队列是否为空。

- `isFull()`: 检查循环队列是否已满。

**示例：**

```
MyCircularQueue circularQueue = new MyCircularQueue(3); // 设置长度为 3
circularQueue.enQueue(1);  // 返回 true
circularQueue.enQueue(2);  // 返回 true
circularQueue.enQueue(3);  // 返回 true
circularQueue.enQueue(4);  // 返回 false，队列已满
circularQueue.Rear();  // 返回 3
circularQueue.isFull();  // 返回 true
circularQueue.deQueue();  // 返回 true
circularQueue.enQueue(4);  // 返回 true
circularQueue.Rear();  // 返回 4
```

**提示：**

- 所有的值都在 0 至 1000 的范围内；

- 操作数将在 1 至 1000 的范围内；

- 请不要使用内置的队列库。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/design-circular-queue/', '4f0bb428acd0245bbc0d97ecb8bb2da50e0b33d255499ad5a7d4becce5a01f9c', 1, 622, 'design-circular-queue', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","拼多多","滴滴","美团","腾讯","阿里巴巴"]', 18, '2026-01-19'),
(910000000000001383, '删除无效的括号', '给你一个由若干括号和字母组成的字符串 `s` ，删除最小数量的无效括号，使得输入的字符串有效。

返回所有可能的结果。答案可以按 **任意顺序** 返回。

**示例 1：**

```
输入：s = "()())()"
输出：["(())()","()()()"]
```

**示例 2：**

```
输入：s = "(a)())()"
输出：["(a())()","(a)()()"]
```

**示例 3：**

```
输入：s = ")("
输出：[""]
```

**提示：**

- `1 <= s.length <= 25`

- `s` 由小写英文字母以及括号 `''(''` 和 `'')''` 组成

- `s` 中至多含 `20` 个括号', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/remove-invalid-parentheses/', 'c8258fdee2fa31f20cee281bd4fec29b69b1d5305ee420efdbfadf71acf3b03d', 1, 301, 'remove-invalid-parentheses', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","腾讯","阿里巴巴"]', 18, '2025-05-17'),
(910000000000000959, '将二叉搜索树转化为排序的双向链表', '该题是 Leetcode Plus 会员题', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/convert-binary-search-tree-to-sorted-doubly-linked-list/', 'f39bbf9cd4881176a6ee775d0e83c381bdad907b276e47d36c2c3fa01275154d', 1, 426, 'convert-binary-search-tree-to-sorted-doubly-linked-list', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","网易","腾讯"]', 18, '2025-04-28'),
(910000000000001462, '完全二叉树的节点个数', '给你一棵**完全二叉树** 的根节点 `root` ，求出该树的节点个数。

完全二叉树 的定义如下：在完全二叉树中，除了最底层节点可能没填满外，其余每层节点数都达到最大值，并且最下面一层的节点都集中在该层最左边的若干位置。若最底层为第 `h` 层，则该层包含 `1~ 2^h` 个节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/01/14/complete.jpg)

```
输入：root = [1,2,3,4,5,6]
输出：6
```

**示例 2：**

```
输入：root = []
输出：0
```

**示例 3：**

```
输入：root = [1]
输出：1
```

**提示：**

- 树中节点的数目范围是`[0, 5 * 10^4]`

- `0 <= Node.val <= 5 * 10^4`

- 题目数据保证输入的树是 **完全二叉树**

**进阶：**遍历树来统计节点是一种时间复杂度为 `O(n)` 的简单解决方案。你可以设计一个更快的算法吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/count-complete-tree-nodes/', '3daee88b79f26646c34adfa7c670407712cf1733aaedc17cb0c4f0f7dc4478c1', 1, 222, 'count-complete-tree-nodes', '["CODETOP","HIGH_FREQ"]', '["bilibili","字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]', 18, '2024-11-02'),
(910000000000001172, '连续数组', '给定一个二进制数组 `nums` , 找到含有相同数量的 `0` 和 `1` 的最长连续子数组，并返回该子数组的长度。

**示例 1:**

```
输入: nums = [0,1]
输出: 2
说明: [0, 1] 是具有相同数量 0 和 1 的最长连续子数组。
```

**示例 2:**

```
输入: nums = [0,1,0]
输出: 2
说明: [0, 1] (或 [1, 0]) 是具有相同数量0和1的最长连续子数组。
```

**提示：**

- `1 <= nums.length <= 10^5`

- `nums[i]` 不是 `0` 就是 `1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/contiguous-array/', '084796e48ca812cd1ceabf78d650780d295c4c2710bcf04fe41737ed66138204', 1, 525, 'contiguous-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","拼多多","腾讯"]', 17, '2026-08-02'),
(910000000000001635, '字母异位词分组', '给你一个字符串数组，请你将 **字母异位词** 组合在一起。可以按任意顺序返回结果列表。

**字母异位词** 是由重新排列源单词的字母得到的一个新单词，所有源单词中的字母都恰好只用一次。

**示例 1:**

```
输入: strs = ["eat", "tea", "tan", "ate", "nat", "bat"]
输出: [["bat"],["nat","tan"],["ate","eat","tea"]]
```

**示例 2:**

```
输入: strs = [""]
输出: [[""]]
```

**示例 3:**

```
输入: strs = ["a"]
输出: [["a"]]
```

**提示：**

- `1 <= strs.length <= 10^4`

- `0 <= strs[i].length <= 100`

- `strs[i]` 仅包含小写字母', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/group-anagrams/', '538810fdb7690f7fb05c21fcc11f3d68f621d6d1cba40d88063063a7de8e10ae', 1, 49, 'group-anagrams', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","微软","百度","美团","阿里巴巴"]', 17, '2026-06-05'),
(910000000000001427, '二叉树的所有路径', '给你一个二叉树的根节点 `root` ，按 **任意顺序** ，返回所有从根节点到叶子节点的路径。

**叶子节点** 是指没有子节点的节点。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/03/12/paths-tree.jpg)

```
输入：root = [1,2,3,null,5]
输出：["1->2->5","1->3"]
```

**示例 2：**

```
输入：root = [1]
输出：["1"]
```

**提示：**

- 树中节点的数目在范围 `[1, 100]` 内

- `-100 <= Node.val <= 100`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-paths/', '01c456b0c751971ade773cb474933ad8fba2646286ea2d973151eed4af4b7cdd', 1, 257, 'binary-tree-paths', '["CODETOP","HIGH_FREQ"]', '["字节跳动","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 17, '2026-05-25'),
(910000000000001496, '买卖股票的最佳时机 IV', '给定一个整数数组 `prices` ，它的第**`i` 个元素 `prices[i]` 是一支给定的股票在第 `i`**天的价格。

设计一个算法来计算你所能获取的最大利润。你最多可以完成 **k** 笔交易。

**注意：**你不能同时参与多笔交易（你必须在再次购买前出售掉之前的股票）。

**示例 1：**

```
输入：k = 2, prices = [2,4,1]
输出：2
解释：在第 1 天 (股票价格 = 2) 的时候买入，在第 2 天 (股票价格 = 4) 的时候卖出，这笔交易所能获得利润 = 4-2 = 2 。
```

**示例 2：**

```
输入：k = 2, prices = [3,2,6,5,0,3]
输出：7
解释：在第 2 天 (股票价格 = 2) 的时候买入，在第 3 天 (股票价格 = 6) 的时候卖出, 这笔交易所能获得利润 = 6-2 = 4 。
     随后，在第 5 天 (股票价格 = 0) 的时候买入，在第 6 天 (股票价格 = 3) 的时候卖出, 这笔交易所能获得利润 = 3-0 = 3 。
```

**提示：**

- `0 <= k <= 100`

- `0 <= prices.length <= 1000`

- `0 <= prices[i] <= 1000`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/best-time-to-buy-and-sell-stock-iv/', '493f267cf9eaab784e04cf78b02c8e6b1ecadb8537dd9362de774fcd6fe8587f', 1, 188, 'best-time-to-buy-and-sell-stock-iv', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","百度","腾讯"]', 17, '2025-10-22'),
(910000000000001554, '被围绕的区域', '给你一个 `m x n` 的矩阵 `board` ，由若干字符 `''X''` 和 `''O''` ，找到所有被 `''X''` 围绕的区域，并将这些区域里所有的 `''O''` 用 `''X''` 填充。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/02/19/xogrid.jpg)

```
输入：board = [["X","X","X","X"],["X","O","O","X"],["X","X","O","X"],["X","O","X","X"]]
输出：[["X","X","X","X"],["X","X","X","X"],["X","X","X","X"],["X","O","X","X"]]
解释：被围绕的区间不会存在于边界上，换句话说，任何边界上的 ''O'' 都不会被填充为 ''X''。 任何不在边界上，或不与边界上的 ''O'' 相连的 ''O'' 最终都会被填充为 ''X''。如果两个元素在水平或垂直方向相邻，则称它们是“相连”的。
```

**示例 2：**

```
输入：board = [["X"]]
输出：[["X"]]
```

**提示：**

- `m == board.length`

- `n == board[i].length`

- `1 <= m, n <= 200`

- `board[i][j]` 为 `''X''` 或 `''O''`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/surrounded-regions/', '2013caabaad50153b33fbc9ee05aab93dca81c08e636f2b5fcac74c05780f302', 1, 130, 'surrounded-regions', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小红书","微软","快手","猿辅导","百度","网易","阿里巴巴"]', 17, '2025-09-28'),
(910000000000000101, '二叉搜索树的后序遍历序列', '输入一个整数数组，判断该数组是不是某二叉搜索树的后序遍历结果。如果是则返回 `true`，否则返回 `false`。假设输入的数组的任意两个数字都互不相同。

参考以下这颗二叉搜索树：

```
5
    / \\
   2   6
  / \\
 1   3
```

**示例 1：**

```
输入: [1,6,3,2,5]
输出: false
```

**示例 2：**

```
输入: [1,3,2,6,5]
输出: true
```

**提示：**

- `数组长度 <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-hou-xu-bian-li-xu-lie-lcof/', 'c12498f4e1395fef2df300ba194bcf47b80ee07248b5b96b3dc27d62a8ae602c', 1, NULL, 'er-cha-sou-suo-shu-de-hou-xu-bian-li-xu-lie-lcof', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","小米","微软","拼多多","滴滴","网易","美团","虾皮"]', 17, '2025-09-23'),
(910000000000000831, '二叉树中所有距离为 K 的结点', '给定一个二叉树（具有根结点 `root`）， 一个目标结点 `target` ，和一个整数值 `K` 。

返回到目标结点 `target` 距离为 `K` 的所有结点的值的列表。 答案可以以任何顺序返回。

**示例 1：**

```
输入：root = [3,5,1,6,2,0,8,null,null,7,4], target = 5, K = 2
输出：[7,4,1]
解释：
所求结点为与目标结点（值为 5）距离为 2 的结点，
值分别为 7，4，以及 1

注意，输入的 "root" 和 "target" 实际上是树上的结点。
上面的输入仅仅是对这些对象进行了序列化描述。
```

**提示：**

- 给定的树是非空的。

- 树上的每个结点都具有唯一的值 `0 <= node.val <= 500` 。

- 目标结点 `target` 是树上的结点。

- `0 <= K <= 1000`.', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/all-nodes-distance-k-in-binary-tree/', '7eae1bc8a47a06105cebc42aba4c7d11aa92d39efe62a03873bffb16f97452a0', 1, 863, 'all-nodes-distance-k-in-binary-tree', '["CODETOP","HIGH_FREQ"]', '["bilibili","字节跳动","微软","美团","腾讯","阿里巴巴"]', 17, '2025-09-18'),
(910000000000001032, '最长连续递增序列', '给定一个未经排序的整数数组，找到最长且**连续递增的子序列**，并返回该序列的长度。

**连续递增的子序列** 可以由两个下标 `l` 和 `r`（`l < r`）确定，如果对于每个 `l <= i < r`，都有 `nums[i] < nums[i + 1]` ，那么子序列 `[nums[l], nums[l + 1], ..., nums[r - 1], nums[r]]` 就是连续递增子序列。

**示例 1：**

```
输入：nums = [1,3,5,4,7]
输出：3
解释：最长连续递增序列是 [1,3,5], 长度为3。
尽管 [1,3,5,7] 也是升序的子序列, 但它不是连续的，因为 5 和 7 在原数组里被 4 隔开。
```

**示例 2：**

```
输入：nums = [2,2,2,2,2]
输出：1
解释：最长连续递增序列是 [2], 长度为1。
```

**提示：**

- `1 <= nums.length <= 10^4`

- `-10^9 <= nums[i] <= 10^9`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-continuous-increasing-subsequence/', '557f6113432d151978d8c8e66b0323bf890faef780d076af68f04970a05fbf0c', 1, 674, 'longest-continuous-increasing-subsequence', '["CODETOP","HIGH_FREQ"]', '["字节跳动","拼多多","猿辅导","网易","美团","腾讯"]', 17, '2025-03-31'),
(910000000000001517, '两数之和 II - 输入有序数组', '给定一个已按照****非递减顺序排列 **的整数数组 `numbers` ，请你从数组中找出两个数满足相加之和等于目标数 `target` 。

函数应该以长度为 `2` 的整数数组的形式返回这两个数的下标值*。*`numbers` 的下标 **从 1 开始计数** ，所以答案数组应当满足 `1 <= answer[0] < answer[1] <= numbers.length` 。

你可以假设每个输入 **只对应唯一的答案** ，而且你 **不可以** 重复使用相同的元素。

**示例 1：**

```
输入：numbers = [2,7,11,15], target = 9
输出：[1,2]
解释：2 与 7 之和等于目标数 9 。因此 index1 = 1, index2 = 2 。
```

**示例 2：**

```
输入：numbers = [2,3,4], target = 6
输出：[1,3]
```

**示例 3：**

```
输入：numbers = [-1,0], target = -1
输出：[1,2]
```

**提示：**

- `2 <= numbers.length <= 3 * 10^4`

- `-1000 <= numbers[i] <= 1000`

- `numbers` 按 **非递减顺序** 排列

- `-1000 <= target <= 1000`

- **仅存在一个有效答案**', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/two-sum-ii-input-array-is-sorted/', 'dacecb29c2967e50de5efd14942d02b656781c5ae8fe3865e3edf9f0068bd265', 1, 167, 'two-sum-ii-input-array-is-sorted', '["CODETOP","HIGH_FREQ"]', '["商汤","字节跳动","小米","快手","百度","美团","腾讯","虾皮"]', 17, '2024-12-05'),
(910000000000001584, '相同的树', '给你两棵二叉树的根节点 `p` 和 `q` ，编写一个函数来检验这两棵树是否相同。

如果两个树在结构上相同，并且节点具有相同的值，则认为它们是相同的。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/12/20/ex1.jpg)

```
输入：p = [1,2,3], q = [1,2,3]
输出：true
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/12/20/ex2.jpg)

```
输入：p = [1,2], q = [1,null,2]
输出：false
```

**示例 3：**

![题目配图](https://assets.leetcode.com/uploads/2020/12/20/ex3.jpg)

```
输入：p = [1,2,1], q = [1,1,2]
输出：false
```

**提示：**

- 两棵树上的节点数目都在范围 `[0, 100]` 内

- `-10^4 <= Node.val <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/same-tree/', '6bd271b005f3ed30e0dcbf2eb6339bdcb0ad59aec8796ba2be0bde393b1cd193', 1, 100, 'same-tree', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","快手","滴滴","百度","腾讯","阿里巴巴"]', 17, '2024-04-26'),
(910000000000001143, '反转字符串中的单词 III', '给定一个字符串，你需要反转字符串中每个单词的字符顺序，同时仍保留空格和单词的初始顺序。

**示例：**

```
输入："Let''s take LeetCode contest"
输出："s''teL ekat edoCteeL tsetnoc"
```

**提示：**

- 在字符串中，每个单词由单个空格分隔，并且字符串中不会有任何额外的空格。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/reverse-words-in-a-string-iii/', '5945000f388149b5952243a1913e2933905d4fddf8c366e5eba263ff6106982a', 1, 557, 'reverse-words-in-a-string-iii', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","猿辅导","百度","美团","腾讯","阿里巴巴"]', 17, '2022-08-01'),
(910000000000001252, '找到字符串中所有字母异位词', '给定两个字符串 `s` 和 `p`，找到 `s`****中所有 `p`****的 **异位词**的子串，返回这些子串的起始索引。不考虑答案输出的顺序。

**异位词**指字母相同，但排列不同的字符串。

**示例 1:**

```
输入: s = "cbaebabacd", p = "abc"
输出: [0,6]
解释:
起始索引等于 0 的子串是 "cba", 它是 "abc" 的异位词。
起始索引等于 6 的子串是 "bac", 它是 "abc" 的异位词。
```

**示例 2:**

```
输入: s = "abab", p = "ab"
输出: [0,1,2]
解释:
起始索引等于 0 的子串是 "ab", 它是 "ab" 的异位词。
起始索引等于 1 的子串是 "ba", 它是 "ab" 的异位词。
起始索引等于 2 的子串是 "ab", 它是 "ab" 的异位词。
```

**提示:**

- `1 <= s.length, p.length <= 3 * 10^4`

- `s` 和 `p` 仅包含小写字母', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-all-anagrams-in-a-string/', '7e86ccb911684a00b1081e6c5739749b3a2b43088679b7b203337bf1fc1cc5f3', 1, 438, 'find-all-anagrams-in-a-string', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","微软","百度"]', 16, '2026-11-18'),
(910000000000001577, '二叉树的层次遍历 II', '给定一个二叉树，返回其节点值自底向上的层序遍历。 （即按从叶子节点所在层到根节点所在的层，逐层从左向右遍历）

例如：

给定二叉树 `[3,9,20,null,null,15,7]`,

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回其自底向上的层序遍历为：

```
[
  [15,7],
  [9,20],
  [3]
]
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/binary-tree-level-order-traversal-ii/', '57e2e67ac2cde3a0d3d1e737d8136aec24ff379f5724645fa8ad250ee955cc11', 1, 107, 'binary-tree-level-order-traversal-ii', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","小米","拼多多","滴滴","百度","美团","腾讯"]', 16, '2026-08-06'),
(910000000000001534, '逆波兰表达式求值', '根据 逆波兰表示法，求表达式的值。

有效的算符包括 `+`、`-`、`*`、`/` 。每个运算对象可以是整数，也可以是另一个逆波兰表达式。

**说明：**

- 整数除法只保留整数部分。

- 给定逆波兰表达式总是有效的。换句话说，表达式总会得出有效数值且不存在除数为 0 的情况。

**示例 1：**

```
输入：tokens = ["2","1","+","3","*"]
输出：9
解释：该算式转化为常见的中缀算术表达式为：((2 + 1) * 3) = 9
```

**示例 2：**

```
输入：tokens = ["4","13","5","/","+"]
输出：6
解释：该算式转化为常见的中缀算术表达式为：(4 + (13 / 5)) = 6
```

**示例 3：**

```
输入：tokens = ["10","6","9","3","+","-11","*","/","*","17","+","5","+"]
输出：22
解释：
该算式转化为常见的中缀算术表达式为：
  ((10 * (6 / ((9 + 3) * -11))) + 17) + 5
= ((10 * (6 / (12 * -11))) + 17) + 5
= ((10 * (6 / -132)) + 17) + 5
= ((10 * 0) + 17) + 5
= (0 + 17) + 5
= 17 + 5
= 22
```

**提示：**

- `1 <= tokens.length <= 10^4`

- `tokens[i]` 要么是一个算符（`"+"`、`"-"`、`"*"` 或 `"/"`），要么是一个在范围 `[-200, 200]` 内的整数

**逆波兰表达式：**

逆波兰表达式是一种后缀表达式，所谓后缀就是指算符写在后面。

- 平常使用的算式则是一种中缀表达式，如 `( 1 + 2 ) * ( 3 + 4 )` 。

- 该算式的逆波兰表达式写法为 `( ( 1 2 + ) ( 3 4 + ) * )` 。

逆波兰表达式主要有以下两个优点：

- 去掉括号后表达式无歧义，上式即便写成 `1 2 + 3 4 + * `也可以依据次序计算出正确结果。

- 适合用栈操作运算：遇到数字则入栈；遇到算符则取出栈顶两个数字进行计算，并将结果压入栈中。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/evaluate-reverse-polish-notation/', 'eb1e33b2c34fdcdb745e0670c169ab62fa810829c2737b7d84f1a84c912f96b4', 1, 150, 'evaluate-reverse-polish-notation', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","小红书","微软","快手","拼多多","百度","美团","腾讯"]', 16, '2026-05-19'),
(910000000000001449, '二叉搜索树的最近公共祖先', '给定一个二叉搜索树, 找到该树中两个指定节点的最近公共祖先。

百度百科中最近公共祖先的定义为：&ldquo;对于有根树 T 的两个结点 p、q，最近公共祖先表示为一个结点 x，满足 x 是 p、q 的祖先且 x 的深度尽可能大（**一个节点也可以是它自己的祖先**）。&rdquo;

例如，给定如下二叉搜索树:  root = [6,2,8,0,4,7,9,null,null,3,5]

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/binarysearchtree_improved.png)

**示例 1:**

```
输入: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 8
输出: 6
解释: 节点 2 和节点 8 的最近公共祖先是 6。
```

**示例 2:**

```
输入: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 4
输出: 2
解释: 节点 2 和节点 4 的最近公共祖先是 2, 因为根据定义最近公共祖先节点可以为节点本身。
```

**说明:**

- 所有节点的值都是唯一的。

- p、q 为不同节点且均存在于给定的二叉搜索树中。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lowest-common-ancestor-of-a-binary-search-tree/', 'a638157ed5b5267b6b4b9c5f3fa4a0e2a5c5638064956e71cb5369433821ec01', 1, 235, 'lowest-common-ancestor-of-a-binary-search-tree', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","快手","拼多多","滴滴","猿辅导","腾讯","阿里巴巴"]', 16, '2026-03-19'),
(910000000000000710, '有序数组的平方', '给你一个按 **非递减顺序** 排序的整数数组 `nums`，返回 **每个数字的平方** 组成的新数组，要求也按 **非递减顺序** 排序。

**示例 1：**

```
输入：nums = [-4,-1,0,3,10]
输出：[0,1,9,16,100]
解释：平方后，数组变为 [16,1,0,9,100]
排序后，数组变为 [0,1,9,16,100]
```

**示例 2：**

```
输入：nums = [-7,-3,2,3,11]
输出：[4,9,9,49,121]
```

**提示：**

- `1 <= nums.length <= 10^4`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 已按 **非递减顺序** 排序

**进阶：**

- 请你设计时间复杂度为 `O(n)` 的算法解决本问题', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/squares-of-a-sorted-array/', '571552bc6f9f9e769be636367e7b7d19dcfb9c489bd6ca098e92753bb2aee7a2', 1, 977, 'squares-of-a-sorted-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","阿里巴巴"]', 16, '2026-01-19'),
(910000000000001607, '组合', '给定两个整数 `n` 和 `k`，返回范围 `[1, n]` 中所有可能的 `k` 个数的组合。

你可以按 **任何顺序** 返回答案。

**示例 1：**

```
输入：n = 4, k = 2
输出：
[
  [2,4],
  [3,4],
  [2,3],
  [1,2],
  [1,3],
  [1,4],
]
```

**示例 2：**

```
输入：n = 1, k = 1
输出：[[1]]
```

**提示：**

- `1 <= n <= 20`

- `1 <= k <= n`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/combinations/', '1f8bb29f765a85d9fad03af2786d777b8c81620f8c6628bb397c62411c21fcd9', 1, 77, 'combinations', '["CODETOP","HIGH_FREQ"]', '["京东","商汤","字节跳动","微软","拼多多","腾讯","虾皮"]', 16, '2025-06-10'),
(910000000000001416, '缺失数字', '给定一个包含 `[0, n]` 中 `n` 个数的数组 `nums` ，找出 `[0, n]` 这个范围内没有出现在数组中的那个数。

**进阶：**

- 你能否实现线性时间复杂度、仅使用额外常数空间的算法解决此问题?

**示例 1：**

```
输入：nums = [3,0,1]
输出：2
解释：n = 3，因为有 3 个数字，所以所有的数字都在范围 [0,3] 内。2 是丢失的数字，因为它没有出现在 nums 中。
```

**示例 2：**

```
输入：nums = [0,1]
输出：2
解释：n = 2，因为有 2 个数字，所以所有的数字都在范围 [0,2] 内。2 是丢失的数字，因为它没有出现在 nums 中。
```

**示例 3：**

```
输入：nums = [9,6,4,2,3,5,7,0,1]
输出：8
解释：n = 9，因为有 9 个数字，所以所有的数字都在范围 [0,9] 内。8 是丢失的数字，因为它没有出现在 nums 中。
```

**示例 4：**

```
输入：nums = [0]
输出：1
解释：n = 1，因为有 1 个数字，所以所有的数字都在范围 [0,1] 内。1 是丢失的数字，因为它没有出现在 nums 中。
```

**提示：**

- `n == nums.length`

- `1 <= n <= 10^4`

- `0 <= nums[i] <= n`

- `nums` 中的所有数字都 **独一无二**', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/missing-number/', '8614e247899cd0b372f2eae65c635216a4491f0341072cb257472c1de2ce63a4', 1, 268, 'missing-number', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]', 16, '2023-08-22'),
(910000000000001253, '路径总和 III', '给定一个二叉树的根节点 `root` ，和一个整数 `targetSum` ，求该二叉树里节点值之和等于 `targetSum` 的 **路径** 的数目。

**路径** 不需要从根节点开始，也不需要在叶子节点结束，但是路径方向必须是向下的（只能从父节点到子节点）。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/04/09/pathsum3-1-tree.jpg)

```
输入：root = [10,5,-3,3,2,null,11,3,-2,null,1], targetSum = 8
输出：3
解释：和等于 8 的路径有 3 条，如图所示。
```

**示例 2：**

```
输入：root = [5,4,8,11,null,13,4,7,2,null,null,5,1], targetSum = 22
输出：3
```

**提示:**

- 二叉树的节点个数的范围是 `[0,1000]`

- `-10^9 <= Node.val <= 10^9`

- `-1000 <= targetSum <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/path-sum-iii/', '93073c76c70356a2793cc3b4e1b4468e69c52f7dae0deb4074cb5256674f821b', 1, 437, 'path-sum-iii', '["CODETOP","HOT100","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","猿辅导","网易","腾讯"]', 15, '2026-10-08'),
(910000000000001378, '累加数', '累加数是一个字符串，组成它的数字可以形成累加序列。

一个有效的累加序列必须**至少**包含 3 个数。除了最开始的两个数以外，字符串中的其他数都等于它之前两个数相加的和。

给定一个只包含数字 `''0''-''9''` 的字符串，编写一个算法来判断给定输入是否是累加数。

**说明: **累加序列里的数不会以 0 开头，所以不会出现 `1, 2, 03` 或者 `1, 02, 3` 的情况。

**示例 1:**

```
输入: "112358"
输出: true
解释: 累加序列为: 1, 1, 2, 3, 5, 8 。1 + 1 = 2, 1 + 2 = 3, 2 + 3 = 5, 3 + 5 = 8
```

**示例 2:**

```
输入: "199100199"
输出: true
解释: 累加序列为: 1, 99, 100, 199。1 + 99 = 100, 99 + 100 = 199
```

**进阶:**

你如何处理一个溢出的过大的整数输入?', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/additive-number/', '9308d990e798efca24e091270fbb2439b2e714881e268832a6e5a0aac8eda3db', 1, 306, 'additive-number', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","猿辅导","腾讯","阿里巴巴"]', 15, '2026-09-03'),
(910000000000001330, '俄罗斯套娃信封问题', '给你一个二维整数数组 `envelopes` ，其中 `envelopes[i] = [w_i, h_i]` ，表示第 `i` 个信封的宽度和高度。

当另一个信封的宽度和高度都比这个信封大的时候，这个信封就可以放进另一个信封里，如同俄罗斯套娃一样。

请计算 **最多能有多少个** 信封能组成一组“俄罗斯套娃”信封（即可以把一个信封放到另一个信封里面）。

**注意**：不允许旋转信封。

**示例 1：**

```
输入：envelopes = [[5,4],[6,4],[6,7],[2,3]]
输出：3
解释：最多信封的个数为 3, 组合为: [2,3] => [5,4] => [6,7]。
```

**示例 2：**

```
输入：envelopes = [[1,1],[1,1],[1,1]]
输出：1
```

**提示：**

- `1 <= envelopes.length <= 5000`

- `envelopes[i].length == 2`

- `1 <= w_i, h_i <= 10^4`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/russian-doll-envelopes/', '6d1cc59d06781dcf412013a289d2e1e7ee57ac0b62dc5e61caabc2b1eefd1192', 1, 354, 'russian-doll-envelopes', '["CODETOP","HIGH_FREQ"]', '["亚马逊","华为","字节跳动","微软","拼多多","美团","腾讯","虾皮","阿里巴巴"]', 15, '2026-09-02'),
(910000000000001451, '数字 1 的个数', '给定一个整数 `n`，计算所有小于等于 `n` 的非负整数中数字 `1` 出现的个数。

**示例 1：**

```
输入：n = 13
输出：6
```

**示例 2：**

```
输入：n = 0
输出：0
```

**提示：**

- `0 <= n <= 10^9`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/number-of-digit-one/', 'db468a5baebd09f562175ff5d0265f5b86a670d0123b94934c88a7612193c7f4', 1, 233, 'number-of-digit-one', '["CODETOP","JIANZHI","HIGH_FREQ"]', '["字节跳动","微软","快手","旷视","腾讯","阿里巴巴"]', 15, '2026-08-15'),
(910000000000000742, '验证栈序列', '给定 `pushed` 和 `popped` 两个序列，每个序列中的 **值都不重复**，只有当它们可能是在最初空栈上进行的推入 push 和弹出 pop 操作序列的结果时，返回 `true`；否则，返回 `false` 。

**示例 1：**

```
输入：pushed = [1,2,3,4,5], popped = [4,5,3,2,1]
输出：true
解释：我们可以按以下顺序执行：
push(1), push(2), push(3), push(4), pop() -> 4,
push(5), pop() -> 5, pop() -> 3, pop() -> 2, pop() -> 1
```

**示例 2：**

```
输入：pushed = [1,2,3,4,5], popped = [4,3,5,1,2]
输出：false
解释：1 不能在 2 之前弹出。
```

**提示：**

- `1 <= pushed.length <= 1000`

- `0 <= pushed[i] <= 1000`

- `pushed` 的所有元素 **互不相同**

- `popped.length == pushed.length`

- `popped` 是 `pushed` 的一个排列', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/validate-stack-sequences/', '1943f9bf0b99f7d8273c500629738e171e47d8b04b77bd0d5ef4bb31f052f309', 1, 946, 'validate-stack-sequences', '["CODETOP","HIGH_FREQ"]', '["商汤","字节跳动","小米","小红书","微软","快手","旷视","百度","美团"]', 15, '2026-06-01'),
(910000000000001298, '字典序排数', '给定一个整数 *n*, 返回从 *1 *到 *n *的字典顺序。

例如，

给定 *n* =1 3，返回 [1,10,11,12,13,2,3,4,5,6,7,8,9] 。

请尽可能的优化算法的时间复杂度和空间复杂度。 输入的数据 *n *小于等于 5,000,000。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/lexicographical-numbers/', '6663889c9d819ff9e6f30136408f625cb0304e61589c708de50a07fe24576222', 1, 386, 'lexicographical-numbers', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","拼多多","猿辅导","百度","虾皮","阿里巴巴"]', 15, '2026-05-11'),
(910000000000001174, '连续的子数组和', '给你一个整数数组 `nums` 和一个整数 `k` ，编写一个函数来判断该数组是否含有同时满足下述条件的连续子数组：

- 子数组大小 **至少为 2** ，且

- 子数组元素总和为 `k` 的倍数。

如果存在，返回 `true` ；否则，返回 `false` 。

如果存在一个整数 `n` ，令整数 `x` 符合 `x = n * k` ，则称 `x` 是 `k` 的一个倍数。`0` 始终视为 `k` 的一个倍数。

**示例 1：**

```
输入：nums = [23,2,4,6,7], k = 6
输出：true
解释：[2,4] 是一个大小为 2 的子数组，并且和为 6 。
```

**示例 2：**

```
输入：nums = [23,2,6,4,7], k = 6
输出：true
解释：[23, 2, 6, 4, 7] 是大小为 5 的子数组，并且和为 42 。
42 是 6 的倍数，因为 42 = 7 * 6 且 7 是一个整数。
```

**示例 3：**

```
输入：nums = [23,2,6,4,7], k = 13
输出：false
```

**提示：**

- `1 <= nums.length <= 10^5`

- `0 <= nums[i] <= 10^9`

- `0 <= sum(nums[i]) <= 2^31 - 1`

- `1 <= k <= 2^31 - 1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/continuous-subarray-sum/', '321213395544988b4fbf7b531f0e6e64f53edfdeb31dc9e243fd100be4aaede1', 1, 523, 'continuous-subarray-sum', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","快手","拼多多","滴滴","百度","腾讯"]', 15, '2026-05-08'),
(910000000000000693, '腐烂的橘子', '在给定的网格中，每个单元格可以有以下三个值之一：

- 值 `0` 代表空单元格；

- 值 `1` 代表新鲜橘子；

- 值 `2` 代表腐烂的橘子。

每分钟，任何与腐烂的橘子（在 4 个正方向上）相邻的新鲜橘子都会腐烂。

返回直到单元格中没有新鲜橘子为止所必须经过的最小分钟数。如果不可能，返回 `-1`。

**示例 1：**

**![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2019/02/16/oranges.png)**

```
输入：[[2,1,1],[1,1,0],[0,1,1]]
输出：4
```

**示例 2：**

```
输入：[[2,1,1],[0,1,1],[1,0,1]]
输出：-1
解释：左下角的橘子（第 2 行， 第 0 列）永远不会腐烂，因为腐烂只会发生在 4 个正向上。
```

**示例 3：**

```
输入：[[0,2]]
输出：0
解释：因为 0 分钟时已经没有新鲜橘子了，所以答案就是 0 。
```

**提示：**

- `1 <= grid.length <= 10`

- `1 <= grid[0].length <= 10`

- `grid[i][j]` 仅为 `0`、`1` 或 `2`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/rotting-oranges/', 'f9d7505ddfe411bae7e1fe9273f0fec46de204bc6ca4b41d84600467b4c0f2c6', 1, 994, 'rotting-oranges', '["CODETOP","HOT100","HIGH_FREQ"]', '["华为","字节跳动","小米","小红书","微软","拼多多","滴滴","腾讯","阿里巴巴"]', 15, '2026-04-21'),
(910000000000001159, '有序数组中的单一元素', '给定一个只包含整数的有序数组，每个元素都会出现两次，唯有一个数只会出现一次，找出这个数。

**示例 1:**

```
输入: nums = [1,1,2,3,3,4,4,8,8]
输出: 2
```

**示例 2:**

```
输入: nums =  [3,3,7,7,10,11,11]
输出: 10
```

**提示:**

- `1 <= nums.length <= 10^5`

- `0 <= nums[i] <= 10^5`

**进阶:** 采用的方案可以在 `O(log n)` 时间复杂度和 `O(1)` 空间复杂度中运行吗？', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/single-element-in-a-sorted-array/', 'dd7a75df12ea20a44fcc324cb99778c78d9dea4c5cc2ec72e6d79b7452ff7d9b', 1, 540, 'single-element-in-a-sorted-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","快手","拼多多","猿辅导","美团","腾讯","阿里巴巴"]', 15, '2026-04-10'),
(910000000000001446, '除自身以外数组的乘积', '给你一个长度为 *n* 的整数数组 `nums`，其中 *n* > 1，返回输出数组 `output` ，其中 `output[i]` 等于 `nums` 中除 `nums[i]` 之外其余各元素的乘积。

**示例:**

```
输入: [1,2,3,4]
输出: [24,12,8,6]
```

**提示：**题目数据保证数组之中任意元素的全部前缀元素和后缀（甚至是整个数组）的乘积都在 32 位整数范围内。

**说明:**请**不要使用除法，**且在 O(*n*) 时间复杂度内完成此题。

**进阶：**

你可以在常数空间复杂度内完成这个题目吗？（ 出于对空间复杂度分析的目的，输出数组**不被视为**额外空间。）', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/product-of-array-except-self/', '39da1b9d7a8487d901e395ec3422941da061dd447a1ccf560756390b410bbc55', 1, 238, 'product-of-array-except-self', '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","拼多多","旷视","百度","腾讯","虾皮","阿里巴巴"]', 15, '2026-03-31'),
(910000000000000900, '设计哈希映射', '不使用任何内建的哈希表库设计一个哈希映射（HashMap）。

实现 `MyHashMap` 类：

- `MyHashMap()` 用空映射初始化对象

- `void put(int key, int value)` 向 HashMap 插入一个键值对 `(key, value)` 。如果 `key` 已经存在于映射中，则更新其对应的值 `value` 。

- `int get(int key)` 返回特定的 `key` 所映射的 `value` ；如果映射中不包含 `key` 的映射，返回 `-1` 。

- `void remove(key)` 如果映射中存在 `key` 的映射，则移除 `key` 和它所对应的 `value` 。

**示例：**

```
输入：
["MyHashMap", "put", "put", "get", "get", "put", "get", "remove", "get"]
[[], [1, 1], [2, 2], [1], [3], [2, 1], [2], [2], [2]]
输出：
[null, null, null, 1, -1, null, 1, null, -1]

解释：
MyHashMap myHashMap = new MyHashMap();
myHashMap.put(1, 1); // myHashMap 现在为 [[1,1]]
myHashMap.put(2, 2); // myHashMap 现在为 [[1,1], [2,2]]
myHashMap.get(1);    // 返回 1 ，myHashMap 现在为 [[1,1], [2,2]]
myHashMap.get(3);    // 返回 -1（未找到），myHashMap 现在为 [[1,1], [2,2]]
myHashMap.put(2, 1); // myHashMap 现在为 [[1,1], [2,1]]（更新已有的值）
myHashMap.get(2);    // 返回 1 ，myHashMap 现在为 [[1,1], [2,1]]
myHashMap.remove(2); // 删除键为 2 的数据，myHashMap 现在为 [[1,1]]
myHashMap.get(2);    // 返回 -1（未找到），myHashMap 现在为 [[1,1]]
```

**提示：**

- `0 <= key, value <= 10^6`

- 最多调用 `10^4` 次 `put`、`get` 和 `remove` 方法

**进阶：**你能否不使用内置的 HashMap 库解决此问题？', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/design-hashmap/', '359fd7ad007006331676290dfc3d5ffd7371c7cd01125ae68e330bc31646d278', 1, 706, 'design-hashmap', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","拼多多","携程","百度","腾讯"]', 15, '2026-03-18'),
(910000000000001480, '计数质数', '统计所有小于非负整数 *`n` *的质数的数量。

**示例 1：**

```
输入：n = 10
输出：4
解释：小于 10 的质数一共有 4 个, 它们是 2, 3, 5, 7 。
```

**示例 2：**

```
输入：n = 0
输出：0
```

**示例 3：**

```
输入：n = 1
输出：0
```

**提示：**

- `0 <= n <= 5 * 10^6`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/count-primes/', '641835e12dd73677e498b0da6790a28d1c44e77c0eda9f7ed72a46625f287574', 1, 204, 'count-primes', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","微软","快手","百度","美团","阿里巴巴"]', 15, '2026-03-11'),
(910000000000001671, '罗马数字转整数', '罗马数字包含以下七种字符: `I`， `V`， `X`， `L`，`C`，`D` 和 `M`。

```
字符          数值
I             1
V             5
X             10
L             50
C             100
D             500
M             1000
```

例如， 罗马数字 2 写做 `II` ，即为两个并列的 1。12 写做 `XII` ，即为 `X` + `II` 。 27 写做  `XXVII`, 即为 `XX` + `V` + `II` 。

通常情况下，罗马数字中小的数字在大的数字的右边。但也存在特例，例如 4 不写做 `IIII`，而是 `IV`。数字 1 在数字 5 的左边，所表示的数等于大数 5 减小数 1 得到的数值 4 。同样地，数字 9 表示为 `IX`。这个特殊的规则只适用于以下六种情况：

- `I` 可以放在 `V` (5) 和 `X` (10) 的左边，来表示 4 和 9。

- `X` 可以放在 `L` (50) 和 `C` (100) 的左边，来表示 40 和 90。

- `C` 可以放在 `D` (500) 和 `M` (1000) 的左边，来表示 400 和 900。

给定一个罗马数字，将其转换成整数。输入确保在 1 到 3999 的范围内。

**示例 1:**

```
输入: "III"
输出: 3
```

**示例 2:**

```
输入: "IV"
输出: 4
```

**示例 3:**

```
输入: "IX"
输出: 9
```

**示例 4:**

```
输入: "LVIII"
输出: 58
解释: L = 50, V= 5, III = 3.
```

**示例 5:**

```
输入: "MCMXCIV"
输出: 1994
解释: M = 1000, CM = 900, XC = 90, IV = 4.
```

**提示：**

- `1 <= s.length <= 15`

- `s` 仅含字符 `(''I'', ''V'', ''X'', ''L'', ''C'', ''D'', ''M'')`

- 题目数据保证 `s` 是一个有效的罗马数字，且表示整数在范围 `[1, 3999]` 内

- 题目所给测试用例皆符合罗马数字书写规则，不会出现跨位等情况。

- IL 和 IM 这样的例子并不符合题目要求，49 应该写作 XLIX，999 应该写作 CMXCIX 。

- 关于罗马数字的详尽书写规则，可以参考 罗马数字 - Mathematics 。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/roman-to-integer/', 'b31271b3d1611b227be08b82e500146656e1422511de4680b19519a433c8122c', 1, 13, 'roman-to-integer', '["CODETOP","HIGH_FREQ"]', '["亚马逊","字节跳动","微软","旷视","猿辅导","网易"]', 15, '2025-09-25'),
(910000000000001553, '分割回文串', '给你一个字符串 `s`，请你将**`s`**分割成一些子串，使每个子串都是 **回文串** 。返回 `s` 所有可能的分割方案。

**回文串** 是正着读和反着读都一样的字符串。

**示例 1：**

```
输入：s = "aab"
输出：[["a","a","b"],["aa","b"]]
```

**示例 2：**

```
输入：s = "a"
输出：[["a"]]
```

**提示：**

- `1 <= s.length <= 16`

- `s` 仅由小写英文字母组成', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/palindrome-partitioning/', 'a789154740346e590ab180aa925a0d046026f17c9a469577914af5fa262603e8', 1, 131, 'palindrome-partitioning', '["CODETOP","HOT100","HIGH_FREQ"]', '["字节跳动","百度","美团","腾讯","阿里巴巴"]', 15, '2025-07-17'),
(910000000000000816, '链表的中间结点', '给定一个头结点为 `head` 的非空单链表，返回链表的中间结点。

如果有两个中间结点，则返回第二个中间结点。

**示例 1：**

```
输入：[1,2,3,4,5]
输出：此列表中的结点 3 (序列化形式：[3,4,5])
返回的结点值为 3 。 (测评系统对该结点序列化表述是 [3,4,5])。
注意，我们返回了一个 ListNode 类型的对象 ans，这样：
ans.val = 3, ans.next.val = 4, ans.next.next.val = 5, 以及 ans.next.next.next = NULL.
```

**示例 2：**

```
输入：[1,2,3,4,5,6]
输出：此列表中的结点 4 (序列化形式：[4,5,6])
由于该列表有两个中间结点，值分别为 3 和 4，我们返回第二个结点。
```

**提示：**

- 给定链表的结点数介于 `1` 和 `100` 之间。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/middle-of-the-linked-list/', 'a449b04f0088418d03d6d7f3c02e1f9d6663cae566dcf2be8d34f2ffb5ccdd39', 1, 876, 'middle-of-the-linked-list', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","小米","百度","美团","腾讯","虾皮"]', 15, '2024-08-31'),
(910000000000001146, '砖墙', '你的面前有一堵矩形的、由 `n` 行砖块组成的砖墙。这些砖块高度相同（也就是一个单位高）但是宽度不同。每一行砖块的宽度之和相等。

你现在要画一条 **自顶向下**的、穿过 **最少**砖块的垂线。如果你画的线只是从砖块的边缘经过，就不算穿过这块砖。**你不能沿着墙的两个垂直边缘之一画线，这样显然是没有穿过一块砖的。**

给你一个二维数组 `wall` ，该数组包含这堵墙的相关信息。其中，`wall[i]` 是一个代表从左至右每块砖的宽度的数组。你需要找出怎样画才能使这条线 **穿过的砖块数量最少** ，并且返回 **穿过的砖块数量** 。

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2021/04/24/cutwall-grid.jpg)

```
输入：wall = [[1,2,2,1],[3,1,2],[1,3,2],[2,4],[3,1,2],[1,3,1,1]]
输出：2
```

**示例 2：**

```
输入：wall = [[1],[1],[1]]
输出：3
```

**提示：**

- `n == wall.length`

- `1 <= n <= 10^4`

- `1 <= wall[i].length <= 10^4`

- `1 <= sum(wall[i].length) <= 2 * 10^4`

- 对于每一行 `i` ，`sum(wall[i])` 是相同的

- `1 <= wall[i][j] <= 2^31 - 1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/brick-wall/', '34529c5dbab7e63cbe5fb59c2e70ed8f4cb0235baddf5c0a687f51bdb41e41d6', 1, 554, 'brick-wall', '["CODETOP","HIGH_FREQ"]', '["华为"]', 15, '2023-08-29'),
(910000000000000713, '和可被 K 整除的子数组', '给定一个整数数组 `A`，返回其中元素之和可被 `K` 整除的（连续、非空）子数组的数目。

**示例：**

```
输入：A = [4,5,0,-2,-3,1], K = 5
输出：7
解释：
有 7 个子数组满足其元素之和可被 K = 5 整除：
[4, 5, 0, -2, -3, 1], [5], [5, 0], [5, 0, -2, -3], [0], [0, -2, -3], [-2, -3]
```

**提示：**

- `1 <= A.length <= 30000`

- `-10000 <= A[i] <= 10000`

- `2 <= K <= 10000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/subarray-sums-divisible-by-k/', '68c39bcd0706247ede3ede6a8bae0618b63263fb3f5ba108c171006177905fcf', 1, 974, 'subarray-sums-divisible-by-k', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","腾讯"]', 14, '2026-08-27'),
(910000000000000192, '字符串压缩', '字符串压缩。利用字符重复出现的次数，编写一种方法，实现基本的字符串压缩功能。比如，字符串`aabcccccaaa`会变为`a2b1c5a3`。若“压缩”后的字符串没有变短，则返回原先的字符串。你可以假设字符串中只包含大小写英文字母（a至z）。

 **示例1:**

```
输入："aabcccccaaa"
 输出："a2b1c5a3"
```

 **示例2:**

```
输入："abbccd"
 输出："abbccd"
 解释："abbccd"压缩后为"a1b2c2d1"，比原字符串长度更长。
```

**提示：**

- 字符串长度在[0, 50000]范围内。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/compress-string-lcci/', 'c8950952ddd6df71d0b0f578e8f08400d204e864ba8dbf8e98fc41cefd5a2d29', 1, NULL, 'compress-string-lcci', '["CODETOP","HIGH_FREQ"]', '["京东","字节跳动","美团","腾讯","虾皮","阿里巴巴"]', 14, '2026-07-21'),
(910000000000001255, '无重叠区间', '给定一个区间的集合，找到需要移除区间的最小数量，使剩余区间互不重叠。

**注意:**

- 可以认为区间的终点总是大于它的起点。

- 区间 [1,2] 和 [2,3] 的边界相互&ldquo;接触&rdquo;，但没有相互重叠。

**示例 1:**

```
输入: [ [1,2], [2,3], [3,4], [1,3] ]

输出: 1

解释: 移除 [1,3] 后，剩下的区间没有重叠。
```

**示例 2:**

```
输入: [ [1,2], [1,2], [1,2] ]

输出: 2

解释: 你需要移除两个 [1,2] 来使剩下的区间没有重叠。
```

**示例 3:**

```
输入: [ [1,2], [2,3] ]

输出: 0

解释: 你不需要移除任何区间，因为它们已经是无重叠的了。
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/non-overlapping-intervals/', '92f00fc2ffe1928f00b523cfc6ff3cd8609410a02cb3761c87df29ef6ec345fa', 1, 435, 'non-overlapping-intervals', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小红书","微软","滴滴","猿辅导","腾讯"]', 14, '2026-06-23'),
(910000000000001344, '至多包含 K 个不同字符的最长子串', '该题是 Leetcode Plus 会员题', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/longest-substring-with-at-most-k-distinct-characters/', 'db4c7d7eb9b81aa43a8acb07d82cb639f272267009bf86bda9b099bbfae39a56', 1, 340, 'longest-substring-with-at-most-k-distinct-characters', '["CODETOP","HIGH_FREQ"]', '["字节跳动","猿辅导","腾讯"]', 14, '2026-05-14'),
(910000000000001026, '验证回文字符串 Ⅱ', '给定一个非空字符串 `s`，**最多**删除一个字符。判断是否能成为回文字符串。

**示例 1:**

```
输入: s = "aba"
输出: true
```

**示例 2:**

```
输入: s = "abca"
输出: true
解释: 你可以删除c字符。
```

**示例 3:**

```
输入: s = "abc"
输出: false
```

**提示:**

- `1 <= s.length <= 10^5`

- `s` 由小写英文字母组成', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/valid-palindrome-ii/', '3baf6c4e6a1bf8675d0a93de00283dea9f37d9501a4ffc87d0805e5c696bda60', 1, 680, 'valid-palindrome-ii', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","百度","虾皮"]', 14, '2026-04-15'),
(910000000000001575, '有序链表转换二叉搜索树', '给定一个单链表，其中的元素按升序排序，将其转换为高度平衡的二叉搜索树。

本题中，一个高度平衡二叉树是指一个二叉树*每个节点 *的左右两个子树的高度差的绝对值不超过 1。

**示例:**

```
给定的有序链表： [-10, -3, 0, 5, 9],

一个可能的答案是：[0, -3, 9, -10, null, 5], 它可以表示下面这个高度平衡二叉搜索树：

      0
     / \\
   -3   9
   /   /
 -10  5
```', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/convert-sorted-list-to-binary-search-tree/', '8f50f295e33a32812f184da2bb77ef690ab2d3187775c549d46e4d8490890a97', 1, 109, 'convert-sorted-list-to-binary-search-tree', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小红书","微软","快手","拼多多","猿辅导","阿里巴巴"]', 14, '2026-01-25'),
(910000000000001087, '合并二叉树', '给定两个二叉树，想象当你将它们中的一个覆盖到另一个上时，两个二叉树的一些节点便会重叠。

你需要将他们合并为一个新的二叉树。合并的规则是如果两个节点重叠，那么将他们的值相加作为节点合并后的新值，否则**不为 **NULL 的节点将直接作为新二叉树的节点。

**示例 1:**

```
输入:
	Tree 1                     Tree 2
          1                         2
         / \\                       / \\
        3   2                     1   3
       /                           \\   \\
      5                             4   7
输出:
合并后的树:
	     3
	    / \\
	   4   5
	  / \\   \\
	 5   4   7
```

**注意:** 合并必须从两个树的根节点开始。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/merge-two-binary-trees/', 'f6c036548e8bcfd87651177b9594c16288cbae4cde9c5f50f5741a739e4ceaeb', 1, 617, 'merge-two-binary-trees', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","快手","拼多多","网易","美团","阿里巴巴"]', 14, '2025-10-29'),
(910000000000000048, '汉诺塔问题', '在经典汉诺塔问题中，有 3 根柱子及 N 个不同大小的穿孔圆盘，盘子可以滑入任意一根柱子。一开始，所有盘子自上而下按升序依次套在第一根柱子上(即每一个盘子只能放在更大的盘子上面)。移动圆盘时受到以下限制:

(1) 每次只能移动一个盘子;

(2) 盘子只能从柱子顶端滑出移到下一根柱子;

(3) 盘子只能叠在比它大的盘子上。

请编写程序，用栈将所有盘子从第一根柱子移到最后一根柱子。

你需要原地修改栈。

**示例1:**

```
输入：A = [2, 1, 0], B = [], C = []
 输出：C = [2, 1, 0]
```

**示例2:**

```
输入：A = [1, 0], B = [], C = []
 输出：C = [1, 0]
```

**提示:**

- A中盘子的数目不大于14个。', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/hanota-lcci/', '15955bdd807b661faa002f6cb4e3b0aab03edf00973eac42f9a1563d9802cfe7', 1, NULL, 'hanota-lcci', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","快手","美团","腾讯"]', 14, '2025-09-15'),
(910000000000000153, '搜索旋转数组', '搜索旋转数组。给定一个排序后的数组，包含n个整数，但这个数组已被旋转过很多次了，次数不详。请编写代码找出数组中的某个元素，假设数组元素原先是按升序排列的。若有多个相同元素，返回索引值最小的一个。

**示例1:**

```
输入: arr = [15, 16, 19, 20, 25, 1, 3, 4, 5, 7, 10, 14], target = 5
 输出: 8（元素5在该数组中的索引）
```

**示例2:**

```
输入：arr = [15, 16, 19, 20, 25, 1, 3, 4, 5, 7, 10, 14], target = 11
 输出：-1 （没有找到）
```

**提示:**

- arr 长度范围在[1, 1000000]之间', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/search-rotate-array-lcci/', '7c7f3a087528b450dbcfa213fa2fc7a5de75fca2d5e0ffdfba618446a2038b6a', 1, NULL, 'search-rotate-array-lcci', '["CODETOP","HIGH_FREQ"]', '["字节跳动","小米","微软","滴滴","百度","美团"]', 14, '2025-08-28'),
(910000000000000547, '山脉数组中查找目标值', '（这是一个 **交互式问题 **）

给你一个 **山脉数组** `mountainArr`，请你返回能够使得 `mountainArr.get(index)` **等于** `target` **最小** 的下标 `index` 值。

如果不存在这样的下标 `index`，就请返回 `-1`。

何为山脉数组？如果数组 `A` 是一个山脉数组的话，那它满足如下条件：

**首先**，`A.length >= 3`

**其次**，在 `0 < i < A.length - 1` 条件下，存在 `i` 使得：

- `A[0] < A[1] < ... A[i-1] < A[i]`

- `A[i] > A[i+1] > ... > A[A.length - 1]`

你将 **不能直接访问该山脉数组**，必须通过 `MountainArray` 接口来获取数据：

- `MountainArray.get(k)` - 会返回数组中索引为`k` 的元素（下标从 0 开始）

- `MountainArray.length()` - 会返回该数组的长度

**注意：**

对 `MountainArray.get` 发起超过 `100` 次调用的提交将被视为错误答案。此外，任何试图规避判题系统的解决方案都将会导致比赛资格被取消。

为了帮助大家更好地理解交互式问题，我们准备了一个样例 &ldquo;**答案**&rdquo;：https://leetcode-cn.com/playground/RKhe3ave，请注意这 **不是一个正确答案**。

**示例 1：**

```
输入：array = [1,2,3,4,5,3,1], target = 3
输出：2
解释：3 在数组中出现了两次，下标分别为 2 和 5，我们返回最小的下标 2。
```

**示例 2：**

```
输入：array = [0,1,2,4,2,1], target = 3
输出：-1
解释：3 在数组中没有出现，返回 -1。
```

**提示：**

- `3 <= mountain_arr.length() <= 10000`

- `0 <= target <= 10^9`

- `0 <= mountain_arr.get(index) <= 10^9`', 'CODE', 'CODING', 3, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/find-in-mountain-array/', '1e24a79612f405fb1a3a34b98560fdb51c1631a1794017e6bba952ceb76a79a0', 1, 1095, 'find-in-mountain-array', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","百度","美团","腾讯"]', 14, '2024-07-09'),
(910000000000001655, '两数相除', '给定两个整数，被除数 `dividend` 和除数 `divisor`。将两数相除，要求不使用乘法、除法和 mod 运算符。

返回被除数 `dividend` 除以除数 `divisor` 得到的商。

整数除法的结果应当截去（`truncate`）其小数部分，例如：`truncate(8.345) = 8` 以及 `truncate(-2.7335) = -2`

**示例 1:**

```
输入: dividend = 10, divisor = 3
输出: 3
解释: 10/3 = truncate(3.33333..) = truncate(3) = 3
```

**示例 2:**

```
输入: dividend = 7, divisor = -3
输出: -2
解释: 7/-3 = truncate(-2.33333..) = -2
```

**提示：**

- 被除数和除数均为 32 位有符号整数。

- 除数不为 0。

- 假设我们的环境只能存储 32 位有符号整数，其数值范围是 [−2^31,  2^31 − 1]。本题中，如果除法结果溢出，则返回 2^31 − 1。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/divide-two-integers/', '35f380117775da95fd022d265834246b93f8b56ff5b5a589e93fd7b303528d2e', 1, 29, 'divide-two-integers', '["CODETOP","HIGH_FREQ"]', '["华为","字节跳动","微软","百度","美团","腾讯"]', 13, '2026-10-26'),
(910000000000001077, '三个数的最大乘积', '给你一个整型数组 `nums` ，在数组中找出由三个数组成的最大乘积，并输出这个乘积。

**示例 1：**

```
输入：nums = [1,2,3]
输出：6
```

**示例 2：**

```
输入：nums = [1,2,3,4]
输出：24
```

**示例 3：**

```
输入：nums = [-1,-2,-3]
输出：-6
```

**提示：**

- `3 <= nums.length <= 10^4`

- `-1000 <= nums[i] <= 1000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/maximum-product-of-three-numbers/', '6c93503db3abc941f34b5397944cbd489a5b73c1287a4b128325c0bab176c142', 1, 628, 'maximum-product-of-three-numbers', '["CODETOP","HIGH_FREQ"]', '["字节跳动","微软","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]', 13, '2026-08-31'),
(910000000000000110, '栈的压入、弹出序列', '输入两个整数序列，第一个序列表示栈的压入顺序，请判断第二个序列是否为该栈的弹出顺序。假设压入栈的所有数字均不相等。例如，序列 {1,2,3,4,5} 是某栈的压栈序列，序列 {4,5,3,2,1} 是该压栈序列对应的一个弹出序列，但 {4,3,5,1,2} 就不可能是该压栈序列的弹出序列。

**示例 1：**

```
输入：pushed = [1,2,3,4,5], popped = [4,5,3,2,1]
输出：true
解释：我们可以按以下顺序执行：
push(1), push(2), push(3), push(4), pop() -> 4,
push(5), pop() -> 5, pop() -> 3, pop() -> 2, pop() -> 1
```

**示例 2：**

```
输入：pushed = [1,2,3,4,5], popped = [4,3,5,1,2]
输出：false
解释：1 不能在 2 之前弹出。
```

**提示：**

- `0 <= pushed.length == popped.length <= 1000`

- `0 <= pushed[i], popped[i] < 1000`

- `pushed` 是 `popped` 的排列。

注意：本题与主站 946 题相同：https://leetcode-cn.com/problems/validate-stack-sequences/', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/zhan-de-ya-ru-dan-chu-xu-lie-lcof/', '798bceb1e243b7da0b228bc26853e46cb3d814b01285491bc7e0942b301fc9a9', 1, NULL, 'zhan-de-ya-ru-dan-chu-xu-lie-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","微软","网易","美团","虾皮","阿里巴巴"]', 13, '2024-04-29'),
(910000000000000100, '第一个只出现一次的字符', '在字符串 s 中找出第一个只出现一次的字符。如果没有，返回一个单空格。 s 只包含小写字母。

**示例 1:**

```
输入：s = "abaccdeff"
输出：''b''
```

**示例 2:**

```
输入：s = ""
输出：'' ''
```

**限制：**

`0 <= s 的长度 <= 50000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/di-yi-ge-zhi-chu-xian-yi-ci-de-zi-fu-lcof/', '136cc9ed914985285d69f653d371c10222aad694b99e423c0cbe25e72f41f6b8', 1, NULL, 'di-yi-ge-zhi-chu-xian-yi-ci-de-zi-fu-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","滴滴","百度","美团","腾讯"]', 12, '2026-10-30'),
(910000000000001611, '矩阵置零', '给定一个 `m x n` 的矩阵，如果一个元素为 **0**，则将其所在行和列的所有元素都设为 **0** 。请使用 **原地** 算法**。**

**进阶：**

- 一个直观的解决方案是使用 `O(mn)` 的额外空间，但这并不是一个好的解决方案。

- 一个简单的改进方案是使用 `O(m + n)` 的额外空间，但这仍然不是最好的解决方案。

- 你能想出一个仅使用常量空间的解决方案吗？

**示例 1：**

![题目配图](https://assets.leetcode.com/uploads/2020/08/17/mat1.jpg)

```
输入：matrix = [[1,1,1],[1,0,1],[1,1,1]]
输出：[[1,0,1],[0,0,0],[1,0,1]]
```

**示例 2：**

![题目配图](https://assets.leetcode.com/uploads/2020/08/17/mat2.jpg)

```
输入：matrix = [[0,1,2,0],[3,4,5,2],[1,3,1,5]]
输出：[[0,0,0,0],[0,4,5,0],[0,3,1,0]]
```

**提示：**

- `m == matrix.length`

- `n == matrix[0].length`

- `1 <= m, n <= 200`

- `-2^31 <= matrix[i][j] <= 2^31 - 1`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/set-matrix-zeroes/', 'e7a0acbe02e139f93ef7cfe2d68c6b188aad00aa287dcfcf703dec8a77ca209b', 1, 73, 'set-matrix-zeroes', '["CODETOP","HOT100"]', '["字节跳动","微软","猿辅导","美团","腾讯","虾皮","阿里巴巴"]', 11, '2025-03-19'),
(910000000000001566, '杨辉三角', '给定一个非负整数 *`numRows`，*生成「杨辉三角」的前 *`numRows`*行。

在「杨辉三角」中，每个数是它左上方和右上方的数的和。

![题目配图](https://pic.leetcode-cn.com/1626927345-DZmfxB-PascalTriangleAnimated2.gif)

**示例 1:**

```
输入: numRows = 5
输出: [[1],[1,1],[1,2,1],[1,3,3,1],[1,4,6,4,1]]
```

**示例 2:**

```
输入: numRows = 1
输出: [[1]]
```

**提示:**

- `1 <= numRows <= 30`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/pascals-triangle/', 'bc4acb047fabce3b1e441940549b51448b0b97bd426b63b89994d1c8c162fae0', 1, 118, 'pascals-triangle', '["CODETOP","HOT100"]', '["亚马逊","字节跳动","快手","百度","美团","腾讯","虾皮"]', 11, '2025-09-03'),
(910000000000000092, '和为s的连续正数序列', '输入一个正整数 `target` ，输出所有和为 `target` 的连续正整数序列（至少含有两个数）。

序列内的数字由小到大排列，不同序列按照首个数字从小到大排列。

**示例 1：**

```
输入：target = 9
输出：[[2,3,4],[4,5]]
```

**示例 2：**

```
输入：target = 15
输出：[[1,2,3,4,5],[4,5,6],[7,8]]
```

**限制：**

- `1 <= target <= 10^5`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/he-wei-sde-lian-xu-zheng-shu-xu-lie-lcof/', '66bac87080ab32169a6cd7324121817aa8b18490c2dcde5a96ad16fd48d6b230', 1, NULL, 'he-wei-sde-lian-xu-zheng-shu-xu-lie-lcof', '["CODETOP","JIANZHI"]', '["华为","字节跳动","拼多多","美团","腾讯"]', 11, '2026-05-22'),
(910000000000000077, 'n个骰子的点数', '把n个骰子扔在地上，所有骰子朝上一面的点数之和为s。输入n，打印出s的所有可能的值出现的概率。

你需要用一个浮点数数组返回答案，其中第 i 个元素代表这 n 个骰子所能掷出的点数集合中第 i 小的那个的概率。

**示例 1:**

```
输入: 1
输出: [0.16667,0.16667,0.16667,0.16667,0.16667,0.16667]
```

**示例 2:**

```
输入: 2
输出: [0.02778,0.05556,0.08333,0.11111,0.13889,0.16667,0.13889,0.11111,0.08333,0.05556,0.02778]
```

**限制：**

`1 <= n <= 11`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/nge-tou-zi-de-dian-shu-lcof/', '76a437c159dfd09fc0f1933e96b8aec0db4d3fb83c9c7106d19f8ebf5c0e882e', 1, NULL, 'nge-tou-zi-de-dian-shu-lcof', '["CODETOP","JIANZHI"]', '["bilibili","京东","字节跳动","快手","拼多多","阿里巴巴"]', 10, '2026-06-23'),
(910000000000000081, '不用加减乘除做加法', '写一个函数，求两个整数之和，要求在函数体内不得使用 &ldquo;+&rdquo;、&ldquo;-&rdquo;、&ldquo;*&rdquo;、&ldquo;/&rdquo; 四则运算符号。

**示例:**

```
输入: a = 1, b = 1
输出: 2
```

**提示：**

- `a`, `b` 均可能是负数或 0

- 结果不会溢出 32 位整数', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/bu-yong-jia-jian-cheng-chu-zuo-jia-fa-lcof/', '014f1abf3cc91bd11b1f59138f739cdb577869dcf2a49b1c0781697ee4129664', 1, NULL, 'bu-yong-jia-jian-cheng-chu-zuo-jia-fa-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","快手","携程","百度","美团","腾讯"]', 9, '2024-08-14'),
(910000000000001649, '搜索插入位置', '给定一个排序数组和一个目标值，在数组中找到目标值，并返回其索引。如果目标值不存在于数组中，返回它将会被按顺序插入的位置。

请必须使用时间复杂度为 `O(log n)` 的算法。

**示例 1:**

```
输入: nums = [1,3,5,6], target = 5
输出: 2
```

**示例 2:**

```
输入: nums = [1,3,5,6], target = 2
输出: 1
```

**示例 3:**

```
输入: nums = [1,3,5,6], target = 7
输出: 4
```

**示例 4:**

```
输入: nums = [1,3,5,6], target = 0
输出: 0
```

**示例 5:**

```
输入: nums = [1], target = 0
输出: 0
```

**提示:**

- `1 <= nums.length <= 10^4`

- `-10^4 <= nums[i] <= 10^4`

- `nums` 为**无重复元素**的**升序**排列数组

- `-10^4 <= target <= 10^4`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/search-insert-position/', 'b0db2f2041185e4e9cdb0cdfdf042afe046271615fe249e2e2e13a0bb1fdd6f8', 1, 35, 'search-insert-position', '["CODETOP","HOT100"]', '["字节跳动","微软","快手","腾讯"]', 8, '2025-07-24'),
(910000000000000120, '打印从1到最大的n位数', '输入数字 `n`，按顺序打印出从 1 到最大的 n 位十进制数。比如输入 3，则打印出 1、2、3 一直到最大的 3 位数 999。

**示例 1:**

```
输入: n = 1
输出: [1,2,3,4,5,6,7,8,9]
```

说明：

- 用返回一个整数列表来代替打印

- n 为正整数', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/da-yin-cong-1dao-zui-da-de-nwei-shu-lcof/', '8a7828a681025ec63b071b83e5b962e58aab4d4600b255d0854e74e4a963f301', 1, NULL, 'da-yin-cong-1dao-zui-da-de-nwei-shu-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","拼多多","美团","腾讯"]', 7, '2025-08-04'),
(910000000000000124, '二进制中1的个数', '编写一个函数，输入是一个无符号整数（以二进制串的形式），返回其二进制表达式中数字位数为 ''1'' 的个数（也被称为 汉明重量).）。

**提示：**

- 请注意，在某些语言（如 Java）中，没有无符号整数类型。在这种情况下，输入和输出都将被指定为有符号整数类型，并且不应影响您的实现，因为无论整数是有符号的还是无符号的，其内部的二进制表示形式都是相同的。

- 在 Java 中，编译器使用 二进制补码 记法来表示有符号整数。因此，在上面的 **示例 3**中，输入表示有符号整数 `-3`。

**示例 1：**

```
输入：n = 11 (控制台输入 00000000000000000000000000001011)
输出：3
解释：输入的二进制串 00000000000000000000000000001011 中，共有三位为 ''1''。
```

**示例 2：**

```
输入：n = 128 (控制台输入 00000000000000000000000010000000)
输出：1
解释：输入的二进制串 00000000000000000000000010000000 中，共有一位为 ''1''。
```

**示例 3：**

```
输入：n = 4294967293 (控制台输入 11111111111111111111111111111101，部分语言中 n = -3）
输出：31
解释：输入的二进制串 11111111111111111111111111111101 中，共有 31 位为 ''1''。
```

**提示：**

- 输入必须是长度为 `32` 的 **二进制串** 。

注意：本题与主站 191 题相同：https://leetcode-cn.com/problems/number-of-1-bits/', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/er-jin-zhi-zhong-1de-ge-shu-lcof/', '2906e97e12dd0c8be0d9d5f231646a009385dc7d10d14f2440b38fde2b62e50d', 1, NULL, 'er-jin-zhi-zhong-1de-ge-shu-lcof', '["CODETOP","JIANZHI"]', '["微软","携程","美团","虾皮"]', 5, '2022-04-18'),
(910000000000000079, '队列的最大值', '请定义一个队列并实现函数 `max_value` 得到队列里的最大值，要求函数`max_value`、`push_back` 和 `pop_front` 的**均摊**时间复杂度都是O(1)。

若队列为空，`pop_front` 和 `max_value` 需要返回 -1

**示例 1：**

```
输入:
["MaxQueue","push_back","push_back","max_value","pop_front","max_value"]
[[],[1],[2],[],[],[]]
输出: [null,null,null,2,1,2]
```

**示例 2：**

```
输入:
["MaxQueue","pop_front","max_value"]
[[],[],[]]
输出: [null,-1,-1]
```

**限制：**

- `1 <= push_back,pop_front,max_value的总操作数 <= 10000`

- `1 <= value <= 10^5`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/dui-lie-de-zui-da-zhi-lcof/', 'f6cdcb4422ecc5bce7d72965bbfc5973912b7f6a967e7cffa128610a8e4a838d', 1, NULL, 'dui-lie-de-zui-da-zhi-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","快手"]', 5, '2022-03-30'),
(910000000000000135, '机器人的运动范围', '地上有一个m行n列的方格，从坐标 `[0,0]` 到坐标 `[m-1,n-1]` 。一个机器人从坐标 `[0, 0] `的格子开始移动，它每次可以向左、右、上、下移动一格（不能移动到方格外），也不能进入行坐标和列坐标的数位之和大于k的格子。例如，当k为18时，机器人能够进入方格 [35, 37] ，因为3+5+3+7=18。但它不能进入方格 [35, 38]，因为3+5+3+8=19。请问该机器人能够到达多少个格子？

**示例 1：**

```
输入：m = 2, n = 3, k = 1
输出：3
```

**示例 2：**

```
输入：m = 3, n = 1, k = 0
输出：1
```

**提示：**

- `1 <= n,m <= 100`

- `0 <= k <= 20`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/ji-qi-ren-de-yun-dong-fan-wei-lcof/', '1a9cba358e622243c94533d83796590d15247153276d3f34bf624e8e1cbb5351', 1, NULL, 'ji-qi-ren-de-yun-dong-fan-wei-lcof', '["CODETOP","JIANZHI"]', '["微软","快手","百度","美团"]', 4, '2024-04-02'),
(910000000000000117, '删除链表的节点', '给定单向链表的头指针和一个要删除的节点的值，定义一个函数删除该节点。

返回删除后的链表的头节点。

**注意：**此题对比原题有改动

**示例 1:**

```
输入: head = [4,5,1,9], val = 5
输出: [4,1,9]
解释: 给定你链表中值为 5 的第二个节点，那么在调用了你的函数之后，该链表应变为 4 -> 1 -> 9.
```

**示例 2:**

```
输入: head = [4,5,1,9], val = 1
输出: [4,5,9]
解释: 给定你链表中值为 1 的第三个节点，那么在调用了你的函数之后，该链表应变为 4 -> 5 -> 9.
```

**说明：**

- 题目保证链表中节点的值互不相同

- 若使用 C 或 C++ 语言，你不需要 `free` 或 `delete` 被删除的节点', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shan-chu-lian-biao-de-jie-dian-lcof/', '95f1ba69015453eea3ae00fba268c760f107932fe8b56f2a65f8af340b94e99c', 1, NULL, 'shan-chu-lian-biao-de-jie-dian-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","快手","猿辅导"]', 4, '2020-08-29'),
(910000000000000103, '数字序列中某一位的数字', '数字以0123456789101112131415…的格式序列化到一个字符序列中。在这个序列中，第5位（从下标0开始计数）是5，第13位是1，第19位是4，等等。

请写一个函数，求任意第n位对应的数字。

**示例 1：**

```
输入：n = 3
输出：3
```

**示例 2：**

```
输入：n = 11
输出：0
```

**限制：**

- `0 <= n < 2^31`

注意：本题与主站 400 题相同：https://leetcode-cn.com/problems/nth-digit/', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shu-zi-xu-lie-zhong-mou-yi-wei-de-shu-zi-lcof/', '05b9c4576547bcf82eb7b5a01ab9a59d4a6d72d4b7e74cfd082740f7db48abab', 1, NULL, 'shu-zi-xu-lie-zhong-mou-yi-wei-de-shu-zi-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","腾讯"]', 3, '2025-08-19'),
(910000000000000089, '礼物的最大价值', '在一个 m*n 的棋盘的每一格都放有一个礼物，每个礼物都有一定的价值（价值大于 0）。你可以从棋盘的左上角开始拿格子里的礼物，并每次向右或者向下移动一格、直到到达棋盘的右下角。给定一个棋盘及其上面的礼物的价值，请计算你最多能拿到多少价值的礼物？

**示例 1:**

```
输入:
[
  [1,3,1],
  [1,5,1],
  [4,2,1]
]
输出: 12
解释: 路径 1→3→5→2→1 可以拿到最多价值的礼物
```

提示：

- `0 < grid.length <= 200`

- `0 < grid[0].length <= 200`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/li-wu-de-zui-da-jie-zhi-lcof/', 'f9b01ec929afd75ee0c5ed15f189088972a07d42f119212d9bf2b9afde207c8a', 1, NULL, 'li-wu-de-zui-da-jie-zhi-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","阿里巴巴"]', 3, '2023-01-05'),
(910000000000000095, '数组中数字出现的次数 II', '在一个数组 `nums` 中除一个数字只出现一次之外，其他数字都出现了三次。请找出那个只出现一次的数字。

**示例 1：**

```
输入：nums = [3,4,3,3]
输出：4
```

**示例 2：**

```
输入：nums = [9,1,7,9,7,9,7]
输出：1
```

**限制：**

- `1 <= nums.length <= 10000`

- `1 <= nums[i] < 2^31`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-ii-lcof/', '632a01deba74bc074afd4112311f91eaa8eb3b030219ad3d0ba40d61bb414ef9', 1, NULL, 'shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-ii-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","微软","腾讯"]', 3, '2022-03-23'),
(910000000000000136, '替换空格', '请实现一个函数，把字符串 `s` 中的每个空格替换成"%20"。

**示例 1：**

```
输入：s = "We are happy."
输出："We%20are%20happy."
```

**限制：**

`0 <= s 的长度 <= 10000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/ti-huan-kong-ge-lcof/', 'e28af7b56ac213e92d5b2f353933d701348419cc8c255cc7414ac823b6f07bc8', 1, NULL, 'ti-huan-kong-ge-lcof', '["CODETOP","JIANZHI"]', '["京东","字节跳动","美团"]', 3, '2021-09-14'),
(910000000000000131, '剪绳子 II', '给你一根长度为 `n` 的绳子，请把绳子剪成整数长度的 `m` 段（m、n都是整数，n>1并且m>1），每段绳子的长度记为 `k[0],k[1]...k[m - 1]` 。请问 `k[0]*k[1]*...*k[m - 1]` 可能的最大乘积是多少？例如，当绳子的长度是8时，我们把它剪成长度分别为2、3、3的三段，此时得到的最大乘积是18。

答案需要取模 1e9+7（1000000007），如计算初始结果为：1000000008，请返回 1。

**示例 1：**

```
输入: 2
输出: 1
解释: 2 = 1 + 1, 1 × 1 = 1
```

**示例 2:**

```
输入: 10
输出: 36
解释: 10 = 3 + 3 + 4, 3 × 3 × 4 = 36
```

**提示：**

- `2 <= n <= 1000`

注意：本题与主站 343 题相同：https://leetcode-cn.com/problems/integer-break/', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/jian-sheng-zi-ii-lcof/', '5bfdded1d15f9b888f04a056e314f906f54e2878226d2a3364964e4f8e12f6c5', 1, NULL, 'jian-sheng-zi-ii-lcof', '["CODETOP","JIANZHI"]', '["字节跳动"]', 2, '2022-06-15'),
(910000000000000126, '表示数值的字符串', '请实现一个函数用来判断字符串是否表示**数值**（包括整数和小数）。

**数值**（按顺序）可以分成以下几个部分：

- 若干空格

- 一个 **小数** 或者 **整数**

- （可选）一个 `''e''` 或 `''E''` ，后面跟着一个 **整数**

- 若干空格

**小数**（按顺序）可以分成以下几个部分：

- （可选）一个符号字符（`''+''` 或 `''-''`）

- 下述格式之一：
 至少一位数字，后面跟着一个点 `''.''`

- 至少一位数字，后面跟着一个点 `''.''` ，后面再跟着至少一位数字

- 一个点 `''.''` ，后面跟着至少一位数字

**整数**（按顺序）可以分成以下几个部分：

- （可选）一个符号字符（`''+''` 或 `''-''`）

- 至少一位数字

部分**数值**列举如下：

- `["+100", "5e2", "-123", "3.1416", "-1E-16", "0123"]`

部分**非数值**列举如下：

- `["12e", "1a3.14", "1.2.3", "+-5", "12e+5.4"]`

**示例 1：**

```
输入：s = "0"
输出：true
```

**示例 2：**

```
输入：s = "e"
输出：false
```

**示例 3：**

```
输入：s = "."
输出：false
```

**示例 4：**

```
输入：s = "    .1  "
输出：true
```

**提示：**

- `1 <= s.length <= 20`

- `s` 仅含英文字母（大写和小写），数字（`0-9`），加号 `''+''` ，减号 `''-''` ，空格 `'' ''` 或者点 `''.''` 。', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/biao-shi-shu-zhi-de-zi-fu-chuan-lcof/', '65e672d597600f916c4d2d9b731d4b1d6a670e6a8ab876c45f5e15e73796681d', 1, NULL, 'biao-shi-shu-zhi-de-zi-fu-chuan-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","快手"]', 2, '2022-04-28'),
(910000000000000070, '二叉搜索树的最近公共祖先', '给定一个二叉搜索树, 找到该树中两个指定节点的最近公共祖先。

百度百科中最近公共祖先的定义为：&ldquo;对于有根树 T 的两个结点 p、q，最近公共祖先表示为一个结点 x，满足 x 是 p、q 的祖先且 x 的深度尽可能大（**一个节点也可以是它自己的祖先**）。&rdquo;

例如，给定如下二叉搜索树:  root = [6,2,8,0,4,7,9,null,null,3,5]

![题目配图](https://assets.leetcode-cn.com/aliyun-lc-upload/uploads/2018/12/14/binarysearchtree_improved.png)

**示例 1:**

```
输入: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 8
输出: 6
解释: 节点 2 和节点 8 的最近公共祖先是 6。
```

**示例 2:**

```
输入: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 4
输出: 2
解释: 节点 2 和节点 4 的最近公共祖先是 2, 因为根据定义最近公共祖先节点可以为节点本身。
```

**说明:**

- 所有节点的值都是唯一的。

- p、q 为不同节点且均存在于给定的二叉搜索树中。

注意：本题与主站 235 题相同：https://leetcode-cn.com/problems/lowest-common-ancestor-of-a-binary-search-tree/', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/er-cha-sou-suo-shu-de-zui-jin-gong-gong-zu-xian-lcof/', 'eecec09bd92d929eddba0ca743e2e0a3556793c02c2365ad27c4176e1168824d', 1, NULL, 'er-cha-sou-suo-shu-de-zui-jin-gong-gong-zu-xian-lcof', '["CODETOP","JIANZHI"]', '["字节跳动"]', 2, '2022-04-19'),
(910000000000000105, '从上到下打印二叉树', '从上到下打印出二叉树的每个节点，同一层的节点按照从左到右的顺序打印。

例如:

给定二叉树: `[3,9,20,null,null,15,7]`,

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回：

```
[3,9,20,15,7]
```

**提示：**

- `节点总数 <= 1000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/cong-shang-dao-xia-da-yin-er-cha-shu-lcof/', 'e884d55da85ce5b88753e53b7a706a675a566c81bf877ec8d958886f8c341841', 1, NULL, 'cong-shang-dao-xia-da-yin-er-cha-shu-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","猿辅导"]', 2, '2022-04-01'),
(910000000000000074, '平衡二叉树', '输入一棵二叉树的根节点，判断该树是不是平衡二叉树。如果某二叉树中任意节点的左右子树的深度相差不超过1，那么它就是一棵平衡二叉树。

**示例 1:**

给定二叉树 `[3,9,20,null,null,15,7]`

```
3
   / \\
  9  20
    /  \\
   15   7
```

返回 `true` 。

**示例 2:**

给定二叉树 `[1,2,2,3,3,null,null,4,4]`

```
1
      / \\
     2   2
    / \\
   3   3
  / \\
 4   4
```

返回 `false` 。

**限制：**

- `0 <= 树的结点个数 <= 10000`

注意：本题与主站 110 题相同：https://leetcode-cn.com/problems/balanced-binary-tree/', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/ping-heng-er-cha-shu-lcof/', '5ff7252ae47e012a9f82843f516120708770215c5faee1726dd0e84e4fdac901', 1, NULL, 'ping-heng-er-cha-shu-lcof', '["CODETOP","JIANZHI"]', '["字节跳动"]', 2, '2022-03-30'),
(910000000000000134, '从尾到头打印链表', '输入一个链表的头节点，从尾到头反过来返回每个节点的值（用数组返回）。

**示例 1：**

```
输入：head = [1,3,2]
输出：[2,3,1]
```

**限制：**

`0 <= 链表长度 <= 10000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/cong-wei-dao-tou-da-yin-lian-biao-lcof/', '6211e2f13904e5b80752c783ece3d857140a3f4df944aa33b237091f1844c496', 1, NULL, 'cong-wei-dao-tou-da-yin-lian-biao-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","阿里巴巴"]', 2, '2022-02-20'),
(910000000000000094, '和为s的两个数字', '输入一个递增排序的数组和一个数字s，在数组中查找两个数，使得它们的和正好是s。如果有多对数字的和等于s，则输出任意一对即可。

**示例 1：**

```
输入：nums = [2,7,11,15], target = 9
输出：[2,7] 或者 [7,2]
```

**示例 2：**

```
输入：nums = [10,26,30,31,47,60], target = 40
输出：[10,30] 或者 [30,10]
```

**限制：**

- `1 <= nums.length <= 10^5`

- `1 <= nums[i] <= 10^6`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/he-wei-sde-liang-ge-shu-zi-lcof/', 'da8c68e86431f73c3ef0fb0086de7da67bed2055245de65025c27e09c8748321', 1, NULL, 'he-wei-sde-liang-ge-shu-zi-lcof', '["CODETOP","JIANZHI"]', '["百度","美团"]', 2, '2021-10-11'),
(910000000000000071, '求1+2+…+n', '求 `1+2+...+n` ，要求不能使用乘除法、for、while、if、else、switch、case等关键字及条件判断语句（A?B:C）。

**示例 1：**

```
输入: n = 3
输出: 6
```

**示例 2：**

```
输入: n = 9
输出: 45
```

**限制：**

- `1 <= n <= 10000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/qiu-12n-lcof/', 'bebbf9fe0dc483d663cacaadfab7d649ef1e279e83cd415b2553cf3dcc12e777', 1, NULL, 'qiu-12n-lcof', '["CODETOP","JIANZHI"]', '["快手","百度"]', 2, '2021-09-19'),
(910000000000000096, '数组中数字出现的次数', '一个整型数组 `nums` 里除两个数字之外，其他数字都出现了两次。请写程序找出这两个只出现一次的数字。要求时间复杂度是O(n)，空间复杂度是O(1)。

**示例 1：**

```
输入：nums = [4,1,4,6]
输出：[1,6] 或 [6,1]
```

**示例 2：**

```
输入：nums = [1,2,10,4,1,4,3,3]
输出：[2,10] 或 [10,2]
```

**限制：**

- `2 <= nums.length <= 10000`', 'CODE', 'CODING', 2, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-lcof/', 'db184eeab4ba76678b31f39c73bb272c6d655200da22fb99824329c57114061f', 1, NULL, 'shu-zu-zhong-shu-zi-chu-xian-de-ci-shu-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","百度"]', 2, '2021-03-29'),
(910000000000000086, '左旋转字符串', '字符串的左旋转操作是把字符串前面的若干个字符转移到字符串的尾部。请定义一个函数实现字符串左旋转操作的功能。比如，输入字符串"abcdefg"和数字2，该函数将返回左旋转两位得到的结果"cdefgab"。

**示例 1：**

```
输入: s = "abcdefg", k = 2
输出: "cdefgab"
```

**示例 2：**

```
输入: s = "lrloseumgh", k = 6
输出: "umghlrlose"
```

**限制：**

- `1 <= k < s.length <= 10000`', 'CODE', 'CODING', 1, 2000, 256, '算法', NULL, '{"JAVA":"import java.util.*;\\n\\npublic class Main {\\n    public static void main(String[] args) {\\n        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果\\n    }\\n}","PYTHON":"import sys\\n\\ndef main():\\n    # 在这里构造输入、调用你的解法，并用 print 输出结果\\n    pass\\n\\nif __name__ == \\"__main__\\":\\n    main()"}', NULL, NULL, NULL, 'CRAWL', 'https://leetcode.cn/problems/zuo-xuan-zhuan-zi-fu-chuan-lcof/', 'b8361a8c159e237a655c4b3ee0e636af0113d585d8a2644273bc4cc0fc7e4bdd', 1, NULL, 'zuo-xuan-zhuan-zi-fu-chuan-lcof', '["CODETOP","JIANZHI"]', '["字节跳动","美团"]', 2, '2020-09-22')
ON DUPLICATE KEY UPDATE
    source_url = VALUES(source_url),
    leetcode_no = VALUES(leetcode_no),
    leetcode_slug = VALUES(leetcode_slug),
    tags = VALUES(tags),
    companies = VALUES(companies),
    hot_score = VALUES(hot_score),
    last_asked_at = VALUES(last_asked_at);

-- ---------- 5. 剑指 Offer 与力扣同题去重 ----------
-- liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof -> intersection-of-two-linked-lists
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof' AND t.leetcode_slug = 'intersection-of-two-linked-lists'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","商汤","字节跳动","微软","快手","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof'
JOIN question_bank t ON t.leetcode_slug = 'intersection-of-two-linked-lists'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'intersection-of-two-linked-lists'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'intersection-of-two-linked-lists';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof'
JOIN question_bank t ON t.leetcode_slug = 'intersection-of-two-linked-lists'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'intersection-of-two-linked-lists'
WHERE d.leetcode_slug = 'liang-ge-lian-biao-de-di-yi-ge-gong-gong-jie-dian-lcof';

-- er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof -> lowest-common-ancestor-of-a-binary-tree
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof' AND t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","华为","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof'
JOIN question_bank t ON t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof'
JOIN question_bank t ON t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'lowest-common-ancestor-of-a-binary-tree'
WHERE d.leetcode_slug = 'er-cha-shu-de-zui-jin-gong-gong-zu-xian-lcof';

-- er-cha-shu-de-shen-du-lcof -> maximum-depth-of-binary-tree
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof' AND t.leetcode_slug = 'maximum-depth-of-binary-tree'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","华为","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof'
JOIN question_bank t ON t.leetcode_slug = 'maximum-depth-of-binary-tree'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'maximum-depth-of-binary-tree'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'maximum-depth-of-binary-tree';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof'
JOIN question_bank t ON t.leetcode_slug = 'maximum-depth-of-binary-tree'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'maximum-depth-of-binary-tree'
WHERE d.leetcode_slug = 'er-cha-shu-de-shen-du-lcof';

-- er-cha-shu-de-jing-xiang-lcof -> invert-binary-tree
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof' AND t.leetcode_slug = 'invert-binary-tree'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","商汤","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof'
JOIN question_bank t ON t.leetcode_slug = 'invert-binary-tree'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'invert-binary-tree'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'invert-binary-tree';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof'
JOIN question_bank t ON t.leetcode_slug = 'invert-binary-tree'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'invert-binary-tree'
WHERE d.leetcode_slug = 'er-cha-shu-de-jing-xiang-lcof';

-- cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof -> binary-tree-level-order-traversal
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof' AND t.leetcode_slug = 'binary-tree-level-order-traversal'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof'
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-level-order-traversal'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'binary-tree-level-order-traversal'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'binary-tree-level-order-traversal';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof'
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-level-order-traversal'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-level-order-traversal'
WHERE d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-ii-lcof';

-- cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof -> binary-tree-zigzag-level-order-traversal
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof' AND t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","滴滴","猿辅导","百度","网易","美团","腾讯","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof'
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof'
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'binary-tree-zigzag-level-order-traversal'
WHERE d.leetcode_slug = 'cong-shang-dao-xia-da-yin-er-cha-shu-iii-lcof';

-- bao-han-minhan-shu-de-zhan-lcof -> min-stack
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof' AND t.leetcode_slug = 'min-stack'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","字节跳动","微软","快手","拼多多","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'min-stack'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'min-stack'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'min-stack';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'min-stack'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'min-stack'
WHERE d.leetcode_slug = 'bao-han-minhan-shu-de-zhan-lcof';

-- fan-zhuan-lian-biao-lcof -> reverse-linked-list
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'fan-zhuan-lian-biao-lcof' AND t.leetcode_slug = 'reverse-linked-list'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'fan-zhuan-lian-biao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'reverse-linked-list'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'reverse-linked-list'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-lian-biao-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-lian-biao-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'reverse-linked-list';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-lian-biao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'reverse-linked-list'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'reverse-linked-list'
WHERE d.leetcode_slug = 'fan-zhuan-lian-biao-lcof';

-- he-bing-liang-ge-pai-xu-de-lian-biao-lcof -> merge-two-sorted-lists
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof' AND t.leetcode_slug = 'merge-two-sorted-lists'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'merge-two-sorted-lists'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'merge-two-sorted-lists'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'merge-two-sorted-lists';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'merge-two-sorted-lists'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'merge-two-sorted-lists'
WHERE d.leetcode_slug = 'he-bing-liang-ge-pai-xu-de-lian-biao-lcof';

-- fu-za-lian-biao-de-fu-zhi-lcof -> copy-list-with-random-pointer
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof' AND t.leetcode_slug = 'copy-list-with-random-pointer'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","字节跳动","小米","微软","快手","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'copy-list-with-random-pointer'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'copy-list-with-random-pointer'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'copy-list-with-random-pointer';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'copy-list-with-random-pointer'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'copy-list-with-random-pointer'
WHERE d.leetcode_slug = 'fu-za-lian-biao-de-fu-zhi-lcof';

-- dui-cheng-de-er-cha-shu-lcof -> symmetric-tree
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof' AND t.leetcode_slug = 'symmetric-tree'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","字节跳动","小米","快手","拼多多","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'symmetric-tree'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'symmetric-tree'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'symmetric-tree';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'symmetric-tree'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'symmetric-tree'
WHERE d.leetcode_slug = 'dui-cheng-de-er-cha-shu-lcof';

-- shu-ju-liu-zhong-de-zhong-wei-shu-lcof -> find-median-from-data-stream
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof' AND t.leetcode_slug = 'find-median-from-data-stream'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","字节跳动","微软","快手","拼多多","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'find-median-from-data-stream'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'find-median-from-data-stream'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'find-median-from-data-stream';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'find-median-from-data-stream'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'find-median-from-data-stream'
WHERE d.leetcode_slug = 'shu-ju-liu-zhong-de-zhong-wei-shu-lcof';

-- shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof -> majority-element
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof' AND t.leetcode_slug = 'majority-element'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","华为","字节跳动","小米","快手","拼多多","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'majority-element'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'majority-element'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'majority-element';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'majority-element'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'majority-element'
WHERE d.leetcode_slug = 'shu-zu-zhong-chu-xian-ci-shu-chao-guo-yi-ban-de-shu-zi-lcof';

-- zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof -> longest-substring-without-repeating-characters
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof' AND t.leetcode_slug = 'longest-substring-without-repeating-characters'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'longest-substring-without-repeating-characters'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'longest-substring-without-repeating-characters'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'longest-substring-without-repeating-characters';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'longest-substring-without-repeating-characters'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'longest-substring-without-repeating-characters'
WHERE d.leetcode_slug = 'zui-chang-bu-han-zhong-fu-zi-fu-de-zi-zi-fu-chuan-lcof';

-- gou-jian-cheng-ji-shu-zu-lcof -> product-of-array-except-self
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof' AND t.leetcode_slug = 'product-of-array-except-self'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","字节跳动","微软","快手","拼多多","旷视","百度","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'product-of-array-except-self'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'product-of-array-except-self'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'product-of-array-except-self';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'product-of-array-except-self'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'product-of-array-except-self'
WHERE d.leetcode_slug = 'gou-jian-cheng-ji-shu-zu-lcof';

-- zheng-ze-biao-da-shi-pi-pei-lcof -> regular-expression-matching
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof' AND t.leetcode_slug = 'regular-expression-matching'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","字节跳动","微软","快手","拼多多","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof'
JOIN question_bank t ON t.leetcode_slug = 'regular-expression-matching'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'regular-expression-matching'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'regular-expression-matching';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof'
JOIN question_bank t ON t.leetcode_slug = 'regular-expression-matching'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'regular-expression-matching'
WHERE d.leetcode_slug = 'zheng-ze-biao-da-shi-pi-pei-lcof';

-- hua-dong-chuang-kou-de-zui-da-zhi-lcof -> sliding-window-maximum
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof' AND t.leetcode_slug = 'sliding-window-maximum'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","旷视","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'sliding-window-maximum'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'sliding-window-maximum'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'sliding-window-maximum';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'sliding-window-maximum'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'sliding-window-maximum'
WHERE d.leetcode_slug = 'hua-dong-chuang-kou-de-zui-da-zhi-lcof';

-- ju-zhen-zhong-de-lu-jing-lcof -> word-search
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof' AND t.leetcode_slug = 'word-search'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","华为","商汤","字节跳动","小米","微软","快手","拼多多","携程","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof'
JOIN question_bank t ON t.leetcode_slug = 'word-search'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'word-search'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'word-search';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof'
JOIN question_bank t ON t.leetcode_slug = 'word-search'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'word-search'
WHERE d.leetcode_slug = 'ju-zhen-zhong-de-lu-jing-lcof';

-- fan-zhuan-dan-ci-shun-xu-lcof -> reverse-words-in-a-string
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof' AND t.leetcode_slug = 'reverse-words-in-a-string'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","华为","字节跳动","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'reverse-words-in-a-string'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'reverse-words-in-a-string'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'reverse-words-in-a-string';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'reverse-words-in-a-string'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'reverse-words-in-a-string'
WHERE d.leetcode_slug = 'fan-zhuan-dan-ci-shun-xu-lcof';

-- gu-piao-de-zui-da-li-run-lcof -> best-time-to-buy-and-sell-stock
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof' AND t.leetcode_slug = 'best-time-to-buy-and-sell-stock'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof'
JOIN question_bank t ON t.leetcode_slug = 'best-time-to-buy-and-sell-stock'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'best-time-to-buy-and-sell-stock'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'best-time-to-buy-and-sell-stock';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof'
JOIN question_bank t ON t.leetcode_slug = 'best-time-to-buy-and-sell-stock'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'best-time-to-buy-and-sell-stock'
WHERE d.leetcode_slug = 'gu-piao-de-zui-da-li-run-lcof';

-- lian-xu-zi-shu-zu-de-zui-da-he-lcof -> maximum-subarray
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof' AND t.leetcode_slug = 'maximum-subarray'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","京东","华为","商汤","图森","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof'
JOIN question_bank t ON t.leetcode_slug = 'maximum-subarray'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'maximum-subarray'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'maximum-subarray';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof'
JOIN question_bank t ON t.leetcode_slug = 'maximum-subarray'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'maximum-subarray'
WHERE d.leetcode_slug = 'lian-xu-zi-shu-zu-de-zui-da-he-lcof';

-- zhong-jian-er-cha-shu-lcof -> construct-binary-tree-from-preorder-and-inorder-traversal
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof' AND t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","字节跳动","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'construct-binary-tree-from-preorder-and-inorder-traversal'
WHERE d.leetcode_slug = 'zhong-jian-er-cha-shu-lcof';

-- qing-wa-tiao-tai-jie-wen-ti-lcof -> climbing-stairs
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof' AND t.leetcode_slug = 'climbing-stairs'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof'
JOIN question_bank t ON t.leetcode_slug = 'climbing-stairs'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'climbing-stairs'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'climbing-stairs';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof'
JOIN question_bank t ON t.leetcode_slug = 'climbing-stairs'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'climbing-stairs'
WHERE d.leetcode_slug = 'qing-wa-tiao-tai-jie-wen-ti-lcof';

-- shun-shi-zhen-da-yin-ju-zhen-lcof -> spiral-matrix
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof' AND t.leetcode_slug = 'spiral-matrix'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","商汤","字节跳动","小米","小红书","微软","快手","拼多多","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof'
JOIN question_bank t ON t.leetcode_slug = 'spiral-matrix'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'spiral-matrix'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'spiral-matrix';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof'
JOIN question_bank t ON t.leetcode_slug = 'spiral-matrix'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'spiral-matrix'
WHERE d.leetcode_slug = 'shun-shi-zhen-da-yin-ju-zhen-lcof';

-- chou-shu-lcof -> ugly-number-ii
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'chou-shu-lcof' AND t.leetcode_slug = 'ugly-number-ii'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","字节跳动","小米","微软","快手","拼多多","滴滴","百度","美团","腾讯","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'chou-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'ugly-number-ii'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'ugly-number-ii'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'chou-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'chou-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'ugly-number-ii';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'chou-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'ugly-number-ii'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'ugly-number-ii'
WHERE d.leetcode_slug = 'chou-shu-lcof';

-- fei-bo-na-qi-shu-lie-lcof -> fibonacci-number
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof' AND t.leetcode_slug = 'fibonacci-number'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["字节跳动","小米","微软","快手","滴滴","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof'
JOIN question_bank t ON t.leetcode_slug = 'fibonacci-number'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'fibonacci-number'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'fibonacci-number';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof'
JOIN question_bank t ON t.leetcode_slug = 'fibonacci-number'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'fibonacci-number'
WHERE d.leetcode_slug = 'fei-bo-na-qi-shu-lie-lcof';

-- jian-sheng-zi-lcof -> integer-break
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'jian-sheng-zi-lcof' AND t.leetcode_slug = 'integer-break'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","商汤","字节跳动","拼多多","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'jian-sheng-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'integer-break'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'integer-break'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'jian-sheng-zi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'jian-sheng-zi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'integer-break';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'jian-sheng-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'integer-break'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'integer-break'
WHERE d.leetcode_slug = 'jian-sheng-zi-lcof';

-- er-wei-shu-zu-zhong-de-cha-zhao-lcof -> search-a-2d-matrix-ii
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof' AND t.leetcode_slug = 'search-a-2d-matrix-ii'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["bilibili","亚马逊","京东","华为","字节跳动","小米","小红书","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'search-a-2d-matrix-ii'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'search-a-2d-matrix-ii'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'search-a-2d-matrix-ii';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof'
JOIN question_bank t ON t.leetcode_slug = 'search-a-2d-matrix-ii'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'search-a-2d-matrix-ii'
WHERE d.leetcode_slug = 'er-wei-shu-zu-zhong-de-cha-zhao-lcof';

-- xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof -> find-minimum-in-rotated-sorted-array
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof' AND t.leetcode_slug = 'find-minimum-in-rotated-sorted-array'
SET t.tags = '["CODETOP","HOT100","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","字节跳动","微软","快手","拼多多","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'find-minimum-in-rotated-sorted-array'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'find-minimum-in-rotated-sorted-array'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'find-minimum-in-rotated-sorted-array';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'find-minimum-in-rotated-sorted-array'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'find-minimum-in-rotated-sorted-array'
WHERE d.leetcode_slug = 'xuan-zhuan-shu-zu-de-zui-xiao-shu-zi-lcof';

-- shu-de-zi-jie-gou-lcof -> subtree-of-another-tree
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'shu-de-zi-jie-gou-lcof' AND t.leetcode_slug = 'subtree-of-another-tree'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["华为","商汤","字节跳动","微软","快手","滴滴","猿辅导","百度","美团","腾讯","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'shu-de-zi-jie-gou-lcof'
JOIN question_bank t ON t.leetcode_slug = 'subtree-of-another-tree'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'subtree-of-another-tree'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-de-zi-jie-gou-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-de-zi-jie-gou-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'subtree-of-another-tree';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-de-zi-jie-gou-lcof'
JOIN question_bank t ON t.leetcode_slug = 'subtree-of-another-tree'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'subtree-of-another-tree'
WHERE d.leetcode_slug = 'shu-de-zi-jie-gou-lcof';

-- que-shi-de-shu-zi-lcof -> missing-number
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'que-shi-de-shu-zi-lcof' AND t.leetcode_slug = 'missing-number'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","字节跳动","微软","快手","滴滴","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'que-shi-de-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'missing-number'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'missing-number'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'que-shi-de-shu-zi-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'que-shi-de-shu-zi-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'missing-number';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'que-shi-de-shu-zi-lcof'
JOIN question_bank t ON t.leetcode_slug = 'missing-number'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'missing-number'
WHERE d.leetcode_slug = 'que-shi-de-shu-zi-lcof';

-- 1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof -> number-of-digit-one
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof' AND t.leetcode_slug = 'number-of-digit-one'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["字节跳动","微软","快手","旷视","百度","美团","腾讯","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'number-of-digit-one'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'number-of-digit-one'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'number-of-digit-one';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'number-of-digit-one'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'number-of-digit-one'
WHERE d.leetcode_slug = '1nzheng-shu-zhong-1chu-xian-de-ci-shu-lcof';

-- er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof -> path-sum-ii
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof' AND t.leetcode_slug = 'path-sum-ii'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","商汤","字节跳动","小米","微软","快手","拼多多","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof'
JOIN question_bank t ON t.leetcode_slug = 'path-sum-ii'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'path-sum-ii'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'path-sum-ii';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof'
JOIN question_bank t ON t.leetcode_slug = 'path-sum-ii'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'path-sum-ii'
WHERE d.leetcode_slug = 'er-cha-shu-zhong-he-wei-mou-yi-zhi-de-lu-jing-lcof';

-- ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof -> decode-ways
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof' AND t.leetcode_slug = 'decode-ways'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","华为","商汤","字节跳动","微软","快手","拼多多","猿辅导","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'decode-ways'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'decode-ways'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'decode-ways';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof'
JOIN question_bank t ON t.leetcode_slug = 'decode-ways'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'decode-ways'
WHERE d.leetcode_slug = 'ba-shu-zi-fan-yi-cheng-zi-fu-chuan-lcof';

-- ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof -> string-to-integer-atoi
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof' AND t.leetcode_slug = 'string-to-integer-atoi'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","华为","字节跳动","小米","微软","快手","拼多多","携程","滴滴","猿辅导","百度","网易","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'string-to-integer-atoi'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'string-to-integer-atoi'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'string-to-integer-atoi';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof'
JOIN question_bank t ON t.leetcode_slug = 'string-to-integer-atoi'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'string-to-integer-atoi'
WHERE d.leetcode_slug = 'ba-zi-fu-chuan-zhuan-huan-cheng-zheng-shu-lcof';

-- shu-zhi-de-zheng-shu-ci-fang-lcof -> powx-n
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof' AND t.leetcode_slug = 'powx-n'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["亚马逊","京东","商汤","字节跳动","快手","滴滴","猿辅导","百度","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof'
JOIN question_bank t ON t.leetcode_slug = 'powx-n'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'powx-n'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'powx-n';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof'
JOIN question_bank t ON t.leetcode_slug = 'powx-n'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'powx-n'
WHERE d.leetcode_slug = 'shu-zhi-de-zheng-shu-ci-fang-lcof';

-- zi-fu-chuan-de-pai-lie-lcof -> permutations-ii
UPDATE question_bank t
JOIN question_bank d ON d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof' AND t.leetcode_slug = 'permutations-ii'
SET t.tags = '["CODETOP","JIANZHI","HIGH_FREQ"]',
    t.companies = '["京东","华为","图森","字节跳动","小米","微软","快手","滴滴","猿辅导","美团","腾讯","虾皮","阿里巴巴"]',
    t.hot_score = GREATEST(t.hot_score, d.hot_score),
    t.last_asked_at = IF(t.last_asked_at IS NULL
        OR (d.last_asked_at IS NOT NULL AND d.last_asked_at > t.last_asked_at),
        d.last_asked_at, t.last_asked_at);
UPDATE question_attempts a
JOIN question_bank d ON d.id = a.question_id AND d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof'
JOIN question_bank t ON t.leetcode_slug = 'permutations-ii'
SET a.question_id = t.id;
UPDATE user_question_progress k
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'permutations-ii'
JOIN user_question_progress o ON o.user_id = k.user_id
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof'
SET k.attempt_count = k.attempt_count + o.attempt_count,
    k.best_score = GREATEST(COALESCE(k.best_score, 0), COALESCE(o.best_score, 0)),
    k.last_score = COALESCE(o.last_score, k.last_score),
    k.last_attempt_at = IF(k.last_attempt_at IS NULL
        OR (o.last_attempt_at IS NOT NULL AND o.last_attempt_at > k.last_attempt_at),
        o.last_attempt_at, k.last_attempt_at),
    k.status = GREATEST(k.status, o.status);
DELETE o
FROM user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof'
JOIN user_question_progress k ON k.user_id = o.user_id
JOIN question_bank t ON t.id = k.question_id AND t.leetcode_slug = 'permutations-ii';
UPDATE user_question_progress o
JOIN question_bank d ON d.id = o.question_id AND d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof'
JOIN question_bank t ON t.leetcode_slug = 'permutations-ii'
SET o.question_id = t.id;
DELETE d
FROM question_bank d
JOIN question_bank t ON t.leetcode_slug = 'permutations-ii'
WHERE d.leetcode_slug = 'zi-fu-chuan-de-pai-lie-lcof';

-- 统计：
-- 写入 307 道，回填 342 道；Hot100 共 100 道，剑指 Offer 74 道，高频 285 道