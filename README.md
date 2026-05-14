# XP Arcade & Bar - Android TV Kiosk + Pix Backend

Projeto MVP para bloqueio/liberacao de estacoes gamer em Android TV integrado ao `xp-pdv`.

- App Android TV nativo em Kotlin + Jetpack Compose (modo kiosk pragmatico)
- App em modo PDV: a venda acontece no caixa e a TV libera automaticamente pelo `xp-pdv`
- Backend Node.js + TypeScript + Express + Prisma + PostgreSQL mantido como legado/alternativa futura
- Provider Pix `MOCK`/`SICOOB` mantido no backend legado, mas nao e usado no fluxo atual do APK

## Modo atual: xp-pdv como backend da TV

O APK agora aponta direto para o PDV:

```text
Backend URL: https://xp-pdv.vercel.app
Healthcheck:  GET /api/health
Polling TV:   GET /api/integrations/tv/status?stationId=tv-01
Header TV:    x-device-key: <XP_TV_DEVICE_KEY>
```

Estacoes iniciais:

```text
tv-01 = TV 01 - PS5
tv-02 = TV 02 - Simulador
```

Como o PDV identifica a TV:

1. Na primeira abertura do APK, selecione `TV 01 - PS5` ou `TV 02 - Simulador`.
2. O APK salva localmente o `stationId` escolhido.
3. No PDV, ao vender um produto Gameplay, o operador escolhe a mesma estacao.
4. A venda salva a liberacao dessa estacao no banco do `xp-pdv`.
5. O APK consulta o status pelo mesmo `stationId`.
6. Quando o status volta `PREPARING`, a TV mostra 30 segundos de preparo.
7. Quando o status vira `ACTIVE`, a TV libera ate `unlockedUntil`.

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
2. Se for a primeira abertura, o APK pede para escolher qual TV esta sendo configurada.
3. APK fica bloqueado aguardando liberacao do caixa.
4. APK consulta o `xp-pdv`: `GET /api/integrations/tv/status?stationId=...`.
5. Caixa conclui a venda no PDV e escolhe a estacao da TV.
6. O `xp-pdv` grava a sessao com 30 segundos de preparacao.
7. APK recebe `PREPARING` e mostra a contagem 30, 29, 28...
8. Ao zerar, o APK recebe/ativa `ACTIVE`, salva a sessao localmente e mostra o contador.
9. Se a TV reiniciar, o APK recupera a sessao salva no DataStore.
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

### Webhooks

- `POST /api/webhooks/sicoob` (producao)
- `POST /api/webhooks/mock` (MVP)

### Admin

- `POST /api/admin/stations/:stationId/force-unlock`
- `POST /api/admin/sessions/:sessionId/end`
- `POST /api/admin/payments/:providerPaymentId/mock-confirm`

### Integracoes

- `POST /api/integrations/pdv/release`
- `GET /api/integrations/tv/status?stationId=tv-01`

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

Na primeira abertura, o app mostra duas opcoes prontas:

- `TV 01 - PS5`
- `TV 02 - Simulador`

Selecione pelo controle remoto. Essa escolha fica salva na propria TV.

No app, use o admin local apenas se precisar ajustar:
- Nome da estacao
- `stationId`
- `deviceKey` (chave da TV usada no endpoint `/api/integrations/tv/status`)
- URL do PDV
- PIN admin

Valores recomendados:

```text
TV 01 - PS5
Backend URL: https://xp-pdv.vercel.app
stationId: tv-01

TV 02 - Simulador
Backend URL: https://xp-pdv.vercel.app
stationId: tv-02
```

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

## 11. Backend legado: Mock primeiro, Sicoob depois

Esta secao vale apenas se o backend Express separado voltar a ser usado.

### MVP funcional legado

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
3. APK em modo `PDV` ou `Hibrido` consulta `GET /api/integrations/tv/status?stationId=...`.
4. Ao detectar sessao ativa, a TV libera automaticamente.

Headers para integracao do PDV:
- `x-integration-key: <PDV_INTEGRATION_KEY>`

Headers para consulta da TV:
- `x-device-key: <DEVICE_KEY_DA_TV>`

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

Resposta exemplo da TV:
```json
{
  "stationId": "tv-01",
  "status": "ACTIVE",
  "saleId": "VEN-20260514-ABC",
  "planCode": "SIMULADOR-10",
  "unlockedUntil": "2026-05-14T20:40:00.000Z",
  "remainingSeconds": 582,
  "serverTime": "2026-05-14T20:30:18.000Z"
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
