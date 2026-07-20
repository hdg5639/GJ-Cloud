import { NextRequest, NextResponse } from "next/server";

const ADMIN_DOMAIN = process.env.ADMIN_DOMAIN;
const STATIC_ASSET_RE = /\.(svg|png|jpg|jpeg|gif|webp|ico|webmanifest)$/;

export function middleware(request: NextRequest) {
  if (STATIC_ASSET_RE.test(request.nextUrl.pathname)) {
    return NextResponse.next();
  }

  if (!ADMIN_DOMAIN) {
    // 환경변수 누락 시 관리자 라우팅 비활성화 (fail-safe)
    if (request.nextUrl.pathname.startsWith("/admin")) {
      return NextResponse.rewrite(new URL("/404", request.url));
    }
    return NextResponse.next();
  }

  const hostname = request.headers.get("host");

  if (hostname === ADMIN_DOMAIN) {
    if (!request.nextUrl.pathname.startsWith("/admin")) {
      return NextResponse.rewrite(
        new URL(`/admin${request.nextUrl.pathname === "/" ? "" : request.nextUrl.pathname}`, request.url)
      );
    }
    return NextResponse.next();
  }

  // 일반 도메인에서 /admin 직접 접근 시도 차단
  if (request.nextUrl.pathname.startsWith("/admin")) {
    return NextResponse.rewrite(new URL("/404", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
