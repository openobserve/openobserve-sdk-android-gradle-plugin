#!/usr/bin/env node
/**
 * OpenObserve rebrand codemod for the openobserve-sdk-android fork.
 *
 * Two phases (order matters):
 *   1. pathRenames — git mv the Kotlin package namespace (com/datadog/android ->
 *      com/openobserve/android) and the Maven module dirs (dd-sdk-android-* ->
 *      oo-sdk-android-*). Directory moves must happen before content rewrites so
 *      the content pass sees the final tree.
 *   2. content rules — rewrite file text (package/import decls, Maven coordinates,
 *      public API type names, JNI symbols, _dd -> _oo).
 *
 * Run from the repository root:
 *   node scripts/openobserve/rebrand.mjs            # apply (git mv + content)
 *   node scripts/openobserve/rebrand.mjs --check    # report only, no writes/moves
 *   node scripts/openobserve/rebrand.mjs --content-only   # skip git mv (measure content)
 *
 * NOTE: the full rebrand MUST be verified with a Gradle + NDK build — this codemod
 * is mechanical but a missed path/JNI symbol only surfaces at compile/link time.
 */
import { execFileSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const ROOT = process.cwd()
const HERE = path.dirname(fileURLToPath(import.meta.url))
const CHECK = process.argv.includes('--check')
const CONTENT_ONLY = process.argv.includes('--content-only')

const map = JSON.parse(fs.readFileSync(path.join(HERE, 'rename-map.json'), 'utf8'))
const { rules, pathRenames, fileRenames = [] } = map

const keepOursPaths = fs
  .readFileSync(path.join(HERE, 'keep-ours.txt'), 'utf8')
  .split('\n')
  .map((l) => l.trim())
  .filter((l) => l && !l.startsWith('#'))

const SKIP = [
  /(^|\/)\.git\//,
  /(^|\/)build\//,
  /(^|\/)\.gradle\//,
  /(^|\/)\.idea\//,
  /^scripts\/openobserve\//,
  /\.(png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|otf|so|a|jar|aar|keystore|bin|zip|pdf)$/i,
]

function git(args) {
  return execFileSync('git', args, { cwd: ROOT, maxBuffer: 256 * 1024 * 1024 }).toString('utf8')
}
function tracked() {
  return git(['ls-files', '-z']).split('\0').filter(Boolean)
}

// ---- Phase 1: path renames (git mv) -----------------------------------------
// Each rule is applied against a FRESH scan of the tree, so rules compose: the
// package-dir rule (com/datadog -> com/openobserve) runs first on the inner dirs,
// then the module-dir rule (dd-sdk-android-* -> oo-sdk-android-*) moves the outer
// dirs carrying the already-renamed package dirs. Within a single rule we drop a
// move nested under another move OF THE SAME RULE (git mv carries nested content);
// we do NOT drop across rules — that was the bug that lost the package-dir moves.
function dirsNow() {
  return [
    ...new Set(
      tracked()
        .filter((f) => !SKIP.some((re) => re.test(f)))
        .map((f) => path.dirname(f))
    ),
  ]
}
function movesForRule(rule, dirs) {
  const moves = []
  if (rule.from && rule.to) {
    // Boundary-match the segment ANYWHERE in the path (files live deeper than the
    // segment dir, so leaf-matching misses it). Extract each unique `<prefix>/com/
    // datadog/android` dir and rename it to `<prefix>/com/openobserve/android`.
    const from = rule.from // com/datadog/android
    const srcs = new Set()
    for (const d of dirs) {
      const idx = d.indexOf(from)
      if (idx === -1) continue
      const before = idx === 0 || d[idx - 1] === '/'
      const afterPos = idx + from.length
      const after = afterPos === d.length || d[afterPos] === '/'
      if (before && after) srcs.add(d.slice(0, afterPos))
    }
    for (const src of srcs) {
      const idx = src.length - from.length
      moves.push([src, src.slice(0, idx) + rule.to])
    }
  } else if (rule.fromPrefix && rule.toPrefix) {
    for (const d of dirs) {
      const base = path.basename(d)
      if (base.startsWith(rule.fromPrefix)) {
        const parent = path.dirname(d)
        const nb = rule.toPrefix + base.slice(rule.fromPrefix.length)
        moves.push([d, parent === '.' ? nb : `${parent}/${nb}`])
      }
    }
  }
  const uniq = [...new Map(moves.map((m) => [m[0], m])).values()].sort(
    (a, b) => a[0].split('/').length - b[0].split('/').length
  )
  const done = []
  return uniq.filter(([from]) => {
    if (done.some((p) => from === p || from.startsWith(p + '/'))) return false
    done.push(from)
    return true
  })
}

// ---- Phase 2: content rules -------------------------------------------------
const compiled = rules.map((r) => ({ ...r, matcher: r.regex ? new RegExp(r.regex, 'g') : null }))
// Apache-2.0 §4(b)/(c): never rewrite upstream attribution lines — the per-file
// license header (in ANY comment style: /* */, #, <!-- ~ -->) and the third-party
// copyright manifest (LICENSE-3rdparty.csv). Matched by CONTENT, per line, so the
// Datadog->OpenObserve rules only touch code. Datadog's copyright/attribution stays
// verbatim; the OpenObserve modification statement lives in NOTICE + README (kept
// via keep-ours.txt).
const ATTRIBUTION_RE = /software developed at Datadog \(https:\/\/www\.datadoghq\.com\/\)|Copyright\b[^\n]*\bDatadog, Inc\./
function applyRules(s) {
  return s
    .split('\n')
    .map((line) => {
      if (ATTRIBUTION_RE.test(line)) return line
      let out = line
      for (const r of compiled) out = r.matcher ? out.replace(r.matcher, r.to) : out.split(r.literal).join(r.to)
      return out
    })
    .join('\n')
}

// ---- Run --------------------------------------------------------------------
const LIST = process.argv.includes('--list')
let totalMoves = 0
for (const rule of pathRenames) {
  // For --check, dirsNow() is stable (no mutation) so each rule reports against the
  // original tree — counts are exact even though nested target paths shown are pre-move.
  const moves = movesForRule(rule, dirsNow())
  totalMoves += moves.length
  console.log(`pathRename [${rule.from || rule.fromPrefix}]: ${moves.length} moves ${CHECK ? '(would run)' : ''}`)
  if (LIST) moves.slice(0, 20).forEach(([a, b]) => console.log(`  ${a} -> ${b}`))
  if (!CHECK && !CONTENT_ONLY) {
    for (const [from, to] of moves) {
      fs.mkdirSync(path.dirname(to), { recursive: true })
      git(['mv', from, to])
    }
  }
}
console.log(`pathRenames total: ${totalMoves} directory moves`)

// ---- Phase 1.5: file renames (basename token) -------------------------------
// Rename class-name FILES whose basename still carries a Datadog token even though
// the class INSIDE was already rebranded by the content rules (e.g.
// DatadogLogGenerator.kt holding `class OpenObserveLogGenerator`). Literal token
// replacement on the basename only; directory segments were handled by pathRenames.
let fileMoves = 0
for (const rule of fileRenames) {
  for (const f of tracked().filter(
    (x) => !SKIP.some((re) => re.test(x)) && !keepOursPaths.some((p) => x === p || x.startsWith(`${p}/`))
  )) {
    const base = path.basename(f)
    if (!base.includes(rule.token)) continue
    const to = path.join(path.dirname(f), base.split(rule.token).join(rule.to))
    fileMoves++
    if (!CHECK && !CONTENT_ONLY) git(['mv', f, to])
  }
}
console.log(`fileRenames: ${fileMoves} files ${CHECK ? 'would be' : ''} moved`)

let changed = 0
for (const file of tracked().filter((f) => !SKIP.some((re) => re.test(f)) && !keepOursPaths.some((p) => f === p || f.startsWith(`${p}/`)))) {
  const abs = path.join(ROOT, file)
  let stat
  try {
    stat = fs.lstatSync(abs)
  } catch {
    continue
  }
  if (!stat.isFile()) continue
  const buf = fs.readFileSync(abs)
  if (buf.includes(0)) continue
  const content = buf.toString('utf8')
  const next = applyRules(content)
  if (next !== content) {
    changed++
    if (!CHECK) fs.writeFileSync(abs, next)
  }
}
console.log(`content: ${changed} files ${CHECK ? 'would be' : ''} rebranded`)
if (CHECK && (changed > 0 || totalMoves > 0 || fileMoves > 0)) process.exit(1)
