"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { getExchangedToken } from "@/lib/api-client";
import type { VmMetricsHistoryResponse } from "@/lib/types";
import { AreaChart, Area, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";

interface SSEMetrics {
  vmId: string;
  cpuUsagePercent: number;
  allocatedCpu: number;
  memoryUsedBytes: number;
  memoryAllocatedBytes: number;
  diskUsedBytes: number;
  diskAllocatedBytes: number;
  networkInBytes: number;
  networkOutBytes: number;
  uptimeSeconds: number;
  status: string;
  timestamp: number;
}

interface ChartPoint {
  time: string;
  cpu: number;
  mem: number;
  netin: number;
  netout: number;
}

export default function MetricsPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [currentMetrics, setCurrentMetrics] = useState<SSEMetrics | null>(null);
  const [chartData, setChartData] = useState<ChartPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reconnectRef = useRef<() => void>(() => {});

  useEffect(() => {
    if (!accessToken) return;

    let eventSource: EventSource | null = null;
    let closed = false;
    let autoCloseTimer: ReturnType<typeof setTimeout> | null = null;

    function closeEs() {
      if (autoCloseTimer) { clearTimeout(autoCloseTimer); autoCloseTimer = null; }
      if (eventSource) {
        eventSource.onmessage = null;
        eventSource.onerror = null;
        eventSource.close();
        eventSource = null;
      }
    }

    async function connect() {
      if (closed || !accessToken) return;
      try {
        const vmToken = await getExchangedToken(accessToken, "vm-service");
        if (closed) return;

        const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/${vmId}/metrics/stream?token=${vmToken}`;
        eventSource = new EventSource(url);

        autoCloseTimer = setTimeout(() => {
          eventSource?.close();
          eventSource = null;
          autoCloseTimer = null;
        }, 99_000);

        eventSource.onmessage = (event) => {
          try {
            const metrics = JSON.parse(event.data) as SSEMetrics;
            setCurrentMetrics(metrics);

            setChartData(prev => {
              const date = new Date(metrics.timestamp * 1000);
              const hours = String(date.getHours()).padStart(2, "0");
              const minutes = String(date.getMinutes()).padStart(2, "0");
              const newPoint: ChartPoint = {
                time: `${hours}:${minutes}`,
                cpu: Math.round(metrics.cpuUsagePercent * 100),
                mem: Math.round((metrics.memoryUsedBytes / metrics.memoryAllocatedBytes) * 100),
                netin: Math.round(metrics.networkInBytes / (1024 * 1024)),
                netout: Math.round(metrics.networkOutBytes / (1024 * 1024)),
              };

              const updated = [...prev, newPoint];
              return updated.slice(-12);
            });

            setLoading(false);
            setError(null);
          } catch (err) {
            console.error("메트릭 파싱 실패:", err);
          }
        };

        eventSource.onerror = () => {
          if (!closed) {
            setError("메트릭 수신 실패");
            closeEs();
          }
        };
      } catch (err) {
        console.error("메트릭 연결 실패:", err);
      }
    }

    reconnectRef.current = () => {
      closeEs();
      setError(null);
      connect();
    };

    connect();

    return () => {
      closed = true;
      reconnectRef.current = () => {};
      closeEs();
    };
  }, [accessToken, vmId]);

  const reconnect = useCallback(() => reconnectRef.current(), []);

  if (!currentMetrics) {
    return (
      <div className="p-8 text-center">
        <p className="text-gray-500">메트릭 데이터 로딩 중...</p>
      </div>
    );
  }

  const memoryGb = (currentMetrics.memoryUsedBytes / (1024 ** 3)).toFixed(1);
  const maxMemoryGb = (currentMetrics.memoryAllocatedBytes / (1024 ** 3)).toFixed(1);
  const memPercent = Math.round((currentMetrics.memoryUsedBytes / currentMetrics.memoryAllocatedBytes) * 100);
  const cpuPercent = Math.round(currentMetrics.cpuUsagePercent * 100);
  const diskPercent = currentMetrics.diskAllocatedBytes > 0
    ? Math.round((currentMetrics.diskUsedBytes / currentMetrics.diskAllocatedBytes) * 100)
    : 0;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            aria-label="뒤로가기"
          >
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900">메트릭</h1>
        </div>
        <button
          onClick={reconnect}
          title="동기화"
          className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50"
        >
          <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-6 text-sm">
          {error}
        </div>
      )}

      {/* 메트릭 카드 그리드 */}
      <div className="grid grid-cols-4 gap-3 mb-6">
        <MetricCard label="CPU 사용률" value={`${cpuPercent}%`} subtext={`${currentMetrics.allocatedCpu} cores`} />
        <MetricCard label="메모리" value={`${memPercent}%`} subtext={`${memoryGb} GB / ${maxMemoryGb} GB`} />
        <MetricCard label="디스크" value={`${diskPercent}%`} subtext={`${(currentMetrics.diskUsedBytes / (1024 ** 3)).toFixed(1)} GB / ${(currentMetrics.diskAllocatedBytes / (1024 ** 3)).toFixed(1)} GB`} />
        <MetricCard label="상태" value={currentMetrics.status.toUpperCase()} subtext="실시간" />
      </div>

      {/* 차트 그리드 */}
      <div className="grid grid-cols-2 gap-3 mb-3">
        <ChartCard title="CPU 사용률 (최근 1시간)" dataKey="cpu" color="#3b82f6" data={chartData} />
        <ChartCard title="메모리 사용률 (최근 1시간)" dataKey="mem" color="#10b981" data={chartData} />
      </div>

      {/* 네트워크 차트 (풀 너비) */}
      <div className="bg-gray-50 border border-slate-200 rounded-lg p-4">
        <p className="text-sm font-medium text-gray-900 mb-4">네트워크 트래픽 (최근 1시간)</p>
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" vertical={false} />
            <XAxis dataKey="time" stroke="#9ca3af" style={{ fontSize: "12px" }} />
            <YAxis stroke="#9ca3af" style={{ fontSize: "12px" }} />
            <Tooltip
              contentStyle={{ backgroundColor: "#f9fafb", border: "1px solid #e5e7eb", borderRadius: "6px" }}
              labelStyle={{ color: "#111827" }}
            />
            <Line type="monotone" dataKey="netin" stroke="#3b82f6" name="In (MB)" dot={false} strokeWidth={2} />
            <Line type="monotone" dataKey="netout" stroke="#f59e0b" name="Out (MB)" dot={false} strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function MetricCard({ label, value, subtext }: { label: string; value: string; subtext: string }) {
  return (
    <div className="bg-gray-50 border border-slate-200 rounded-lg p-4">
      <p className="text-xs text-gray-500 mb-2 font-medium">{label}</p>
      <p className="text-xl font-medium text-gray-900">{value}</p>
      <p className="text-xs text-gray-600 mt-1">{subtext}</p>
    </div>
  );
}

function ChartCard({ title, dataKey, color, data }: { title: string; dataKey: string; color: string; data: ChartPoint[] }) {
  return (
    <div className="bg-gray-50 border border-slate-200 rounded-lg p-4">
      <p className="text-sm font-medium text-gray-900 mb-4">{title}</p>
      <ResponsiveContainer width="100%" height={250}>
        <AreaChart data={data}>
          <defs>
            <linearGradient id={`gradient-${dataKey}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.2} />
              <stop offset="95%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" vertical={false} />
          <XAxis dataKey="time" stroke="#9ca3af" style={{ fontSize: "12px" }} />
          <YAxis stroke="#9ca3af" style={{ fontSize: "12px" }} domain={[0, 100]} />
          <Tooltip
            contentStyle={{ backgroundColor: "#f9fafb", border: "1px solid #e5e7eb", borderRadius: "6px" }}
            labelStyle={{ color: "#111827" }}
          />
          <Area
            type="monotone"
            dataKey={dataKey}
            stroke={color}
            strokeWidth={2}
            fill={`url(#gradient-${dataKey})`}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
