/**
 * 后端枚举值的常量映射。数据库里存的是数字,页面里禁止直接写裸数字。
 */

/** 面试会话状态,对应 interview_sessions.status */
export const SessionStatus = {
  /** 进行中 */
  ONGOING: 0,
  /** 已结束 */
  FINISHED: 1,
} as const

/** 面试题目状态,对应 interview_questions.status */
export const QuestionStatus = {
  /** 未开始 */
  NOT_STARTED: 0,
  /** 提问中 */
  ASKING: 1,
  /** 作答中 */
  ANSWERING: 2,
  /** 已收束 */
  CLOSED: 3,
  /** 已放弃 */
  ABANDONED: 4,
} as const

/** 简历解析状态,对应 resumes.parse_status */
export const ParseStatus = {
  /** 解析中 */
  PARSING: 0,
  /** 解析成功 */
  SUCCESS: 1,
  /** 解析失败 */
  FAILED: 2,
} as const
