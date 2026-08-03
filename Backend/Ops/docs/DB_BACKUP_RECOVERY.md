# DB 백업 복구 런북

이 문서는 Ops가 생성한 암호화 DB 백업을 주기적으로 검증하고 일회용 DB에 복구하는 운영 절차다. 운영 DB에 바로 복구하지 않는다.

## 사전 조건

- `BACKUP_READ` 권한이 있는 운영자가 수행한다.
- 운영과 분리된 일회용 PostgreSQL, MySQL, MongoDB 또는 Redis 인스턴스를 준비한다.
- 다운로드한 덤프는 테스트 종료 후 안전하게 삭제한다.

## 검증 절차

1. 백업 이력에서 `status=SUCCESS`, `checksumSha256`, `encryptionVersion`, `expiresAt`을 확인한다.
2. `POST /ops/{vmId}/backups/{backupId}/verify`를 호출한다. 이 요청은 암호문 전체를 복호화해 AES-GCM tag와 평문 SHA-256를 다시 검증한다.
3. `verifiedAt`이 갱신됐는지 확인한다.
4. `GET /ops/{vmId}/backups/{backupId}/download`로 복호화된 덤프를 다운로드한다. 파일 브라우저 경로는 사용하지 않는다.
5. 일회용 DB에 덤프를 적용한다.

## DB별 복구 예시

```bash
# PostgreSQL custom archive
pg_restore --clean --if-exists --no-owner --dbname TEST_DATABASE backup.dump

# MySQL SQL dump
mysql --database TEST_DATABASE < backup.sql

# MongoDB archive
mongorestore --drop --archive=backup.archive

# Redis RDB: 일회용 Redis를 중지하고 dump.rdb를 dir/dbfilename 경로에 배치한 뒤 재시작
```

## 합격 기준과 기록

- 스키마 조회, 핵심 테이블/콜렉션/키 개수, 표본 쿼리를 검증한다.
- 검증일, VM ID, backup ID, DB 종류, `checksumSha256`, 복구 결과와 소요 시간을 운영 기록에 남긴다. 비밀번호나 덤프 내용은 기록하지 않는다.
- 최소 분기 1회와 DB 주요 버전 업그레이드 전에 수행한다.
- 복구 실패 시 해당 백업을 사용하지 않고, 암호화 키·DB 버전·덤프 로그를 확인한 뒤 새 백업으로 재시험한다.
