import { existsSync, readdirSync, rmdirSync, unlinkSync } from 'node:fs'
import { join, resolve } from 'node:path'

function removeDir(dir) {
  if (!existsSync(dir)) return
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const target = join(dir, entry.name)
    if (entry.isDirectory()) removeDir(target)
    else unlinkSync(target)
  }
  rmdirSync(dir)
}

// Remove dist before building so stale hashed chunks cannot accumulate.
removeDir(resolve(process.cwd(), 'dist'))
