const fs = require('fs');
const path = require('path');

function walk(dir, fileList = []) {
  fs.readdirSync(dir).forEach(file => {
    const p = path.join(dir, file);
    if (fs.statSync(p).isDirectory()) fileList = walk(p, fileList);
    else if (p.endsWith('.tsx') || p.endsWith('.ts')) fileList.push(p);
  });
  return fileList;
}

const files = walk('C:/Users/julia/Documents/Github/LastChat_dev/web-ui/app');
const lucideImports = new Set();

files.forEach(f => {
  const content = fs.readFileSync(f, 'utf8');
  const matches = content.matchAll(/import\s+\{([^}]+)\}\s+from\s+['"]lucide-react['"]/g);
  for (const match of matches) {
    match[1].split(',').map(s => s.trim().split(" as ")[0]).filter(s => s).forEach(s => lucideImports.add(s));
  }
});

console.log(Array.from(lucideImports));
