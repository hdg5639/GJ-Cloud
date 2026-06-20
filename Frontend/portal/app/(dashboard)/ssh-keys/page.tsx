"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { SshKeyResponse } from "@/lib/types";

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
    <div>
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-lg font-medium text-gray-900">SSH 키</h1>
          <p className="text-sm text-gray-500">인스턴스 접속에 사용할 공개키를 등록하세요</p>
        </div>
        <button
          onClick={() => setModalOpen(true)}
          className="bg-[#03C75A] text-white text-sm font-medium px-3.5 h-8 rounded-md"
        >
          키 등록
        </button>
      </div>

      {loading ? (
        <p className="text-sm text-gray-400">불러오는 중...</p>
      ) : keys.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="text-sm">등록된 SSH 키가 없습니다.</p>
          <button onClick={() => setModalOpen(true)} className="text-[#03C75A] text-sm font-medium mt-2 inline-block">
            키 등록하기 →
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {keys.map((key) => (
            <div
              key={key.id}
              className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3.5"
            >
              <div>
                <p className="text-sm font-medium text-gray-900">{key.name}</p>
                <p className="text-xs text-gray-400 font-mono mt-0.5">{key.fingerprint}</p>
              </div>
              <div className="flex items-center gap-3.5">
                <span className="text-xs text-gray-400">
                  {new Date(key.createdAt).toLocaleDateString("ko-KR")}
                </span>
                <button
                  onClick={() => handleDelete(key.id)}
                  className="text-gray-400 hover:text-red-500 text-sm transition-colors"
                >
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {modalOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-[420px]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-medium text-gray-900">SSH 키 등록</h2>
              <button onClick={handleModalClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
            </div>

            <div className="flex gap-0 mb-5 border-b border-gray-200">
              <button
                onClick={() => setTab("generate")}
                className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
                  tab === "generate" ? "border-[#03C75A] text-[#03C75A]" : "border-transparent text-gray-500"
                }`}
              >
                자동 생성
              </button>
              <button
                onClick={() => setTab("register")}
                className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
                  tab === "register" ? "border-[#03C75A] text-[#03C75A]" : "border-transparent text-gray-500"
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
        </div>
      )}
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
    const blob = new Blob([privateKey], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${name}_id_ed25519`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (privateKey) {
    return (
      <div>
        <div className="bg-amber-50 border border-amber-200 rounded-md p-3 mb-3">
          <p className="text-xs font-medium text-amber-700">
            ⚠ 이 개인키는 다시 보여드릴 수 없습니다. 지금 반드시 다운로드하세요.
          </p>
        </div>
        <textarea
          readOnly
          value={privateKey}
          className="w-full h-32 font-mono text-xs border border-gray-300 rounded-md p-2 mb-3 resize-none"
        />
        <button
          onClick={handleDownload}
          className="w-full h-9 bg-[#03C75A] text-white rounded-md text-sm font-medium mb-2"
        >
          다운로드
        </button>
        <button onClick={onDone} className="w-full h-9 border border-gray-300 rounded-md text-sm text-gray-600">
          닫기
        </button>
      </div>
    );
  }

  return (
    <div>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="키 이름 (예: macbook)"
        className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm mb-3 focus:outline-none focus:border-[#03C75A]"
      />
      <button
        onClick={handleGenerate}
        disabled={!name || loading}
        className="w-full h-9 bg-[#03C75A] text-white rounded-md text-sm font-medium disabled:opacity-60"
      >
        {loading ? "생성 중..." : "생성"}
      </button>
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
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="키 이름"
        required
        className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm mb-3 focus:outline-none focus:border-[#03C75A]"
      />
      <textarea
        value={publicKey}
        onChange={(e) => setPublicKey(e.target.value)}
        placeholder="ssh-ed25519 AAAA..."
        required
        className="w-full h-24 font-mono text-xs border border-gray-300 rounded-md p-2 mb-3 resize-none focus:outline-none focus:border-[#03C75A]"
      />
      <button
        type="submit"
        disabled={!name || !publicKey || loading}
        className="w-full h-9 bg-[#03C75A] text-white rounded-md text-sm font-medium disabled:opacity-60"
      >
        {loading ? "등록 중..." : "등록"}
      </button>
    </form>
  );
}
