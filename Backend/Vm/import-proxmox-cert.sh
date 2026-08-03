#!/bin/sh
# 컨테이너 기동 시마다 현재 PROXMOX_URL이 가리키는 서버의 인증서를 그 자리에서 받아와
# JVM 트러스트스토어에 등록한다. Proxmox가 자체 서명 인증서를 쓰는 홈랩 서버라
# (SEC-003: prod는 trust-all 자체가 불가능, JVM 트러스트스토어에 등록해야 함) 필요한 절차이며,
# 매 기동마다 다시 받아오므로 Proxmox의 IP나 인증서가 나중에 바뀌어도 재배포 없이 재시작만으로
# 자동으로 맞춰진다. 인증서를 못 받아와도 앱 기동 자체는 막지 않고 경고만 남긴다.

if [ -n "$PROXMOX_URL" ]; then
  HOST_PORT=$(echo "$PROXMOX_URL" | sed -E 's#^[a-zA-Z]+://##; s#/.*$##')
  HOST=$(echo "$HOST_PORT" | cut -d: -f1)
  PORT=$(echo "$HOST_PORT" | cut -d: -f2)
  if [ "$PORT" = "$HOST" ]; then
    PORT=443
  fi

  echo "Proxmox 인증서 가져오는 중: ${HOST}:${PORT}"
  CERT=$(echo | openssl s_client -connect "${HOST}:${PORT}" -servername "$HOST" 2>/dev/null | openssl x509 -outform PEM 2>/dev/null)

  if [ -n "$CERT" ]; then
    echo "$CERT" > /tmp/proxmox.crt
    keytool -delete -alias proxmox -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit >/dev/null 2>&1
    if keytool -importcert -alias proxmox -file /tmp/proxmox.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit -noprompt >/tmp/keytool.log 2>&1; then
      echo "Proxmox 인증서 등록 완료 (alias=proxmox)"
    else
      echo "Proxmox 인증서 등록 실패 — 아래 내용을 확인:" >&2
      cat /tmp/keytool.log >&2
    fi
  else
    echo "Proxmox(${HOST}:${PORT})에서 인증서를 받아오지 못했습니다 — TLS 검증이 실패할 수 있습니다" >&2
  fi
fi

exec java -jar /app.jar
