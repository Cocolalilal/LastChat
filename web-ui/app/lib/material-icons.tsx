import * as React from "react";

import AddSvg from "@material-symbols/svg-400/rounded/add.svg?react";
import ForkLeftSvg from "@material-symbols/svg-400/rounded/fork_left.svg?react";
import ArrowDownwardSvg from "@material-symbols/svg-400/rounded/arrow_downward.svg?react";
import ArrowUpwardSvg from "@material-symbols/svg-400/rounded/arrow_upward.svg?react";
import AudioFileSvg from "@material-symbols/svg-400/rounded/audio_file.svg?react";
import BookSvg from "@material-symbols/svg-400/rounded/book.svg?react";
import BookmarkSvg from "@material-symbols/svg-400/rounded/bookmark.svg?react";
import BookmarkRemoveSvg from "@material-symbols/svg-400/rounded/bookmark_remove.svg?react";
import BoltSvg from "@material-symbols/svg-400/rounded/bolt.svg?react";
import BrokenImageSvg from "@material-symbols/svg-400/rounded/broken_image.svg?react";
import BuildSvg from "@material-symbols/svg-400/rounded/build.svg?react";
import CategorySvg from "@material-symbols/svg-400/rounded/category.svg?react";
import ChatBubbleSvg from "@material-symbols/svg-400/rounded/chat_bubble.svg?react";
import CheckCircleSvg from "@material-symbols/svg-400/rounded/check_circle.svg?react";
import CheckSvg from "@material-symbols/svg-400/rounded/check.svg?react";
import ChevronLeftSvg from "@material-symbols/svg-400/rounded/chevron_left.svg?react";
import ChevronRightSvg from "@material-symbols/svg-400/rounded/chevron_right.svg?react";
import CircleSvg from "@material-symbols/svg-400/rounded/circle.svg?react";
import CloseSvg from "@material-symbols/svg-400/rounded/close.svg?react";
import ComputerSvg from "@material-symbols/svg-400/rounded/computer.svg?react";
import ContentCopySvg from "@material-symbols/svg-400/rounded/content_copy.svg?react";
import ContentPasteGoSvg from "@material-symbols/svg-400/rounded/content_paste_go.svg?react";
import ContentPasteSvg from "@material-symbols/svg-400/rounded/content_paste.svg?react";
import DarkModeSvg from "@material-symbols/svg-400/rounded/dark_mode.svg?react";
import DeleteSvg from "@material-symbols/svg-400/rounded/delete.svg?react";
import DescriptionSvg from "@material-symbols/svg-400/rounded/description.svg?react";
import DownloadSvg from "@material-symbols/svg-400/rounded/download.svg?react";
import DragIndicatorSvg from "@material-symbols/svg-400/rounded/drag_indicator.svg?react";
import EditSvg from "@material-symbols/svg-400/rounded/edit.svg?react";
import ErrorSvg from "@material-symbols/svg-400/rounded/error.svg?react";
import FavoriteFillSvg from "@material-symbols/svg-400/rounded/favorite-fill.svg?react";
import FavoriteSvg from "@material-symbols/svg-400/rounded/favorite.svg?react";
import FlashOnSvg from "@material-symbols/svg-400/rounded/flash_on.svg?react";
import FolderOpenSvg from "@material-symbols/svg-400/rounded/folder_open.svg?react";
import InfoSvg from "@material-symbols/svg-400/rounded/info.svg?react";
import ImageSvg from "@material-symbols/svg-400/rounded/image.svg?react";
import KeepOffSvg from "@material-symbols/svg-400/rounded/keep_off.svg?react";
import KeepSvg from "@material-symbols/svg-400/rounded/keep.svg?react";
import KeyboardArrowDownSvg from "@material-symbols/svg-400/rounded/keyboard_arrow_down.svg?react";
import KeyboardArrowUpSvg from "@material-symbols/svg-400/rounded/keyboard_arrow_up.svg?react";
import LeftPanelOpenSvg from "@material-symbols/svg-400/rounded/left_panel_open.svg?react";
import LightModeSvg from "@material-symbols/svg-400/rounded/light_mode.svg?react";
import LightOffSvg from "@material-symbols/svg-400/rounded/light_off.svg?react";
import LightbulbCircleSvg from "@material-symbols/svg-400/rounded/lightbulb_circle.svg?react";
import LightbulbSvg from "@material-symbols/svg-400/rounded/lightbulb.svg?react";
import LiveHelpSvg from "@material-symbols/svg-400/rounded/live_help.svg?react";
import LogoutSvg from "@material-symbols/svg-400/rounded/logout.svg?react";
import MemorySvg from "@material-symbols/svg-400/rounded/memory.svg?react";
import MicSvg from "@material-symbols/svg-400/rounded/mic.svg?react";
import MoreHorizSvg from "@material-symbols/svg-400/rounded/more_horiz.svg?react";
import MoveItemSvg from "@material-symbols/svg-400/rounded/move_item.svg?react";
import OpenInNewSvg from "@material-symbols/svg-400/rounded/open_in_new.svg?react";
import PaletteSvg from "@material-symbols/svg-400/rounded/palette.svg?react";
import ProgressActivitySvg from "@material-symbols/svg-400/rounded/progress_activity.svg?react";
import PublicSvg from "@material-symbols/svg-400/rounded/public.svg?react";
import RefreshSvg from "@material-symbols/svg-400/rounded/refresh.svg?react";
import ScheduleSvg from "@material-symbols/svg-400/rounded/schedule.svg?react";
import SearchSvg from "@material-symbols/svg-400/rounded/search.svg?react";
import SendSvg from "@material-symbols/svg-400/rounded/send.svg?react";
import StarsSvg from "@material-symbols/svg-400/rounded/stars.svg?react";
import StopSvg from "@material-symbols/svg-400/rounded/stop.svg?react";
import TerminalSvg from "@material-symbols/svg-400/rounded/terminal.svg?react";
import TranslateSvg from "@material-symbols/svg-400/rounded/translate.svg?react";
import VideoLibrarySvg from "@material-symbols/svg-400/rounded/video_library.svg?react";
import VideocamOffSvg from "@material-symbols/svg-400/rounded/videocam_off.svg?react";
import VolumeOffSvg from "@material-symbols/svg-400/rounded/volume_off.svg?react";
import WarningSvg from "@material-symbols/svg-400/rounded/warning.svg?react";
import CallSplitSvg from "@material-symbols/svg-400/rounded/call_split.svg?react";

