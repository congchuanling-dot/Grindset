// 校验 data/question-bank/*.json 的编程题数据是否符合导入接口与自由运行约定。
// 用法：node scripts/validate-question-bank.mjs [文件名关键字]
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const DIR = 'data/question-bank'
const filter = process.argv[2]

const files = readdirSync(DIR)
  .filter((name) => name.endsWith('.json') && !name.startsWith('_'))
  .filter((name) => !filter || name.includes(filter))
  .sort()

const errors = []
const titles = new Map()
let total = 0

function fail(file, question, message) {
  const name = question && question.title ? `「${question.title}」` : ''
  errors.push(`${file} ${name}${message}`)
}

for (const file of files) {
  const payload = JSON.parse(readFileSync(join(DIR, file), 'utf8'))
  if (!Array.isArray(payload.questions)) {
    errors.push(`${file} 缺少 questions 数组`)
    continue
  }
  for (const q of payload.questions) {
    total++
    for (const key of ['title', 'content', 'domain', 'type', 'judgeMode']) {
      if (!q[key] || String(q[key]).trim() === '') {
        fail(file, q, `缺少 ${key}`)
      }
    }
    if (![1, 2, 3].includes(q.difficulty)) fail(file, q, 'difficulty 必须是 1/2/3')
    if (q.type !== 'CODE') fail(file, q, 'type 必须是 CODE')
    if (q.judgeMode !== 'CODING') fail(file, q, 'judgeMode 必须是 CODING')

    const key = String(q.title || '').trim()
    if (titles.has(key)) {
      errors.push(`${file} 标题重复：「${key}」，已出现在 ${titles.get(key)}`)
    } else {
      titles.set(key, file)
    }

    // 自由运行模式不再需要测试用例与函数签名
    if (q.testCases) fail(file, q, '不再支持 testCases，请删除')
    if (q.codeMode) fail(file, q, '不再支持 codeMode，请删除')
    if (q.functionName || q.paramTypes || q.returnType || q.starterCodeFunction) {
      fail(file, q, '不再支持函数签名相关字段，请删除')
    }

    const content = String(q.content || '')
    if (/ACM\s*模式|力扣模式|输入输出格式/.test(content)) {
      fail(file, q, '题面仍包含 ACM/力扣模式或输入输出格式段落')
    }

    const starter = q.starterCode || {}
    if (!starter.JAVA || !starter.PYTHON) fail(file, q, 'starterCode 必须同时包含 JAVA/PYTHON')
    if (starter.JAVA && !starter.JAVA.includes('class Main')) {
      fail(file, q, 'Java 初始代码缺少 class Main')
    }
    if (starter.PYTHON && !starter.PYTHON.includes('def main')) {
      fail(file, q, 'Python 初始代码缺少 main 函数')
    }
  }
  console.log(`${file}: ${payload.questions.length} 题`)
}

console.log(`\n共 ${total} 题，${errors.length} 个问题`)
if (errors.length > 0) {
  for (const message of errors.slice(0, 200)) console.log(`- ${message}`)
  process.exitCode = 1
}
