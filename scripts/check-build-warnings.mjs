/**
 * 用途：校验前端构建日志中的 warning 数量是否超阈值。
 * 入口参数：--log, --max-circular, --forbid-empty。
 */
import { readFile } from 'node:fs/promises'

function parseArgs(argv) {
  const args = {
    log: '',
    maxCircular: 7,
    forbidEmpty: true,
  }

  for (let i = 0; i < argv.length; i++) {
    const value = argv[i]
    if (value === '--log') {
      args.log = argv[++i] || ''
    } else if (value === '--max-circular') {
      args.maxCircular = Number(argv[++i] || 7)
    } else if (value === '--forbid-empty') {
      const raw = (argv[++i] || 'true').toLowerCase()
      args.forbidEmpty = raw === 'true' || raw === '1'
    }
  }

  return args
}

async function main() {
  const { log, maxCircular, forbidEmpty } = parseArgs(process.argv.slice(2))
  if (!log) {
    throw new Error('missing --log')
  }

  const content = await readFile(log, 'utf8')
  const circularCount = (content.match(/Circular chunk:/g) || []).length
  const emptyCount = (content.match(/Generated an empty chunk:/g) || []).length

  console.log(`[warning-gate] circular chunk warnings: ${circularCount}`)
  console.log(`[warning-gate] empty chunk warnings: ${emptyCount}`)

  if (circularCount > maxCircular) {
    throw new Error(`[warning-gate] circular warnings exceeded threshold ${maxCircular}`)
  }

  if (forbidEmpty && emptyCount > 0) {
    throw new Error('[warning-gate] empty chunk warnings detected')
  }

  console.log('[warning-gate] warning thresholds passed')
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
