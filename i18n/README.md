# RikkaHub I18n Translation Manager

AI-powered translation manager for Android string resources with interactive TUI.

## Features

- 🌐 Support for multiple target languages (Chinese, Japanese, Traditional Chinese)
- 🤖 AI-powered translations using Vercel AI SDK (Google Gemini, OpenAI)
- 📊 Translation progress tracking with completion percentages
- 🔍 Search and filter capabilities
- ✏️ Interactive editing of translations
- 📁 Multi-module Android project support
- 💾 Automatic XML file generation and updates

## Setup

1. **Install dependencies**:
   ```bash
   npm install
   # or
   bun install
   ```

2. **Configure environment variables**:
   Copy `.env.example` to `.env` and add your API keys:
   ```bash
   cp .env.example .env
   ```
   
   Edit `.env` with your API key:
   ```env
   # For Google Gemini (default)
   GOOGLE_GENERATIVE_AI_API_KEY=your_gemini_api_key_here
   
   # For OpenAI
   OPENAI_API_KEY=your_openai_api_key_here
   ```

3. **Configure targets**:
   Edit `config.yml` to set your target languages and modules:
   ```yaml
   targets:
     - zh           # Simplified Chinese
     - ja           # Japanese  
     - zh-rTW       # Traditional Chinese
   
   modules:
     - app          # Main app module
     - search       # Search module
     # Add other modules as needed
   
   provider:
     type: google   # or "openai"
     model: gemini-2.5-flash
   ```

## Usage

Run the translation manager:

```bash
npm run dev
```

### Navigation

**Module Selection:**
- ↑↓: Navigate between modules
- Enter: Select module
- Shows completion progress for each module

**Language Selection:**
- ↑↓: Navigate between target languages  
- Enter: Select language
- Shows translation statistics

**Translation Table:**
- ↑↓: Navigate between translation entries
- `e`: Edit selected translation
- `t`: Translate all missing entries with AI
- `f`: Toggle filter to show only missing translations
- `q`: Go back to language selection
- Ctrl+C: Exit application

**Edit Mode:**
- Type to edit translation
- Enter: Save changes
- Esc: Cancel edit

### Translation Status Icons

- ✅ Translated
- ❌ Missing translation
- 🚫 Not translatable (marked with translatable="false")

## File Structure

```
i18n/
├── src/
│   ├── config.ts              # Configuration loader
│   ├── xml-parser.ts          # Android XML string parser
│   ├── translator.ts          # AI translation service
│   ├── module-loader.ts       # Module data loader
│   └── tui/                   # Terminal UI components
│       ├── App.tsx            # Main app component
│       ├── ModuleSelector.tsx # Module selection screen
│       └── TranslationTable.tsx # Translation management screen
├── config.yml                 # Configuration file
├── .env.example               # Environment variables template
└── package.json               # Dependencies and scripts
```

## Supported AI Providers

- **Google Gemini** (recommended): `gemini-2.5-flash`, `gemini-1.5-pro`
- **OpenAI**: `gpt-4`, `gpt-3.5-turbo`, `gpt-4-turbo`

## Translation Process

1. The tool scans all configured modules for `strings.xml` files
2. Compares default strings with existing translations
3. Uses AI to translate missing entries with context awareness
4. Preserves Android formatting (e.g., `%1$d`, `%1$s`, `\\n`, `\\'`)
5. Saves translations to appropriate `values-{locale}/strings.xml` files

## Troubleshooting

**API Rate Limits**: The tool includes automatic delays between translations to avoid rate limits.

**Missing Translations**: Use the `f` key to filter and show only missing translations.

**File Permissions**: Ensure write permissions for the Android module directories.

**API Key Issues**: Verify your API key is correctly set in the `.env` file and has sufficient quota.