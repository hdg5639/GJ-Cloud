export const PORT_PRESETS = [
  { id: "custom", label: "직접 입력", port: "", protocol: "HTTP", nickname: "", visibility: "PUBLIC" },
  { id: "web", label: "웹 서비스", port: "80", protocol: "HTTP", nickname: "web", visibility: "PUBLIC" },
  { id: "web-dev", label: "웹 개발 서버", port: "3000", protocol: "HTTP", nickname: "web", visibility: "PUBLIC" },
  { id: "ssh", label: "SSH", port: "22", protocol: "TCP", nickname: "ssh", visibility: "PRIVATE" },
  { id: "postgresql", label: "PostgreSQL", port: "5432", protocol: "TCP", nickname: "postgres", visibility: "PRIVATE" },
  { id: "mysql", label: "MySQL", port: "3306", protocol: "TCP", nickname: "mysql", visibility: "PRIVATE" },
  { id: "redis", label: "Redis", port: "6379", protocol: "TCP", nickname: "redis", visibility: "PRIVATE" },
  { id: "mongodb", label: "MongoDB", port: "27017", protocol: "TCP", nickname: "mongo", visibility: "PRIVATE" },
  { id: "rdp", label: "원격 데스크톱 (RDP)", port: "3389", protocol: "TCP", nickname: "rdp", visibility: "PRIVATE" },
] as const;
