# 로컬 전용 — Console이 테넌트 가입(WF5) 때 메일로 보내는 것과 같은 "비밀번호 설정 링크"를 만든다.
# 화면(UI-TP-04)과 API를 Console 없이 시험할 때 쓴다. 운영 비밀 값으로 쓰지 않는다.
#
#   .\docker\local\password-link.ps1                       # 기본 테넌트 k7f2m9qx4b1t, 화면 개발 서버(5174)용 주소
#   .\docker\local\password-link.ps1 -TenantId d4n8p2r6s0v1 -BaseUrl http://localhost:8082
#
# 서명 규칙은 Console PasswordLinkSigner와 같다: base64url(HMAC-SHA256(secret, tenant_id + exp + 현재 password 값))
# 현재 비밀번호 해시가 서명에 들어가므로, 이미 설정한 테넌트는 로컬 DB에서 해시를 읽어 계산한다(재설정 링크 시험).
param(
	[string]$TenantId = "k7f2m9qx4b1t",
	[string]$BaseUrl = "http://localhost:5174",
	[int]$Hours = 24
)
$ErrorActionPreference = "Stop"
$apiDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($TenantId -notmatch '^[a-z0-9]{12}$') { throw "tenant_id 형식이 아닙니다: $TenantId" }

$envFile = Join-Path $apiDir ".env"
$secret = (Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^PASSWORD_LINK_HMAC_SECRET=' }) -replace '^PASSWORD_LINK_HMAC_SECRET=', ''
if (-not $secret) { throw ".env에 PASSWORD_LINK_HMAC_SECRET이 없습니다." }

# 현재 비밀번호 해시(없으면 빈 문자열) — 로컬 MySQL 컨테이너에서 읽는다. SQL은 표준 입력으로 넘긴다(따옴표 중첩 방지)
Push-Location $apiDir
try {
	$current = "SELECT IFNULL(password,'') FROM tetra_platform.tenant WHERE tenant_id='$TenantId';" |
		docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N -B'
}
finally {
	Pop-Location
}
if ($LASTEXITCODE -ne 0) { throw "로컬 MySQL에 접속하지 못했습니다 (docker compose up -d 했는지 확인)." }
if ($null -eq $current) { throw "테넌트 $TenantId 이(가) 로컬 DB에 없습니다." }
$current = "$current".Trim()

$exp = [DateTimeOffset]::UtcNow.AddHours($Hours).ToUnixTimeSeconds()
$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
$digest = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$TenantId$exp$current"))
$sig = [Convert]::ToBase64String($digest).TrimEnd('=').Replace('+', '-').Replace('/', '_')

"$BaseUrl/password/setup?t=$TenantId&exp=$exp&sig=$sig"
