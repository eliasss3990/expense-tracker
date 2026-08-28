# Expense Tracker

App Android personal para registrar gastos y detectarlos automáticamente
a partir de notificaciones de pago (banco, billeteras, etc.), sin nunca
confirmar un gasto sin que el usuario lo revise.

## Cómo funciona

- `NotificationListenerService` capta notificaciones de otras apps. Cada
  fuente prueba primero su propio parser (`UenoBankParser` para la app y
  los mails de Ueno Bank, `BancoFamiliarParser` para las transferencias
  salientes de Banco Familiar/Eko vía Gmail) y si ninguno aplica cae al
  parser genérico (regex sobre `Gs.`/`₲`/`PYG` + monto, restringido a una
  lista blanca de paquetes conocidos). El resultado es siempre un
  `ExpenseCandidate` — **nunca** un gasto confirmado directamente.
- Las notificaciones de dinero **entrante** (transferencias recibidas,
  "Recibiste"/"acreditado"/etc.) se descartan explícitamente en cada
  parser — nunca se detectan como gasto.
- Antes de crear el candidato, se chequea si ya existe uno muy parecido
  detectado hace poco (mismo monto+moneda, comercio equivalente o sin
  comercio confiable todavía, dentro de una ventana de 5 minutos) —
  evita duplicar cuando el banco y una billetera/mail avisan la misma
  compra casi al mismo tiempo (o cuando un mismo banco manda dos mails
  de confirmación para la misma operación, como hace Banco Familiar). El
  chequeo está protegido con un `Mutex` compartido entre todos los casos
  de uso que tocan candidatos/gastos, para que operaciones casi
  simultáneas no se cuelen como duplicados ni pisen datos entre sí.
- El candidato dispara una notificación propia con **Aceptar / Editar /
  Rechazar**. Aceptar y Rechazar son idempotentes: repetir la acción
  sobre un candidato ya resuelto no hace nada.
- Hay una bandeja interna (tab "Bandeja") que sobrevive aunque se
  descarte la notificación de Android — la fuente de verdad es el
  `ExpenseCandidate` en la base, no la notificación del sistema. Editar
  ahí es inline, en la misma tarjeta (no una ventana separada).
- Un Quick Settings Tile abre una ventanita flotante (overlay propio,
  no una Activity) para el alta rápida, con su propio soporte de tema
  claro/oscuro (incluso si el tema del sistema cambia con la ventanita
  abierta). Reutiliza el mismo caso de uso que la confirmación de
  candidatos — la única diferencia es el campo `source`.
- Dashboard: resumen del mes, desglose por categoría y lista de
  "Últimos gastos" con paginación ("Cargar más" cada 20), filtros por
  período (hoy / últimos 7 días / este mes / cualquier otro mes con
  movimientos) y por categoría, edición/borrado directo desde la lista
  — con selección múltiple y confirmación antes de borrar — y un modal
  de detalle completo al tocar un gasto (incluye la descripción
  opcional, si tiene una cargada).
- También se puede cargar un gasto a mano desde el Dashboard (botón
  flotante), con descripción opcional, sin pasar por una notificación
  detectada. Los campos obligatorios (Monto, Comercio) se marcan con
  asterisco rojo.
- Categorías fijas seleccionables al aceptar/editar/cargar un gasto,
  cada una con su color e ícono propios (mismo mapeo en toda la app,
  incluida la ventanita del Quick Settings Tile).
- Tab "Actividad": traza de auditoría de todo lo que pasó (detectado →
  aceptado/editado/rechazado/registrado/eliminado), separada de la
  Bandeja (que solo muestra lo pendiente). También paginada.
- Onboarding de permisos unificado la primera vez que se abre la app
  (notificaciones + acceso a notificaciones en una sola pantalla, con
  opción de omitir), y un banner general de "permisos faltantes" el
  resto del tiempo — deslizable hacia los costados para descartarlo,
  como una notificación real, con colapso animado.
- La app chequea sola si hay una versión más nueva publicada en GitHub
  Releases al abrir (y a demanda desde el menú del Dashboard). Si hay
  una, un banner deslizable ofrece instalarla **directo desde la app**:
  descarga el APK con `DownloadManager` y lanza el instalador del
  sistema (`FileProvider` + `REQUEST_INSTALL_PACKAGES`), sin pasar por
  el navegador. No necesita ningún token — los assets de un repo público
  son de lectura libre.
- Exportar backup en JSON desde el Dashboard (vía el selector de
  archivos del sistema, sin pedir permisos de storage).
