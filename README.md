# Expense Tracker

App Android personal para registrar gastos y detectarlos automáticamente
a partir de notificaciones de pago (banco, billeteras, etc.), sin nunca
confirmar un gasto sin que el usuario lo revise.

## Cómo funciona

- `NotificationListenerService` capta notificaciones de otras apps. Un
  parser genérico (regex sobre `Gs.`/`₲`/`PYG` + monto) las convierte en
  un `ExpenseCandidate` — **nunca** un gasto confirmado directamente.
- Antes de crear el candidato, se chequea si ya existe uno muy parecido
  detectado hace poco (mismo monto+moneda, comercio equivalente, dentro
  de una ventana de 5 minutos) — evita duplicar cuando el banco y una
  billetera avisan la misma compra casi al mismo tiempo.
- El candidato dispara una notificación propia con **Aceptar / Editar /
  Rechazar**. Aceptar y Rechazar son idempotentes: repetir la acción
  sobre un candidato ya resuelto no hace nada.
- Hay una bandeja interna (tab "Bandeja") que sobrevive aunque se
  descarte la notificación de Android — la fuente de verdad es el
  `ExpenseCandidate` en la base, no la notificación del sistema.
- Un Quick Settings Tile abre una pantalla de alta rápida, que reutiliza
  el mismo caso de uso que la confirmación de candidatos (la única
  diferencia es el campo `source`).
- Categorías fijas seleccionables al aceptar/editar un gasto.
- Tab "Actividad": traza de auditoría de todo lo que pasó (detectado →
  aceptado/editado/rechazado → registrado), separada de la Bandeja
  (que solo muestra lo pendiente).
- Exportar backup en JSON desde el Dashboard (vía el selector de
  archivos del sistema, sin pedir permisos de storage).

## Arquitectura

Capas separadas al estilo Clean Architecture:

```text
ui/            Compose (MainActivity, QuickAddActivity)
notification/  NotificationListenerService, parser, receiver de acciones
quicksettings/ TileService
domain/        model/ repository/ (interfaces) usecase/ — sin Android
data/local/    implementación con SQLite detrás de las interfaces de domain
di/            ServiceLocator + AppContainer (DI manual, sin Hilt)
```

El dominio (`domain/`) no importa nada de Android ni de SQLite — solo
conoce sus propias interfaces de repositorio. Eso es lo que permite tener
tests unitarios puros en `src/test/` (11 tests, corren en JVM sin
emulador) para la idempotencia y la deduplicación.

**Por qué SQLite directo y no Room:** Room 2.8.x necesita KSP para el
codegen de `@Entity`/`@Dao`, y al momento de armar este proyecto KSP
todavía no publicaba soporte para Kotlin 2.4.10 (el que usa el Kotlin
embebido de AGP 9). En vez de bajar la versión de Kotlin del proyecto
entero por una sola librería, `data/local/DbHelper.kt` implementa la
persistencia a mano detrás de las mismas interfaces de dominio. Migrar a
Room después no debería tocar nada fuera de `data/local/`.

**Por qué DI manual y no Hilt:** el grafo de dependencias es chico
(un puñado de repositorios y casos de uso) — `di/AppContainer.kt` arma
todo a mano en unas pocas líneas, sin la complejidad de un framework.

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

## Cómo correr los tests

```bash
cd ~/workspaces/expense-tracker
JAVA_HOME=/home/eliasgonzalez/.sdkman/candidates/java/current ./gradlew testDebugUnitTest
```

Son tests puros de JVM (sin Robolectric ni emulador) sobre los casos de
uso de dominio, con fakes en memoria de los repositorios
(`src/test/kotlin/.../domain/usecase/FakeRepositories.kt`).

## Cómo probar la detección

No hace falta un pago real: cualquier notificación (de cualquier app) cuyo
texto contenga un patrón como `Gs. 85.000`, `₲85.000` o `PYG 85000` dispara
el candidato. Formas rápidas de generarla:

- Enviarte un mensaje/notificación de prueba (por ejemplo desde Telegram o
  un recordatorio) con ese texto.
- Usar `adb shell "cmd notification post -t '<título>' <tag> '<texto>'"`
  (con comillas simples adentro, para que sobrevivan el viaje por varios
  shells si se corre desde WSL vía el `adb.exe` de Windows).

Al aparecer la notificación "💳 Gasto detectado", probar los tres botones y
verificar en la pestaña **Bandeja** que el candidato desaparece de pendientes
al aceptar/rechazar, en **Actividad** que quedó la traza completa, y en
**Dashboard** que el monto se suma solo cuando se acepta o edita (nunca si
está pendiente o rechazado).

## Qué NO tiene todavía

Room (ver arriba por qué), Hilt, parsers por banco específico (el parser
genérico no distingue Itaú de Google Wallet), merchant aliases/aprendizaje
de usuario, presupuestos, voz, OCR, ni sincronización con ningún proveedor
externo — la app es 100% local. El export a JSON es la única vía de
backup por ahora.
