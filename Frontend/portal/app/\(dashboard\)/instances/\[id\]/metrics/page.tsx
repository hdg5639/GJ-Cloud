"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmMetricsCurrentResponse, VmMetricsHistoryResponse } from "@/lib/types";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";

export default function MetricsPage() {
  const params = useParams();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [currentMetrics, setCurrentMetrics] = useState<VmMetricsCurrentResponse | null>(null);
  const [historyMetrics, setHistoryMetrics] = useState<VmMetricsHistoryResponse | null>(null);
  const [timeframe, setTimeframe] = useState<string>("hour");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return;
    setLoading(true);

    Promise.all([
      api.vm.metricsCurrent(accessToken, vmId),
      api.vm.metricsHistory(accessToken, vmId, timeframe),
    ])
      .then(([current, history]) => {
        setCurrentMetrics(current);
        setHistoryMetrics(history);
      })
      .catch((err) => console.error("메트릭 조회 실패:", err))
      .finally(() => setLoading(false));
  }, [accessToken, vmId, timeframe]);

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + " " + sizes[i];
  };

  const formatUptime = (seconds: number) => {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    return `${days}d ${hours}h ${mins}m`;
  };

  if (loading) {
    return <div className="p-6 text-gray-400">메트릭 로딩 중...</div>;
  }

  if (!currentMetrics) {
    return <div className="p-6 text-red-400">메트릭을 불러올 수 없습니다.</div>;
  }

  const memPercent = (currentMetrics.memoryUsedBytes / currentMetrics.memoryAllocatedBytes) * 100;
  const diskPercent = currentMetrics.diskAllocatedBytes > 0
    ? (currentMetrics.diskUsedBytes / currentMetrics.diskAllocatedBytes) * 100
    : 0;

  const chartData = historyMetrics?.dataPoints.map((point) => ({
    time: new Date(point.timestamp * 1000).toLocaleTimeString("ko-KR", {
      hour: "2-digit",
      minute: "2-digit",
    }),
    cpu: (point.cpuPercent * 100).toFixed(1),
    mem: (point.memoryUsedBytes / (1024 * 1024 * 1024)).toFixed(2),
  })) || [];

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">VM 메트릭</h1>
        <div className="flex gap-2">
          {["hour", "day", "week", "month"].map((tf) => (
            <button
              key={tf}
              onClick={() => setTimeframe(tf)}
              className={`px-3 py-1 rounded text-sm font-medium transition-colors ${
                timeframe === tf
                  ? "bg-blue-600 text-white"
                  : "bg-gray-800 text-gray-300 hover:bg-gray-700"
              }`}
            >
              {tf === "hour" ? "1시간" : tf === "day" ? "1일" : tf === "week" ? "1주" : "1개월"}
            </button>
          ))}
        </div>
      </div>

      {/* 메트릭 카드 */}
      <div className="grid grid-cols-2 gap-4">
        {/* CPU */}
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div className="text-sm text-gray-500 mb-2">CPU 사용률</div>
          <div className="text-3xl font-bold text-blue-400">
            {(currentMetrics.cpuUsagePercent * 100).toFixed(1)}%
          </div>
          <div className="text-xs text-gray-500 mt-2">
            {currentMetrics.allocatedCpu} vCPU 중 사용 중
          </div>
        </div>

        {/* 메모리 */}
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div className="text-sm text-gray-500 mb-2">메모리</div>
          <div className="text-3xl font-bold text-purple-400">
            {memPercent.toFixed(1)}%
          </div>
          <div className="text-xs text-gray-500 mt-2">
            {formatBytes(currentMetrics.memoryUsedBytes)} / {formatBytes(currentMetrics.memoryAllocatedBytes)}
          </div>
        </div>

        {/* 디스크 */}
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div className="text-sm text-gray-500 mb-2">디스크</div>
          <div className="text-3xl font-bold text-orange-400">
            {diskPercent.toFixed(1)}%
          </div>
          <div className="text-xs text-gray-500 mt-2">
            {formatBytes(currentMetrics.diskUsedBytes)} / {formatBytes(currentMetrics.diskAllocatedBytes)}
          </div>
        </div>

        {/* 업타임 */}
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <div className="text-sm text-gray-500 mb-2">가동 시간</div>
          <div className="text-3xl font-bold text-green-400">
            {formatUptime(currentMetrics.uptimeSeconds)}
          </div>
          <div className="text-xs text-gray-500 mt-2">상태: {currentMetrics.status}</div>
        </div>
      </div>

      {/* 네트워크 정보 */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <h2 className="text-lg font-semibold text-white mb-4">네트워크</h2>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="text-sm text-gray-500 mb-1">수신</div>
            <div className="text-2xl font-bold text-cyan-400">
              {formatBytes(currentMetrics.networkInBytes)}
            </div>
          </div>
          <div>
            <div className="text-sm text-gray-500 mb-1">송신</div>
            <div className="text-2xl font-bold text-cyan-400">
              {formatBytes(currentMetrics.networkOutBytes)}
            </div>
          </div>
        </div>
      </div>

      {/* 그래프 */}
      {chartData.length > 0 && (
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
          <h2 className="text-lg font-semibold text-white mb-4">시계열 데이터</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#444" />
              <XAxis dataKey="time" stroke="#999" />
              <YAxis stroke="#999" />
              <Tooltip contentStyle={{ backgroundColor: "#1f2937", borderColor: "#4b5563" }} />
              <Legend />
              <Line type="monotone" dataKey="cpu" stroke="#60a5fa" name="CPU (%)" isAnimationActive={false} />
              <Line type="monotone" dataKey="mem" stroke="#a78bfa" name="메모리 (GB)" isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
