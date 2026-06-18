import os, shutil, re

rikka_base = r'C:\Users\julian\Downloads\rikkahub-master\rikkahub-master\app\src\main\java\me\rerere\rikkahub\ui\pages\extensions\workspace'
last_base = r'C:\Users\julian\Documents\LastChat\app\src\main\java\me\rerere\rikkahub\ui\pages\extensions\workspace'

files_to_copy = ['WorkspacePage.kt', 'WorkspaceDetailPage.kt', 'WorkspaceTerminalPage.kt', 'WorkspaceTerminalSession.kt']

for f in files_to_copy:
    shutil.copy(os.path.join(rikka_base, f), os.path.join(last_base, f))

def patch(filename, rules):
    path = os.path.join(last_base, filename)
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    for p, r in rules:
        content = re.sub(p, r, content)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

common_ui_rules = [
    (r'import hugeicons\..*', r''),
    (r'import me.rerere.rikkahub.ui.theme.CustomColors', r''),
    (r'import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog', r'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.TextButton\nimport androidx.compose.material3.TopAppBarDefaults\nimport androidx.compose.material3.CardDefaults'),
    (r'colors\s*=\s*CustomColors\.topBarColors', r'colors = TopAppBarDefaults.topAppBarColors()'),
    (r',\s*containerColor\s*=\s*CustomColors\.topBarColors\.containerColor', r''),
    (r'colors\s*=\s*CustomColors\.cardColorsOnSurfaceContainer', r'colors = CardDefaults.cardColors()'),
    (r'HugeIcons\.Stroke\.Rounded\.([A-Za-z0-9]+)', r'Icons.Rounded.\1'),
    (r'Icons\.Rounded\.Add01', r'Icons.Rounded.Add'),
    (r'Icons\.Rounded\.Search01', r'Icons.Rounded.Search'),
    (r'Icons\.Rounded\.MoreVertical', r'Icons.Rounded.MoreVert'),
    (r'Icons\.Rounded\.Tick01', r'Icons.Rounded.Check'),
    (r'Icons\.Rounded\.Cancel01', r'Icons.Rounded.Close'),
    (r'Icons\.Rounded\.Edit02', r'Icons.Rounded.Edit'),
    (r'Icons\.Rounded\.Delete01', r'Icons.Rounded.Delete'),
    (r'Icons\.Rounded\.ArrowLeft01', r'Icons.Rounded.ArrowBack'),
    (r'Icons\.Rounded\.Terminal', r'Icons.Rounded.Code'),
    (r'Icons\.Rounded\.Folder01', r'Icons.Rounded.Folder'),
    (r'Icons\.Rounded\.File02', r'Icons.Rounded.Description'),
    (r'stringResource\(id\s*=\s*R\.string\.([a-zA-Z0-9_]+)\)', r'stringResource(R.string.\1)'),
]

patch('WorkspacePage.kt', common_ui_rules + [
    (r'RikkaConfirmDialog\([\s\S]*?onDismiss\s*=\s*\{[\s\S]*?\}[\s\S]*?\)', r'''if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.workspace_page_delete)) },
            text = { Text(stringResource(R.string.workspace_page_delete_confirm)) },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { vm.delete(it) }; deleteTarget = null }) { Text(stringResource(R.string.common_confirm)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }''')
])

patch('WorkspaceDetailPage.kt', common_ui_rules + [
    (r'RikkaConfirmDialog\([\s\S]*?onDismiss\s*=\s*\{[\s\S]*?\}[\s\S]*?\)', r'''if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.workspace_page_delete)) },
            text = { Text(stringResource(R.string.workspace_page_delete_confirm)) },
            confirmButton = { TextButton(onClick = { vm.delete(workspace); showConfirmDelete = false; navController.popBackStack() }) { Text(stringResource(R.string.common_confirm)) } },
            dismissButton = { TextButton(onClick = { showConfirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }''')
])

patch('WorkspaceTerminalPage.kt', common_ui_rules + [
    (r'colorMode\s*=\s*.*?,', r''),
    (r'jetbrains_mono', r'monospace'),
])

patch('WorkspaceTerminalSession.kt', [
    (r'import me.rerere.rikkahub.data.files.FileFolders', r''),
    (r'FileFolders\.FONTS', r'"fonts"'),
    (r'FileFolders\.SKILLS', r'"skills"'),
])

print('Done copying and patching')
