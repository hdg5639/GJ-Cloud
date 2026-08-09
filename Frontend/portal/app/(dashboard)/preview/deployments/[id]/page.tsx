import { redirect } from "next/navigation";

export default async function LegacyManagedPreviewDeploymentPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  redirect(`/auto-preview/deployments/${encodeURIComponent(id)}`);
}
