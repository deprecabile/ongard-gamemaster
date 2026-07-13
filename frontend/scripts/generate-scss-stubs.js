/**
 * Deletes all existing .d.scss.ts, then regenerates them by parsing .module.scss files
 * and extracting real class names. This ensures `tsc -b` catches typos in class usage.
 */
import fs from 'node:fs';
import path from 'node:path';

const srcDir = path.resolve(import.meta.dirname, '..', 'src');

/** Extract CSS Module class names from SCSS source. */
function extractClassNames(scss) {
  let clean = scss;
  // Remove block comments
  clean = clean.replace(/\/\*[\s\S]*?\*\//g, '');
  // Remove line comments
  clean = clean.replace(/\/\/.*/g, '');
  // Remove string literals
  clean = clean.replace(/'[^']*'/g, '""');
  clean = clean.replace(/"[^"]*"/g, '""');
  // Remove :global(...) so we don't pick up global class names
  clean = clean.replace(/:global\([^)]*\)/g, '');

  const classes = new Set();

  // .className at start of line, after whitespace, or after combinators/comma
  for (const m of clean.matchAll(/(?:^|[\s,{;>+~(])\.([a-zA-Z_][\w-]*)/g)) {
    classes.add(m[1]);
  }
  // &.className (modifier attached to parent selector)
  for (const m of clean.matchAll(/&\.([a-zA-Z_][\w-]*)/g)) {
    classes.add(m[1]);
  }

  return [...classes].sort();
}

function buildDts(classNames) {
  if (classNames.length === 0) {
    return 'declare const classNames: Record<string, string>;\nexport default classNames;\n';
  }
  const isSimpleIdent = (n) => /^[a-zA-Z_$][a-zA-Z0-9_$]*$/.test(n);
  const lines = classNames.map((n) => {
    const key = isSimpleIdent(n) ? n : `"${n}"`;
    return `  readonly ${key}: "${n}";`;
  });
  return `declare const classNames: {\n${lines.join('\n')}\n};\nexport default classNames;\n`;
}

function walk(dir, action) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, action);
    } else {
      action(full, entry.name);
    }
  }
}

// 1. Delete all existing stubs
walk(srcDir, (full, name) => {
  if (name.endsWith('.module.d.scss.ts')) {
    fs.unlinkSync(full);
  }
});

// 2. Parse each .module.scss and generate typed stub
walk(srcDir, (full, name) => {
  if (name.endsWith('.module.scss')) {
    const scss = fs.readFileSync(full, 'utf-8');
    const classNames = extractClassNames(scss);
    const dts = full.replace(/\.scss$/, '.d.scss.ts');
    fs.writeFileSync(dts, buildDts(classNames));
  }
});
