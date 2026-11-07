# Contexto técnico completo — IA local para detección de gastos

> Documento de traspaso para continuar el desarrollo de la funcionalidad de análisis de notificaciones mediante IA local. Describe el estado real de la rama al momento de su creación; no implica que la implementación esté validada en dispositivo ni que los hallazgos pendientes estén corregidos.

- Repositorio: https://github.com/eliasss3990/expense-tracker
- Rama de trabajo: **feat/ai-expense-detection**
- Documento complementario de auditoría: [AI_EXPENSE_DETECTION_CODE_REVIEW.md](reviews/AI_EXPENSE_DETECTION_CODE_REVIEW.md)
- Último commit de funcionalidad antes de documentar: **ff73290cc9** — docs: document local AI pipeline and rollout safeguards
- Commit que agrega este contexto: se registra en el historial de la rama al guardar este archivo.
- Plataforma: Android nativo, Kotlin, Jetpack Compose, SQLite, coroutines y DI manual.

---

## 1. Resumen ejecutivo

Se incorporó una segunda vía para detectar movimientos financieros a partir de notificaciones: un analizador de lenguaje ejecutado enteramente en el dispositivo. La IA complementa a los parsers deterministas existentes; no los reemplaza ni se convierte en la única fuente de verdad.

La implementación añade:

1. Un contrato de dominio para representar notificaciones, resultados de parsers, extracciones del modelo, decisiones y métricas.
2. Una implementación de inferencia local con **Gemma 4 E2B Instruct**, usando **LiteRT-LM Android 0.18.1**.
3. Un instalador explícito del modelo, descargado desde Hugging Face y verificado por tamaño y SHA-256.
4. Una cola SQLite persistente para que las notificaciones no se pierdan si el proceso Android muere.
5. Una reconciliación determinista entre lo que dice la notificación, lo que extrajo el modelo y lo que reconoció el parser.
6. Tres modos operativos: Sombra, Asistido y Automático.
7. Un camino de auto-registro que persiste directamente un Expense, sin crear un ExpenseCandidate temporal.
8. Una pantalla de configuración, instalación y métricas.
9. Tests unitarios para los contratos, reconciliación, cola, auto-registro, elegibilidad de notificaciones y migración.

**Estado funcional pretendido:** empezar en Sombra, observar el comportamiento real, pasar a Asistido para validar candidatos y habilitar Automático solo de forma deliberada. Automático debe registrar únicamente decisiones AUTO_REGISTER; cualquier duda o contradicción debe terminar en revisión. La precisión y la prevención de falsos positivos tienen prioridad sobre la cantidad de operaciones detectadas.

**Estado real importante:** la revisión estática posterior detectó defectos que deben corregirse antes de activar Automático con datos reales. Ver la sección 15 y el documento de code review. No se ejecutó Gradle ni se probó la app en un Galaxy S24 Ultra durante este trabajo.

---

## 2. Objetivo y problema que se quiso resolver

### 2.1 Situación anterior

La app ya recibe notificaciones mediante NotificationListenerService y utiliza parsers deterministas:

- UenoBankParser para formatos conocidos de Ueno.
- BancoFamiliarParser para formatos conocidos de Banco Familiar/Eko.
- GenericPurchaseParser como parser de contención para paquetes expresamente confiables.

Estos parsers son rápidos, predecibles y adecuados para formatos conocidos. Sin embargo, dependen de estructuras de texto y expresiones regulares. Si una entidad cambia el formato de una notificación o llega un correo con una estructura nueva, el parser puede no extraer el movimiento, el monto o el comercio.

Antes de esta ampliación, que un parser devolviera null significaba que no había una extracción utilizable por el flujo legacy. La nueva funcionalidad busca que una notificación elegible todavía pueda analizarse aunque el parser no reconozca su formato.

### 2.2 Resultado que se busca

La IA recibe dos entradas en conjunto:

- La notificación original: paquete, nombre de aplicación, título, texto, texto expandido y timestamp.
- El resultado del parser actual, si existe; puede ser null.

El modelo propone una extracción estructurada. Después, el dominio valida esa extracción contra la evidencia literal de la notificación y contra el parser. El modelo no decide por sí solo qué se guarda en la base.

### 2.3 Qué se considera un movimiento

El contrato reconoce:

- EXPENSE: compra, pago, consumo, débito o egreso.
- OUTGOING_TRANSFER: transferencia saliente.
- INCOME: dinero recibido, transferencia entrante, acreditación o depósito.
- REFUND: devolución/reembolso.
- NON_TRANSACTION: mensaje que no representa una operación registrable.
- UNKNOWN: el movimiento no puede determinarse.

La intención es no convertir ingresos, transferencias entrantes, reembolsos, operaciones rechazadas o mensajes informativos en gastos.

### 2.4 Requisitos y decisiones acordadas

Estas decisiones provienen del diseño acordado antes de implementar:

- **Parsers:** conservar los parsers actuales y tratarlos como fuente determinista complementaria.
- **Entrada del modelo:** notificación original + resultado del parser, incluso cuando el parser sea null.
- **Latencia:** flexible; se prioriza la precisión y la ejecución local sobre una respuesta instantánea.
- **Fallback:** comportamiento legacy cuando el modelo no está instalado o no puede ejecutarse.
- **Arquitectura:** respetar la separación en capas ya existente.
- **Registro automático:** si la reconciliación autoriza AUTO_REGISTER, registrar Expense directamente; no crear un candidato temporal.
- **Revisión:** si la evidencia es insuficiente o hay conflicto, utilizar el flujo existente de ExpenseCandidate.
- **Seguridad:** no procesar cualquier notificación del teléfono; mantener la allowlist de fuentes financieras.
- **Despliegue:** modo Sombra inicial; Automático desactivado hasta que el usuario lo habilite expresamente.
- **Procesamiento:** local, sin enviar notificaciones a un proveedor externo de IA.

### 2.5 Fuera del alcance de esta fase

No se implementó en esta entrega:

- Chat financiero conversacional.
- Lectura de tickets/facturas mediante cámara o galería.
- OCR de documentos o imágenes.
- Sincronización o inferencia en la nube.
- Un parser específico nuevo para cada banco.
- Un mecanismo de aprendizaje automático basado en las correcciones del usuario.
- Una evaluación estadística real de precisión sobre un conjunto etiquetado de notificaciones.
- Activación automática del modelo sin consentimiento del usuario.