type SvgIconComponent = React.ComponentType<React.SVGProps<SVGSVGElement>>;

export type LucideProps = Omit<React.SVGProps<SVGSVGElement>, "ref"> & {
  size?: string | number;
  absoluteStrokeWidth?: boolean;
};

function renderIcon(
  Component: SvgIconComponent,
  {
    size,
    absoluteStrokeWidth: _absoluteStrokeWidth,
    height,
    width,
    fill,
    ...props
  }: LucideProps,
) {
  return (
    <Component
      height={size ?? height}
      width={size ?? width}
      fill={fill ?? "currentColor"}
      {...props}
    />
  );
}

function createIcon(Component: SvgIconComponent) {
  const Icon = (props: LucideProps) => renderIcon(Component, props);
  return Icon;
}

function createFilledIcon(OutlineComponent: SvgIconComponent, FilledComponent: SvgIconComponent) {
  const Icon = ({ className, ...props }: LucideProps) =>
    renderIcon(
      /\bfill-current\b/.test(className ?? "") ? FilledComponent : OutlineComponent,
      { className, ...props },
    );
  return Icon;
}

export const Add = createIcon(AddSvg);
export const ArrowDown = createIcon(ArrowDownwardSvg);
export const ArrowDownIcon = ArrowDown;
export const ArrowDownward = ArrowDown;
export const ArrowUp = createIcon(ArrowUpwardSvg);
export const ArrowUpward = ArrowUp;
export const AudioFile = createIcon(AudioFileSvg);
export const AudioLines = AudioFile;
export const Book = createIcon(BookSvg);
export const BookHeart = createIcon(BookmarkSvg);
export const BookOpen = Book;
export const BookX = createIcon(BookmarkRemoveSvg);
export const Bookmark = createIcon(BookmarkSvg);
export const BookmarkRemove = createIcon(BookmarkRemoveSvg);
export const Bolt = createIcon(BoltSvg);
export const Brain = createIcon(LightbulbSvg);
export const Build = createIcon(BuildSvg);
export const Category = createIcon(CategorySvg);
export const ChatBubble = createIcon(ChatBubbleSvg);
export const Check = createIcon(CheckSvg);
export const CheckCircle = createIcon(CheckCircleSvg);
export const CheckIcon = Check;
export const ChevronDown = createIcon(KeyboardArrowDownSvg);
export const ChevronDownIcon = ChevronDown;
export const ChevronLeft = createIcon(ChevronLeftSvg);
export const ChevronRight = createIcon(ChevronRightSvg);
export const ChevronRightIcon = ChevronRight;
export const ChevronUp = createIcon(KeyboardArrowUpSvg);
export const ChevronUpIcon = ChevronUp;
export const CircleCheckIcon = createIcon(CheckCircleSvg);
export const CircleIcon = createIcon(CircleSvg);
export const Clipboard = createIcon(ContentPasteSvg);
export const ClipboardPaste = createIcon(ContentPasteGoSvg);
export const Clock3 = createIcon(ScheduleSvg);
export const Close = createIcon(CloseSvg);
export const Code2 = createIcon(TerminalSvg);
export const Computer = createIcon(ComputerSvg);
export const ContentCopy = createIcon(ContentCopySvg);
export const Copy = ContentCopy;
export const DarkMode = createIcon(DarkModeSvg);
export const Delete = createIcon(DeleteSvg);
export const Description = createIcon(DescriptionSvg);
export const Download = createIcon(DownloadSvg);
export const DownloadIcon = Download;
export const DragIndicator = createIcon(DragIndicatorSvg);
export const Earth = createIcon(PublicSvg);
export const Edit = createIcon(EditSvg);
export const Ellipsis = createIcon(MoreHorizSvg);
export const Error = createIcon(ErrorSvg);
export const ExternalLink = createIcon(OpenInNewSvg);
export const Favorite = createFilledIcon(FavoriteSvg, FavoriteFillSvg);
export const File = createIcon(DescriptionSvg);
export const FileDown = Download;
export const FileText = createIcon(DescriptionSvg);
export const FlashOn = createIcon(FlashOnSvg);
export const FolderOpen = createIcon(FolderOpenSvg);
export const GitFork = createIcon(CallSplitSvg);
export const Globe = createIcon(PublicSvg);
export const GripVerticalIcon = createIcon(DragIndicatorSvg);
export const Heart = createFilledIcon(FavoriteSvg, FavoriteFillSvg);
export const Image = createIcon(ImageSvg);
export const ImageOff = createIcon(BrokenImageSvg);
export const InfoIcon = createIcon(InfoSvg);
export const KeyboardArrowDown = ChevronDown;
export const KeyboardArrowUp = ChevronUp;
export const Languages = createIcon(TranslateSvg);
export const Laptop = createIcon(ComputerSvg);
export const LeftPanelOpen = createIcon(LeftPanelOpenSvg);
export const LightMode = createIcon(LightModeSvg);
export const Lightbulb = createIcon(LightbulbSvg);
export const LightbulbCircle = createIcon(LightbulbCircleSvg);
export const LightbulbOff = createIcon(LightOffSvg);
export const Loader2 = createIcon(ProgressActivitySvg);
export const Loader2Icon = Loader2;
export const LoaderCircle = Loader2;
export const LogOut = createIcon(LogoutSvg);
export const Memory = createIcon(MemorySvg);
export const MessageCircleQuestion = createIcon(LiveHelpSvg);
export const MessageSquare = createIcon(ChatBubbleSvg);
export const Mic = createIcon(MicSvg);
export const Moon = createIcon(DarkModeSvg);
export const MoreHoriz = createIcon(MoreHorizSvg);
export const MoreHorizontal = MoreHoriz;
export const MoveRight = createIcon(MoveItemSvg);
export const OctagonXIcon = createIcon(ErrorSvg);
export const PanelLeftIcon = createIcon(LeftPanelOpenSvg);
export const Palette = createIcon(PaletteSvg);
export const Pencil = createIcon(EditSvg);
export const Pin = createIcon(KeepSvg);
export const PinOff = createIcon(KeepOffSvg);
export const Plus = createIcon(AddSvg);
export const ProgressActivity = createIcon(ProgressActivitySvg);
export const Public = createIcon(PublicSvg);
export const Refresh = createIcon(RefreshSvg);
export const RefreshCw = Refresh;
export const Schedule = createIcon(ScheduleSvg);
export const Search = createIcon(SearchSvg);
export const Send = createIcon(SendSvg);
export const Sparkles = createIcon(StarsSvg);
export const Square = createIcon(StopSvg);
export const Stars = createIcon(StarsSvg);
export const Stop = createIcon(StopSvg);
export const Sun = createIcon(LightModeSvg);
export const Terminal = createIcon(TerminalSvg);
export const Translate = createIcon(TranslateSvg);
export const Trash2 = createIcon(DeleteSvg);
export const TriangleAlertIcon = createIcon(WarningSvg);
export const Video = createIcon(VideoLibrarySvg);
export const VideoOff = createIcon(VideocamOffSvg);
export const VideoLibrary = createIcon(VideoLibrarySvg);
export const VolumeX = createIcon(VolumeOffSvg);
export const Warning = createIcon(WarningSvg);
export const Wrench = createIcon(BuildSvg);
export const X = createIcon(CloseSvg);
export const XIcon = X;
export const Zap = createIcon(BoltSvg);

export const ForkLeft = createIcon(ForkLeftSvg);


