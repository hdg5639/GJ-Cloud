export type BlueprintTone = "ok" | "warn" | "idle" | "danger" | "neutral" | "brand";

export type BlueprintRecord = Record<string, unknown>;

export interface BlueprintMetric {
  id: string;
  label: string;
  value: string | number;
  hint?: string;
  delta?: number;
  tone?: BlueprintTone;
}

export interface BlueprintAction {
  id: string;
  label: string;
  description?: string;
  tone?: "primary" | "secondary" | "danger" | "ghost";
  disabled?: boolean;
}

export interface BlueprintNavItem {
  id: string;
  label: string;
  badge?: string | number;
  active?: boolean;
  description?: string;
}

export interface BlueprintField {
  key: string;
  label?: string;
  value: unknown;
  emphasize?: boolean;
  copyable?: boolean;
}

export interface BlueprintTimelineEvent {
  id: string;
  title: string;
  description?: string;
  timestamp?: string;
  actor?: string;
  status?: string;
  tone?: BlueprintTone;
  metadata?: BlueprintField[];
}

export interface BlueprintKanbanCard {
  id: string;
  title: string;
  subtitle?: string;
  status?: string;
  owner?: string;
  dueAt?: string;
  tags?: string[];
  metadata?: BlueprintField[];
}

export interface BlueprintKanbanColumn {
  id: string;
  title: string;
  description?: string;
  tone?: BlueprintTone;
  cards: BlueprintKanbanCard[];
}

export interface BlueprintMediaItem {
  id: string;
  title: string;
  subtitle?: string;
  thumbnailUrl?: string | null;
  mediaType?: string;
  sizeLabel?: string;
  status?: string;
}

export interface BlueprintDirectoryEntry {
  id: string;
  title: string;
  subtitle?: string;
  avatarUrl?: string | null;
  role?: string;
  status?: string;
  metadata?: BlueprintField[];
}

export interface BlueprintAlert {
  id: string;
  title: string;
  description?: string;
  severity: "INFO" | "WARNING" | "ERROR" | "CRITICAL";
  timestamp?: string;
  source?: string;
  acknowledged?: boolean;
}

export interface BlueprintChartSeries {
  id: string;
  label: string;
  values: number[];
  tone?: BlueprintTone;
}

export interface BlueprintChartData {
  labels: string[];
  series: BlueprintChartSeries[];
}

export interface BlueprintOption {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
}

export interface BlueprintPermission {
  id: string;
  label: string;
  description?: string;
  enabled: boolean;
  locked?: boolean;
}

export interface BlueprintWorkflowStep {
  id: string;
  label: string;
  description?: string;
  status?: "PENDING" | "ACTIVE" | "COMPLETED" | "ERROR";
}

export interface BlueprintBaseProps {
  className?: string;
}
