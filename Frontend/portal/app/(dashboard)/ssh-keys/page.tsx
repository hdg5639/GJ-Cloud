"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { SshKeyResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Button } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { Table, Th, Td } from "@/components/ui/table";
import { Modal } from "@/components/ui/modal";
import { Input, Textarea } from "@/components/ui/field";

export default function SshKeysPage() {
  const { accessToken } = useAuth();
  const [keys, setKeys] = useState<SshKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [tab, setTab] = useState<"generate" | "register">("generate");

  useEffect(() => {
    if (!accessToken) return;
    api.user.sshKeys(accessToken).then(setKeys).catch(() => {}).finally(() => setLoading(false));
  }, [accessToken]);

  async function handleDelete(keyId: string) {
    if (!accessToken) return;
    if (!confirm("SSH 키를 삭제하시겠습니까?")) return;
    try {
      await api.user.deleteSshKey(accessToken, keyId);
      setKeys((prev) => prev.filter((k) => k.id !== keyId));
    } catch (err) {
      alert(err instanceof Error ? err.message : "삭제에 실패했습니다");
    }
  }

  function handleModalClose() {
    setModalOpen(false);
    // 키 목록 새로고침
    if (accessToken) {
      api.user.sshKeys(accessToken).then(setKeys).catch(() => {});
    }
  }

  return (
    <div className="mx-auto max-w-[1380px]">
      <header className="mb-[22px] flex items-center gap-6">
        <div>
          <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">ACCESS</span>
          <h1 className="my-[5px] text-[29px] font-extrabold tracking-tight">SSH 키</h1>
          <p className="m-0 text-sm text-muted">인스턴스 접속에 사용할 공개키를 관리합니다.</p>
        </div>
        <Button variant="primary" onClick={() => setModalOpen(true)}>
          ＋ 키 등록
        </Button>
      </header>

      {loading ? (
        <PageLoader />
      ) : (
        <Panel>
          <Table>
            <thead>
              <tr>
                <Th>이름</Th>
                <Th>Fingerprint</Th>
                <Th>등록일</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {keys.length === 0 ? (
                <tr>
                  <Td colSpan={4} className="py-16 text-center text-muted-soft">
                    등록된 SSH 키가 없습니다.{" "}
                    <button onClick={() => setModalOpen(true)} className="font-bold text-brand-strong">
                      키 등록하기 →
                    </button>
                  </Td>
                </tr>
              ) : (
                keys.map((key) => (
                  <tr key={key.id}>
                    <Td className="font-bold">{key.name}</Td>
                    <Td className="font-mono text-muted-soft">{key.fingerprint}</Td>
                    <Td className="text-muted-soft">{new Date(key.createdAt).toLocaleDateString("ko-KR")}</Td>
                    <Td>
                      <button
                        onClick={() => handleDelete(key.id)}
                        className="text-muted-soft hover:text-danger text-xs font-bold transition-colors"
                      >
                        삭제
                      </button>
                    </Td>
                  </tr>
                ))
              )}
            </tbody>
          </Table>
        </Panel>
      )}

      <Modal open={modalOpen} onClose={handleModalClose}>
        <div className="mx-auto w-[420px] rounded-panel bg-panel p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-bold">SSH 키 등록</h2>
            <button onClick={handleModalClose} className="text-muted-soft hover:text-muted text-xl leading-none">×</button>
          </div>

          <div className="flex gap-0 mb-5 border-b border-line">
            <button
              onClick={() => setTab("generate")}
              className={`px-4 py-2 text-sm font-bold border-b-2 -mb-px ${
                tab === "generate" ? "border-brand text-brand-strong" : "border-transparent text-muted"
              }`}
            >
              자동 생성
            </button>
            <button
              onClick={() => setTab("register")}
              className={`px-4 py-2 text-sm font-bold border-b-2 -mb-px ${
                tab === "register" ? "border-brand text-brand-strong" : "border-transparent text-muted"
              }`}
            >
              직접 등록
            </button>
          </div>

          {tab === "generate" ? (
            <GenerateKeyForm accessToken={accessToken} onDone={handleModalClose} />
          ) : (
            <RegisterKeyForm accessToken={accessToken} onDone={handleModalClose} />
          )}
        </div>
      </Modal>
    </div>
  );
}

function GenerateKeyForm({
  accessToken,
  onDone,
}: {
  accessToken: string | null;
  onDone: () => void;
}) {
  const [name, setName] = useState("");
  const [privateKey, setPrivateKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleGenerate() {
    if (!accessToken || !name) return;
    setLoading(true);
    try {
      const result = await api.user.generateSshKey(accessToken, name);
      setPrivateKey(result.privateKey);
    } catch (err) {
      alert(err instanceof Error ? err.message : "키 생성에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  function handleDownload() {
    if (!privateKey) return;
    const blob = new Blob([privateKey], { type: "application/x-pem-file" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${name}_id_ed25519.pem`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (privateKey) {
    return (
      <div>
        <div className="rounded-md border border-[#f3dfa8] bg-[#fffaf0] p-3 mb-3">
          <p className="text-xs font-bold text-[#9c6b1f]">
            ⚠ 이 개인키는 다시 보여드릴 수 없습니다. 지금 반드시 다운로드하세요.
          </p>
        </div>
        <Textarea
          id="ssh-private-key-display"
          name="ssh-private-key-display"
          readOnly
          value={privateKey}
          className="h-32 font-mono resize-none mb-3"
        />
        <Button variant="primary" onClick={handleDownload} className="w-full mb-2">
          다운로드
        </Button>
        <Button onClick={onDone} className="w-full">
          닫기
        </Button>
      </div>
    );
  }

  return (
    <div>
      <Input
        id="ssh-generate-name"
        name="ssh-generate-name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="키 이름 (예: macbook)"
        className="mb-3"
      />
      <Button variant="primary" onClick={handleGenerate} disabled={!name || loading} className="w-full">
        {loading ? "생성 중..." : "생성"}
      </Button>
    </div>
  );
}

function RegisterKeyForm({
  accessToken,
  onDone,
}: {
  accessToken: string | null;
  onDone: () => void;
}) {
  const [name, setName] = useState("");
  const [publicKey, setPublicKey] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    setLoading(true);
    try {
      await api.user.registerSshKey(accessToken, { publicKey, name });
      onDone();
    } catch (err) {
      alert(err instanceof Error ? err.message : "키 등록에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <Input
        id="ssh-register-name"
        name="ssh-register-name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="키 이름"
        required
        className="mb-3"
      />
      <Textarea
        id="ssh-register-public-key"
        name="ssh-register-public-key"
        value={publicKey}
        onChange={(e) => setPublicKey(e.target.value)}
        placeholder="ssh-ed25519 AAAA..."
        required
        className="h-24 font-mono resize-none mb-3"
      />
      <Button type="submit" variant="primary" disabled={!name || !publicKey || loading} className="w-full">
        {loading ? "등록 중..." : "등록"}
      </Button>
    </form>
  );
}
