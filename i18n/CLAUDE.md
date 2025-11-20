# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RikkaHub I18n is an AI-powered translation manager for Android string resources with an interactive Terminal UI (TUI). This tool manages translations for the main RikkaHub Android application, which is a native LLM chat client supporting multiple AI providers.

## Architecture Overview

### Core Components

- **Config System** (`src/config.ts`): YAML-based configuration loader for targets, modules, and AI providers
- **XML Parser** (`src/xml-parser.ts`): Android string resource parser and generator with merge capabilities
- **Translator** (`src/translator.ts`): AI-powered translation service with Google Gemini and OpenAI support
- **Module Loader** (`src/module-loader.ts`): Multi-module Android project scanner and statistics calculator
- **TUI Components** (`src/tui/`): Interactive terminal interface built with React and Ink

### Key Technologies

- **TypeScript**: Primary language with strict configuration
- **React + Ink**: Terminal UI framework for interactive components
- **Vercel AI SDK**: Unified AI provider interface (@ai-sdk/google, @ai-sdk/openai)
- **xml2js**: XML parsing and building for Android string resources
- **YAML**: Configuration file format

## Development Commands

### Development
```bash
# Start the interactive TUI (recommended for development)
npm run dev
# or
bun run dev
```

### Build and Production
```bash
# Compile TypeScript to dist/
npm run build

# Run compiled version
npm start
```

## Configuration

### Environment Setup
Create `.env` file with AI provider API keys:
```env
# For Google Gemini (default)
GOOGLE_GENERATIVE_AI_API_KEY=your_gemini_api_key_here

# For OpenAI 
OPENAI_API_KEY=your_openai_api_key_here
```

### Project Configuration (`config.yml`)
```yaml
# Target languages (Android resource directory suffixes)
targets:
  - zh          # Simplified Chinese
  - ja          # Japanese
  - zh-rTW      # Traditional Chinese

# Workspace root relative to i18n directory
workspaceRoot: ".."

# Android modules to scan for string resources
modules:
  - app
  - ai
  - highlight
  - search
  - rag

# AI provider configuration
provider:
  type: google        # "google" or "openai"
  model: gemini-2.5-flash
```

## File Structure and Paths

### Android String Resource Paths
- Default strings: `{modulePath}/src/main/res/values/strings.xml`
- Localized strings: `{modulePath}/src/main/res/values-{locale}/strings.xml`

### Key Files
- `config.yml`: Main configuration
- `logs.txt`: Translation process logs (auto-generated)
- `package.json`: Dependencies and scripts
- `tsconfig.json`: TypeScript configuration with strict settings

## Translation Process

### Workflow
1. Scans configured Android modules for `strings.xml` files
2. Compares default strings with existing translations
3. Calculates completion statistics per module/language
4. Uses AI to translate missing entries with context awareness
5. Preserves Android formatting (`%1$d`, `%1$s`, `\\n`, `\\'`)
6. Saves translations to appropriate `values-{locale}/strings.xml` files

### TUI Navigation
- **Module Selection**: ↑↓ navigate, Enter select, shows completion progress
- **Language Selection**: ↑↓ navigate, Enter select, shows translation statistics  
- **Translation Table**: ↑↓ navigate, `e` edit, `t` translate all, `f` filter missing, `q` back
- **Edit Mode**: Type to edit, Enter save, Esc cancel

### AI Translation Features
- Context-aware translations with module and key information
- Automatic rate limiting with 100ms delays
- Error handling with fallback to original text
- Comprehensive logging to `logs.txt`
- Support for Android string formatting preservation

## Supported Languages

Current target languages with full language names for AI context:
- `zh`: Simplified Chinese (简体中文)
- `zh-rTW`: Traditional Chinese (繁體中文) 
- `ja`: Japanese (日本語)
- Additional languages can be added to `LANGUAGE_NAMES` mapping

## Development Guidelines

### Code Style
- Follows strict TypeScript configuration
- Uses ESNext modules with bundler resolution
- Implements proper error handling and logging
- React functional components with hooks

### Adding New AI Providers
Extend `getModel()` function in `translator.ts` and add configuration options to support additional Vercel AI SDK providers.

### Module Detection
The tool automatically scans for Android modules containing `src/main/res/values/strings.xml` files. Non-existent modules are skipped with warnings.

### Error Handling
- File permission issues: Check write access to Android module directories
- API rate limits: Built-in delays and retry logic
- Missing translations: Filter functionality to focus on incomplete items
- API key issues: Verify `.env` configuration and quota