- Ícono adaptativo y splash screen propios (ver `app/src/main/res/`) en
  vez de los genéricos que trae la plantilla de Android.

## Arquitectura

Capas separadas al estilo Clean Architecture:

```text
app/src/main/kotlin/com/eliasgonzalez/expensetracker/
├── ExpenseTrackerApp.kt        Application - inicializa ServiceLocator
├── ui/                       Compose - toda la pantalla principal
│   ├── MainActivity.kt         Shell: navegación inferior, permisos, chequeo de updates
│   ├── QuickAddActivity.kt     Editar candidato desde la notificación del sistema
│   ├── dashboard/               Dashboard: resumen, filtros, lista, alta/edición manual
│   │   ├── DashboardScreen.kt
│   │   ├── ExpenseFilterSheet.kt
│   │   ├── ExpenseRow.kt
│   │   └── ManualAddSheet.kt
│   ├── tray/                    Bandeja de candidatos pendientes
│   │   └── TrayScreen.kt
│   ├── activitylog/              Traza de auditoría (tab "Actividad")
│   │   └── ActivityLogScreen.kt
│   ├── onboarding/                Onboarding de permisos (primera apertura)
│   │   ├── OnboardingPrefs.kt
│   │   └── PermissionsOnboardingScreen.kt
│   ├── banners/                   Banners deslizables (permisos faltantes, update disponible)
│   │   ├── MissingPermissionsBanner.kt
│   │   └── UpdateAvailableBanner.kt
│   ├── common/                    Helpers compartidos entre pantallas (ScreenSupport, etc.)
│   │   └── ScreenSupport.kt
│   └── theme/                     Colores, tipografía y estilo por categoría
│       ├── CategoryStyle.kt
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── notification/             Detección de gastos vía notificaciones
│   ├── ExpenseNotificationListenerService.kt
│   ├── ParserEngine.kt          Prueba el parser de la fuente antes que el genérico
│   ├── NotificationParser.kt    Interfaz que implementa cada parser
│   ├── NotificationContext.kt   title/text/bigText/packageName normalizado
│   ├── NotificationAccess.kt    Chequeo del permiso de acceso a notificaciones
│   ├── UenoBankParser.kt        Parser específico (app + mail de Ueno Bank)
│   ├── BancoFamiliarParser.kt   Parser específico (mails de Banco Familiar/Eko)
│   ├── GenericPurchaseParser.kt Red de contención para fuentes sin parser propio
│   └── CandidateActionReceiver.kt  Acciones Aceptar/Editar/Rechazar de la notificación
├── quicksettings/            Quick Settings Tile
│   ├── RegisterExpenseTileService.kt
│   ├── QuickAddTrampolineActivity.kt  Activity invisible, solo para cerrar el panel QS
│   └── QuickAddOverlayService.kt      Ventanita flotante (SYSTEM_ALERT_WINDOW)
├── update/                   Chequeo e instalación de nuevas versiones
│   ├── UpdateChecker.kt         Consulta la última Release de GitHub, compara versiones
│   └── ApkInstaller.kt          Descarga (DownloadManager) + instala (FileProvider)
├── domain/                   Sin nada de Android - testeable en JVM puro
│   ├── model/                   Expense, ExpenseCandidate, ActivityEntry, Category
│   ├── repository/              Interfaces (implementadas en data/local)
│   ├── filter/                  ExpenseFilters (funciones puras de filtrado del Dashboard)
│   ├── text/                    AmountInput (sanitizado puro del campo Monto)
│   └── usecase/                 RegisterExpense, CreateCandidate, ConfirmCandidate,
│                                 EditCandidate, RejectCandidate, EditExpense,
│                                 DeleteExpense, ExportBackup, Observe*, FindPendingCandidate
├── data/local/               Implementación con SQLite detrás de las interfaces de domain
│   ├── DbHelper.kt
│   ├── LocalExpenseRepository.kt
│   ├── LocalCandidateRepository.kt
│   └── LocalActivityRepository.kt
└── di/                       ServiceLocator + AppContainer (DI manual, sin Hilt)
```

El dominio (`domain/`) no importa nada de Android ni de SQLite — solo
conoce sus propias interfaces de repositorio. Eso es lo que permite tener
tests unitarios puros en `src/test/` (250+ tests en 25 archivos, corren en
JVM sin emulador ni Robolectric) para la idempotencia, la deduplicación,
los parsers, los filtros del Dashboard y el sanitizado de inputs:

```text
app/src/test/kotlin/com/eliasgonzalez/expensetracker/
├── domain/
│   ├── filter/ExpenseFiltersTest.kt
│   ├── text/AmountInputTest.kt
│   └── usecase/
│       ├── FakeRepositories.kt              Fakes en memoria (con soporte de race delay)
│       ├── CandidateActionsSharedMutexTest.kt
│       ├── ConfirmCandidateTest.kt / EditCandidateTest.kt / RejectCandidateTest.kt
│       ├── CreateCandidateTest.kt           Incluye deduplicación
│       ├── RegisterExpenseTest.kt / EditExpenseTest.kt / DeleteExpenseTest.kt
│       ├── RepositoryAtomicityTest.kt       Race conditions con delay inyectado
│       ├── ExportBackupTest.kt
│       ├── FindPendingCandidateTest.kt
│       └── Observe{Activity,Candidates,Expenses}Test.kt
├── notification/
│   ├── ParserEngineTest.kt          Prioridad entre parsers específicos y genérico
│   ├── UenoBankParserTest.kt
│   ├── BancoFamiliarParserTest.kt
│   └── GenericPurchaseParserTest.kt
├── ui/
│   ├── common/ScreenSupportTest.kt
│   ├── dashboard/DashboardScreenTest.kt
│   ├── activitylog/ActivityLogScreenTest.kt
│   └── banners/MissingPermissionsBannerTest.kt
└── update/UpdateCheckerTest.kt
```

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
CI (lint + tests + build) y publica un APK firmado como GitHub Release. La
propia app chequea al abrir si hay una versión más nueva que la instalada
y avisa con un banner deslizable; también se puede forzar el chequeo a
mano desde el menú (⋮) del Dashboard. Tocando el banner (o su botón
"Actualizar") la app descarga el APK y abre el instalador del sistema
directamente — no hace falta pasar por el navegador. Para instalar por
primera vez sí hay que ir al navegador: descargar el APK de la
[última Release](https://github.com/eliasss3990/expense-tracker/releases/latest)
desde el celular y abrirlo (Android va a pedir permiso para instalar
desde esa fuente, y puede mostrar un aviso de Play Protect por no venir
de Play Store — es esperable para cualquier app instalada así, se
resuelve tocando "Analizar app").

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
   O usar `/mnt/c/Scripts/expense-tracker-deploy/deploy.bat` (debug) /
   `deploy-release.bat` (firmado con la misma clave que las Releases
   reales, para evitar el error de "no se pudo actualizar" por firmas
   distintas si ya hay una build debug instalada).
3. Al abrir por primera vez, un onboarding pide juntos los permisos de
   notificaciones (Android 13+) y el **acceso a notificaciones**
   (`NotificationListenerService`, que Android no deja pedir con un
   diálogo por lo sensible que es). Si se omite alguno, un banner
   deslizable lo recuerda después.
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

No hace falta un pago real: cualquier notificación de un paquete
confiable (ver `TRUSTED_PACKAGES` en `GenericPurchaseParser.kt`) cuyo
texto contenga un patrón como `Gs. 85.000`, `₲85.000` o `PYG 85000`
dispara el candidato (siempre que no contenga una palabra clave de
ingreso como "recib"/"acredit"/"ingreso", en cuyo caso se ignora a
propósito). Formas rápidas de generarla:

- Enviarte un mensaje/notificación de prueba con ese texto desde una app
  ya confiable (ej. Gmail).
- Usar `adb shell "cmd notification post -t '<título>' <tag> '<texto>'"`
  (con comillas simples adentro, para que sobrevivan el viaje por varios
  shells si se corre desde WSL vía el `adb.exe` de Windows) — esto posta
  la notificación como si viniera del propio shell/`android`, así que
  solo sirve si ese paquete está en la lista blanca, o para probar el
  camino de rechazo de paquetes no confiables.

Al aparecer la notificación "💳 Gasto detectado", probar los tres botones y
verificar en la pestaña **Bandeja** que el candidato desaparece de pendientes
al aceptar/rechazar, en **Actividad** que quedó la traza completa, y en
**Dashboard** que el monto se suma solo cuando se acepta o edita (nunca si
está pendiente o rechazado).

## Qué NO tiene todavía

Room (ver arriba por qué), Hilt, parsers para bancos/billeteras más allá
de Ueno Bank y Banco Familiar/Eko (el parser genérico cubre el resto de
forma más básica, sin distinguir de qué fuente viene el comercio),
merchant aliases/aprendizaje de usuario, presupuestos, voz, OCR, ni
sincronización con ningún proveedor externo (Google Drive, etc.) — la app
es 100% local. El export a JSON es la única vía de backup por ahora.
