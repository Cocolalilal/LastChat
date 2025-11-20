import { existsSync } from 'fs';
import path from 'path';
import { I18nConfig, getModulePaths, getStringResourcePath } from './config';
import { parseXmlFile, StringResource, ModuleStrings, findMissingTranslations } from './xml-parser';

export interface TranslationStats {
  total: number;
  translated: number;
  missing: number;
  percentage: number;
}

export interface ModuleInfo extends ModuleStrings {
  stats: Record<string, TranslationStats>;
}

export async function loadModules(config: I18nConfig): Promise<ModuleInfo[]> {
  const modulePaths = getModulePaths(config);
  const modules: ModuleInfo[] = [];

  for (const modulePath of modulePaths) {
    const moduleName = path.basename(modulePath);
    const defaultStringsPath = getStringResourcePath(modulePath);
    
    if (!existsSync(defaultStringsPath)) {
      console.warn(`No strings.xml found for module: ${moduleName}`);
      continue;
    }

    try {
      const defaultStrings = await parseXmlFile(defaultStringsPath);
      const translations: Record<string, StringResource[]> = {};
      const stats: Record<string, TranslationStats> = {};

      // Load translations for each target language
      for (const locale of config.targets) {
        const translationPath = getStringResourcePath(modulePath, locale);
        const translatedStrings = existsSync(translationPath) 
          ? await parseXmlFile(translationPath)
          : [];
        
        translations[locale] = translatedStrings;
        
        // Calculate stats
        const translatableStrings = defaultStrings.filter(s => s.translatable !== false);
        const translatedKeys = new Set(translatedStrings.map(s => s.key));
        const translatedCount = translatableStrings.filter(s => translatedKeys.has(s.key)).length;
        
        stats[locale] = {
          total: translatableStrings.length,
          translated: translatedCount,
          missing: translatableStrings.length - translatedCount,
          percentage: translatableStrings.length > 0 ? (translatedCount / translatableStrings.length) * 100 : 0
        };
      }

      modules.push({
        moduleName,
        modulePath,
        defaultStrings,
        translations,
        stats
      });
    } catch (error) {
      console.error(`Error loading module ${moduleName}:`, error);
    }
  }

  return modules;
}

export function getMissingTranslations(module: ModuleInfo, locale: string): StringResource[] {
  const defaultStrings = module.defaultStrings;
  const translatedStrings = module.translations[locale] || [];
  return findMissingTranslations(defaultStrings, translatedStrings);
}

export function getAllStringsForLocale(module: ModuleInfo, locale: string): Array<{
  key: string;
  defaultValue: string;
  translatedValue?: string;
  isTranslated: boolean;
  translatable: boolean;
}> {
  const translatedMap = new Map(
    (module.translations[locale] || []).map(s => [s.key, s.value])
  );

  return module.defaultStrings.map(defaultString => ({
    key: defaultString.key,
    defaultValue: defaultString.value,
    translatedValue: translatedMap.get(defaultString.key),
    isTranslated: translatedMap.has(defaultString.key),
    translatable: defaultString.translatable !== false
  }));
}