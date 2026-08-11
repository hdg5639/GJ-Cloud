import type { NextConfig } from "next";

const devApiProxyTarget = process.env.DEV_API_PROXY_TARGET?.replace(/\/+$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    if (process.env.NODE_ENV !== "development" || !devApiProxyTarget) {
      return [];
    }

    const target = new URL(devApiProxyTarget);
    if (!["http:", "https:"].includes(target.protocol) || target.pathname !== "/") {
      throw new Error("DEV_API_PROXY_TARGET must be an HTTP(S) origin without a path");
    }

    return [
      {
        source: "/:service(auth|users|vms|ops|ws)/:path*",
        destination: `${target.origin}/:service/:path*`,
      },
    ];
  },
};

export default nextConfig;
