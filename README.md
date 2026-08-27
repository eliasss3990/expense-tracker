# Expense Tracker — POC

Prueba de concepto para validar la pieza más riesgosa del plan de
`finanzas_personales_arquitectura.md`: detectar notificaciones de pago,
convertirlas en un `ExpenseCandidate` (nunca un gasto confirmado
automáticamente), y dejar que el usuario Acepte/Edite/Rechace desde una
notificación propia. También incluye un Quick Settings Tile para registro
manual ultrarrápido.

No usa Room ni Hilt todavía — todo vive en memoria (`CandidateStore`) a
propósito, para iterar rápido sobre el mecanismo antes de invertir en
persistencia.

## Qué valida esta POC

- `NotificationListenerService` capturando notificaciones de otras apps.
- Un parser genérico (regex sobre `Gs.`/`₲`/`PYG` + monto) que no depende de
  un banco específico — el contrato real (`canHandle`/`parse`) queda para
  cuando se agreguen parsers por fuente.
- Notificación propia con acciones **Aceptar / Editar / Rechazar**, donde
  Aceptar/Rechazar son idempotentes (un candidato ya procesado ignora
  acciones repetidas).
- Bandeja interna (tab "Bandeja") que sobrevive aunque se descarte la
  notificación de Android — la fuente de verdad es el `ExpenseCandidate`,
  no la notificación del sistema.
- Quick Settings Tile que abre una pantalla mínima de alta rápida.

## Requisitos

- **No hace falta Android Studio.** El Android SDK (cmdline-tools,
  platform 37, build-tools) y el JDK ya están instalados del lado de WSL
  (`~/Android/Sdk`, JDK 21 vía sdkman).
- Un teléfono Android 8.0+ (API 26+) conectado por USB con depuración
  habilitada.
- `adb` en el PATH de Windows para instalar en el celular (el USB queda
  atado al lado Windows).

## Cómo correrla

Opción rápida: doble clic en `C:\Scripts\expense-tracker-deploy\deploy.bat`
— compila dentro de WSL y automáticamente instala + abre la app en el cel.

Manual, paso a paso:

1. Compilar dentro de WSL:
   ```bash
   cd ~/workspaces/expense-tracker
   JAVA_HOME=/home/eliasgonzalez/.sdkman/candidates/java/current ./gradlew assembleDebug
   ```
2. Instalar y abrir desde Windows (con el celular conectado por USB):
   ```
   adb install -r \\wsl.localhost\Ubuntu\home\eliasgonzalez\workspaces\expense-tracker\app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.eliasgonzalez.expensetracker/.ui.MainActivity
   ```
3. Al abrir, la app pide sola el permiso de notificaciones (Android 13+) y,
   si falta el **acceso a notificaciones** (`NotificationListenerService`,
   que Android no deja pedir con un diálogo por lo sensible que es),
   muestra un banner amarillo con un botón "Activar ahora" que lleva
   directo a esa pantalla de Ajustes. Al volver, el banner desaparece solo.
4. Agregar el tile "Gasto" a los Ajustes rápidos: deslizar el panel de
   Ajustes rápidos → lápiz de editar → arrastrar el tile de Expense Tracker.

## Cómo probar la detección

No hace falta un pago real: cualquier notificación (de cualquier app) cuyo
texto contenga un patrón como `Gs. 85.000`, `₲85.000` o `PYG 85000` dispara
el candidato. Formas rápidas de generarla:

- Enviarte un mensaje/notificación de prueba (por ejemplo desde Telegram o
  un recordatorio) con ese texto.
- Usar `adb shell cmd notification post` o una app de notificaciones de
  prueba con ese contenido.

Al aparecer la notificación "💳 Gasto detectado", probar los tres botones y
verificar en la pestaña **Bandeja** que el candidato desaparece de pendientes
al aceptar/rechazar, y en **Dashboard** que el monto se suma solo cuando se
acepta o edita (nunca si está pendiente o rechazado).

## Qué NO es esta POC

No implementa Room, categorías, merchant normalization, deduplicación,
parsers por banco, sincronización, ni persistencia entre reinicios de la
app (el `CandidateStore` es un singleton en memoria). Eso es intencional:
la POC existe para validar el mecanismo Candidate → Confirm → Expense y
cómo se siente en el celular antes de invertir en la Fase 1 completa del
plan.
