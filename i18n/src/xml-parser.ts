import { parseString, Builder } from 'xml2js';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import path from 'path';

export interface StringResource {
  key: string;
  value: string;
  translatable?: boolean;
}

export interface ModuleStrings {
  moduleName: string;
  modulePath: string;
  defaultStrings: StringResource[];
  translations: Record<string, StringResource[]>;
}

export async function parseXmlFile(filePath: string): Promise<StringResource[]> {
  if (!existsSync(filePath)) {
    return [];
  }

  const content = readFileSync(filePath, 'utf-8');
  
  return new Promise((resolve, reject) => {
    parseString(content, (err, result) => {
      if (err) {
        reject(err);
        return;
      }

      const resources: StringResource[] = [];
      
      if (result.resources && result.resources.string) {
        for (const stringItem of result.resources.string) {
          const key = stringItem.$.name;
          const value = typeof stringItem._ === 'string' ? stringItem._ : stringItem;
          const translatable = stringItem.$.translatable !== 'false';
          
          resources.push({
            key,
            value: typeof value === 'string' ? value : String(value),
            translatable
          });
        }
      }

      resolve(resources);
    });
  });
}

export async function writeXmlFile(filePath: string, resources: StringResource[]): Promise<void> {
  const xmlData = {
    resources: {
      string: resources.map(resource => ({
        $: { 
          name: resource.key,
          ...(resource.translatable === false ? { translatable: 'false' } : {})
        },
        _: resource.value
      }))
    }
  };

  const builder = new Builder({
    xmldec: { version: '1.0', encoding: 'UTF-8' },
    renderOpts: { pretty: true, indent: '  ' }
  });
  
  const xml = builder.buildObject(xmlData);
  
  // Ensure directory exists
  const dir = path.dirname(filePath);
  if (!existsSync(dir)) {
    const { mkdirSync } = await import('fs');
    mkdirSync(dir, { recursive: true });
  }
  
  writeFileSync(filePath, xml);
}

export function findMissingTranslations(
  defaultStrings: StringResource[], 
  translatedStrings: StringResource[]
): StringResource[] {
  const translatedKeys = new Set(translatedStrings.map(s => s.key));
  
  return defaultStrings.filter(defaultString => 
    defaultString.translatable !== false && 
    !translatedKeys.has(defaultString.key)
  );
}

export function mergeTranslations(
  existingStrings: StringResource[],
  newTranslations: StringResource[]
): StringResource[] {
  const existingMap = new Map(existingStrings.map(s => [s.key, s]));
  
  // Add or update translations
  newTranslations.forEach(newTranslation => {
    existingMap.set(newTranslation.key, newTranslation);
  });
  
  return Array.from(existingMap.values()).sort((a, b) => a.key.localeCompare(b.key));
}