import React, { useState, useEffect } from 'react';
import { Text, Box } from 'ink';
import { config as dotenvConfig } from 'dotenv';
import { ModuleSelector } from './ModuleSelector';
import { TranslationTable } from './TranslationTable';
import { loadModules, ModuleInfo } from '../module-loader';
import { I18nConfig } from '../config';

interface AppProps {
  config: I18nConfig;
}

export default function App({ config }: AppProps) {
  const [modules, setModules] = useState<ModuleInfo[]>([]);
  const [selectedModule, setSelectedModule] = useState<ModuleInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadModules(config)
      .then(setModules)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [config]);

  if (loading) {
    return (
      <Box>
        <Text color="yellow">🔄 Loading modules...</Text>
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <Text color="red">❌ Error: {error}</Text>
      </Box>
    );
  }

  if (modules.length === 0) {
    return (
      <Box>
        <Text color="yellow">⚠️  No modules found with string resources</Text>
      </Box>
    );
  }

  if (!selectedModule) {
    return (
      <ModuleSelector
        modules={modules}
        config={config}
        onSelect={setSelectedModule}
      />
    );
  }

  return (
    <TranslationTable
      module={selectedModule}
      config={config}
      onBack={() => setSelectedModule(null)}
      onModuleUpdate={(updatedModule) => {
        setModules(modules.map(m => 
          m.moduleName === updatedModule.moduleName ? updatedModule : m
        ));
        setSelectedModule(updatedModule);
      }}
    />
  );
}