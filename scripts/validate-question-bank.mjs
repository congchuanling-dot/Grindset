// 校验 data/question-bank/*.json 的编程题数据是否符合导入接口与判题器的约定。
// 用法：node scripts/validate-question-bank.mjs [文件名关键字]
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const DIR = 'data/question-bank'
const filter = process.argv[2]
const SUPPORTED_TYPES = new Set([
  'int', 'long', 'double', 'boolean', 'char', 'String',
  'int[]', 'long[]', 'double[]', 'boolean[]', 'char[]', 'String[]',
  'int[][]', 'char[][]', 'String[][]',
  'List<Integer>', 'List<String>', 'List<List<Integer>>',
])

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

function parseJson(text) {
  return JSON.parse(text)
}

for (const file of files) {
  const payload = parseJson(readFileSync(join(DIR, file), 'utf8'))
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

    const cases = Array.isArray(q.testCases) ? q.testCases : []
    if (cases.length === 0) fail(file, q, '至少需要一个测试用例')
    const modes = new Set()
    let samples = 0
    cases.forEach((c, index) => {
      if (!['ACM', 'LEETCODE'].includes(c.caseMode)) fail(file, q, `用例 ${index + 1} caseMode 非法`)
      if (!c.expectedOutput || String(c.expectedOutput).length === 0) {
        fail(file, q, `用例 ${index + 1} 缺少 expectedOutput`)
      }
      if (c.isSample !== 0 && c.isSample !== 1) fail(file, q, `用例 ${index + 1} isSample 必须是 0/1`)
      if (!Number.isInteger(c.sort)) fail(file, q, `用例 ${index + 1} sort 必须是整数`)
      if (c.isSample === 1) samples++
      modes.add(c.caseMode)
      if (c.caseMode === 'LEETCODE') {
        try {
          const args = parseJson(c.input)
          if (!Array.isArray(args)) fail(file, q, `用例 ${index + 1} 力扣输入必须是 JSON 数组`)
        } catch {
          fail(file, q, `用例 ${index + 1} 力扣输入不是合法 JSON`)
        }
        try {
          parseJson(c.expectedOutput)
        } catch {
          fail(file, q, `用例 ${index + 1} 力扣期望输出不是合法 JSON`)
        }
      }
    })
    if (samples === 0) fail(file, q, '至少需要一个 isSample=1 的例题')

    const starter = q.starterCode || {}
    if (!starter.JAVA || !starter.PYTHON) fail(file, q, 'starterCode 必须同时包含 JAVA/PYTHON')

    if (modes.has('LEETCODE')) {
      if (!q.functionName) fail(file, q, '力扣模式缺少 functionName')
      if (!Array.isArray(q.paramTypes)) fail(file, q, '力扣模式缺少 paramTypes 数组')
      else {
        for (const type of q.paramTypes) {
          if (!SUPPORTED_TYPES.has(type)) fail(file, q, `paramTypes 含不支持的类型 ${type}`)
        }
      }
      if (!q.returnType) fail(file, q, '力扣模式缺少 returnType')
      const fn = q.starterCodeFunction || {}
      if (!fn.JAVA || !fn.PYTHON) fail(file, q, 'starterCodeFunction 必须同时包含 JAVA/PYTHON')
      const camel = /^[a-zA-Z][a-zA-Z0-9]*$/
      if (q.functionName && !camel.test(q.functionName)) fail(file, q, 'functionName 必须是驼峰标识符')
      if (fn.JAVA && q.functionName && !fn.JAVA.includes(q.functionName)) {
        fail(file, q, 'Java 力扣初始代码里没有函数名')
      }
      if (fn.PYTHON && q.functionName && !fn.PYTHON.includes(q.functionName)) {
        fail(file, q, 'Python 力扣初始代码里没有函数名')
      }
    } else if (q.functionName) {
      fail(file, q, '没有力扣用例却填了 functionName')
    }

    for (const [lang, code] of Object.entries(starter)) {
      if (typeof code !== 'string' || code.trim() === '') fail(file, q, `starterCode.${lang} 为空`)
    }
    if (starter.JAVA && !starter.JAVA.includes('class Main')) {
      fail(file, q, 'Java ACM 初始代码缺少 class Main')
    }
  }
  console.log(`${file}: ${payload.questions.length} 题`)
}

console.log(`\n共 ${total} 题，${errors.length} 个问题`)
if (errors.length > 0) {
  for (const message of errors.slice(0, 200)) console.log(`- ${message}`)
  process.exitCode = 1
}
