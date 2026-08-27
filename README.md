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

- Android Studio (o al menos JDK 17+ y Android SDK con `compileSdk 36`).
- Un teléfono Android 8.0+ (API 26+) conectado por USB con depuración
  habilitada, o un emulador.

## Cómo correrla

1. Abrir esta carpeta como proyecto en Android Studio y dejar que sincronice
   Gradle (usa el wrapper incluido, Gradle 9.7.1).
2. Conectar el celular por USB y correr la app (▶) apuntando al dispositivo.
3. Al abrir por primera vez, otorgar el permiso de **acceso a notificaciones**
   a la app: `Ajustes → Apps → Acceso especial → Acceso a notificaciones →
   Expense Tracker POC`. Sin este permiso el listener no recibe nada.
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