---

## 3. Principios de diseño

### 3.1 Los parsers siguen existiendo

No se reemplazó el sistema determinista. ParserEngine mantiene la prioridad de parsers específicos y luego el parser genérico. La IA amplía la cobertura para paquetes confiables cuando el parser no puede reconocer el formato.

### 3.2 El modelo extrae; el dominio decide

El modelo entrega campos y citas de evidencia. La decisión final se toma en código Kotlin del dominio. El valor de confianza que un LLM pudiera declarar no es un criterio de seguridad y no se utiliza para autorizar auto-registro.

### 3.3 Evidencia literal

Las evidencias deben ser citas breves copiadas literalmente del título, texto o texto expandido de la notificación. El dominio comprueba que la cita esté presente en el texto original. No se acepta una explicación inventada por el modelo como evidencia.

### 3.4 Fallar de forma conservadora

Si hay contradicción, falta evidencia o no se puede validar un campo, la salida esperada es revisión o rechazo fundamentado; nunca asumir que una extracción dudosa es correcta.

### 3.5 Persistencia local y recuperación

La cola guarda el evento antes de inferirlo. Un trabajo que quedó PROCESSING por cierre del proceso vuelve a PENDING al iniciar la app. El diseño busca evitar perder una notificación por la muerte del proceso.

### 3.6 Mantener las convenciones del repositorio

- Kotlin y estructura de paquetes existentes.
- Clean Architecture pragmática: domain sin dependencias Android/SQLite.
- Repositorios como interfaces de dominio y adaptadores en data/local.
- Compose para UI.
- ServiceLocator/AppContainer para DI manual.
- SQLite directa; no se introdujeron Room, Hilt ni KSP.
- Casos de uso para coordinar acciones de negocio.
- Coroutines para trabajo asíncrono.
- Tests JVM para las reglas puras y SQLite real en JVM para migraciones.

---

## 4. Arquitectura general

La funcionalidad se distribuye entre las capas existentes:

~~~text
Android NotificationListenerService
        |
        v
NotificationContext
        |
        v
ParserEngine
  |                 |
  | parser result   | parser null
  +--------+--------+
           |
           v
TransactionAnalysisInput
(notification snapshot + optional parser snapshot)
           |
           v
NotificationAnalysisQueueRepository
           |
           v
SQLite: notification_analysis_queue
           |
           v
ProcessNotificationAnalysisQueue
           |
           v
AnalyzeTransaction
      |              |
      v              v
TransactionAnalyzer  ReconcileTransaction
(LiteRT-LM/Gemma)    (domain, deterministic)
      |              |
      +-------+------+
              |
              v
      ReconciledTransaction
              |
      +-------+-----------------------------+
      |                 |                   |
      v                 v                   v
    SHADOW           ASSISTED            AUTOMATIC
      |                 |                   |
      v                 v                   +-- AUTO_REGISTER
 legacy candidate       |                   |      |
 (si parser)            |                   |      v
                        |                   | RegisterAnalyzedExpense
                        |                   |      |
                        v                   |      v
                ExpenseCandidate            |   Expense directo
                + notification              |
                                            +-- REQUIRES_REVIEW
                                            |      |
                                            |      v
                                            | ExpenseCandidate
                                            |
                                            +-- REJECT
                                                   |
                                                   v
                                                sin alta
~~~

La cola registra el resultado de análisis y la decisión. El efecto de negocio se ejecuta según el modo vigente. El modelo no accede a repositorios ni escribe en SQLite directamente.

---

## 5. Flujo de una notificación, paso a paso

1. Android invoca ExpenseNotificationListenerService.onNotificationPosted.
2. Se ignoran las notificaciones originadas por la propia app.
3. Se extraen packageName, applicationName, title, text, bigText y postTime.
4. ParserEngine.isEligibleForAnalysis determina si la fuente puede entrar al análisis.
5. Si la fuente no es elegible, se termina el procesamiento. La IA no recibe notificaciones arbitrarias.
6. ParserEngine.parse ejecuta primero los parsers específicos y luego GenericPurchaseParser.
7. Se crea TransactionAnalysisInput con NotificationSnapshot y ParserSnapshot nullable.
8. Se encola el input mediante ServiceLocator. La inserción es asíncrona.
9. La cola evita insertar dos veces el mismo evento mediante una clave SHA-256 derivada de paquete, timestamp, título, texto y texto expandido.
10. El worker obtiene un PENDING, lo marca PROCESSING e incrementa attempts.
11. AnalyzeTransaction llama al puerto TransactionAnalyzer.
12. LiteRtTransactionAnalyzer prepara el prompt JSON, ejecuta Gemma y convierte la respuesta JSON al contrato de dominio.
13. ReconcileTransaction valida movimiento, evidencias, monto, moneda y comercio; compara con el parser cuando existe.
14. ProcessNotificationAnalysisQueue lee el modo actual y ejecuta el flujo correspondiente.
15. El trabajo se marca COMPLETED, FALLBACK o FAILED según el camino.
16. Los trabajos PROCESSING se recuperan como PENDING al iniciar la app.

La inferencia ocurre fuera del callback de NotificationListenerService. El runtime utiliza un executor de un solo hilo para serializar carga, conversación e inferencia.

---

## 6. Contratos de dominio

