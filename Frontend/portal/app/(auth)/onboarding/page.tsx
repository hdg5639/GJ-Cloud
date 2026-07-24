"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { AuthBrand } from "@/components/ui/auth-card";
import { AuthSplitLayout, AuthStepsPanel } from "@/components/ui/auth-split-layout";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { Avatar } from "@/components/ui/avatar";
import { validateProfileImage } from "@/lib/profile-image";

export default function OnboardingPage() {
  const router = useRouter();
  const { accessToken, user, isLoading } = useAuth();
  const [checking, setChecking] = useState(true);
  const [nickname, setNickname] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 이미 닉네임이 있으면(온보딩을 마친 뒤 URL로 직접 재방문 등) 할 일이 없으니 바로 대시보드로.
  useEffect(() => {
    if (isLoading) return;
    if (!accessToken) {
      router.replace("/login");
      return;
    }
    api.user
      .profile(accessToken)
      .then((p) => {
        if (p.nickname?.trim()) {
          router.replace("/instances");
          return;
        }
        setChecking(false);
      })
      .catch(() => setChecking(false));
  }, [accessToken, isLoading, router]);

  // objectURL은 렌더 중 계산(useMemo)하고, 이펙트는 이전 URL 해제(revoke)라는 부수효과만 담당 —
  // setState를 이펙트 안에서 하지 않아도 돼서 불필요한 리렌더가 한 번 덜 발생한다.
  const imagePreview = useMemo(() => (imageFile ? URL.createObjectURL(imageFile) : null), [imageFile]);
  useEffect(() => {
    return () => {
      if (imagePreview) URL.revokeObjectURL(imagePreview);
    };
  }, [imagePreview]);

  function handleImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setImageError(null);
    setImageFile(null);
    if (!file) return;
    const validationError = validateProfileImage(file);
    if (validationError) {
      setImageError(validationError);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setImageFile(file);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    const trimmed = nickname.trim();
    if (trimmed.length < 2 || trimmed.length > 12) {
      setError("닉네임은 2~12자로 입력해주세요");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      if (imageFile) {
        await api.user.uploadProfileImage(accessToken, imageFile);
      }
      await api.user.updateProfile(accessToken, { nickname: trimmed });
      router.push("/instances");
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다");
      setSaving(false);
    }
  }

  if (checking) {
    return (
      <div className="theme-dark flex min-h-screen items-center justify-center bg-background">
        <div className="h-40 w-[360px] rounded-panel border border-line bg-panel" />
      </div>
    );
  }

  return (
    <AuthSplitLayout visual={<AuthStepsPanel currentStep={3} />}>
      <div className="mb-7 lg:hidden">
        <AuthBrand />
      </div>

      <h1 className="mb-1 text-lg font-bold">프로필 설정</h1>
      <p className="mb-5 text-sm text-muted">닉네임을 정해주세요. 다른 사용자와 겹쳐도 괜찮아요.</p>

      <form onSubmit={handleSubmit}>
        <div className="mb-5 flex items-center gap-4">
          <Avatar
            nickname={nickname || null}
            email={user?.email}
            profileImageUrl={imagePreview}
            sizePx={56}
            textSizeClassName="text-lg"
          />
          <div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={handleImageChange}
            />
            <Button type="button" variant="secondary" size="small" onClick={() => fileInputRef.current?.click()}>
              프로필 사진 선택 (선택)
            </Button>
            {imageError && <p className="mt-1.5 text-xs text-danger">{imageError}</p>}
          </div>
        </div>

        <Field label="닉네임" htmlFor="onboarding-nickname" className="mb-5">
          <Input
            id="onboarding-nickname"
            name="nickname"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="다른 사람에게 보여질 이름 (2~12자)"
            maxLength={12}
            required
          />
        </Field>

        {error && <p className="mb-3 text-xs text-danger">{error}</p>}

        <Button type="submit" variant="primary" disabled={saving} className="w-full">
          {saving ? "저장 중..." : "시작하기"}
        </Button>
      </form>
    </AuthSplitLayout>
  );
}
