import { redirect } from "next/navigation";

export default async function LegacyAutoPreviewPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const legacySearchParams = await searchParams;
  const nextSearchParams = new URLSearchParams();
  for (const [name, value] of Object.entries(legacySearchParams)) {
    if (Array.isArray(value)) value.forEach((entry) => nextSearchParams.append(name, entry));
    else if (value !== undefined) nextSearchParams.set(name, value);
  }
  const query = nextSearchParams.toString();
  redirect(query ? `/auto-preview?${query}` : "/auto-preview");
}
