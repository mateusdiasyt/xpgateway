# XP Arcade TV Kiosk - Arquitetura Inicial

## Objetivo
Construir um sistema Android TV + Backend para bloqueio/liberação de estação gamer via pagamento Pix, com instalação por APK sideload (pendrive) sem dependência de Google Play.

## Estrutura de Pastas

```text
xpgateway/
  android-tv-kiosk/
    app/
      src/main/
        java/com/xparcade/tvkiosk/
          app/
          data/
          domain/
          ui/
          kiosk/
          admin/
          receiver/
        res/
      build.gradle.kts
    build.gradle.kts
    settings.gradle.kts
    gradle.properties

  backend/
    src/
      config/
      core/
      db/
      modules/
        payments/
        sessions/
        stations/
        admin/
        webhooks/
      app.ts
      server.ts
    prisma/
      schema.prisma
      seed.ts
    package.json
    tsconfig.json
    .env.example

  docs/
    ARCHITECTURE.md
    ANDROID_TV_LIMITATIONS.md

  README.md
```

## Fluxo Técnico (Alta Visão)

1. TV inicia e abre o app (`BootReceiver` + launcher/leanback).
2. Tela bloqueada exibe planos e preços recebidos/configurados.
3. Usuário escolhe tempo.
4. APK chama backend `/api/sessions/create-payment` com `stationId` + `durationMinutes`.
5. Backend valida preço oficial (não confia no preço enviado pelo APK).
6. Backend usa provider ativo:
   - `PaymentProviderMock` (MVP)
   - `PaymentProviderSicoob` (produção)
7. Backend retorna `sessionId`, `qrCode`, `pixCopiaECola`, `status=PENDING`.
8. APK exibe QR Pix e faz polling em `/api/sessions/:sessionId/status` a cada 3-5s.
9. Quando status vira `PAID`, APK inicia sessão local e libera uso por tempo.
10. Contador roda por `expiresAt` persistido (DataStore), inclusive após reboot.
11. Quando expira, APK invalida sessão local e volta ao bloqueio.
12. Webhook do Sicoob confirma pagamento no backend e atualiza `sessions/payments`.

## Estados de Aplicação

- `IDLE`
- `SELECTING_TIME`
- `PAYMENT_PENDING`
- `PAYMENT_PAID`
- `SESSION_ACTIVE`
- `SESSION_WARNING`
- `SESSION_EXPIRED`
- `ADMIN_MODE`
- `ERROR`

## Estratégia de Segurança

- Credenciais sensíveis apenas no backend.
- Autenticação estação: `stationId` + `stationToken` (header `X-Station-Token`).
- Backend valida:
  - estação ativa
  - duração permitida
  - preço oficial da tabela
- Logs de auditoria para ações administrativas.
- Webhook com validação de assinatura (na integração real Sicoob).

## Kiosk e Liberação

- Base: app TV-first em tela cheia imersiva.
- Caminho recomendado em TVs comuns:
  - App como launcher padrão.
  - Durante sessão, mostrar estado “TV liberada” com instruções de uso HDMI.
  - Ao fim, retornar tela bloqueada.
- Controle direto de HDMI depende de OEM/permissões de sistema; arquitetura ficará preparada para extensões (CEC/relay/ESP32).

## Fases de Entrega

### Fase 1 (MVP)
- Backend com mock Pix.
- APK com fluxo completo ponta a ponta em mock.
- Sessão persistente + polling + administração local.

### Fase 2 (Produção Sicoob)
- Implementar `PaymentProviderSicoob`.
- OAuth/certificados/webhook assinado.
- Hardening operacional e observabilidade.
