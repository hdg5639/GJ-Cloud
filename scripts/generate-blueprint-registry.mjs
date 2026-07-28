import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const blueprintRoot = path.join(root, "Frontend/portal/components/preview-runtime/blueprints");
const manifestPath = path.join(blueprintRoot, "manifests/component-manifest.json");
const generatedRegistryPath = path.join(blueprintRoot, "adapters/generatedPartComponents.ts");

const kindDirectory = {
  ACTION: "actions",
  COLLECTION: "collections",
  DASHBOARD: "dashboards",
  DETAIL: "details",
  FEEDBACK: "feedback",
  FORM: "forms",
  LAYOUT: "layouts",
  MODAL: "modals",
  NAVIGATION: "navigation",
  THEME: "themes",
  WORKFLOW: "workflows",
};

const mountPointByKind = {
  ACTION: "ACTIONS",
  COLLECTION: "COLLECTION",
  DASHBOARD: "DASHBOARD",
  DETAIL: "DETAIL",
  FEEDBACK: "FEEDBACK",
  FORM: "OVERLAY",
  LAYOUT: "LAYOUT",
  MODAL: "OVERLAY",
  NAVIGATION: "NAVIGATION",
  THEME: "THEME",
  WORKFLOW: "OVERLAY",
};

const syntheticSurfaceByMountPoint = {
  LAYOUT: "page.layout",
  NAVIGATION: "page.navigation",
  FEEDBACK: "page.feedback",
  THEME: "page.theme",
};

function humanize(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[-_]+/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function supportedModes(part) {
  if (part.kind === "FORM") return ["CREATE", "UPDATE"];
  if (part.kind === "WORKFLOW") return ["CREATE", "UPDATE", "COMMAND"];
  if (part.kind === "MODAL") {
    if (part.family === "destructive" || part.componentId.includes("danger")) return ["DELETE"];
    return ["CREATE", "UPDATE", "COMMAND"];
  }
  return [];
}

function normalize(parts) {
  const seen = new Set();
  return parts.map((part) => {
    if (seen.has(part.componentId)) throw new Error(`중복 componentId: ${part.componentId}`);
    seen.add(part.componentId);
    const directory = kindDirectory[part.kind];
    if (!directory) throw new Error(`지원하지 않는 kind: ${part.kind} (${part.componentId})`);
    const sourcePath = `${directory}/${part.exportName}.tsx`;
    const implementationPath = path.join(blueprintRoot, sourcePath);
    if (!fs.existsSync(implementationPath)) {
      throw new Error(`구현 파일 누락: ${path.relative(root, implementationPath)}`);
    }
    const implementation = fs.readFileSync(implementationPath, "utf8");
    const exportPattern = new RegExp(`export\\s+(?:function|const)\\s+${part.exportName}\\b`);
    if (!exportPattern.test(implementation)) {
      throw new Error(`export 누락: ${sourcePath} -> ${part.exportName}`);
    }
    const mountPoint = mountPointByKind[part.kind];
    const syntheticSurface = syntheticSurfaceByMountPoint[mountPoint];
    const acceptedSurfaces = part.kind === "DETAIL"
      ? [...part.acceptedSurfaces, "page.primary"]
      : part.acceptedSurfaces;
    const overlayPresentation = mountPoint === "OVERLAY"
      ? (part.kind === "MODAL" || /\bopen\b/.test(implementation) ? "SELF_HOSTED" : "WRAPPED")
      : null;
    return {
      ...part,
      label: humanize(part.exportName),
      mountPoint,
      acceptedSurfaces: syntheticSurface
        ? Array.from(new Set([...acceptedSurfaces, syntheticSurface]))
        : Array.from(new Set(acceptedSurfaces)),
      supportedModes: supportedModes(part),
      overlayPresentation,
      autoSelectable: part.kind !== "FEEDBACK" && part.kind !== "THEME",
      sourcePath,
    };
  });
}

function generatedRegistry(parts) {
  const imports = parts
    .map((part) => `import { ${part.exportName} } from "../${part.sourcePath.replace(/\.tsx$/, "")}";`)
    .join("\n");
  const entries = parts
    .map((part) => `  ${JSON.stringify(part.componentId)}: ${part.exportName},`)
    .join("\n");
  return `// 이 파일은 scripts/generate-blueprint-registry.mjs가 생성한다. 직접 수정하지 않는다.
import type { ComponentType } from "react";
${imports}

export const BLUEPRINT_COMPONENTS = {
${entries}
} as const;

export type GeneratedBlueprintPartId = keyof typeof BLUEPRINT_COMPONENTS;

export function blueprintComponent(componentId: string): ComponentType<Record<string, unknown>> | undefined {
  const component = BLUEPRINT_COMPONENTS[componentId as GeneratedBlueprintPartId];
  return component as unknown as ComponentType<Record<string, unknown>> | undefined;
}
`;
}

function writeOrCheck(file, content, check) {
  if (check) {
    const current = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
    if (current !== content) {
      throw new Error(`${path.relative(root, file)}가 manifest와 동기화되지 않았습니다. npm run blueprint:generate를 실행하세요.`);
    }
    return;
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}

const args = new Set(process.argv.slice(2));
const check = args.has("--check");

if (!fs.existsSync(manifestPath)) {
  throw new Error("component-manifest.json이 없습니다.");
}

const parts = normalize(JSON.parse(fs.readFileSync(manifestPath, "utf8")));
writeOrCheck(manifestPath, `${JSON.stringify(parts, null, 2)}\n`, check);
writeOrCheck(generatedRegistryPath, generatedRegistry(parts), check);

const counts = Object.fromEntries(
  Object.entries(parts.reduce((result, part) => {
    result[part.kind] = (result[part.kind] ?? 0) + 1;
    return result;
  }, {})).sort(([left], [right]) => left.localeCompare(right))
);
console.log(`Blueprint registry ${check ? "검증" : "생성"} 완료: ${parts.length} parts`, counts);
