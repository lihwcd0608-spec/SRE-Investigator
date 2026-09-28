import type { ReactNode, SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Svg({ size = 18, children, ...props }: IconProps & { children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  );
}

export function IconPulse(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M22 12h-4l-3 8L9 4l-3 8H2" />
    </Svg>
  );
}

export function IconSquares(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="3" y="3" width="7" height="7" rx="1.2" />
      <rect x="14" y="3" width="7" height="7" rx="1.2" />
      <rect x="3" y="14" width="7" height="7" rx="1.2" />
      <rect x="14" y="14" width="7" height="7" rx="1.2" />
    </Svg>
  );
}

export function IconGitBranch(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="6" cy="5" r="2.2" />
      <circle cx="6" cy="19" r="2.2" />
      <circle cx="18" cy="12" r="2.2" />
      <path d="M6 7.2v9.6" />
      <path d="M8.1 18.1c3.2 0 5.9-2.5 7.8-5.1" />
    </Svg>
  );
}

export function IconPlugs(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8.5 8.5 6 11a4.2 4.2 0 0 0 7 7l2.5-2.5" />
      <path d="M15.5 15.5 18 13a4.2 4.2 0 0 0-7-7l-2.5 2.5" />
      <path d="m7 7-3-3" />
      <path d="m17 17 3 3" />
    </Svg>
  );
}

export function IconChat(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M7 18.5 4 21V7.5A2.5 2.5 0 0 1 6.5 5h11A2.5 2.5 0 0 1 20 7.5v8A2.5 2.5 0 0 1 17.5 18H7Z" />
      <path d="M8 10h8" />
      <path d="M8 13.5h5" />
    </Svg>
  );
}

export function IconCpu(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="6" y="6" width="12" height="12" rx="2" />
      <rect x="9.5" y="9.5" width="5" height="5" rx="0.8" />
      <path d="M9 3v3M15 3v3M9 18v3M15 18v3M3 9h3M3 15h3M18 9h3M18 15h3" />
    </Svg>
  );
}

export function IconPencil(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12.5 6.5 17.5 11.5" />
      <path d="M4.5 19.5 7 13.5 16.5 4a1.8 1.8 0 0 1 2.5 2.5L9.5 16.5 4.5 19.5Z" />
    </Svg>
  );
}

export function IconTrash(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M5 7h14" />
      <path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7" />
      <path d="M8 7v12a1.5 1.5 0 0 0 1.5 1.5h5A1.5 1.5 0 0 0 16 19V7" />
      <path d="M10.5 11v5M13.5 11v5" />
    </Svg>
  );
}

export function IconPlus(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 5v14M5 12h14" />
    </Svg>
  );
}

export function IconPaperclip(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m8.5 13.5 6.2-6.2a2.6 2.6 0 1 1 3.7 3.7L9.4 20a4.2 4.2 0 0 1-5.9-5.9l9.1-9.1" />
    </Svg>
  );
}

export function IconSend(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m5 12 14-7-4 16-3.5-6.5L5 12Z" />
      <path d="m11.5 14.5 3-9" />
    </Svg>
  );
}

export function IconColumns(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="3.5" y="4" width="7" height="16" rx="1.2" />
      <rect x="13.5" y="4" width="7" height="16" rx="1.2" />
    </Svg>
  );
}

export function IconRows(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </Svg>
  );
}

export function IconChevronUp(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m6 14 6-6 6 6" />
    </Svg>
  );
}

export function IconChevronDown(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m6 10 6 6 6-6" />
    </Svg>
  );
}

export function IconSearch(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </Svg>
  );
}

export function IconArrowLeft(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M19 12H5M11 18l-6-6 6-6" />
    </Svg>
  );
}

export function IconArrowRight(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </Svg>
  );
}

export function IconMenu(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </Svg>
  );
}

export function IconX(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m6 6 12 12M18 6 6 18" />
    </Svg>
  );
}

export function IconRefresh(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
      <path d="M21 3v5h-5" />
      <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
      <path d="M3 21v-5h5" />
    </Svg>
  );
}

export function IconFolder(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3.5 7.5A1.5 1.5 0 0 1 5 6h4.2l1.8 2H19a1.5 1.5 0 0 1 1.5 1.5V18A1.5 1.5 0 0 1 19 19.5H5A1.5 1.5 0 0 1 3.5 18Z" />
    </Svg>
  );
}

export function IconDatabase(props: IconProps) {
  return (
    <Svg {...props}>
      <ellipse cx="12" cy="6.5" rx="7" ry="2.5" />
      <path d="M5 6.5v11c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-11" />
      <path d="M5 12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5" />
    </Svg>
  );
}

export function IconLayers(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m12 4 8 4-8 4-8-4 8-4Z" />
      <path d="m4 12 8 4 8-4" />
      <path d="m4 16 8 4 8-4" />
    </Svg>
  );
}

export function IconLeaf(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M5 19c8-1 14-8 14-16-8 0-15 6-16 14 3-1 6-.3 8 1-3 .6-5.2 1-6 1Z" />
      <path d="M9 15c2-4 6-8 10-10" />
    </Svg>
  );
}

