"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
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
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [currentMetrics, setCurrentMetrics] = useState<SSEMetrics | null>(null);
  const [chartData, setChartData] = useState<ChartPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;

    const url = `${process.env.NEXT_PUBLIC_VM_API}/vms/${vmId}/metrics/stream`;
    const eventSource = new EventSource(url, { withCredentials: true });

    eventSource.onmessage = (event) => {
      try {
        const metrics = JSON.parse(event.data) as SSEMetrics;
        setCurrentMetrics(metrics);
        
        setChartData(prev => {
          const newPoint: ChartPoint = {
            time: new Date(metrics.timestamp * 1000).toLocaleTimeString("ko-KR", {
              hour: "2-digit",
              minute: "2-digit",
            }),
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
      setError("메트릭 수신 실패");
      eventSource.close();
    };

    return () => eventSource.close();
  }, [accessToken, vmId]);

  if (!currentMetrics) {
    return (
      <div className="p-8 text-center">
        <p className="text-gray-400">메트릭 데이터 로딩 중...</p>
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
    <div className="p-6 bg-gray-950 min-h-screen">
      <div className="mb-8">
        <h1 className="text-2xl font-medium text-white mb-1">인스턴스 모니터링</h1>
        <p className="text-sm text-gray-400">{vmId}</p>
      </div>

      {error && (
        <div className="bg-red-900/20 text-red-400 px-4 py-3 rounded-lg mb-6 text-sm">
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
        <ChartCard title="CPU 사용률" dataKey="cpu" color="#60a5fa" data={chartData} />
        <ChartCard title="메모리 사용률" dataKey="mem" color="#34d399" data={chartData} />
      </div>

      {/* 네트워크 차트 (풀 너비) */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
        <p className="text-sm font-medium text-white mb-4">네트워크 트래픽</p>
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
            <XAxis dataKey="time" stroke="#999" style={{ fontSize: "12px" }} />
            <YAxis stroke="#999" style={{ fontSize: "12px" }} />
            <Tooltip
              contentStyle={{ backgroundColor: "#1f2937", border: "1px solid #4b5563", borderRadius: "6px" }}
              labelStyle={{ color: "#fff" }}
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
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <p className="text-xs text-gray-500 mb-2 font-medium">{label}</p>
      <p className="text-xl font-medium text-white">{value}</p>
      <p className="text-xs text-gray-600 mt-1">{subtext}</p>
    </div>
  );
}

function ChartCard({ title, dataKey, color, data }: { title: string; dataKey: string; color: string; data: ChartPoint[] }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
      <p className="text-sm font-medium text-white mb-4">{title}</p>
      <ResponsiveContainer width="100%" height={250}>
        <AreaChart data={data}>
          <defs>
            <linearGradient id={`gradient-${dataKey}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.3} />
              <stop offset="95%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
          <XAxis dataKey="time" stroke="#999" style={{ fontSize: "12px" }} />
          <YAxis stroke="#999" style={{ fontSize: "12px" }} domain={[0, 100]} />
          <Tooltip
            contentStyle={{ backgroundColor: "#1f2937", border: "1px solid #4b5563", borderRadius: "6px" }}
            labelStyle={{ color: "#fff" }}
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
