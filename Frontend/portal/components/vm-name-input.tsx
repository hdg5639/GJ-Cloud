import { InputHTMLAttributes } from "react";
import { Input } from "@/components/ui/field";

const VM_NAME_MAX_LENGTH = 63;
const VM_NAME_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/;

export function sanitizeVmName(value: string) {
  return value
    .replace(/[^A-Za-z0-9-]/g, "")
    .replace(/^-+/, "")
    .slice(0, VM_NAME_MAX_LENGTH);
}

export function isValidVmName(value: string) {
  return VM_NAME_PATTERN.test(value);
}

export function VmNameInput({
  value,
  onValueChange,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> & {
  value: string;
  onValueChange: (value: string) => void;
}) {
  return (
    <>
      <Input
        {...props}
        value={value}
        onChange={(event) => onValueChange(sanitizeVmName(event.target.value))}
        maxLength={VM_NAME_MAX_LENGTH}
        pattern="[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
      />
      <span className="text-[11px] font-normal text-muted-soft">
        영문, 숫자, 하이픈(-)만 사용할 수 있으며 하이픈으로 시작하거나 끝날 수 없습니다.
      </span>
    </>
  );
}
