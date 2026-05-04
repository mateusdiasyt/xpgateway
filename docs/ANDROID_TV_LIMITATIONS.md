# Limitações Reais Android TV (Kiosk / HDMI)

## 1) Botão Home e saída do app
Em Android TV comum, app de terceiro não consegue “bloquear tudo” de forma absoluta sem modo corporativo/device owner.

- Sem device owner, `startLockTask()` pode não segurar Home em todos os modelos.
- A abordagem mais confiável para uso comercial em TV comum é ser launcher padrão.

Referência:
- https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode

## 2) Troca de HDMI por app comum
Controle direto do input HDMI (mudar para HDMI1/2 automaticamente) não é padronizado para apps comuns.

- Varia por fabricante/firmware.
- APIs de TV Input e controle profundo costumam exigir privilégios de sistema/OEM.

Referências:
- https://developer.android.com/reference/kotlin/android/media/tv/TvInputManager
- https://source.android.com/devices/tv/

## 3) TV App / TIF / permissões avançadas
Partes do ecossistema de TV Input são reservadas a apps de sistema ou assinadas pelo fabricante.

- Isso limita controle completo de entrada e eventos em APK sideload comum.

Referência:
- https://source.android.com/devices/tv/

## 4) Kiosk robusto de verdade
Para kiosk forte (com bloqueio mais rígido), o ideal é:

- Provisionar como device owner (quando possível).
- Definir launcher custom como padrão.
- Controlar permissões e comportamento por política de dispositivo.

Referência:
- https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode

## 5) Estratégia recomendada para este projeto
MVP realista para Android TV de mercado:

1. App em full screen imersivo.
2. App como launcher padrão.
3. Bloqueio visual e funcional enquanto sem sessão ativa.
4. Durante sessão paga, mostrar “TV liberada” + instruções para HDMI.
5. Ao expirar, trazer lock screen para frente automaticamente.

## 6) Extensão futura
Se precisar controle físico real de bloqueio/liberação independente de limitações do Android TV:

- Integrar relé/ESP32 (ex.: alimentação/sinal do setup).
- Integrar fluxo de automação externa ao backend (webhook/event bus).