Ubicación: **app/src/main/kotlin/com/eliasgonzalez/expensetracker/domain/analysis/**

### 6.1 TransactionAnalysis.kt

Define los datos independientes de Android:

- NotificationSnapshot: packageName, applicationName, title, text, bigText y timestamp. Incluye searchableText() para concatenar los campos textuales.
- ParserSnapshot: amount, currency, merchant, merchantConfident, parserId y confidence.
- DetectedMovement: los seis tipos de movimiento descritos antes.
- TransactionExtraction: movimiento, importe, moneda, comercio y evidencias literales para cada campo.
- TransactionAnalysisInput: snapshot de notificación más resultado opcional del parser.
- TransactionAnalysisResult: extracción y modelVersion.
- TransactionAnalyzer: interfaz suspendida que desacopla el dominio del runtime LiteRT-LM.
- NotificationAnalysisQueueItem: ID, input y número de intentos.
- AnalysisQueueMetrics: conteos de estados y decisiones.

El contrato no importa clases Android, LiteRT-LM ni SQLite.

### 6.2 AnalyzeTransaction.kt

Orquesta dos pasos:

1. Ejecuta TransactionAnalyzer.
2. Pasa el resultado a ReconcileTransaction.

Devuelve AnalyzedTransaction, que conserva tanto modelResult como reconciled. De esta forma, inferencia y reglas de negocio se pueden probar por separado.

### 6.3 AnalysisMode.kt

Define:

- SHADOW: analizar y medir, pero mantener el comportamiento legacy.
- ASSISTED: la IA puede proponer candidatos, nunca registrar automáticamente.
- AUTOMATIC: permite el registro directo solo cuando la reconciliación devuelve AUTO_REGISTER.

AnalysisModeProvider permite consultar el modo sin que el dominio dependa de SharedPreferences.

### 6.4 ReconcileTransaction.kt

Define AnalysisDecision:

- AUTO_REGISTER
- REQUIRES_REVIEW
- REJECT

ReconciledTransaction transporta decisión, movimiento, importe, moneda, comercio, fecha de operación y conjunto de razones.

Reglas implementadas actualmente:

- Comprueba que movementEvidence exista literalmente en la notificación.
- Comprueba que la evidencia del movimiento contenga términos reconocidos de egreso.
- Trata INCOME y NON_TRANSACTION según su evidencia.
- Incluye vetos para ciertos estados negativos y para indicios de dinero entrante.
- Comprueba que el importe sea positivo.
- Comprueba que la moneda tenga formato ISO de tres letras.
- Comprueba que amountEvidence esté presente y que el token numérico coincida con el importe extraído.
- Si no hay parser, exige evidencia textual de moneda.
- Si hay parser, comprueba conflictos de importe, moneda y comercio cuando el parser considera confiable el comercio.
- Exige que el comercio sea confiable por el parser o por evidencia textual.
- No utiliza el confidence autorreportado por el modelo.
- AUTO_REGISTER solo se devuelve cuando el conjunto de razones queda vacío; los demás casos ambiguos terminan en REQUIRES_REVIEW, salvo vetos explícitos que devuelven REJECT.

**Advertencia:** estas reglas son la implementación actual, no una garantía de que todos los casos financieros estén cubiertos. La revisión estática detectó que la vinculación semántica de importes y los vetos de reembolsos/estados negativos requieren fortalecimiento. Ver sección 15.

---

## 7. Runtime local y modelo

Ubicación principal: **data/analysis/**

### 7.1 Modelo seleccionado

- Modelo: **Gemma 4 E2B Instruct**.
- Runtime: **LiteRT-LM Android 0.18.1**.
- Archivo: **gemma-4-E2B-it.litertlm**.
- Tamaño exacto esperado: **2,588,147,712 bytes** (aprox. 2.59 GB).
- Revisión Hugging Face fijada: **9262660a1676eed6d0c477ab1a86344430854664**.
- SHA-256 esperado: **181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c**.
- Destino: directorio privado externo de archivos de la app bajo models; fallback al directorio interno si getExternalFilesDir no está disponible.
- No se empaqueta el modelo dentro del APK.

La revisión del repositorio fijada en la URL evita que el contenido remoto cambie silenciosamente mientras se mantiene el checksum esperado.

### 7.2 ModelInstaller.kt

Responsabilidades:

- Solicitar la descarga únicamente por acción explícita del usuario.
- Utilizar Android DownloadManager.
- Guardar el archivo en el directorio privado de la app.
- Desactivar redes medidas y roaming; la descarga queda limitada a Wi-Fi.
- Comprobar espacio libre antes de iniciar: tamaño del modelo más 128 MiB de margen.
- Exponer estados NOT_INSTALLED, QUEUED, DOWNLOADING, PAUSED, DOWNLOADED, VERIFYING, INSTALLED y FAILED.
- Calcular progreso con los bytes reportados por DownloadManager.
- Verificar tamaño y SHA-256 en Dispatchers.IO.
- Marcar instalado solo si ambas verificaciones pasan.
- Borrar el archivo si la verificación falla.
- Permitir cancelar la descarga y limpiar preferencias asociadas.

El instalador no descarga automáticamente en segundo plano por el mero hecho de recibir una notificación.

### 7.3 LiteRtTransactionAnalyzer.kt

Responsabilidades:

- Implementar el puerto TransactionAnalyzer.
- Mantener una instancia Engine por instancia del adaptador.
- Ejecutar las operaciones en un executor de un solo hilo llamado expense-transaction-ai.
- Verificar que el modelo esté instalado y validado antes de inferir.
- Intentar backend GPU primero.
- Si GPU no inicializa, inicializar CPU.
- Si GPU inicializa pero falla en la primera inferencia, cerrar Engine y reintentar una vez con CPU.
- Crear una conversación por análisis y cerrarla mediante use.
- Usar generación determinista: topK 1, topP 0.1 y temperature 0.0.
- Construir un JSON con la notificación original y parser_result (objeto o null).
- Parsear la respuesta JSON a TransactionAnalysisResult.
- Asociar modelVersion al nombre del archivo del modelo.

La instrucción del sistema al modelo indica que la notificación es dato no confiable y que no debe obedecer instrucciones embebidas en ella. También exige no inventar valores, diferenciar egresos/ingresos/reembolsos/no-transacciones y devolver evidencias literales.

**Pendientes del runtime:** no se implementó ni validó un cierre explícito del Engine ligado al ciclo de vida de la app; tampoco se realizó stress test de memoria, latencia, timeout o estabilidad prolongada en el dispositivo objetivo. Estos puntos aparecen en el code review.

### 7.4 SharedPreferencesAnalysisModeProvider.kt

Persiste el modo operativo en SharedPreferences. El dominio solo conoce AnalysisModeProvider; la implementación Android queda en data/analysis.

---

## 8. Cola persistente SQLite

### 8.1 Contrato

**domain/repository/NotificationAnalysisQueueRepository.kt** define:

- enqueue(input)
- nextPending()
- markProcessing(id)
- markCompleted(id, result, reconciled)
- markFallback(id, errorType)
- markFailed(id, errorType)
- recoverProcessing()
- retryFailed()
- pruneTerminal(beforeTimestamp)
- metrics()

### 8.2 Implementación

**data/local/LocalNotificationAnalysisQueueRepository.kt** implementa el contrato con SQLite y Dispatchers.IO.

Tabla: notification_analysis_queue.

Columnas principales:

- Identidad del evento: id, event_key.
- Origen: package_name, application_name.
- Contenido: title, notification_text, big_text, posted_at.
- Snapshot del parser: parser_amount, parser_currency, parser_merchant, parser_merchant_confident, parser_id, parser_confidence.
- Estado: status, attempts, last_error.
- Resultado: model_version, decision, movement, result_amount, result_currency, result_merchant, occurred_at, reasons.
- Auditoría temporal: created_at, updated_at.

Estados utilizados:

- PENDING: espera procesamiento.
- PROCESSING: el worker tomó el trabajo.
- COMPLETED: el análisis terminó y se aplicó el flujo del modo.
- FALLBACK: el modelo no pudo analizar, pero se ejecutó el flujo legacy con parser.
- FAILED: no se pudo completar; queda disponible para reintento según la política actual.

### 8.3 Idempotencia y recuperación

- event_key es SHA-256 de packageName, timestamp, title, text y bigText unidos con separador nulo.
- event_key tiene restricción UNIQUE.
- enqueue usa CONFLICT_IGNORE.
- Si el evento duplicado ya está FAILED, el repositorio lo devuelve a PENDING al volver a recibirlo.
- markProcessing incrementa attempts.
- recoverProcessing convierte PROCESSING en PENDING.
- retryFailed convierte FAILED en PENDING.
- pruneTerminal elimina COMPLETED, FALLBACK y FAILED antiguos.
- La retención solicitada por el worker es de 30 días.

### 8.4 Migración de base

DbSchema.VERSION pasó a **5**.

La migración v5 crea notification_analysis_queue. También se mantiene la columna merchant_confident agregada en v4. La migración se integra en el sistema aditivo ya existente.

**Regla crítica del proyecto:** las migraciones deben ser aditivas y no destructivas. El historial del proyecto incluye un incidente anterior de pérdida de gastos por una migración destructiva. No introducir DROP, DELETE ni reemplazos de datos existentes para esta funcionalidad.

---

## 9. Coordinación de modos y efectos de negocio

Ubicación: **domain/usecase/**

### 9.1 ProcessNotificationAnalysisQueue.kt

Es el worker/orquestador de la cola.

- Serializa el drenado con un Mutex.
- Encola desde appScope.
- Recupera PROCESSING al iniciar.
- Puede reintentar FAILED cuando se solicita.
- Toma un PENDING por vez.
- Marca PROCESSING antes de analizar.
- Propaga CancellationException.
- Si falla el analizador, ejecuta el fallback.
- Según el modo, decide crear candidato, registrar gasto o no hacer nada.
- Marca el trabajo como COMPLETED, FALLBACK o FAILED.

Comportamiento por modo:

| Decisión reconciliada | Sombra | Asistido | Automático |
|---|---|---|---|
| AUTO_REGISTER | Flujo legacy (candidato si parser existe) | Candidato para revisar | Expense directo |
| REQUIRES_REVIEW | Flujo legacy | Candidato para revisar | Candidato para revisar |
| REJECT | Flujo legacy (si parser existe) | No crear candidato | No crear candidato |

Sombra existe para medir el resultado de la IA sin cambiar la experiencia que ya tenía el usuario.

### 9.2 Fallback

Si el modelo falla:

- Con parser disponible: se crea el candidato legacy a partir del ParserSnapshot y se marca FALLBACK.
- Sin parser: se marca FAILED para permitir reintento posterior cuando el modelo esté disponible.

El fallback no convierte una inferencia fallida en un auto-registro.

### 9.3 Candidato asistido

Para REQUIRES_REVIEW se construye un ExpenseCandidate usando los datos reconciliados. Cuando existe un conflicto explícito de importe, moneda o comercio, el candidato prioriza el valor del parser para ese campo. Si faltan datos mínimos, no crea un candidato inválido.

El candidato se persiste mediante CreateCandidate y luego se publica una alerta Android mediante CandidateAlertPublisher.

### 9.4 Publicación de alerta

CandidateAlertPublisher desacopla el dominio de Android. AndroidCandidateAlertPublisher crea una notificación con acciones Aceptar, Editar y Rechazar, reutilizando CandidateActionReceiver y QuickAddActivity.

En la implementación actual, el fallo de publicación se captura con runCatching después de persistir el candidato. El candidato sigue en la bandeja interna, pero la alerta puede no aparecer; el code review recomienda separar y reintentar la entrega de la alerta.

### 9.5 RegisterAnalyzedExpense.kt

Caso de uso exclusivo para AUTO_REGISTER:

- Rechaza cualquier decisión que no sea AUTO_REGISTER.
- Solo permite EXPENSE u OUTGOING_TRANSFER.
- Exige importe positivo, moneda de tres letras y comercio no vacío.
- Espera a que los datos observables estén hidratados.
- Calcula una sourceReference estable y negativa a partir del hash del evento.
- Comparte el Mutex de candidatos con CreateCandidate/ConfirmCandidate.
- Comprueba si el evento exacto ya se registró.
- Comprueba candidatos recientes para evitar duplicar un gasto que ya está pendiente o resuelto.
- Comprueba gastos recientes, incluidos gastos ingresados manualmente.
- Reutiliza RegisterExpense para persistir el Expense y registrar la actividad.

No crea ExpenseCandidate en el camino AUTO_REGISTER.

**Riesgo detectado:** el caso de uso devuelve Long? y null puede significar varias cosas (duplicado, no elegible, datos no listos). El worker actualmente marca COMPLETED después de esa llamada aunque null pueda deberse a que awaitReady devolvió false. Ver P1-3 en el code review.

### 9.6 Deduplicación

La implementación tiene varias defensas:

- Dedupe exacto del evento en la cola (event_key).
- sourceReference estable en el Expense para reintentos del mismo evento.
- Comparación con candidatos existentes.
- Comparación con gastos recientes, incluidos los manuales.
- Mutex compartido para evitar carreras entre creación/confirmación de candidatos y registro de gastos.

La revisión detectó que la comparación por similitud y el shortcut app+timestamp pueden ser demasiado amplios. No asumir que la deduplicación actual es perfecta; separar identidad exacta del evento y similitud financiera.

---

## 10. Allowlist y elegibilidad

La allowlist sigue estando en GenericPurchaseParser.TRUSTED_PACKAGES:

- py.com.elcomercio.retailbanking
- com.google.android.gm
- com.google.android.apps.walletnfcrel

ParserEngine.isEligibleForAnalysis permite:

- Notificaciones que puede manejar un parser específico.
- Notificaciones de paquetes confiables cuando GenericPurchaseParser puede manejarlas o detecta indicios financieros.

Los indicios financieros cubren vocabulario de compras, pagos, transferencias, transacciones, débitos/créditos, tarjetas, retiros, reembolsos, depósitos y términos equivalentes en inglés.

La lista no se amplía automáticamente por el contenido de una notificación. Para agregar una fuente se debe verificar el packageName real y agregarlo deliberadamente a la allowlist. Si el formato cambia, la IA puede analizar la notificación aunque el parser devuelva null.

No se modificó el principio de seguridad que evita confiar en notificaciones publicadas por aplicaciones arbitrarias.

---

## 11. UI y configuración

Archivo nuevo: **ui/ai/AiModelScreen.kt**.

La pantalla se conecta desde MainActivity y permite:

- Ver que el modelo seleccionado es Gemma 4 E2B Instruct.
- Ver que el procesamiento ocurre localmente.
- Seleccionar Sombra, Asistido o Automático.
- Descargar el modelo bajo confirmación explícita.
- Ver estado y porcentaje de descarga.
- Cancelar la descarga.
- Verificar integridad del archivo.
- Ver métricas de la cola.
- Solicitar reintento de análisis fallidos.

Automático requiere confirmación explícita mediante un diálogo. Asistido y Automático se habilitan en función de que el modelo esté instalado; Sombra permanece disponible.

Métricas visibles:

- Predicciones de auto-registro.
- Casos que requieren revisión.
- Rechazos.
- Fallback legacy.
- Pendientes, procesando y fallidos.

Estas cifras son conteos de decisiones, **no una medición de precisión**. Para medir precisión hace falta contrastar las decisiones con el resultado real que el usuario acepta, edita o rechaza, y construir métricas etiquetadas.

---

## 12. Inyección de dependencias e integración

La aplicación utiliza DI manual; no se introdujo Hilt.

**di/AppContainer.kt** crea y conecta:

- ModelInstaller.
- LiteRtTransactionAnalyzer.
- SharedPreferencesAnalysisModeProvider.
- ReconcileTransaction.
- AnalyzeTransaction.
- LocalNotificationAnalysisQueueRepository.
- ProcessNotificationAnalysisQueue.
- RegisterAnalyzedExpense.
- CandidateAlertPublisher Android.
- Dependencias existentes de candidatos, gastos, actividad y catálogo.

ServiceLocator expone operaciones para encolar notificaciones, consultar métricas y reintentar análisis fallidos. ExpenseTrackerApp continúa siendo el punto de inicialización del contenedor.

**Regla al continuar:** no crear un segundo grafo de dependencias ni instanciar repositorios/runtime directamente desde la UI o desde NotificationListenerService. Agregar las dependencias en AppContainer y exponer la operación necesaria mediante el mecanismo ya utilizado por ServiceLocator.

---

## 13. Estructura de carpetas relevante

La siguiente estructura corresponde a la rama de esta funcionalidad. Se muestran todos los paquetes de producción relevantes, con detalle de los archivos nuevos o modificados por esta entrega y de los módulos existentes con los que interactúa.

~~~text
expense-tracker/
├── README.md
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/eliasgonzalez/expensetracker/
│       │   │   ├── ExpenseTrackerApp.kt
│       │   │   ├── data/
│       │   │   │   ├── analysis/
│       │   │   │   │   ├── LiteRtTransactionAnalyzer.kt              [NUEVO]
│       │   │   │   │   ├── ModelInstaller.kt                         [NUEVO]
│       │   │   │   │   └── SharedPreferencesAnalysisModeProvider.kt  [NUEVO]
│       │   │   │   └── local/
│       │   │   │       ├── AndroidSqlExecutor.kt                     [MODIFICADO]
│       │   │   │       ├── DbHelper.kt                                [MODIFICADO]
│       │   │   │       ├── DbSchema.kt                                [MODIFICADO]
│       │   │   │       ├── LocalCandidateRepository.kt               [MODIFICADO]
│       │   │   │       └── LocalNotificationAnalysisQueueRepository.kt [NUEVO]
│       │   │   ├── di/
│       │   │   │   └── AppContainer.kt                               [MODIFICADO]
│       │   │   ├── domain/
│       │   │   │   ├── analysis/
│       │   │   │   │   ├── AnalysisMode.kt                            [NUEVO]
│       │   │   │   │   ├── AnalyzeTransaction.kt                      [NUEVO]
│       │   │   │   │   ├── ReconcileTransaction.kt                    [NUEVO]
│       │   │   │   │   └── TransactionAnalysis.kt                     [NUEVO]
│       │   │   │   ├── model/
│       │   │   │   │   └── Expense.kt                                 [MODIFICADO]
│       │   │   │   ├── notification/
│       │   │   │   │   └── CandidateAlertPublisher.kt                 [NUEVO]
│       │   │   │   ├── repository/
│       │   │   │   │   └── NotificationAnalysisQueueRepository.kt     [NUEVO]
│       │   │   │   └── usecase/
│       │   │   │       ├── ProcessNotificationAnalysisQueue.kt       [NUEVO]
│       │   │   │       ├── RegisterAnalyzedExpense.kt                 [NUEVO]
│       │   │   │       └── RegisterExpense.kt                         [MODIFICADO]
│       │   │   ├── notification/
│       │   │   │   ├── AndroidCandidateAlertPublisher.kt              [NUEVO]
│       │   │   │   ├── ExpenseNotificationListenerService.kt          [MODIFICADO]
│       │   │   │   ├── GenericPurchaseParser.kt                       [MODIFICADO]
│       │   │   │   └── ParserEngine.kt                                [MODIFICADO]
│       │   │   │   └── [parsers y receptores existentes conservados]
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt                                [MODIFICADO]
│       │   │   │   └── ai/
│       │   │   │       └── AiModelScreen.kt                           [NUEVO]
│       │   │   └── [resto de módulos UI, quicksettings, logging y update]
│       │   └── res/
│       │       ├── drawable/
│       │       ├── mipmap-anydpi-v26/
│       │       ├── values/
│       │       └── xml/
│       └── test/kotlin/com/eliasgonzalez/expensetracker/
│           ├── data/local/
│           │   └── DbMigrationTest.kt                                [MODIFICADO]
│           ├── domain/analysis/
│           │   └── ReconcileTransactionTest.kt                       [NUEVO]
│           ├── domain/usecase/
│           │   ├── ProcessNotificationAnalysisQueueTest.kt           [NUEVO]
│           │   └── RegisterAnalyzedExpenseTest.kt                     [NUEVO]
│           └── notification/
│               ├── ParserEngineEligibilityTest.kt                    [NUEVO]
│               └── ParserEngineTest.kt                                [MODIFICADO]
├── docs/
│   └── reviews/
│       └── AI_EXPENSE_DETECTION_CODE_REVIEW.md
└── [configuración Gradle, workflows y recursos existentes]
~~~

### 13.1 Responsabilidad de las carpetas

| Carpeta | Responsabilidad | Regla de dependencia |
|---|---|---|
| domain/analysis | Contratos, modos, extracción y reconciliación | Kotlin puro; no Android ni LiteRT |
| domain/repository | Interfaces de persistencia | El dominio define los puertos |
| domain/usecase | Orquestación y reglas de negocio | Depende de contratos/repositorios |
| data/analysis | Runtime LiteRT, descarga y preferencias Android | Implementa puertos del dominio |
| data/local | Adaptadores SQLite y persistencia de la cola | Implementa repositorios |
| notification | Captura Android, parsers y publicación de alertas | Traduce notificaciones Android a snapshots |
| di | Construcción del grafo de dependencias | Único lugar de composición |
| ui/ai | Configuración, instalación y métricas | Consume operaciones expuestas por DI |
| test/ | Pruebas unitarias y de persistencia | Reglas de dominio testeables sin dispositivo |

---

## 14. Archivos modificados y por qué

Comparación de la rama contra main en el momento de documentar:

### Archivos nuevos de producción

| Archivo | Propósito |
|---|---|
| data/analysis/LiteRtTransactionAnalyzer.kt | Adaptador del runtime Gemma/LiteRT-LM al puerto de dominio |
| data/analysis/ModelInstaller.kt | Descarga, estados, espacio libre y verificación del modelo |
| data/analysis/SharedPreferencesAnalysisModeProvider.kt | Persistencia del modo |
| data/local/LocalNotificationAnalysisQueueRepository.kt | Cola y resultados en SQLite |
| domain/analysis/AnalysisMode.kt | Modos de despliegue |
| domain/analysis/AnalyzeTransaction.kt | Orquestación inferencia + reconciliación |
| domain/analysis/ReconcileTransaction.kt | Validación determinista y decisión |
| domain/analysis/TransactionAnalysis.kt | Snapshots y contratos de análisis |
| domain/notification/CandidateAlertPublisher.kt | Puerto de publicación de alerta |
| domain/repository/NotificationAnalysisQueueRepository.kt | Contrato de cola |
| domain/usecase/ProcessNotificationAnalysisQueue.kt | Worker y política por modo |
| domain/usecase/RegisterAnalyzedExpense.kt | Auto-registro directo idempotente |
| notification/AndroidCandidateAlertPublisher.kt | Adaptador Android de alertas |
| ui/ai/AiModelScreen.kt | Configuración y métricas IA |

### Archivos existentes modificados

| Archivo | Cambio |
|---|---|
| app/build.gradle.kts | Dependencia LiteRT-LM Android 0.18.1 |
| AndroidManifest.xml | Permisos/recursos de runtime y bibliotecas nativas opcionales requeridas por backend GPU |
| data/local/AndroidSqlExecutor.kt | Soporte de ejecución SQL usado por pruebas de esquema |
| data/local/DbHelper.kt | Integra el esquema v5 |
| data/local/DbSchema.kt | Agrega tabla de cola y migración v5 |
| data/local/LocalCandidateRepository.kt | Persiste/recupera merchantConfident |
| di/AppContainer.kt | Conecta runtime, cola, worker y casos de uso |
| domain/model/Expense.kt | Agrega/ajusta sourceReference para identificar el origen del registro |
| domain/usecase/RegisterExpense.kt | Ajuste para integrar el registro IA con la ruta existente y el lock compartido |
| notification/ExpenseNotificationListenerService.kt | Captura y encola snapshots de notificaciones elegibles |
| notification/GenericPurchaseParser.kt | Expone elegibilidad/indicios financieros y conserva allowlist |
| notification/ParserEngine.kt | Permite análisis IA en paquetes confiables aunque parser devuelva null |
| ui/MainActivity.kt | Acceso a la pantalla IA local |
| README.md | Documenta comportamiento, instalación, modos, arquitectura y límites |

### Tests nuevos o modificados

- ReconcileTransactionTest: reglas y decisiones de reconciliación.
- ProcessNotificationAnalysisQueueTest: modos, fallback, cola y flujo de revisión/registro.
- RegisterAnalyzedExpenseTest: registro directo, idempotencia y deduplicación.
- ParserEngineEligibilityTest: allowlist y elegibilidad aunque el parser devuelva null.
- DbMigrationTest: migración v5 y tabla de cola.
- ParserEngineTest: expectativas de integración del parser/eligibilidad.

---

## 15. Code review: defectos conocidos que deben corregirse

La revisión estática exhaustiva está documentada en **docs/reviews/AI_EXPENSE_DETECTION_CODE_REVIEW.md**. Esta sección resume los puntos para que quien continúe no los pase por alto.

### 15.1 P1 — Bloqueantes antes de confiar en Automático

#### P1-1. Evidencia de importe no vinculada semánticamente a la operación

Archivo: domain/analysis/ReconcileTransaction.kt.

La validación comprueba que el token numérico aparezca en amountEvidence, pero no demuestra que ese número sea el importe de la compra. Una notificación puede contener el monto de la compra y también un saldo disponible. El modelo podría seleccionar el saldo y citarlo literalmente; la validación actual podría aceptarlo.

Ejemplo conceptual:

- “Compra aprobada por Gs. 85.000”
- “Saldo disponible: Gs. 1.200.000”

Corrección requerida: vincular el importe a un contexto transaccional inequívoco. Si existen varios importes plausibles o no se puede determinar cuál corresponde a la operación, exigir revisión. No basta con que el número exista en el texto.

#### P1-2. Veto determinista incompleto para reembolsos, ingresos y estados negativos

Archivos: ReconcileTransaction.kt y GenericPurchaseParser.kt.

Los vetos actuales no cubren todos los sinónimos y construcciones posibles. El parser puede ser null o puede haber reconocido un gasto antes de que aparezca una indicación de reembolso/anulación. Ejemplos que deben tratarse cuidadosamente:

- reembolsada / reembolsado
- refund / refunded
- devolución / devuelto
- no aprobada / no autorizado
- anulada / anulada posteriormente
- operación revertida

Corrección requerida: vetos independientes del resultado del LLM y del hecho de que exista parser. No permitir AUTO_REGISTER si el texto contiene evidencia determinista de operación fallida, anulada, revertida o reembolsada. Si el contexto no permite decidir, revisión; no asumir gasto confirmado.

#### P1-3. Hidratación fallida puede completar un trabajo sin persistir el gasto

Archivos: RegisterAnalyzedExpense.kt y ProcessNotificationAnalysisQueue.kt.

RegisterAnalyzedExpense devuelve Long? y retorna null si awaitReady() devuelve false. El worker no distingue ese caso de un duplicado o de una salida sin registro; después puede marcar el trabajo COMPLETED. Resultado: no se crea el Expense y tampoco queda el trabajo para reintento.

Corrección requerida: devolver un resultado tipado, por ejemplo Registered, Duplicate, NotReady y Failed. COMPLETED solo cuando el efecto se haya confirmado o cuando se haya demostrado que ya existía un duplicado; NotReady/Failed debe conservar un estado recuperable.

### 15.2 P2 — Riesgos importantes

#### P2-1. Deduplicación mezcla identidad exacta y similitud financiera

Archivo: RegisterAnalyzedExpense.kt.

El shortcut por aplicación + timestamp puede bloquear dos eventos distintos con el mismo timestamp. La comparación de gastos recientes utiliza createdAt contra timestamp de notificación/operación, que son relojes semánticamente distintos. Separar:

- Identidad exacta del evento: hash estable del contenido de origen.
- Similitud financiera: importe, moneda, comercio y occurredAt, con ventana temporal explícita.

#### P2-2. Se pierde ParserSnapshot en algunos caminos de revisión

Archivo: ReconcileTransaction.kt.

Algunas ramas tempranas de INCOME/NON_TRANSACTION construyen el resultado sin preservar todos los datos del parser. Si se necesita revisión por conflicto, debe conservarse el snapshot suficiente para mostrar el contexto y decidir qué valor priorizar.

#### P2-3. Retención y reintentos

Archivos: ProcessNotificationAnalysisQueue.kt y LocalNotificationAnalysisQueueRepository.kt.

En recoverOnStartup se ejecuta retryFailed antes de pruneTerminal. Los FAILED antiguos se vuelven PENDING y dejan de ser terminales, por lo que no los elimina la limpieza de esa ejecución. Además, retryFailed reintenta todos los FAILED sin una política explícita de límite de intentos/backoff. Definir límite, backoff y orden de limpieza.

#### P2-4. Auditoría insuficiente para explicar decisiones

Archivo: LocalNotificationAnalysisQueueRepository.kt.

Se guardan campos finales, decisión y razones, pero no toda la extracción original con sus evidencias ni el resultado concreto del efecto de negocio (ID del Expense, ID de candidate, duplicado, etc.). Para depurar falsos positivos se necesita una auditoría versionada, con sanitización y retención apropiadas. Evaluar privacidad y no registrar más datos de los necesarios.

#### P2-5. Fallo al publicar alerta queda silenciado

Archivo: ProcessNotificationAnalysisQueue.kt.

El candidato puede persistirse y la publicación de la notificación Android fallar. runCatching evita que el error afecte el estado del trabajo, pero no existe una cola independiente para reintentar la entrega. Separar persistencia del candidato y entrega de alerta; la bandeja interna debe seguir siendo la fuente de verdad.

#### P2-6. Lifecycle y límites del runtime

Archivo: LiteRtTransactionAnalyzer.kt.

No hay política explícita suficientemente probada para liberar Engine/memoria, límites de tamaño de prompt/salida, timeout de inferencia o stress test. Definir cierre seguro, recuperación de errores nativos y pruebas prolongadas en hardware real.

### 15.3 Positivos observados en el review

- Se respetó la separación por capas.
- La allowlist de paquetes se mantiene.
- ParserEngine puede encolar una notificación elegible aunque parserResult sea null.
- El auto-registro no crea un candidato temporal.
- Se reutiliza el lock compartido con flujos existentes.
- La migración de base es aditiva.
- El archivo del modelo está fijado a una revisión y se verifica por tamaño y SHA-256.
- El fallback legacy está contemplado.
- La configuración no habilita Automático de forma predeterminada.

---

## 16. Pruebas y estado de validación

### 16.1 Pruebas agregadas

Los tests cubren, entre otros:

- Decisiones AUTO_REGISTER, REQUIRES_REVIEW y REJECT.
- Evidencia literal y conflictos con parser.
- Ingresos y notificaciones no transaccionales.
- Modo Sombra sin auto-registro.
- Modo Asistido creando candidato para revisión.
- Modo Automático registrando Expense directamente.
- Análisis sin parser cuando la evidencia es suficiente.
- Fallback cuando el modelo falla.
- Trabajo sin parser conservado para reintento.
- Idempotencia de auto-registro.
- Detección de candidato/gasto equivalente para reducir duplicados.
- Elegibilidad de fuentes confiables y rechazo de paquetes no confiables.
- Migración SQLite v5.

### 16.2 Validación que NO se realizó

Al momento de cerrar esta implementación/documentación:

- No se ejecutó ./gradlew testDebugUnitTest.
- No se ejecutó ./gradlew assembleDebug.
- No se ejecutaron tests instrumentados.
- No se descargó ni ejecutó el modelo en el Galaxy S24 Ultra como prueba end-to-end.
- No se midieron latencia, RAM, temperatura, consumo energético ni estabilidad de GPU/CPU.
- No hay resultados de CI que permitan afirmar que la rama compila o que los tests pasan.

No afirmar que la implementación está compilada, validada o lista para producción hasta ejecutar esas comprobaciones.

### 16.3 Primeras validaciones recomendadas

Desde el entorno Android con JDK/SDK configurados:

~~~bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
~~~

Luego, en dispositivo físico:

1. Instalar un APK de debug apropiado para pruebas.
2. Confirmar permisos de acceso a notificaciones.
3. Verificar que las fuentes permitidas siguen funcionando con el parser legacy.
4. Probar una notificación elegible cuyo parser devuelve null.
5. Probar que una app fuera de allowlist no entra al análisis.
6. Descargar el modelo desde la pantalla IA local usando Wi-Fi.
7. Verificar que tamaño y SHA-256 permiten marcarlo instalado.
8. Ejecutar en Sombra y comprobar que no hay auto-registros.
9. Inspeccionar métricas y estados de cola.
10. Simular cierre/reinicio del proceso con un trabajo PROCESSING.
11. Probar ingresos, reembolsos, anulaciones, rechazos y notificaciones con múltiples importes.
12. Solo después de corregir P1 y validar un conjunto representativo, evaluar Asistido y luego Automático.

El hardware objetivo declarado para esta evaluación es un Samsung Galaxy S24 Ultra con Snapdragon 8 Gen 3 y 12 GB de RAM. La compatibilidad y el rendimiento en ese dispositivo siguen pendientes de prueba.

---

## 17. Dependencias y configuración técnica

Cambios relevantes en app/build.gradle.kts:

- com.google.ai.edge.litertlm:litertlm-android:0.18.1
- Kotlin coroutines Android ya utilizadas por la app.
- Jetpack Compose existente para UI.
- JUnit y kotlinx-coroutines-test para pruebas.
- org.xerial:sqlite-jdbc para probar migraciones contra SQLite real en JVM.

No se agregó Room ni Hilt.

El manifest declara los permisos/recursos necesarios para conectividad, descarga/instalación de actualizaciones ya existente y uso de bibliotecas nativas opcionales para backend GPU. El modelo no requiere permiso general de almacenamiento porque se guarda en el directorio privado de la aplicación.

---

## 18. Historial de commits de la rama

Commits propios de esta entrega, en orden cronológico:

1. **68eeca27cd** — feat(domain): add transaction analysis and reconciliation contracts
2. **4609265d69** — feat(db): persist notification analysis queue and candidate confidence
3. **2b1906ee90** — feat(ai): add verified on-device Gemma runtime and installer
4. **87dd39181e** — feat(analysis): process queued events with idempotent auto registration
5. **0342e54296** — feat(notification): enqueue allowlisted financial notifications for AI
6. **d5bbe2b284** — feat(ui): wire local AI modes, model setup and analysis metrics
7. **ff73290cc9** — docs: document local AI pipeline and rollout safeguards
8. **8f13628ffa** — docs(review): document local AI detection findings

La rama se trabajó de forma incremental con commits granulares. La documentación de revisión y este contexto forman parte de la misma rama.

---

## 19. Estado de Git y alcance de los cambios

En la última comparación consultada:

- Rama: feat/ai-expense-detection.
- Base comparada: main.
- Estado: ahead.
- La rama tenía 8 commits por delante de main y 0 por detrás en esa comparación.
- No se abrió una rama adicional para el review.
- No se abrió un Pull Request.
- No se modificaron main ni next.
- Los cambios de implementación y documentación se hicieron en la rama autorizada.

Antes de continuar, volver a consultar el HEAD remoto y comparar la rama con main; el usuario puede haber agregado commits desde que se escribió este documento.

---

## 20. Guía para continuar el desarrollo

Orden sugerido para el próximo agente/desarrollador:

1. Leer este documento y el code review completo.
2. Confirmar HEAD actual y árbol real de la rama; no asumir que este documento está actualizado si hubo commits posteriores.
3. No crear otra rama: continuar en feat/ai-expense-detection, salvo instrucción explícita distinta.
4. Corregir primero P1-1, P1-2 y P1-3.
5. Agregar tests de regresión para cada escenario de los P1 antes de modificar la implementación.
6. Resolver los P2 de deduplicación, snapshots, retención, auditoría, publicación de alertas y lifecycle.
7. Ejecutar tests JVM y build Android.
8. Instalar y probar en dispositivo físico con modo Sombra.
9. Construir un conjunto de notificaciones reales anonimizadas y etiquetadas para medir falsos positivos y falsos negativos.
10. No activar Automático hasta que las reglas y los resultados observados sean satisfactorios.
11. Actualizar este documento cuando cambien arquitectura, contratos, estructura, decisiones, limitaciones o estado de validación.
12. Mantener el documento de code review como registro de hallazgos; marcar hallazgos como resueltos solo después de verificar el fix y sus pruebas.

### Reglas que no deben romperse

- No reemplazar parsers deterministas por el LLM.
- No retirar la allowlist ni analizar paquetes arbitrarios.
- No considerar la confianza autorreportada del LLM como prueba.
- No auto-registrar si el movimiento, importe, moneda, comercio o evidencia son ambiguos.
- No tratar ingresos, reembolsos, anulaciones o rechazos como gastos.
- No crear ExpenseCandidate intermedio en el camino AUTO_REGISTER.
- No marcar COMPLETED si no se confirmó el efecto de negocio o un duplicado real.
- No perder notificaciones al fallar el modelo.
- No hacer migraciones destructivas.
- No acoplar el dominio a Android, LiteRT-LM o SQLite.
- No agregar Hilt/Room por conveniencia sin una decisión arquitectónica explícita.
- No declarar tests/build exitosos sin haberlos ejecutado.
- No activar Automático por defecto.

---

## 21. Referencias

- README del proyecto: ../README.md
- Code review detallado: reviews/AI_EXPENSE_DETECTION_CODE_REVIEW.md
- LiteRT-LM Android: https://ai.google.dev/edge/litert-lm/android
- Modelo Gemma 4 E2B LiteRT-LM: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Revisión exacta del modelo usada por el instalador: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/9262660a1676eed6d0c477ab1a86344430854664

