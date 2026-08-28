# Expense Tracker

App Android personal para registrar gastos y detectarlos automáticamente
a partir de notificaciones de pago (banco, billeteras, etc.), sin nunca
confirmar un gasto sin que el usuario lo revise.

## Cómo funciona

- `NotificationListenerService` capta notificaciones de otras apps. Cada
  fuente prueba primero su propio parser (por ahora, `UenoBankParser`
  para la app y los mails de Ueno Bank) y si ninguno aplica cae al
  parser genérico (regex sobre `Gs.`/`₲`/`PYG` + monto). El resultado es
  siempre un `ExpenseCandidate` — **nunca** un gasto confirmado
  directamente.
- Antes de crear el candidato, se chequea si ya existe uno muy parecido
  detectado hace poco (mismo monto+moneda, comercio equivalente o sin
  comercio confiable todavía, dentro de una ventana de 5 minutos) —
  evita duplicar cuando el banco y una billetera/mail avisan la misma
  compra casi al mismo tiempo. El chequeo está protegido con un `Mutex`
  para que dos notificaciones casi simultáneas no se cuelen como dos
  candidatos separados.
- El candidato dispara una notificación propia con **Aceptar / Editar /
  Rechazar**. Aceptar y Rechazar son idempotentes: repetir la acción
  sobre un candidato ya resuelto no hace nada.
- Hay una bandeja interna (tab "Bandeja") que sobrevive aunque se
  descarte la notificación de Android — la fuente de verdad es el
  `ExpenseCandidate` en la base, no la notificación del sistema. Editar
  ahí es inline, en la misma tarjeta (no una ventana separada).
- Un Quick Settings Tile abre una ventanita flotante (overlay propio,
  no una Activity) para el alta rápida, con su propio soporte de tema
  claro/oscuro. Reutiliza el mismo caso de uso que la confirmación de
  candidatos — la única diferencia es el campo `source`.
- Dashboard: resumen del mes, desglose por categoría y lista de
  "Últimos gastos" con paginación ("Cargar más" cada 20), filtros por
  período (hoy / últimos 7 días / este mes / cualquier otro mes con
  movimientos) y por categoría, y edición/borrado directo desde la
  lista — con selección múltiple y confirmación antes de borrar.
- También se puede cargar un gasto a mano desde el Dashboard (botón
  flotante), sin pasar por una notificación detectada.
- Categorías fijas seleccionables al aceptar/editar/cargar un gasto,
  cada una con su color e ícono propios (mismo mapeo en toda la app,
  incluida la ventanita del Quick Settings Tile).
- Tab "Actividad": traza de auditoría de todo lo que pasó (detectado →
  aceptado/editado/rechazado/registrado/eliminado), separada de la
  Bandeja (que solo muestra lo pendiente). También paginada.
- Exportar backup en JSON desde el Dashboard (vía el selector de
  archivos del sistema, sin pedir permisos de storage).
- Ícono adaptativo y splash screen propios (ver `app/src/main/res/`) en
  vez de los genéricos que trae la plantilla de Android.

## Arquitectura

Capas separadas al estilo Clean Architecture:

```text
app/src/main/kotlin/com/eliasgonzalez/expensetracker/
├── ui/                       Compose - toda la pantalla principal
│   ├── MainActivity.kt         Dashboard, Bandeja, Actividad, nav inferior
│   ├── QuickAddActivity.kt     Editar candidato desde la notificación del sistema
│   ├── AmountInput.kt          Sanitizado del campo Monto (función pura, testeada)
│   ├── ExpenseFilters.kt       Filtros de período/mes del Dashboard (funciones puras, testeadas)
│   └── theme/                  Colores, tipografía y estilo por categoría
├── notification/             Detección de gastos vía notificaciones
│   ├── ExpenseNotificationListenerService.kt
│   ├── ParserEngine.kt          Prueba el parser de la fuente antes que el genérico
│   ├── NotificationParser.kt    Interfaz que implementa cada parser
│   ├── UenoBankParser.kt        Parser específico (app + mail de Ueno Bank)
│   ├── GenericPurchaseParser.kt Red de contención para fuentes sin parser propio
│   └── CandidateActionReceiver.kt  Acciones Aceptar/Editar/Rechazar de la notificación
├── quicksettings/            Quick Settings Tile
│   ├── RegisterExpenseTileService.kt
│   ├── QuickAddTrampolineActivity.kt  Activity invisible, solo para cerrar el panel QS
│   └── QuickAddOverlayService.kt      Ventanita flotante (SYSTEM_ALERT_WINDOW)
├── domain/                   Sin nada de Android - testeable en JVM puro
│   ├── model/                  Expense, ExpenseCandidate, ActivityEntry, Category
│   ├── repository/             Interfaces (implementadas en data/local)
│   └── usecase/                RegisterExpense, CreateCandidate, ConfirmCandidate,
│                                EditCandidate, RejectCandidate, EditExpense,
│                                DeleteExpense, ExportBackup
├── data/local/               Implementación con SQLite detrás de las interfaces de domain
└── di/                       ServiceLocator + AppContainer (DI manual, sin Hilt)
```

El dominio (`domain/`) no importa nada de Android ni de SQLite — solo
conoce sus propias interfaces de repositorio. Eso es lo que permite tener
tests unitarios puros en `src/test/` (36 tests en 11 archivos, corren en
JVM sin emulador) para la idempotencia, la deduplicación, los parsers,
los filtros del Dashboard y el sanitizado de inputs.

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

## Instalación

**Uso normal (sin cable, sin compilar nada):** cada push a `main` pasa por
CI (tests + build + E2E) y publica un APK firmado como GitHub Release. La
propia app chequea al abrir si hay una versión más nueva que la instalada
y avisa con un banner; también se puede forzar el chequeo a mano desde el
menú (⋮) del Dashboard. Para instalar por primera vez: descargar el APK
de la [última Release](https://github.com/eliasss3990/expense-tracker/releases/latest)
desde el celular y abrirlo (Android va a pedir permiso para instalar
desde esa fuente la primera vez).

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
   La primera vez que se usa, si falta el permiso "Aparecer encima de
   otras apps" (necesario para la ventanita flotante), la tile lleva
   directo a esa pantalla de Ajustes.

## Cómo correr los tests

```bash
cd ~/workspaces/expense-tracker
JAVA_HOME=/home/eliasgonzalez/.sdkman/candidates/java/current ./gradlew testDebugUnitTest
```

Son tests puros de JVM (sin Robolectric ni emulador) sobre los casos de
uso de dominio y sobre funciones puras de la UI (filtros, sanitizado de
inputs), con fakes en memoria de los repositorios
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

Room (ver arriba por qué), Hilt, parsers para más bancos/billeteras además
de Ueno Bank (el parser genérico no distingue Itaú de Google Wallet),
merchant aliases/aprendizaje de usuario, presupuestos, voz, OCR, ni
sincronización con ningún proveedor externo (Google Drive, etc.) — la app
es 100% local. El export a JSON es la única vía de backup por ahora.