export function IconCheck(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.5 12.2 2.4 2.4 4.6-5.2" />
    </Svg>
  );
}

export function IconWarning(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 4 3.6 19h16.8L12 4Z" />
      <path d="M12 10v4" />
      <path d="M12 16.5h.01" />
    </Svg>
  );
}

export function IconShield(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 3 5 6.2v5.4c0 4.1 2.8 7.1 7 8.4 4.2-1.3 7-4.3 7-8.4V6.2L12 3Z" />
      <path d="m9 12 2 2 4-4.5" />
    </Svg>
  );
}

export function IconBook(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M5 5.5A1.5 1.5 0 0 1 6.5 4H18v14.5H6.5A1.5 1.5 0 0 0 5 20V5.5Z" />
      <path d="M5 19.5A1.5 1.5 0 0 1 6.5 18H18" />
    </Svg>
  );
}

export function IconScale(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 4v16" />
      <path d="M5 8h14" />
      <path d="M7 8 4.5 14h5L7 8Z" />
      <path d="m17 8 2.5 6h-5L17 8Z" />
    </Svg>
  );
}

export function IconUsers(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="9" cy="8" r="3" />
      <path d="M3.5 18.5c.6-3 2.8-4.5 5.5-4.5s4.9 1.5 5.5 4.5" />
      <circle cx="17" cy="8.5" r="2.3" />
      <path d="M16 14c2.2.3 3.8 1.6 4.4 4" />
    </Svg>
  );
}

export function IconKey(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="8" cy="12" r="3.2" />
      <path d="M11 12h9l-2 2 2 2" />
    </Svg>
  );
}

export function IconLogout(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M10 7V5.5A1.5 1.5 0 0 1 11.5 4H18a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 18 20h-6.5A1.5 1.5 0 0 1 10 18.5V17" />
      <path d="M4 12h10M11 8.5 14.5 12 11 15.5" />
    </Svg>
  );
}

export function IconCompass(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m16.2 7.8-2.1 6.3-6.3 2.1 2.1-6.3Z" />
    </Svg>
  );
}

export function IconScroll(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8 4h9.5A1.5 1.5 0 0 1 19 5.5V18" />
      <path d="M8 4a2 2 0 0 0-2 2v12.5A1.5 1.5 0 0 0 7.5 20H18" />
      <path d="M10 8h6M10 12h6M10 16h3" />
    </Svg>
  );
}

export function IconShare(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="6.5" cy="12" r="2.2" />
      <circle cx="17.5" cy="6.5" r="2.2" />
      <circle cx="17.5" cy="17.5" r="2.2" />
      <path d="m8.4 10.8 5.6-3.2M8.4 13.2l5.6 3.2" />
    </Svg>
  );
}

export function IconBot(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="6" y="8" width="12" height="10" rx="2.2" />
      <path d="M12 4v4" />
      <circle cx="12" cy="4.2" r="1.1" />
      <circle cx="9.5" cy="12.2" r="1" fill="currentColor" stroke="none" />
      <circle cx="14.5" cy="12.2" r="1" fill="currentColor" stroke="none" />
      <path d="M9 16h6" />
      <path d="M6 13H4.5A1.5 1.5 0 0 1 3 11.5v0A1.5 1.5 0 0 1 4.5 10H6" />
      <path d="M18 13h1.5A1.5 1.5 0 0 0 21 11.5v0A1.5 1.5 0 0 0 19.5 10H18" />
    </Svg>
  );
}

export function IconSparkle(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 4v4M12 16v4M4 12h4M16 12h4M7.2 7.2l2.4 2.4M14.4 14.4l2.4 2.4M16.8 7.2l-2.4 2.4M9.6 14.4l-2.4 2.4" />
    </Svg>
  );
}

export function IconSun(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.1 5.1l1.6 1.6M17.3 17.3l1.6 1.6M17.3 5.1l1.6-1.6M5.1 18.9l1.6-1.6" />
    </Svg>
  );
}

export function IconMoon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M20 14.2A7.4 7.4 0 0 1 9.8 4 7.8 7.8 0 1 0 20 14.2Z" />
    </Svg>
  );
}

export function IconSlash(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m16 4-8 16" />
    </Svg>
  );
}

export function IconDownload(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 3v12" />
      <path d="m7 11 5 5 5-5" />
      <path d="M5 21h14" />
    </Svg>
  );
}

export function IconEye(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
    </Svg>
  );
}

export function IconEyeOff(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 3l18 18" />
      <path d="M10.6 10.6a2 2 0 1 0 2.8 2.8" />
      <path d="M9.9 5.2A11 11 0 0 1 12 5c6.4 0 10 7 10 7a18 18 0 0 1-3.3 3.9" />
      <path d="M6.1 6.1C3.8 7.7 2 12 2 12s3.6 7 10 7c1.6 0 3-.3 4.3-.9" />
    </Svg>
  );
}

export function IconFeishu(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M5 8.5 12 4l7 4.5v7L12 20l-7-4.5v-7Z" />
      <path d="M12 4v16" />
      <path d="M5 8.5h14" />
    </Svg>
  );
}
