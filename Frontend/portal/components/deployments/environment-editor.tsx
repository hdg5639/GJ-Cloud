"use client";

import type { EnvironmentFile } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/field";

export function EnvironmentEditor({ files, services, onChange, disabled = false }: {
  files: EnvironmentFile[];
  services: string[];
  onChange: (files: EnvironmentFile[]) => void;
  disabled?: boolean;
}) {
  const update = (index: number, value: Partial<EnvironmentFile>) =>
    onChange(files.map((file, i) => i === index ? { ...file, ...value } : file));
  return (
    <section aria-label="환경변수 주입 설정" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-bold">환경변수</h3>
          <p className="mt-1 text-xs text-muted">값을 입력한 뒤 받을 서비스를 선택하세요. 해당 서비스에 env_file을 연결합니다.</p>
        </div>
        <Button type="button" disabled={disabled} onClick={() => onChange([...files, { vmPath: files.length ? "service-" + (files.length + 1) + ".env" : ".env", content: "", serviceNames: [] }])}>파일 추가</Button>
      </div>
      {!files.length && <p className="text-xs text-muted-soft">입력한 값은 배포 설정에 암호화해 보관하며, AI 검수에는 비밀값을 가려서 전달합니다.</p>}
      {files.map((file, index) => (
        <fieldset key={index} disabled={disabled} className="min-w-0 space-y-3 rounded-md border border-line p-3">
          <legend className="px-1 text-xs font-bold">환경 파일 {index + 1}</legend>
          <div className="flex min-w-0 items-center gap-2">
            <label className="min-w-0 flex-1 text-xs text-muted">
              Compose 기준 파일 경로
              <Input aria-label={"환경 파일 " + (index + 1) + " 경로"} key={file.vmPath} defaultValue={file.vmPath} onBlur={e => { if (e.target.value !== file.vmPath) update(index, { vmPath: e.target.value }); }} className="mt-1 w-full" placeholder=".env 또는 config/api.env" />
            </label>
            <button type="button" aria-label={"환경 파일 " + (index + 1) + " 삭제"} onClick={() => onChange(files.filter((_, i) => i !== index))} className="mt-4 rounded p-2 text-xs text-muted hover:text-danger">삭제</button>
          </div>
          {file.serviceNames === undefined && <p className="text-xs text-[#e8b657]">기존 업로드 설정입니다. 서비스를 선택하면 주입 설정으로 전환됩니다. 경로는 Compose 기준으로 확인해주세요.</p>}
          <label className="block text-xs text-muted">
            변수와 값
            <Textarea aria-label={"환경 파일 " + (index + 1) + " 내용"} value={file.content} onChange={e => update(index, { content: e.target.value })} rows={5} spellCheck={false} placeholder={"DATABASE_URL=postgresql://db:5432/app\nPORT=8080"} className="mt-1 w-full font-mono text-xs" />
          </label>
          <div>
            <p className="mb-2 text-xs font-bold">주입할 서비스</p>
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              {services.map(service => (
                <label key={service} className="flex items-center gap-2 text-xs">
                  <input type="checkbox" checked={file.serviceNames?.includes(service) ?? false} onChange={e => update(index, {
                    serviceNames: e.target.checked ? [...(file.serviceNames ?? []), service] : (file.serviceNames ?? []).filter(name => name !== service),
                  })} />{service}
                </label>
              ))}
              {!services.length && <span className="text-xs text-muted-soft">Compose 서비스 분석 후 선택할 수 있습니다.</span>}
            </div>
          </div>
          <p className="text-[11px] leading-relaxed text-muted-soft">
            {!file.serviceNames?.length ? "서비스를 선택하지 않은 .env는 Compose 변수 치환용입니다. 컨테이너에는 자동 주입되지 않습니다." : "선택한 서비스에서 이 파일을 읽습니다. Compose의 environment에 같은 키가 있으면 그 값이 우선합니다."}
            {" "}브라우저에서 접근하는 프론트 변수는 빌드 시점 주입이 필요한 경우가 있습니다. 이 설정은 실행 시점 주입입니다.
          </p>
        </fieldset>
      ))}
    </section>
  );
}
