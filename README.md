# XP Arcade & Bar - Android TV Kiosk + Pix Backend

Projeto MVP para bloqueio/liberacao de estacoes gamer em Android TV via Pix.

- App Android TV nativo em Kotlin + Jetpack Compose (modo kiosk pragmatico)
- Backend Node.js + TypeScript + Express + Prisma + PostgreSQL (Neon Tech)
- Provider de pagamento com troca simples entre `MOCK` e `SICOOB`

## 1. Arquitetura de pastas

```text
xpgateway/
  android-tv-kiosk/
  backend/
  docs/
    ARCHITECTURE.md
    ANDROID_TV_LIMITATIONS.md
```

Documentacao inicial:
- Arquitetura tecnica: `docs/ARCHITECTURE.md`
- Limitacoes reais Android TV/HDMI/kiosk: `docs/ANDROID_TV_LIMITATIONS.md`

## 2. Fluxo tecnico

1. TV inicia -> app abre em modo fullscreen.
2. TV gera automaticamente Pix de 20 minutos (sem depender de controle).
3. APK chama backend: `POST /api/sessions/create-payment`.
4. Backend valida estacao, token, duracao e preco oficial.
5. Backend cria cobranca no provider ativo (`MOCK` no MVP).
6. APK exibe QR Pix + copia e cola.
7. APK faz polling: `GET /api/sessions/:sessionId/status`.
8. Ao pagamento aprovado, sessao e liberada ate `expiresAt`.
9. APK mantem contador local persistente (DataStore) e recupera apos reboot.
10. Ao expirar, volta automaticamente para tela de bloqueio.

## 3. Backend (Node + TypeScript)

Caminho: `backend/`

### 3.1 Pre-requisitos

- Node.js 20+
- Conta Neon Tech (Postgres serverless)

### 3.2 Configuracao (Neon)

1. Crie projeto no Neon e banco `xp_arcade`.
2. Copie URLs do Neon:
- `pooled connection string` (runtime)
- `direct connection string` (migrations)

```bash
cd backend
cp .env.example .env
```

Edite `.env` com:
- `DATABASE_URL` = URL pooled do Neon
- `DIRECT_URL` = URL direct do Neon

### 3.3 Instalacao e banco

```bash
npm install
npx prisma generate
npm run db:init
```

### 3.4 Subir backend

```bash
npm run dev
```

Se o ambiente bloquear watch/esbuild, use:

```bash
npm run build
node dist/server.js
```

Backend padrao: `http://localhost:8080`

### 3.5 Variaveis importantes

Arquivo exemplo: `backend/.env.example`

- `DATABASE_URL` (Neon pooled)
- `DIRECT_URL` (Neon direct)
- `PAYMENT_PROVIDER=MOCK|SICOOB`
- `STATION_TOKEN_SALT`
- `ADMIN_API_KEY`
- `PDV_INTEGRATION_KEY` (autorizacao do sistema de PDV)
- `SICOOB_CLIENT_ID`, `SICOOB_CLIENT_SECRET`
- `SICOOB_PIX_KEY` (chave Pix recebedor)
- `SICOOB_TOKEN_URL` (OAuth client_credentials)
- `SICOOB_BASE_URL` (Pix API v2)
- `SICOOB_SCOPES` (ex: `cob.write cob.read pix.read`)
- `SICOOB_CERT_BASE64`, `SICOOB_KEY_BASE64` (mTLS)

### 3.6 Bootstrap inicial

`npm run db:init` cria/atualiza:
- Estacao `tv-01`
- Nome `TV 01 - PS5`
- Token local `tv01-secret-token`
- Preco fixo:
  - 20 min = R$ 15

## 4. Endpoints principais

### Sessoes

- `POST /api/sessions/create-payment`
- `GET /api/sessions/:sessionId/status`

### Estacoes

- `GET /api/stations/:stationId/config`
- `GET /api/stations/:stationId/last-payment`
- `GET /api/stations/:stationId/live-session`

### Webhooks

- `POST /api/webhooks/sicoob` (producao)
- `POST /api/webhooks/mock` (MVP)

### Admin

- `POST /api/admin/stations/:stationId/force-unlock`
- `POST /api/admin/sessions/:sessionId/end`
- `POST /api/admin/payments/:providerPaymentId/mock-confirm`

### Integracoes

- `POST /api/integrations/pdv/release`

## 5. Banco PostgreSQL (Neon)

Schema Prisma: `backend/prisma/schema.prisma`

Modelos/tabelas:
- `stations`
- `pricing_options`
- `sessions`
- `payments`
- `admin_pin`
- `audit_logs`

## 6. Android TV APK (Kotlin + Compose)

Caminho: `android-tv-kiosk/`

### 6.1 Build no Android Studio

