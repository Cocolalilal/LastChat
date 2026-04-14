const fs = require('fs');
const path = 'c:/Users/julia/Documents/Github/LastChat_dev/web-ui/app/routes/conversations.tsx';
let txt = fs.readFileSync(path, 'utf8');

txt = txt.replace('  const { detail, detailLoading, detailError, selectedNodeMessages, resetDetail } =\n    useConversationDetail(activeId, updateConversationSummary);',
  '  const { detail, detailLoading, detailError, selectedNodeMessages: actualSelectedNodeMessages, resetDetail } =\n    useConversationDetail(activeId, updateConversationSummary);\n\n  const selectedNodeMessages = React.useMemo(() => {\n    if (activeId || !settings || !currentAssistantId) return actualSelectedNodeMessages;\n    const assistant = settings.assistants.find((a) => a.id === currentAssistantId);\n    const presets = assistant?.presetMessages;\n    if (!presets || presets.length === 0) return actualSelectedNodeMessages;\n    return presets.map((msg, index) => ({\n      node: { id: `preset-${index}`, messages: [msg], selectIndex: 0 } as any,\n      message: msg,\n    }));\n  }, [activeId, settings, currentAssistantId, actualSelectedNodeMessages]);'
);

txt = txt.replace('{!isNewChat && (\n        <div className="relative flex min-h-0 flex-1">\n          <ConversationTimeline',
  '{(!isNewChat || selectedNodeMessages.length > 0) && (\n        <div className="relative flex min-h-0 flex-1">\n          <ConversationTimeline'
);

txt = txt.replace('{isNewChat && (\n          <div className="mx-auto mb-6 max-w-2xl text-center">\n            <p className="text-lg text-muted-foreground">\n              <ConversationGreeting />',
  '{isNewChat && selectedNodeMessages.length === 0 && (\n          <div className="mx-auto mb-6 max-w-2xl text-center">\n            <p className="text-lg text-muted-foreground">\n              <ConversationGreeting />'
);

fs.writeFileSync(path, txt);
console.log('Script ran successfully.');
