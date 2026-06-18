import os, re

path = r'C:\Users\julian\Documents\LastChat\app\src\main\res\values\strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.split('\n')
seen = set()
out = []
for line in lines:
    m = re.search(r'<string name="([^"]+)"', line)
    if m:
        name = m.group(1)
        if name in seen:
            continue
        seen.add(name)
    out.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))
print('Duplicates removed.')
