// 按文件把 data/question-bank/*.json 分批导入题库。
// 用法：ADMIN_TOKEN=xxx node scripts/import-question-bank.mjs [baseUrl] [文件名...]
// 例：ADMIN_TOKEN=dev-token node scripts/import-question-bank.mjs http://localhost:8081 01-hash.json
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const baseUrl = (process.argv[2] || process.env.API_BASE || 'http://localhost:8081').replace(/\/$/, '')
const only = process.argv.slice(3)
const token = process.env.ADMIN_TOKEN
const dir = 'data/question-bank'

if (!token) {
  console.error('缺少 ADMIN_TOKEN 环境变量')
  process.exit(1)
}

const files = readdirSync(dir)
  .filter((name) => name.endsWith('.json') && !name.startsWith('_'))
  .filter((name) => only.length === 0 || only.includes(name))
  .sort()

let imported = 0
let skipped = 0
for (const file of files) {
  const payload = JSON.parse(readFileSync(join(dir, file), 'utf8'))
  const res = await fetch(`${baseUrl}/api/admin/questions/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Token': token },
    body: JSON.stringify(payload),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok || !body || body.code !== 0) {
    console.error(`${file} 导入失败：HTTP ${res.status} ${body ? body.message : ''}`)
    process.exitCode = 1
    continue
  }
  imported += body.data.imported
  skipped += body.data.skipped
  console.log(`${file}: 新增 ${body.data.imported}，跳过 ${body.data.skipped}`)
}
console.log(`\n合计新增 ${imported}，跳过 ${skipped}`)
