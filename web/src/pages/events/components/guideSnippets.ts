/** 사전 가이드 (UI-TP-6-1) 예제 코드 — 이벤트 ID·도메인을 넣어 만든다 */

export const GUIDE_LANGUAGES = ['Java', 'JavaScript (ES6)', 'PHP', 'C# / .NET', 'Python'] as const;
export type GuideLanguage = (typeof GUIDE_LANGUAGES)[number];

export const jwtStructure = (eventId: string) => `// Header
{ "alg": "HS256", "typ": "JWT" }

// Payload
{
  "eventId": "${eventId}",
  "sub": "user-98413",          // 고객사 회원 식별자
  "iat": 1790000000,
  "exp": 1790000090,            // 발급 후 1~2분 이내
  "jti": "b7f2c1e0-4a6d-..."    // 1회용 ID (재사용 시 거부)
}`;

export function jwtExample(lang: GuideLanguage, eventId: string) {
  switch (lang) {
    case 'Java':
      return `import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

// 전달받은 연동키(secret)로 JWT를 만드는 예제 (jjwt 0.12)
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
String token = Jwts.builder()
    .claim("eventId", "${eventId}")
    .subject(userId)                                            // 고객사 회원 식별자
    .id(UUID.randomUUID().toString())                           // jti: 1회용
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 90_000)) // 90초 후 만료
    .signWith(key)
    .compact();`;
    case 'JavaScript (ES6)':
      return `import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

// 전달받은 연동키(secret)로 JWT를 만드는 예제 (Node.js, jsonwebtoken)
const token = jwt.sign(
  { eventId: '${eventId}' },
  secret,
  {
    algorithm: 'HS256',
    subject: userId,      // 고객사 회원 식별자
    jwtid: randomUUID(),  // jti: 1회용
    expiresIn: '90s',     // 90초 후 만료
  },
);`;
    case 'PHP':
      return `<?php
use Firebase\\JWT\\JWT;

// 전달받은 연동키($secret)로 JWT를 만드는 예제 (firebase/php-jwt)
$now = time();
$payload = [
    'eventId' => '${eventId}',
    'sub'     => $userId,                    // 고객사 회원 식별자
    'iat'     => $now,
    'exp'     => $now + 90,                  // 90초 후 만료
    'jti'     => bin2hex(random_bytes(16)),  // 1회용
];
$token = JWT::encode($payload, $secret, 'HS256');`;
    case 'C# / .NET':
      return `using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.IdentityModel.Tokens;

// 전달받은 연동키(secret)로 JWT를 만드는 예제 (System.IdentityModel.Tokens.Jwt)
var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret));
var token = new JwtSecurityToken(
    claims: new[]
    {
        new Claim("eventId", "${eventId}"),
        new Claim(JwtRegisteredClaimNames.Sub, userId),                  // 고객사 회원 식별자
        new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString()), // 1회용
        new Claim(JwtRegisteredClaimNames.Iat,
            DateTimeOffset.UtcNow.ToUnixTimeSeconds().ToString(), ClaimValueTypes.Integer64),
    },
    expires: DateTime.UtcNow.AddSeconds(90),                              // 90초 후 만료
    signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));
var jwt = new JwtSecurityTokenHandler().WriteToken(token);`;
    case 'Python':
      return `import time
import uuid
import jwt  # PyJWT

# 전달받은 연동키(secret)로 JWT를 만드는 예제
now = int(time.time())
token = jwt.encode(
    {
        "eventId": "${eventId}",
        "sub": user_id,            # 고객사 회원 식별자
        "iat": now,
        "exp": now + 90,           # 90초 후 만료
        "jti": str(uuid.uuid4()),  # 1회용
    },
    secret,
    algorithm="HS256",
)`;
  }
}

export const sessionForm = (domain: string) => `<form method="GET" action="https://${domain}/session">
  <input type="hidden" name="token" value="{발급한 JWT}">
</form>
<script>document.forms[0].submit();</script>`;
