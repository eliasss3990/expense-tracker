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
  comercio confiable todavía) — dentro de 5 minutos contra candidatos
  pendientes y de 1 minuto contra los ya resueltos (aceptados, editados
  o rechazados), así el segundo aviso de la misma compra no vuelve a
  sugerirse aunque ya hayas resuelto el primero; cada descarte deja una
  línea de log sin montos ni comercios. Los gastos cargados a mano no se
  comparan —
  evita duplicar cuando el banco y una billetera/mail avisan la misma
  compra casi al mismo tiempo (o cuando un mismo banco manda dos mails
  de confirmación para la misma operación, como hace Banco Familiar). El
  chequeo está protegido con un `Mutex` compartido entre todos los casos
  de uso que tocan candidatos/gastos, para que operaciones casi
  simultáneas no se cuelen como duplicados ni pisen datos entre sí. Esos
  casos de uso corren además en una sección **no cancelable**: aceptar,
  rechazar o editar desde la Bandeja no se corta a mitad aunque la tarjeta
  salga de la pantalla (así siempre se retira la notificación y queda la
  entrada en Actividad).
- **IA local (opcional, apagada por defecto):** un modelo de lenguaje que corre
  **en el teléfono** (Gemma 4 E2B vía LiteRT-LM, ~2,6 GB, descarga explícita por
  Wi-Fi) puede leer los avisos de las fuentes confiables, registrar solos los
  gastos seguros y dejar como candidato lo dudoso, ya con la categoría puesta.
  Los parsers siguen existiendo y siguen siendo la vía de siempre; ver la sección
  [IA local](#ia-local) para cómo decide, cómo se audita y cómo se activa.
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
- Categorías gestionables desde la app (menú de tres puntos →
  "Categorías"): crear (con color de una paleta fija), renombrar,
  desactivar/reactivar y eliminar, sin tocar código ni reinstalar. Desactivar
  una categoría solo la oculta de los selectores para elegir: los gastos que
  ya la usan no cambian. Una categoría con gastos (o con candidatos pendientes
  que la sugieren) no se puede eliminar, solo desactivar; "Otro" siempre queda
  activa. Las de fábrica conservan su ícono; las propias muestran la inicial.
  Mismo catálogo en toda la app, incluida la ventanita del Quick Settings Tile.
- Tab "Actividad": traza de auditoría de todo lo que pasó (detectado →
  aceptado/editado/rechazado/registrado/eliminado), separada de la
  Bandeja (que solo muestra lo pendiente). También paginada.
- Onboarding de permisos unificado la primera vez que se abre la app
  (notificaciones + acceso a notificaciones en una sola pantalla, con
  opción de omitir), y un banner general de "permisos faltantes" el
  resto del tiempo — deslizable hacia los costados para descartarlo,
  como una notificación real, con colapso animado.
- La app chequea sola si hay una versión más nueva publicada en GitHub
  Releases al abrir (y a demanda desde el menú del Dashboard). Consulta
  primero la **página web** de la última release y deja la API de GitHub de
  respaldo (la API anónima limita a 60 consultas por hora por IP compartida y
  se agota sola; la web no cuenta contra ese cupo). Si hay
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
│   ├── EditTarget.kt           Qué hace QuickAddActivity según cómo llegó (puro, probado)
│   ├── dashboard/               Dashboard: resumen, filtros, lista, alta/edición manual
│   │   ├── DashboardScreen.kt
│   │   ├── ExpenseFilterSheet.kt
│   │   ├── ExpenseRow.kt
│   │   └── ManualAddSheet.kt
│   ├── catalog/                 Gestión de categorías (crear/renombrar/desactivar/eliminar)
│   │   ├── CategoriesScreen.kt
│   │   ├── CategoryColorChoices.kt  Paleta fija para elegir el color
│   │   └── CategoryMessages.kt      Textos de rechazo, subtítulo e inicial del avatar (puro)
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
│   ├── ai/                        Pantalla de IA local: modelo, modo y por qué de cada decisión
│   │   ├── AiScreen.kt
│   │   └── AiPresentation.kt      Textos en castellano de cada código de la auditoría (puro, probado)
│   ├── common/                    Helpers compartidos entre pantallas (ScreenSupport, etc.)
│   │   └── ScreenSupport.kt
│   └── theme/                     Colores, tipografía y modelo de UI de categoría
│       ├── CategoryStyle.kt       Ícono de las categorías de fábrica, por id
│       ├── CategoryUi.kt          CategoryUi + rememberCategoryOptions/Lookup (leen del catálogo)
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
│   ├── GenericPurchaseParser.kt Red de contención para fuentes sin parser propio (NO es un parser curado)
│   ├── TrustedSources.kt        Paquetes confiables y cuánto se confía en su contenido (banco vs buzón)
│   ├── FinancialPrefilter.kt    Descarta lo que ni menciona dinero antes de despertar al modelo
│   ├── AndroidCandidateAnnouncer.kt   Publica la notificación "Gasto detectado" (Aceptar/Editar/Rechazar)
│   ├── AndroidReviewNoticeAnnouncer.kt  Avisa de un aviso con dinero que no se pudo interpretar
│   └── CandidateActionReceiver.kt  Acciones Aceptar/Editar/Rechazar de la notificación
├── quicksettings/            Quick Settings Tile
│   ├── RegisterExpenseTileService.kt
│   ├── QuickAddTrampolineActivity.kt  Activity invisible, solo para cerrar el panel QS
│   └── QuickAddOverlayService.kt      Ventanita flotante (SYSTEM_ALERT_WINDOW)
├── logging/                  Destinos de log: LogcatSink (único lugar que usa android.util.Log)
├── update/                   Chequeo e instalación de nuevas versiones
│   ├── UpdateChecker.kt         Consulta la última Release de GitHub, compara versiones
│   ├── UpdateFailure.kt         Por qué falló la consulta (límite de GitHub, sin red, TLS...)
│   ├── ReleaseLinks.kt          Versión y enlaces de la release derivados de la etiqueta (puro)
│   └── ApkInstaller.kt          Descarga (DownloadManager) + instala (FileProvider)
├── domain/                   Sin nada de Android - testeable en JVM puro
│   ├── model/                   Expense, ExpenseCandidate, ActivityEntry, CatalogEntry, CategoryIds
│   │                             (CategoryIds = ids de fábrica guardados en la base: nunca cambian)
│   ├── catalog/                 CatalogDefaults, CategoryPolicy (qué categoría se guarda),
│   │                             CategoryDisplay (mostrar por id), CatalogUsage (¿en uso?)
│   ├── analysis/                IA local, sin saber de ningún modelo: contratos (TransactionInterpreter),
│   │                             DecideRegistration (la decisión), puertos de la cola y la auditoría
│   ├── log/                     AppLog (fachada de logging) + LogSink (destino). Sin Android
│   ├── notification/            Puerto CandidateNotifier (retirar la notificación al resolver)
│   ├── retry/                   retrying(): reintento con backoff lineal (puro)
│   ├── repository/              Interfaces (implementadas en data/local)
│   ├── filter/                  ExpenseFilters (funciones puras de filtrado del Dashboard)
│   ├── text/                    AmountInput (sanitizado puro del campo Monto)
│   └── usecase/                 RegisterExpense, CreateCandidate, ConfirmCandidate,
│                                 EditCandidate, RejectCandidate, EditExpense,
│                                 DeleteExpense, ExportBackup, Observe*, FindPendingCandidate,
│                                 Create/Rename/SetActive/DeleteCatalogEntry (catálogo),
│                                 HandleDetectedNotification, ProcessAnalysisQueue, AutoRegisterExpense,
│                                 ResolveCategory, RecoverDiscarded, ListRecentDecisions (IA local)
├── data/analysis/            Todo lo que SÍ conoce a un modelo concreto (reemplazable)
│   ├── ModelDescriptor.kt       Qué modelo es: id, URL fijada a un commit, tamaño y SHA-256
│   ├── DownloadManagerModelManager.kt  Descarga solo por Wi-Fi y verifica el SHA-256
│   ├── LiteRtTransactionInterpreter.kt Único adaptador que usa LiteRT-LM (cancelación real, salida restringida)
│   ├── InterpretationPrompts.kt / InterpretationSchema.kt / ModelOutputParser.kt  Prompt, esquema JSON y lectura
│   └── InterpretationCodec.kt   JSON versionado de lo que interpretó el modelo, para la auditoría
├── data/local/               Implementación con SQLite detrás de las interfaces de domain
│   ├── DbHelper.kt              Adaptador fino de SQLiteOpenHelper: delega en DbSchema
│   ├── DbSchema.kt              Esquema y migraciones SOLO aditivas (nunca DROP), en Kotlin puro
│   ├── AndroidSqlExecutor.kt    SqlExecutor sobre SQLiteDatabase (en los tests: una SQLite real en la JVM)
│   ├── CatalogSeedV3.kt         Lista congelada que siembra la migración v2→v3
│   ├── CatalogRows.kt           Mapeo CatalogEntry ↔ fila de catalog_entries
│   ├── LocalAnalysisQueueRepository.kt / LocalDecisionAuditRepository.kt  Cola y auditoría del análisis (v4)
│   ├── SqlDatabase.kt           insert/update/query sobre SqlExecutor (en los tests: SQLite real)
│   ├── LocalCatalogRepository.kt
│   ├── LocalExpenseRepository.kt
│   ├── LocalCandidateRepository.kt
│   └── LocalActivityRepository.kt
└── di/                       ServiceLocator + AppContainer (DI manual, sin Hilt)
```

El dominio (`domain/`) no importa nada de Android ni de SQLite — solo
conoce sus propias interfaces de repositorio. Eso es lo que permite tener
tests unitarios puros en `src/test/` (unos 600 tests en 61 archivos, corren en
JVM sin emulador ni Robolectric) para la idempotencia, la deduplicación,
los parsers, los filtros del Dashboard y el sanitizado de inputs:

Los tests viven en `app/src/test/kotlin/.../` y se organizan por área (se
describen por carpeta y no archivo por archivo, para que no se desactualice en
cada PR):

| Carpeta | Qué prueba |
|---|---|
| `domain/usecase/` | Casos de uso (candidatos, gastos, catálogo, backup): idempotencia, deduplicación, carreras entre operaciones con retardo inyectado, espera de la carga, casos de uso que no se cortan si el llamador se cancela; fakes en memoria en `FakeRepositories.kt` |
| `domain/catalog/`, `domain/model/` | Política de categorías, qué se muestra por id, ids persistidos de fábrica y el seed |
| `domain/log/`, `domain/retry/` | `AppLog` (un destino que lanza no rompe al que loguea) y el reintento con backoff |
| `domain/filter/`, `domain/text/` | Filtros del Dashboard y sanitizado del campo Monto |
| `domain/analysis/` | La decisión de la IA (`DecideRegistration`): saldo vs importe, reembolsos, moneda, fuentes de confianza, acuerdo entre pasadas, memoria de categoría |
| `data/local/` | **Migraciones de la base contra una SQLite real**, el seed congelado y la cola y auditoría del análisis |
| `data/analysis/` | Prompts, esquema JSON de la salida, lectura tolerante de lo que devuelve el modelo, codec de auditoría y el manejo del motor al cancelar |
| `notification/` | Parsers (Ueno, Banco Familiar, genérico), su prioridad en `ParserEngine` y el recurso del ícono de notificación |
| `appfunctions/` | **Prueba** de AppFunctions (Android 16+): `getSpendingTotal(period)`, función de solo lectura para que un asistente como Gemini consulte el total gastado. Aún sin confirmar que Gemini la ejecute |
| `update/` | Comparación de versiones, derivación de enlaces de la release y clasificación de fallos del actualizador |
| `ui/` | Funciones puras de las pantallas (filtros, mensajes, decisión de `QuickAddActivity`, textos de la pantalla de IA); sin tests de las pantallas en sí |

**Por qué SQLite directo y no Room:** la persistencia está hecha a mano
(`data/local/`: `DbHelper` + `DbSchema`) detrás de las interfaces de
dominio. Se eligió así porque Room 2.8.x necesita KSP para el codegen de
`@Entity`/`@Dao` y, cuando se armó la persistencia, ninguna versión de KSP
compilaba contra Kotlin 2.4.10 (el del Kotlin embebido de AGP 9). Ese
impedimento ya no existe: KSP `2.3.13` compila con este Kotlin y AGP 9.3.2
(lo usa hoy `androidx.appfunctions`, ver `appfunctions/`). Se mantiene
SQLite directo porque ya está implementado y sus migraciones se prueban
contra SQLite real; migrar a Room no debería tocar nada fuera de
`data/local/`. Room no se probó con esta versión de KSP.

**Por qué DI manual y no Hilt:** el grafo de dependencias es chico
(un puñado de repositorios y casos de uso) — `di/AppContainer.kt` arma
todo a mano en unas pocas líneas, sin la complejidad de un framework.

## IA local

Lee los avisos de los bancos con un modelo de lenguaje que corre **dentro del teléfono**: nada de
lo que dice una notificación sale de él. Está **apagada por defecto**; hasta que se descargue el
modelo y se elija un modo, la app se comporta exactamente como antes.

**Cómo se activa:** menú de tres puntos → **IA local** → descargar el modelo (~2,6 GB, solo por Wi-Fi,
se verifica su SHA-256) → elegir el modo:

| Modo | Qué hace |
|---|---|
| **Sombra** (por defecto) | Todo sigue como siempre. El modelo analiza en silencio y queda anotado lo que *habría* hecho, para medirlo sin riesgo |
| **Asistido** | El modelo propone: cada gasto llega como candidato, ya con su categoría |
| **Automático** | Registra solo los gastos que da por seguros; lo dudoso llega como candidato |

**Cómo decide.** El modelo no decide nada por sí solo: rellena campos cerrados (dirección del
dinero, estado de la operación, el papel de cada cifra —importe, saldo, límite, cuota—, comercio,
categoría) y el dominio comprueba que sea coherente. Un gasto se registra solo únicamente si:

- el modelo lo leyó **dos veces** (la segunda sin ver al parser) con el mismo resultado;
- el importe figura **literalmente** en el aviso y la moneda es la del texto (solo guaraníes se registran solos);
- si hay un parser a medida, **coincide** con él;
- la fuente es de fiar: una app de banco sí; un **buzón (Gmail) solo si lo reconoció un parser a medida**, porque
  cualquiera puede mandarte un mail. El parser genérico no cuenta como a medida.

Cualquier duda termina en un **candidato**, nunca en un gasto. Lo único que hace desaparecer un aviso es
descartarlo (ingresos, reembolsos, operaciones rechazadas), y exige que las dos lecturas coincidan y que ninguna
haya dudado; igual queda en la lista de decisiones y se puede **recuperar** como candidato.

**Categoría.** Sale, en este orden, de lo que ya categorizaste para ese comercio (tu historial), de lo que elige
el modelo **entre las categorías activas de tu catálogo** (no puede inventar otra), o del comodín "Otro".

**Por qué cada aviso salió como salió.** La pantalla *IA local* lista las decisiones recientes; al tocar una se ve el
motivo en castellano y de dónde salió la categoría. Cada decisión se guarda en la base (tabla `decision_audit`: efecto,
motivos, lo que interpretó el modelo y su tiempo) y deja una línea en logcat con la etiqueta `AiDecision`
(solo códigos, ids y tiempos: **nunca importes, comercios ni el texto del aviso**). El texto original queda en la cola
unos días para reintentar y reconstruir una decisión, y después se borra.

**Para no perder avisos.** Si el modelo falla, se reintenta con espera creciente y, agotados los intentos, sale el
candidato del parser. Si el modelo deja de estar, lo encolado sale por el flujo de siempre. Un aviso con dinero que
no se pudo interpretar te llega como una notificación para que lo mires en el banco.

**Cambiar de modelo.** Todo lo que depende de un modelo concreto vive en `data/analysis/` y entra por tres líneas de
`AppContainer`. Otro modelo es otro `ModelDescriptor` y, si usa otro runtime, otro `TransactionInterpreter`; la cola,
la auditoría, la decisión y la pantalla no cambian.

**Qué no está verificado todavía:** el comportamiento real del modelo en el teléfono, y que el motor de gramáticas del
runtime acepte el esquema JSON de la salida (si lo rechaza, reintenta sin restricción y lo anota en el log).

### Chat con la IA

El ícono ✨ de la barra superior abre un chat a pantalla completa con el modelo local (requiere el modelo instalado). Sirve de asistente rápido:
consultar ("cuánto gasté ayer", "gastos de la semana pasada"), hablar de un gasto ("contame del último") y cargar, editar o borrar gastos, y también crear, renombrar o borrar categorías. Una categoría con gastos no se borra: se propone desactivarla (como en Categorías); la de respaldo ("Otro") no se toca. Las preguntas como "¿qué podés hacer?" las responde la app con un texto fijo, no el modelo.

- **El modelo solo interpreta.** La app resuelve a qué gasto se refiere, calcula los totales con sus propios datos (el modelo no mira la base) y
  calcula las fechas de los períodos; el modelo elige el período, no las fechas.
- **Lo que escribe, se confirma.** Cargar, editar y borrar muestran primero una propuesta ("¿Son correctos los datos?… Si algo está mal, decime qué
  cambiar") y solo se ejecutan con **Confirmar**. Una operación es siempre sobre un gasto: no existe "borrar todos". Si el gasto cambió entre la
  propuesta y la confirmación, no se toca nada.
- **Contexto solo dentro del chat abierto**: recuerda lo hablado y la propuesta pendiente ("no, era 20000"), en memoria; al salir y volver es un chat
  nuevo. No hay historial entre chats.
- Un solo motor del modelo se comparte con el análisis de avisos (los pesos ocupan ~2,6 GB: dos copias no caben).
- Límites: no cambia el color de una categoría ni la fecha de un gasto ya cargado; el modelo puede heredar el período de la pregunta anterior (la respuesta siempre muestra el
  rango usado).

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

Si la búsqueda de actualizaciones falla, el aviso es genérico ("No se pudo
comprobar actualizaciones") y el motivo queda en el log (ver "Logs").
Siempre se puede descargar el APK de la
[última Release](https://github.com/eliasss3990/expense-tracker/releases/latest)
(la página web no tiene límite de consultas) o instalarlo con
`adb install -r app-release.apk`. **Instalar siempre el APK de una Release**:
un APK de debug tiene otra firma y no actualiza sobre el instalado (obligaría a
desinstalar y perder los datos). `adb install -r` conserva los datos y, si la
firma no coincide, falla sin tocar nada.

Manual, paso a paso (requiere el Android SDK local; en una máquina sin SDK el
build corre solo en el CI):

1. Compilar el APK de debug (requiere JDK 21 y el Android SDK):
   ```bash
   ./gradlew assembleDebug
   ```
2. Instalar y abrir con el celular conectado por USB y la depuración USB activada
   (en Windows con WSL, el USB queda atado al lado Windows: usar el `adb.exe` de
   Windows):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.eliasgonzalez.expensetracker/.ui.MainActivity
   ```
   **Ojo con la firma:** un APK de debug tiene otra firma que el de una Release, así
   que **no actualiza sobre una instalación hecha con una Release** (el sistema lo
   rechaza; la salida sería desinstalar y perder los datos). Para actualizar una
   instalación real, usar el APK de la Release con `adb install -r`.
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

## Logs

La app registra con `AppLog` (`domain/log/`), una fachada con niveles
`d/i/w/e`. El destino lo define un `LogSink`: hoy `LogcatSink`
(`logging/`), el único archivo que usa `android.util.Log`. Cambiar el destino
(un archivo, por ejemplo) es escribir otro `LogSink` y cambiar una línea en
`ExpenseTrackerApp`.

```bash
adb logcat -s UpdateChecker AppContainer CandidateNotifier MainActivity
```

Solo se registran fallos y eventos raros, con su causa (tipo de error, código de
respuesta, ids). **Nunca** montos, comercios ni textos de notificaciones: son
datos financieros del usuario. logcat es un búfer en memoria y se pierde al
reiniciar el celular.

## Cómo se publica una versión

1. Rama de trabajo → PR a `next` (corre el CI: lint, tests y build del APK).
2. PR `next` → `main` (solo valida que la rama de origen sea `next`).
3. Al mergear a `main`, `release.yml` compila el APK **firmado** y publica la
   Release `v<versionName>`.

**Bumpear `versionName` y `versionCode` en `app/build.gradle.kts` es obligatorio**
al llevar algo a `main`: sin bump, el release se republica sobre el tag existente
y la app no detecta ninguna actualización. Un PR que queda desactualizado se
pone al día con `gh pr update-branch` (sin force-push). El caché del SDK de
Android se siembra en cada push a `next` (`warm-sdk-cache.yml`).

## Cómo correr los tests

```bash
./gradlew testDebugUnitTest
```

(Requiere JDK 21 y el Android SDK; sin SDK, el CI corre los mismos tests en cada PR.)

Son tests puros de JVM (sin Robolectric ni emulador) sobre los casos de
uso de dominio y sobre funciones puras de la UI (filtros, sanitizado de
inputs), con fakes en memoria de los repositorios
(`src/test/kotlin/.../domain/usecase/FakeRepositories.kt`).

Las **migraciones de la base** (`data/local/DbSchema.kt`) se prueban contra una
SQLite real en memoria (`sqlite-jdbc`, solo de test), con los esquemas
históricos v1 y v2 como fixture y la regla de que ninguna migración puede
ejecutar `DROP`, `DELETE`, `TRUNCATE`, `REPLACE`, `UPDATE` ni `RENAME`. **Al
escribir un test nuevo, comprobar que corre y que falla cuando se rompe lo que
prueba** (por ejemplo reintroduciendo el bug): un "verde" sin que el test se
ejecute no demuestra nada.

## Cómo probar la detección

No hace falta un pago real: cualquier notificación de un paquete
confiable (ver `TrustedSources.kt`) cuyo
texto contenga un patrón como `Gs. 85.000`, `₲85.000` o `PYG 85000`
dispara el candidato (siempre que no contenga una palabra clave de
ingreso como "recib"/"acredit"/"ingreso", en cuyo caso se ignora a
propósito). Formas rápidas de generarla:

- Enviarte un mensaje/notificación de prueba con ese texto desde una app
  ya confiable (ej. Gmail).
- Usar `adb shell "cmd notification post -t '<título>' <tag> '<texto>'"`
  (con comillas simples adentro, para que sobrevivan el viaje por varios
  shells, por ejemplo WSL → `adb.exe` de Windows) — esto posta
  la notificación como si viniera del propio shell/`android`, así que
  solo sirve si ese paquete está en la lista blanca, o para probar el
  camino de rechazo de paquetes no confiables.

Al aparecer la notificación "💳 Gasto detectado", probar los tres botones y
verificar en la pestaña **Bandeja** que el candidato desaparece de pendientes
al aceptar/rechazar, en **Actividad** que quedó la traza completa, y en
**Dashboard** que el monto se suma solo cuando se acepta o edita (nunca si
está pendiente o rechazado).

## Qué NO tiene todavía

**La IA local todavía no se probó en un dispositivo real:** el código compila, sus
tests pasan y el CI está en verde, pero nadie corrió aún el modelo de verdad (ni
midió su latencia, memoria o exactitud con avisos reales). Hasta entonces conviene
dejarla en modo Sombra. Detalle en [IA local](#ia-local).


Room (ver arriba por qué), Hilt, parsers para bancos/billeteras más allá
de Ueno Bank y Banco Familiar/Eko (el parser genérico cubre el resto de
forma más básica, sin distinguir de qué fuente viene el comercio),
merchant aliases/aprendizaje de usuario, presupuestos, voz, OCR, ni
sincronización con ningún proveedor externo (Google Drive, etc.) — la app
es 100% local. El export a JSON es la única vía de backup por ahora (no hay
importación), no hay un destino de logs a archivo, y las pantallas y
`DbHelper`/repositorios locales no tienen tests automáticos (necesitan Android).
