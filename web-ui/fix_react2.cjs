const fs = require('fs');
const path = 'c:/Users/julia/Documents/Github/LastChat_dev/web-ui/app/routes/conversations.tsx';
let txt = fs.readFileSync(path, 'utf8');

const regex1 = /  const \{ detail, detailLoading, detailError, selectedNodeMessages, resetDetail \} =\r?\n    useConversationDetail\(activeId, updateConversationSummary\);/;

txt = txt.replace(regex1, `  const { detail, detailLoading, detailError, selectedNodeMessages: actualSelectedNodeMessages, resetDetail } =
    useConversationDetail(activeId, updateConversationSummary);

  const selectedNodeMessages = React.useMemo(() => {
    if (activeId || !settings || !currentAssistantId) return actualSelectedNodeMessages;
    const assistant = settings.assistants.find((a) => a.id === currentAssistantId);
    const presets = assistant?.presetMessages;
    if (!presets || presets.length === 0) return actualSelectedNodeMessages;
    return presets.map((msg, index) => ({
      node: { id: \\\`preset-\${index}\\\`, messages: [msg], selectIndex: 0 } as any,
      message: msg,
    }));
  }, [activeId, settings, currentAssistantId, actualSelectedNodeMessages]);`);

fs.writeFileSync(path, txt);
console.log('Script ran successfully.');
