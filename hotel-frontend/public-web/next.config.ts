import type { NextConfig } from "next";
const backend=(process.env.BACKEND_BASE_URL||"http://localhost:8080").replace(/\/$/,"");
const nextConfig:NextConfig={async rewrites(){return [{source:"/api/public/site/media/:path*",destination:`${backend}/api/public/site/media/:path*`}]}};
export default nextConfig;
