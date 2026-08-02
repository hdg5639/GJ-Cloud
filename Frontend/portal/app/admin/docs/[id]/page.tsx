import { AdminDocsEditor } from "@/components/docs/AdminDocsEditor";

export default async function EditAdminDocsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <AdminDocsEditor articleId={id} />;
}
