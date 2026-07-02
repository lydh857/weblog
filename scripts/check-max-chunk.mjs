/**
 * 用途：扫描构建产物目录，校验最大 JS chunk 是否超过阈值。
 * 入口参数：--dir, --max-kb, --label。
 */
import { readdir, stat } from 'node:fs/promises'
import { join } from 'node:path'

function parseArgs(argv) {
  const args = {
    dir: '',
    maxKb: 300,
    label: 'app',
  }

  for (let i = 0; i < argv.length; i++) {
    const value = argv[i]
    if (value === '--dir') {
      args.dir = argv[++i] || ''
    } else if (value === '--max-kb') {
      args.maxKb = Number(argv[++i] || 300)
    } else if (value === '--label') {
      args.label = argv[++i] || 'app'
    }
  }

  return args
}

async function collectJsFiles(directory) {
  const files = []
  const entries = await readdir(directory, { withFileTypes: true })

  for (const entry of entries) {
    const fullPath = join(directory, entry.name)
    if (entry.isDirectory()) {
      const nested = await collectJsFiles(fullPath)
      files.push(...nested)
      continue
    }

    if (entry.isFile() && entry.name.endsWith('.js')) {
      files.push(fullPath)
    }
  }

  return files
}

async function main() {
  const { dir, maxKb, label } = parseArgs(process.argv.slice(2))
  if (!dir) {
    throw new Error('missing --dir')
  }

  if (!Number.isFinite(maxKb) || maxKb <= 0) {
    throw new Error('invalid --max-kb')
  }

  const files = await collectJsFiles(dir)
  if (files.length === 0) {
    throw new Error(`no js chunks found in ${dir}`)
  }

  let largestFile = ''
  let largestBytes = 0

  for (const file of files) {
    const fileStat = await stat(file)
    if (fileStat.size > largestBytes) {
      largestBytes = fileStat.size
      largestFile = file
    }
  }

  const largestKb = largestBytes / 1024
  console.log(`[chunk-gate] ${label} largest chunk: ${largestKb.toFixed(2)} kB (${largestFile})`)

  if (largestKb > maxKb) {
    throw new Error(`[chunk-gate] ${label} exceeded threshold ${maxKb} kB`)
  }

  console.log(`[chunk-gate] ${label} passed threshold ${maxKb} kB`)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
