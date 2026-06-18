import os

# Add imports to WorkspacePage.kt
path1 = r'C:\Users\julian\Documents\LastChat\app\src\main\java\me\rerere\rikkahub\ui\pages\extensions\workspace\WorkspacePage.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c1 = f.read()
c1 = c1.replace('import androidx.compose.material3.TopAppBarDefaults\nimport androidx.compose.material3.TopAppBarDefaults', 'import androidx.compose.material3.TopAppBarDefaults')
c1 = c1.replace('import androidx.compose.material3.CardDefaults\n', 'import androidx.compose.material3.CardDefaults\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.rounded.*\n')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(c1)

# Add imports and fix strings in WorkspaceDetailPage.kt
path2 = r'C:\Users\julian\Documents\LastChat\app\src\main\java\me\rerere\rikkahub\ui\pages\extensions\workspace\WorkspaceDetailPage.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c2 = f.read()

c2 = c2.replace('import androidx.compose.material3.CardDefaults\n', 'import androidx.compose.material3.CardDefaults\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.rounded.*\nimport androidx.compose.material.icons.automirrored.rounded.*\n')
c2 = c2.replace('vm.deleteFile(entry)', 'vm.delete(entry)')
c2 = c2.replace('R.string.workspace_detail_title', 'R.string.workspace_detail')
c2 = c2.replace('R.string.workspace_detail_tab_basic', 'R.string.workspace_detail_workspace_info')
c2 = c2.replace('R.string.workspace_detail_tab_files', 'R.string.workspace_detail_area_files')

with open(path2, 'w', encoding='utf-8') as f:
    f.write(c2)

# Add remaining strings to strings.xml
path3 = r'C:\Users\julian\Documents\LastChat\app\src\main\res\values\strings.xml'
with open(path3, 'r', encoding='utf-8') as f:
    c3 = f.read()

extra_strings = """  <string name="workspace_detail_rootfs_install_failed">Rootfs install failed</string>
  <string name="workspace_detail_import_file">Import File</string>
</resources>"""

c3 = c3.replace('</resources>', extra_strings)

with open(path3, 'w', encoding='utf-8') as f:
    f.write(c3)
print('Done!')
