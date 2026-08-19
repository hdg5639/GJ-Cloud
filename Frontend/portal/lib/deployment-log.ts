import type { DeploymentEventPayload } from "@/lib/types";

type CommandLogPayload = {
  operation?: unknown;
  subject?: unknown;
  exitStatus?: unknown;
  durationMs?: unknown;
  stdout?: unknown;
  stderr?: unknown;
  stdoutTruncated?: unknown;
  stderrTruncated?: unknown;
};

const SECRET_ASSIGNMENT = /^(\s*(?:export\s+)?["']?[A-Za-z0-9_.-]*(?:password|passwd|token|secret|api[_-]?key|client[_-]?secret)[A-Za-z0-9_.-]*["']?\s*[=:]\s*)[^\r\n]+$/gim;

function redactLog(value: string): string {
  return value
    .replace(/(authorization\s*:\s*(?:basic|bearer)\s+)[^\s]+/gi, "$1[REDACTED]")
    .replace(/\bgh[pousr]_[A-Za-z0-9_]{16,}\b/g, "[REDACTED_GITHUB_TOKEN]")
    .replace(SECRET_ASSIGNMENT, "$1[REDACTED]");
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

export function formatDeploymentEventDetail(payload: DeploymentEventPayload["payload"]): string | null {
  if (!payload) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    return redactLog(payload);
  }

  if (typeof parsed === "string") {
    return parsed.trim() ? redactLog(parsed) : null;
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return redactLog(JSON.stringify(parsed, null, 2));
  }

  const command = parsed as CommandLogPayload;
  const operation = stringValue(command.operation);
  const subject = stringValue(command.subject);
  const stdout = stringValue(command.stdout);
  const stderr = stringValue(command.stderr);
  const looksLikeCommandLog = operation || subject || stdout || stderr || typeof command.exitStatus === "number";

  if (!looksLikeCommandLog) {
    return redactLog(JSON.stringify(parsed, null, 2));
  }

  const sections: string[] = [];
  const meta = [
    operation ? `작업: ${operation}` : null,
    subject ? `대상: ${subject}` : null,
    typeof command.exitStatus === "number" ? `종료 코드: ${command.exitStatus}` : null,
    typeof command.durationMs === "number" ? `소요 시간: ${command.durationMs}ms` : null,
  ].filter(Boolean);
  if (meta.length > 0) sections.push(meta.join(" · "));

  if (stdout) {
    sections.push(`${command.stdoutTruncated === true ? "stdout (앞부분 생략)" : "stdout"}:\n${stdout.trimEnd()}`);
  }
  if (stderr) {
    sections.push(`${command.stderrTruncated === true ? "stderr (앞부분 생략)" : "stderr"}:\n${stderr.trimEnd()}`);
  }
  if (!stdout && !stderr) sections.push("명령 출력 없음");

  return redactLog(sections.join("\n\n"));
}
