const fs = require('fs');
const path = require('path');

// Manual overrides for Lucide -> Material Symbols names
const ICON_MAP = {
  Search: "search",
  Check: "check",
  Laptop: "computer",
  Languages: "translate",
  Moon: "dark_mode",
  MoreHorizontal: "more_horiz",
  MoveRight: "arrow_forward",
  Palette: "palette",
  ArrowUp: "arrow_upward",
  Pencil: "edit",
  Pin: "keep",
  PinOff: "keep_off",
  Plus: "add",
  RefreshCw: "refresh",
  LogOut: "logout",
  Sun: "light_mode",
  Trash2: "delete",
  ArrowDownIcon: "arrow_downward",
  DownloadIcon: "download",
  AudioFile: "audio_file",
  ChevronLeft: "chevron_left",
  ChevronRight: "chevron_right",
  File: "description",
  FileDown: "download",
  FlashOn: "flash_on",
  FolderOpen: "folder_open",
  Image: "image",
  LoaderCircle: "progress_activity",
  Square: "stop",
  Video: "video_library",
  X: "close",
  BookOpen: "book",
  Terminal: "terminal",
  ChevronDown: "keyboard_arrow_down",
  Heart: "favorite",
  Lightbulb: "lightbulb",
  LightbulbCircle: "lightbulb_circle",
  Sparkles: "stars",
  Earth: "public",
  ChevronUp: "keyboard_arrow_up",
  Copy: "content_copy",
  Download: "download",
  Build: "build",
  Category: "category",
  Globe: "public",
  Memory: "memory",
  ExternalLink: "open_in_new",
  ArrowDown: "arrow_downward",
  Clock3: "schedule",
  Ellipsis: "more_horiz",
  GitFork: "fork_left",
  Zap: "bolt",
  AudioLines: "mic",
  VolumeX: "volume_off",
  FileText: "description",
  ImageOff: "broken_image",
  Brain: "psychology",
  Loader2: "progress_activity",
  VideoOff: "videocam_off",
  CheckIcon: "check",
  ChevronRightIcon: "chevron_right",
  CircleIcon: "circle",
  XIcon: "close",
  GripVerticalIcon: "drag_indicator",
  ChevronDownIcon: "keyboard_arrow_down",
  ChevronUpIcon: "keyboard_arrow_up",
  PanelLeftIcon: "left_panel_open",
  CircleCheckIcon: "check_circle",
  InfoIcon: "info",
  Loader2Icon: "progress_activity",
  OctagonXIcon: "error",
  TriangleAlertIcon: "warning",
  BookHeart: "favorite", // approximate
  BookX: "bookmark_remove",
  Clipboard: "content_paste",
  ClipboardPaste: "content_paste_go",
  MessageCircleQuestion: "live_help",
  Wrench: "build",
  MessageSquare: "chat_bubble"
};

function camelToSnake(str) {
  return str.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`).replace(/^_/, '');
}

const MATERIAL_LIB_PATH = path.join(__dirname, 'app/lib/material-icons.tsx');
let materialContent = fs.readFileSync(MATERIAL_LIB_PATH, 'utf8');

// Also inject ForkLeft since we needed it for Phase 1!
if (!ICON_MAP.ForkLeft) {
  ICON_MAP.ForkLeft = "fork_left";
}

// Track what needs to be added
const newImports = [];
const newExports = [];

for (const [lucideName, matName] of Object.entries(ICON_MAP)) {
  const compName = `${matName.replace(/_(.)/g, (_, c) => c.toUpperCase()).replace(/^(.)/, c => c.toUpperCase())}Svg`;
  
  if (!materialContent.includes(`export const ${lucideName} =`)) {
    // If not exported under its Lucide name, we add it!
    if (!materialContent.includes(`import ${compName} from`)) {
        newImports.push(`import ${compName} from "@material-symbols/svg-400/rounded/${matName}.svg?react";`);
    }
    newExports.push(`export const ${lucideName} = createIcon(${compName});`);
  }
}

const lines = materialContent.split('\n');
const importIdx = lines.findIndex(l => l.includes('import ') && l.includes('svg?react'));

lines.splice(importIdx + 1, 0, ...newImports);
const resultLib = lines.join('\n') + "\n" + newExports.join('\n') + "\n";
fs.writeFileSync(MATERIAL_LIB_PATH, resultLib);

// Now walk app
function walk(dir, fileList = []) {
  fs.readdirSync(dir).forEach(file => {
    const p = path.join(dir, file);
    if (fs.statSync(p).isDirectory()) fileList = walk(p, fileList);
    else if (p.endsWith('.tsx') || p.endsWith('.ts')) fileList.push(p);
  });
  return fileList;
}

const files = walk('C:/Users/julia/Documents/Github/LastChat_dev/web-ui/app');
files.forEach(f => {
  let content = fs.readFileSync(f, 'utf8');
  if (content.includes('from "lucide-react"') || content.includes("from 'lucide-react'")) {
    // Collect the imports
    let mod = content.replace(/import\s+\{([^}]+)\}\s+from\s+['"]lucide-react['"];?/g, (match, inner) => {
      // Find what needs to be imported
      return `import { ${inner.trim()} } from "~/lib/material-icons";`;
    });
    fs.writeFileSync(f, mod);
    console.log(`Updated ${f}`);
  }
});