1. Abra `android-tv-kiosk` no Android Studio.
2. Aguarde sync do Gradle.
3. Build release:
- `Build > Generate Signed Bundle / APK`
- ou `Build > Build APK(s)` para debug

APK final normalmente em:
- `android-tv-kiosk/app/build/outputs/apk/release/`
- `android-tv-kiosk/app/build/outputs/apk/debug/`

### 6.2 Build via terminal

Se voce gerar/usar o Gradle Wrapper no projeto:

```bash
cd android-tv-kiosk
./gradlew assembleDebug
./gradlew assembleRelease
```

## 7. Instalacao por pendrive (sideload)

1. Copie o `.apk` para pendrive.
2. Conecte na TV Android.
3. Ative instalacao de fontes desconhecidas para o app de arquivos.
4. Abra e instale o APK.

## 8. Configuracao inicial na TV

No app, use o admin local para ajustar:
- Nome da estacao
- `stationId`
- `stationToken`
- URL do backend
- `adminApiKey`
- Modo de liberacao (`PIX`, `PDV`, `Hibrido`)
- PIN admin

Acesso admin por sequencia secreta no controle:
- `UP UP DOWN DOWN LEFT RIGHT LEFT RIGHT OK`

PIN padrao inicial: `1234`

## 9. Modo kiosk/launcher

Implementado no MVP:
- Fullscreen imersivo
- Categoria `HOME` no manifest (launcher custom)
- `BootReceiver` para auto start apos boot
- Bloqueio logico de navegacao de volta no app

Limitacoes reais em `docs/ANDROID_TV_LIMITATIONS.md`.

## 10. Sobre HDMI (importante)

Em TVs Android comuns, APK de terceiro geralmente nao controla troca de HDMI de forma universal.

Estrategia MVP:
- App como launcher/tela de bloqueio.
- Sessao ativa mostra "TV liberada" + contador.
- Operacao HDMI guiada ao usuario no periodo pago.
- Ao expirar, app retorna ao bloqueio.

## 11. Mock primeiro, Sicoob depois

### MVP funcional (agora)

- `PAYMENT_PROVIDER=MOCK`
- Fluxo automatico: QR de 20 min aparece sem controle remoto
- Em mock, confirme pagamento por endpoint admin (`mock-confirm`) para liberar timer

### Integracao Sicoob (implementada no backend)

Arquivo:
- `backend/src/modules/payments/paymentProvider.sicoob.ts`

Fluxo implementado:
1. OAuth `client_credentials` com mTLS.
2. Criacao de cobranca imediata `PUT /cob/{txid}`.
3. Retorno de `brcode` e QR em base64 para o app.
4. Polling de status em `GET /cob/{txid}` no endpoint de sessao.
5. Webhook `/api/webhooks/sicoob` aceitando payload Pix com `pix[].txid`.

Observacao importante (Vercel):
- O webhook oficial do Sicoob e baseado em mTLS no canal de notificacao.
- Vercel nao oferece terminacao mTLS custom para validar certificado cliente do Sicoob.
- Por isso, neste projeto a liberacao automatica funciona tambem por polling de status no backend (independe de webhook).
- Se quiser webhook com mTLS estrito, hospede esse endpoint em infraestrutura propria (Nginx/API Gateway) e encaminhe para o backend.

### Integracao PDV (backend + APK)

Fluxo:
1. Seu PDV conclui a venda e chama `POST /api/integrations/pdv/release`.
2. Backend cria uma sessao ativa na estacao com provider `PDV`.
3. APK em modo `PDV` ou `Hibrido` consulta `GET /api/stations/:stationId/live-session`.
4. Ao detectar sessao ativa, a TV libera automaticamente.

Headers para integracao do PDV:
- `x-integration-key: <PDV_INTEGRATION_KEY>`

Body exemplo:
```json
{
  "integrationId": "pdv-xp-main",
  "saleId": "VEN-20260514-00123",
  "stationId": "tv-01",
  "planCode": "PS5_20",
  "durationMinutes": 20,
  "amount": 15.00,
  "paidAt": "2026-05-14T20:10:00Z",
  "operator": "caixa-01",
  "customerId": "cli-998"
}
```

## 12. Seguranca aplicada

- Sem credencial sensivel no APK
- Token por estacao (`x-station-id` + `x-station-token`)
- Preco validado no backend (nao confia no cliente)
- Logs de auditoria para eventos criticos

## 13. Estado da entrega

Entregue neste MVP:
- Arquitetura e limitacoes documentadas
- Backend completo com provider mock e estrutura Sicoob
- APK Android TV com fluxo completo bloqueio -> Pix -> sessao -> expiracao
- Admin local com configuracoes e acoes de liberacao/encerramento

Pendencias para producao final:
- Ajustar endpoints finais de token/baseURL conforme credencial da sua cooperativa
- Hardening por modelo de TV em campo
- Assinatura/gerenciamento de chave de release definitiva
