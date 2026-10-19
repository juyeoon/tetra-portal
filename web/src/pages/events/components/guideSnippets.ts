/**
 * 사전 가이드 (UI-TP-6-1) 예제 코드 — 이벤트 ID · 테넌트 ID · 도메인을 넣어 만든다.
 * 발급 API(issuance-api JwtAuthFilter) 규칙과 같다: RS256 만, 헤더 kid = 키 버전(승인 메일),
 * 클레임 user_id · tenant_id · event_id(숫자) · iat · exp(최대 5분) · jti(1회용), 세션 URL `GET /api/issuance/session?JWT=`
 */

export const GUIDE_LANGUAGES = ['Java', 'JavaScript (ES6)', 'PHP', 'C# / .NET', 'Python'] as const;
export type GuideLanguage = (typeof GUIDE_LANGUAGES)[number];

export const jwtStructure = (eventId: number, tenantId: string) => `// Header
{ "alg": "RS256", "typ": "JWT", "kid": "1" }   // kid: 승인 메일에 적힌 키 버전

// Payload
{
  "user_id": "user-98413",          // 고객사 회원 식별자 (128자 이하)
  "tenant_id": "${tenantId}",       // 우리 테넌트 ID
  "event_id": ${eventId},                // 이벤트 ID (숫자)
  "iat": 1790000000,
  "exp": 1790000090,                // 발급 후 1~2분 (최대 5분)
  "jti": "b7f2c1e0-4a6d-..."        // 1회용 ID (재사용 시 거부)
}`;

export function jwtExample(lang: GuideLanguage, eventId: number, tenantId: string) {
  switch (lang) {
    case 'Java':
      return `import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

// 승인 메일로 받은 개인키(PKCS#8 PEM)로 서명하는 예제 (jjwt 0.12)
String base64 = privateKeyPem
    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
    .replaceAll("\\\\s", "");
PrivateKey privateKey = KeyFactory.getInstance("RSA")
    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));

String token = Jwts.builder()
    .header().keyId(kid).and()                                  // kid: 승인 메일의 키 버전 (예: "1")
    .claim("user_id", userId)                                   // 고객사 회원 식별자
    .claim("tenant_id", "${tenantId}")
    .claim("event_id", ${eventId}L)
    .id(UUID.randomUUID().toString())                           // jti: 1회용
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 90_000)) // 90초 후 만료
    .signWith(privateKey, Jwts.SIG.RS256)
    .compact();`;
    case 'JavaScript (ES6)':
      return `import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

// 승인 메일로 받은 개인키(PEM 문자열)로 서명하는 예제 (Node.js, jsonwebtoken)
const token = jwt.sign(
  { user_id: userId, tenant_id: '${tenantId}', event_id: ${eventId} },
  privateKeyPem,
  {
    algorithm: 'RS256',
    keyid: kid,           // 승인 메일의 키 버전 (예: '1')
    jwtid: randomUUID(),  // jti: 1회용
    expiresIn: '90s',     // 90초 후 만료
  },
);`;
    case 'PHP':
      return `<?php
use Firebase\\JWT\\JWT;

// 승인 메일로 받은 개인키($privateKeyPem)로 서명하는 예제 (firebase/php-jwt)
$now = time();
$payload = [
    'user_id'   => $userId,                    // 고객사 회원 식별자
    'tenant_id' => '${tenantId}',
    'event_id'  => ${eventId},
    'iat'       => $now,
    'exp'       => $now + 90,                  // 90초 후 만료
    'jti'       => bin2hex(random_bytes(16)),  // 1회용
];
$token = JWT::encode($payload, $privateKeyPem, 'RS256', $kid);  // $kid: 승인 메일의 키 버전`;
    case 'C# / .NET':
      return `using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using Microsoft.IdentityModel.Tokens;

// 승인 메일로 받은 개인키(PEM)로 서명하는 예제 (.NET 6+, System.IdentityModel.Tokens.Jwt)
using var rsa = RSA.Create();
rsa.ImportFromPem(privateKeyPem);
var key = new RsaSecurityKey(rsa) { KeyId = kid };                        // kid: 승인 메일의 키 버전
var token = new JwtSecurityToken(
    claims: new[]
    {
        new Claim("user_id", userId),                                      // 고객사 회원 식별자
        new Claim("tenant_id", "${tenantId}"),
        new Claim("event_id", "${eventId}", ClaimValueTypes.Integer64),
        new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString()), // 1회용
        new Claim(JwtRegisteredClaimNames.Iat,
            DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString(), ClaimValueTypes.Integer64),
    },
    expires: DateTime.UtcNow.AddSeconds(90),                              // 90초 후 만료
    signingCredentials: new SigningCredentials(key, SecurityAlgorithms.RsaSha256));
var jwt = new JwtSecurityTokenHandler().WriteToken(token);`;
    case 'Python':
      return `import time
import uuid
import jwt  # PyJWT + cryptography

# 승인 메일로 받은 개인키(PEM 문자열)로 서명하는 예제
now = int(time.time())
token = jwt.encode(
    {
        "user_id": user_id,          # 고객사 회원 식별자
        "tenant_id": "${tenantId}",
        "event_id": ${eventId},
        "iat": now,
        "exp": now + 90,             # 90초 후 만료
        "jti": str(uuid.uuid4()),    # 1회용
    },
    private_key_pem,
    algorithm="RS256",
    headers={"kid": kid},            # 승인 메일의 키 버전 (예: "1")
)`;
  }
}

export const sessionForm = (domain: string) => `<form method="GET" action="https://${domain}/api/issuance/session">
  <input type="hidden" name="JWT" value="{발급한 JWT}">
</form>
<script>document.forms[0].submit();</script>`;
