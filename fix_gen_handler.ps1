$file = 'app\src\main\java\me\rerere\rikkahub\data\ai\GenerationHandler.kt'
$lines = [System.IO.File]::ReadAllLines($file)
$newLines = [System.Collections.Generic.List[string]]::new()

$skip = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($skip -gt 0) {
        $skip--
        continue
    }
    
    if ($lines[$i].Trim() -eq 'val params = TextGenerationParams(' -and $i -gt 0) {
        # Check if next lines match
        $match = $true
        $expected = @(
            '            model = model,',
            '            temperature = assistant.temperature,',
            '            topP = assistant.topP,',
            '            maxTokens = assistant.maxTokens,',
            '            tools = tools,',
            '            builtInTools = resolveActiveBuiltInTools(model, assistant),',
            '            thinkingBudget = assistant.thinkingBudget,'
        )
        for ($j = 0; $j -lt $expected.Count; $j++) {
            if ($lines[$i + 1 + $j].Trim() -ne $expected[$j].Trim()) {
                $match = $false
                break
            }
        }
        
        if ($match) {
            Write-Host "Found match at line $($i+1)"
            $newLines.Add('        // Override with per-model local inference params if available')
            $newLines.Add('        val localOverride = if (model.providerId == me.rerere.rikkahub.data.ai.local.LOCAL_PROVIDER_ID) {')
            $newLines.Add('            settings.localModelParams[model.modelId]')
            $newLines.Add('        } else null')
            $newLines.Add('        val params = TextGenerationParams(')
            $newLines.Add('            model = model,')
            $newLines.Add('            temperature = localOverride?.temperature ?: assistant.temperature,')
            $newLines.Add('            topP = localOverride?.topP ?: assistant.topP,')
            $newLines.Add('            maxTokens = localOverride?.maxTokens ?: assistant.maxTokens,')
            $newLines.Add('            tools = tools,')
            $newLines.Add('            builtInTools = resolveActiveBuiltInTools(model, assistant),')
            $newLines.Add('            thinkingBudget = localOverride?.thinkingBudget ?: assistant.thinkingBudget,')
            $skip = $expected.Count  # skip the original lines
            continue
        }
    }
    
    $newLines.Add($lines[$i])
}

[System.IO.File]::WriteAllLines($file, $newLines)
Write-Host "Done. Total lines: $($newLines.Count) (was $($lines.Count))"
