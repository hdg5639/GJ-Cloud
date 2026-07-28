import type { ReactNode } from "react";
import type { BlueprintAction, BlueprintMetric, BlueprintRecord, BlueprintTimelineEvent, BlueprintTone } from "./types";

export type BlueprintVisualStyle =
  | "enterprise" | "glass" | "terminal" | "editorial" | "brutalist" | "soft"
  | "commerce" | "command" | "minimal" | "neon" | "paper" | "dense";

export interface MegaDashboardConfig {
  title: string;
  description: string;
  eyebrow?: string;
  style: BlueprintVisualStyle;
  primaryLabel: string;
  secondaryLabel: string;
  activityLabel?: string;
  defaultTone?: BlueprintTone;
}

export interface MegaCollectionConfig {
  title: string;
  description: string;
  style: "table" | "cards" | "board" | "timeline" | "matrix" | "inbox" | "calendar" | "gallery" | "tree" | "map";
  primaryField?: string;
  secondaryField?: string;
  statusField?: string;
  actionLabel?: string;
  emptyLabel?: string;
}

export interface MegaDetailConfig {
  title: string;
  description: string;
  style: "hero" | "split" | "casefile" | "document" | "technical" | "profile" | "commerce" | "timeline";
  statusField?: string;
  primaryFields?: string[];
  secondaryFields?: string[];
}

export interface MegaModalConfig {
  title: string;
  description: string;
  eyebrow: string;
  confirmLabel: string;
  style: "confirm" | "danger" | "form" | "picker" | "review" | "schedule" | "command" | "impact";
  requireReason?: boolean;
  requireText?: string;
  fields?: Array<{ key: string; label: string; type?: "text" | "number" | "date" | "textarea" | "select"; options?: string[]; required?: boolean }>;
}

export interface MegaWorkflowConfig {
  title: string;
  description: string;
  steps: Array<{ id: string; label: string; description: string }>;
  style: "wizard" | "timeline" | "approval" | "provision" | "checkout" | "migration" | "incident";
  completeLabel: string;
}

export interface MegaFormConfig {
  title: string;
  description: string;
  style: "sectioned" | "inline" | "builder" | "composer" | "schema" | "preferences";
  sections: Array<{ title: string; description?: string; fields: Array<{ key: string; label: string; type?: "text" | "number" | "date" | "textarea" | "select" | "toggle"; options?: string[]; placeholder?: string }> }>;
}

export interface MegaActionConfig {
  title?: string;
  style: "toolbar" | "commandbar" | "floating" | "segmented" | "bulk" | "review" | "danger" | "chips";
  actions: BlueprintAction[];
}

export interface MegaNavigationConfig {
  title?: string;
  style: "rail" | "sidebar" | "top" | "breadcrumbs" | "tabs" | "stepper" | "palette" | "mega" | "tree" | "bottom";
}

export interface MegaFeedbackConfig {
  title: string;
  description: string;
  style: "empty" | "error" | "warning" | "success" | "loading" | "offline" | "permission" | "maintenance" | "onboarding";
  actionLabel?: string;
}

export interface MegaLayoutConfig {
  title: string;
  description: string;
  style: "sidebar" | "cockpit" | "studio" | "split" | "portal" | "planner" | "ledger" | "map" | "console" | "canvas";
}

export interface MegaThemeConfig {
  id: string;
  label: string;
  className: string;
  description: string;
}

export interface MegaDashboardProps {
  metrics: BlueprintMetric[];
  records?: BlueprintRecord[];
  activity?: BlueprintTimelineEvent[];
  onSelect?: (record: BlueprintRecord) => void;
  className?: string;
}

export interface MegaCollectionProps {
  records: BlueprintRecord[];
  selectedId?: string;
  onSelect?: (record: BlueprintRecord) => void;
  onAction?: (record: BlueprintRecord) => void;
  className?: string;
}

export interface MegaDetailProps {
  record: BlueprintRecord;
  activity?: BlueprintTimelineEvent[];
  actions?: BlueprintAction[];
  onAction?: (action: BlueprintAction) => void;
  className?: string;
}

export interface MegaModalProps {
  open: boolean;
  context?: BlueprintRecord;
  options?: Array<{ value: string; label: string }>;
  busy?: boolean;
  onClose: () => void;
  onConfirm: (values: BlueprintRecord) => void | Promise<void>;
}

export interface MegaWorkflowProps {
  initialValues?: BlueprintRecord;
  busy?: boolean;
  onCancel?: () => void;
  onComplete: (values: BlueprintRecord) => void | Promise<void>;
  className?: string;
}

export interface MegaFormProps {
  initialValues?: BlueprintRecord;
  busy?: boolean;
  onSubmit: (values: BlueprintRecord) => void | Promise<void>;
  className?: string;
}

export interface MegaLayoutProps {
  navigation?: ReactNode;
  header?: ReactNode;
  summary?: ReactNode;
  toolbar?: ReactNode;
  main: ReactNode;
  aside?: ReactNode;
  secondary?: ReactNode;
  footer?: ReactNode;
  overlay?: ReactNode;
  className?: string;
}
