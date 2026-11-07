# Code review — IA local para detección de gastos

- **Repositorio:** `eliasss3990/expense-tracker`
- **Rama revisada:** `feat/ai-expense-detection`
- **Commit de referencia de la revisión:** `ff73290cc9246e67b218982025cc6a9c0c4858b6`
- **Fecha:** 2026-11-07
- **Alcance:** revisión estática de la implementación de análisis local de notificaciones, reconciliación, registro automático, cola persistente, deduplicación y runtime LiteRT-LM.
- **Estado general:** requiere correcciones antes de habilitar el modo Automático con datos financieros reales.

> Los hallazgos describen riesgos del código revisado en el commit indicado. Las referencias de archivo y líneas son orientativas: pueden desplazarse si la rama cambia. No se ejecutó la suite Gradle ni se compiló el APK durante esta revisión; por tanto, este documento no certifica compilación ni ejecución en dispositivo.

## Resumen ejecutivo

| Severidad | Cantidad | Estado |
|---|---:|---|
| P1 — Alta / bloqueante | 3 | Corregir antes de usar registro automático |
| P2 — Importante | 6 | Corregir antes de considerar robusta la integración |
| Validación pendiente | — | Ejecutar pruebas JVM, compilación Android e instrumentación en dispositivo |

Los tres P1 afectan directamente la integridad de los datos financieros: una evidencia de importe puede corresponder a un saldo y no a la compra; una devolución o una operación denegada puede atravesar los filtros; y un fallo temporal al hidratar dependencias puede marcar un trabajo como completado aunque no se haya registrado nada.

---

## P1 — Hallazgos de alta prioridad

### P1-1. La evidencia del importe no demuestra que sea el importe de la transacción

- **Área:** `domain/analysis/ReconcileTransaction.kt`
- **Ubicación aproximada:** validación de `amountEvidence` y decisión de `AUTO_REGISTER` (aprox. líneas 95–124 y 160–173).
- **Riesgo:** registro automático de un importe incorrecto.

La validación actual comprueba que el valor numérico elegido por el modelo aparece en el texto citado como evidencia. Eso demuestra que el número existe en la notificación, pero no que represente el importe de la compra.

**Escenario reproducible**

Notificación:

> Compra aprobada por Gs. 85.000 en Supermercado Real. Saldo disponible: Gs. 1.200.000.

Si el modelo selecciona `1.200.000` como importe y cita el fragmento que contiene ese saldo, la comprobación literal puede aceptarlo. La coincidencia con moneda, comercio y otros indicios no resuelve la relación semántica entre el importe y la operación.

**Corrección recomendada**

- Exigir que la evidencia vincule explícitamente el importe con la operación (por ejemplo, una etiqueta o relación contextual inequívoca: “compra por”, “importe de la operación”, “total pagado”).
- No aceptar como evidencia suficiente una cifra aislada o un fragmento que solo contenga el número.
- Detectar notificaciones con varios importes plausibles (saldo, límite, cuotas, importe anterior, cashback, etc.). Si no se puede determinar inequívocamente cuál corresponde a la transacción, degradar a `REQUIRES_REVIEW`.
- Aplicar la misma regla al camino en que `parser == null`; es el caso donde más depende la decisión de la extracción del modelo.
- Mantener el rechazo de importes no positivos y validar moneda/escala de forma independiente.

**Pruebas necesarias**

- Compra más saldo disponible: solo se acepta el importe de compra.
- Compra más límite disponible.
- Compra en cuotas con importe de cuota y total.
- Notificación con dos importes sin etiquetas concluyentes: requiere revisión.
- El modelo cita correctamente un saldo, pero no el importe de compra: nunca debe producir `AUTO_REGISTER`.

### P1-2. Los vetos deterministas no cubren suficientemente reembolsos, anulaciones y operaciones no autorizadas

- **Área:** `domain/analysis/ReconcileTransaction.kt` y `notification/GenericPurchaseParser.kt`
- **Ubicación aproximada:** filtros de movimiento/estado (aprox. líneas 25–90 y 202–208) y `incomeKeywordRegex`.
- **Riesgo:** registrar como gasto una devolución, un reembolso o una compra que no llegó a aprobarse.

El veto de ingresos se aplica de forma condicionada cuando no existe parser. Si el parser ya produjo un snapshot, algunas comprobaciones de movimiento entrante no actúan como veto independiente. Además, el conjunto de estados negativos no contempla expresiones frecuentes como:

- `reembolsada`, `reembolso`, `devolución`, `devuelta`, `refund`;
- `anulada`, `cancelada`;
- `no aprobada`, `no autorizada`, `rechazada`;
- `no se pudo completar`, `operación fallida`.

El parser genérico también puede interpretar ciertos textos de devolución como compras si no los clasifica explícitamente como movimiento no elegible.

**Escenario reproducible**

> Compra por Gs. 85.000 en Supermercado Real. La operación fue reembolsada.

El parser puede extraer importe y comercio a partir de “Compra”. Si el modelo clasifica el evento como egreso y las demás evidencias coinciden, la ausencia de un veto para “reembolsada” puede permitir que llegue a `AUTO_REGISTER`.

**Corrección recomendada**

- Implementar vetos deterministas para reembolso, devolución, reversión, anulación, rechazo y falta de autorización.
- Ejecutarlos independientemente del resultado del parser y de la clasificación del modelo.
- Diferenciar una compra aprobada de una notificación posterior que informa su devolución. No convertir la devolución en un gasto nuevo.
- Para señales contradictorias o ambiguas, usar `REQUIRES_REVIEW`; reservar `REJECT` para casos cuya no elegibilidad sea inequívoca.
- Ampliar los términos en español e inglés, contemplando variaciones morfológicas y expresiones habituales de las entidades financieras.
- Revisar que `GenericPurchaseParser` no devuelva un gasto para una notificación de reembolso aunque contenga la palabra “compra”.

**Pruebas necesarias**

- Reembolso de una compra anterior.
- Devolución parcial y total.
- Compra anulada.
- Compra rechazada/no autorizada.
- Transferencia entrante en español e inglés cuando el parser sí reconoce el texto.
- Compra aprobada seguida de una notificación separada de reembolso.

### P1-3. Un fallo de hidratación puede marcar el trabajo como completado sin guardar el gasto

- **Área:** `domain/usecase/RegisterAnalyzedExpense.kt` y `domain/usecase/ProcessNotificationAnalysisQueue.kt`
- **Ubicación aproximada:** retorno temprano de `awaitReady()` y posterior llamada a `markCompleted()` (aprox. líneas 35–55 y 87–100).
- **Riesgo:** pérdida silenciosa de una operación detectada.

Si `awaitReady()` devuelve `false`, el registro puede devolver `null`. El procesador de la cola no distingue ese caso de un resultado válido y termina marcando el trabajo como `COMPLETED`. La notificación deja de reintentarse aunque no se haya creado ni un `Expense` ni un candidato.

Además, la hidratación espera varios repositorios, incluido el catálogo. Un problema temporal en una dependencia que no debería impedir necesariamente toda la operación financiera puede bloquear el registro.

**Corrección recomendada**

- Reemplazar el retorno ambiguo `null` por un resultado tipado, por ejemplo: `Registered`, `Duplicate`, `NotReady` y `Failed`.
- Marcar como completado únicamente los resultados terminales reales (`Registered` o `Duplicate`, según la semántica elegida).
- Para `NotReady` o errores recuperables, dejar el trabajo en estado reintentable y registrar el motivo técnico sin contenido financiero.
- Revisar qué dependencias son realmente obligatorias para persistir el gasto y evitar que una dependencia auxiliar bloquee el registro.
- Garantizar idempotencia para que un reintento tras una escritura parcial no cree duplicados.

**Pruebas necesarias**

- `awaitReady() == false`: el trabajo no debe quedar `COMPLETED`.
- Error temporal de repositorio: el trabajo debe poder reintentarse.
- Reintento después de una escritura parcial: no duplica el gasto.
- Dependencia auxiliar no disponible: comportamiento definido y cubierto por test.

---

## P2 — Hallazgos importantes

### P2-1. La deduplicación mezcla identidad del evento con similitud financiera

- **Área:** `domain/usecase/RegisterAnalyzedExpense.kt`
- **Ubicación aproximada:** `matchesRecentExpense()` y `matchesRecentCandidate()` (aprox. líneas 52–112).
- **Riesgo:** tanto duplicados no detectados como transacciones legítimas suprimidas.

Se compara `existing.createdAt` con `notification.timestamp`, aunque el instante relevante para comparar movimientos debería ser el momento de la transacción (`occurredAt`), cuando esté disponible.

Por otro lado, el atajo que considera duplicado un candidato por coincidir en aplicación y timestamp no exige comparar importe ni comercio. Dos operaciones diferentes de la misma app pueden producir notificaciones con marcas temporales iguales o muy cercanas.

**Corrección recomendada**

- Separar la deduplicación exacta del mismo evento (identificador/hash estable de notificación) de la detección heurística de operaciones financieras similares.
- Para similitud financiera, comparar importe, moneda, comercio normalizado y una ventana temporal basada en `occurredAt` cuando exista.
- No suprimir una operación solo por app + timestamp.
- Definir explícitamente qué hacer cuando falta alguno de los campos: reducir confianza y enviar a revisión, en vez de asumir duplicidad.
- Mantener la protección de concurrencia compartida entre los casos de uso.

**Pruebas necesarias**

- Mismo evento recibido dos veces: un solo registro.
- Dos compras distintas de igual importe y comercio en momentos diferentes: ambas se conservan.
- Dos operaciones de la misma app con timestamp igual, pero distinto importe: ambas se conservan.
- Aviso bancario y aviso de billetera para la misma operación: detectar duplicado según la regla financiera.
- Diferencia entre `createdAt` y `occurredAt`.

### P2-2. Algunas decisiones de revisión pierden el ParserSnapshot

- **Área:** `domain/analysis/ReconcileTransaction.kt`
- **Ubicación aproximada:** ramas `INCOME` y `NON_TRANSACTION` (aprox. líneas 44–75).
- **Riesgo:** candidatos de revisión incompletos pese a que el parser había extraído datos útiles.

En determinadas ramas se construye el resultado reconciliado pasando `null` en lugar del snapshot del parser. Aunque la decisión termine en `REQUIRES_REVIEW`, se pierde información determinista que podría completar el candidato.

El publicador de candidatos no necesariamente recupera los datos del parser cuando el motivo de revisión es un conflicto de movimiento. Si la extracción del modelo omite campos, el candidato puede no crearse o quedar incompleto.

**Corrección recomendada**

- Preservar el ParserSnapshot en todas las ramas que devuelven revisión.
- Definir una única política de combinación de campos para el candidato: conservar los valores deterministas disponibles y marcar como conflictivos los campos disputados.
- No descartar datos útiles del parser por una clasificación de movimiento que precisamente requiere revisión.

**Pruebas necesarias**

- Parser con importe/comercio y modelo con movimiento entrante: el resultado de revisión conserva los campos del parser.
- Modelo con campos incompletos y parser completo: candidato con los datos recuperables.
- Conflicto de movimiento: no se registra automáticamente, pero tampoco se pierden los datos deterministas.

### P2-3. La poda de la cola no retiene correctamente los trabajos FAILED

- **Área:** `domain/usecase/ProcessNotificationAnalysisQueue.kt` y `data/local/LocalNotificationAnalysisQueueRepository.kt`
- **Ubicación aproximada:** orden de `retryFailed()` y `pruneTerminal()` (aprox. líneas 35–55 y 155–190).
- **Riesgo:** retención indefinida de texto original de notificaciones y reintentos sin límite.

El procesador vuelve a poner los trabajos `FAILED` en `PENDING` antes de ejecutar la poda de registros terminales. Como resultado, los trabajos fallidos antiguos pueden dejar de ser elegibles para la limpieza. También debe verificarse que el contador `attempts` tenga un límite efectivo.

**Corrección recomendada**

- Podar primero los registros terminales vencidos y luego recuperar trabajos reintentables.
- Aplicar un máximo de intentos y una política de backoff.
- Definir el comportamiento al alcanzar el máximo: estado terminal con retención acotada y limpieza posterior.
- Basar la política en timestamps persistidos para que sobreviva al reinicio del proceso.
- Mantener la retención de datos financieros dentro del plazo documentado.

**Pruebas necesarias**

- `FAILED` antiguo: se elimina al superar el plazo de retención.
- `FAILED` reciente: se reintenta según la política.
- Máximo de intentos alcanzado: no vuelve a `PENDING`.
- Reinicio del proceso: no reinicia el contador ni el backoff.

### P2-4. La auditoría no conserva la extracción original ni la evidencia que justificó la decisión

- **Área:** `data/local/LocalNotificationAnalysisQueueRepository.kt`
- **Ubicación aproximada:** `markCompleted()`.
- **Riesgo:** imposibilidad de investigar falsos positivos y medir la calidad real del modelo.

Se persisten campos reconciliados, motivos y versión del modelo, pero no queda guardada de forma estructurada la extracción original ni toda la evidencia textual usada para justificar importe, moneda, comercio y tipo de movimiento. Tampoco queda claramente asociado el efecto final de la decisión (ID del gasto, candidato, duplicado, etc.).

**Corrección recomendada**

- Persistir la salida original del modelo en un formato versionado (por ejemplo, JSON con versión de esquema) o columnas específicas.
- Guardar por separado extracción, reconciliación y resultado del efecto secundario.
- Registrar IDs de `Expense`/`ExpenseCandidate` cuando corresponda.
- Evitar guardar más contenido sensible del necesario; establecer retención y sanitización explícitas.
- Facilitar la comparación entre predicción, parser y resolución posterior del usuario.

**Pruebas necesarias**

- Se puede reconstruir por qué una decisión fue `AUTO_REGISTER` o `REQUIRES_REVIEW`.
- La versión del esquema permite leer registros antiguos.
- La limpieza elimina también la evidencia asociada cuando vence la retención.
- Los logs no exponen el texto financiero completo.

### P2-5. Los errores de publicación de alertas se silencian y no se reintentan

- **Área:** `domain/usecase/ProcessNotificationAnalysisQueue.kt`
- **Ubicación aproximada:** `publishCandidate()`.
- **Riesgo:** candidato persistido pero usuario sin notificación de que debe revisarlo.

La publicación de la alerta usa `runCatching` sin inspeccionar ni propagar el resultado. La cola puede quedar como completada aunque la alerta falle. Los errores del analizador, por su parte, parecen persistirse solo como clase de excepción, sin suficiente contexto técnico para diagnosticar el fallo.

**Corrección recomendada**

- Separar el estado de persistencia del candidato del estado de entrega de la alerta.
- Registrar el fallo técnico de publicación y permitir reintentar la entrega sin volver a crear el candidato.
- Usar logs sanitizados: clase de error, etapa, versión del modelo y contexto técnico mínimo; nunca el texto completo de la notificación ni datos financieros sensibles.
- Definir qué sucede si la alerta falla repetidamente.

**Pruebas necesarias**

- Candidato guardado y alerta fallida: el candidato sigue existiendo y la entrega queda reintentable.
- Reintento de alerta: no crea un segundo candidato.
- Error permanente: estado observable y log sanitizado.

### P2-6. El runtime no define una política explícita de liberación de memoria ni límites de entrada/salida

- **Área:** `data/analysis/LiteRtTransactionAnalyzer.kt`
- **Riesgo:** consumo sostenido de memoria, latencia elevada o bloqueo de la cola ante notificaciones anormalmente grandes.

El engine/executor se mantiene vivo sin una política clara de cierre o descarga por inactividad. La API de LiteRT-LM dispone de liberación explícita de recursos mediante `Engine.close()`. Tampoco se observan límites explícitos suficientemente claros para el tamaño del prompt, tokens de salida y duración de una inferencia. Una notificación extensa (por ejemplo, un correo) podría consumir recursos desproporcionados.

**Corrección recomendada**

- Definir ciclo de vida del engine: inicialización perezosa, uso serializado y cierre/descarga cuando sea seguro.
- Incorporar límites de longitud de entrada y tokens de salida.
- Definir timeout y política de cancelación/recovery compatible con las garantías reales de la API síncrona utilizada.
- Evitar cerrar el engine mientras exista una inferencia en curso.
- Medir RAM, latencia y estabilidad en el Galaxy S24 Ultra antes de recomendar el modo Automático.

**Pruebas necesarias**

- Entrada excesivamente larga: truncamiento controlado o revisión/fallback, nunca bloqueo.
- Inferencia fallida o timeout: la cola continúa procesando trabajos posteriores.
- Cierre y reapertura del runtime: recursos liberados y reinicialización correcta.
- Pruebas de estrés con varias notificaciones encoladas.

---

## Validación pendiente

No se ejecutaron durante esta revisión:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Pruebas instrumentadas del runtime LiteRT-LM en Android.
- Pruebas de inferencia reales en el Galaxy S24 Ultra.
- Pruebas de estrés de cola, memoria y latencia.

En el commit revisado no había check-runs de CI asociados. El workflow del proyecto está configurado para ejecutarse en pull requests hacia `next`/`main`; no se abrió ningún PR como parte de esta revisión. Por lo tanto, **no debe interpretarse la ausencia de fallos de CI como una compilación validada**.

### Matriz mínima recomendada antes de habilitar AUTOMATIC

| Área | Casos mínimos |
|---|---|
| Importe | Compra + saldo; cuotas; múltiples importes; importe ambiguo |
| Movimiento | Ingreso; reembolso; devolución; anulación; rechazo; compra aprobada |
| Parser | Parser válido; parser nulo; parser/modelo en conflicto |
| Persistencia | Hidratación fallida; error temporal; escritura parcial; reintento |
| Deduplicación | Evento repetido; dos compras distintas; banco + billetera |
| Cola | Recuperación PROCESSING; FAILED; backoff; máximo de intentos; retención |
| Alertas | Publicación exitosa; fallo; reintento sin duplicar candidato |
| Runtime | Prompt grande; inferencia fallida; liberación de engine; carga sostenida |

---

## Aspectos positivos observados

- Se conserva la arquitectura por capas y la inyección de dependencias manual existente.
- Se mantiene la allowlist de aplicaciones; no se amplía el análisis a notificaciones arbitrarias.
- El flujo de análisis puede recibir el resultado del parser o `null`, según el diseño acordado.
- El modo Automático no está habilitado por defecto y existe un modo Sombra para observar decisiones sin alterar el flujo visible.
- El auto-registro no necesita crear un candidato temporal antes del `Expense`.
- Se reutilizan mecanismos existentes de mutex/idempotencia para proteger operaciones concurrentes.
- La migración SQLite es aditiva; no se observaron operaciones destructivas de borrado de tablas en la migración revisada.
- El artefacto del modelo está fijado a una revisión concreta y se valida mediante tamaño y SHA-256 antes de su uso.
- El fallback legacy está contemplado para escenarios donde el modelo no está instalado o falla.

## Orden recomendado de corrección

1. **P1-1:** vincular semánticamente la evidencia del importe con la transacción.
2. **P1-2:** reforzar vetos de reembolsos, ingresos y estados negativos.
3. **P1-3:** impedir que un fallo de hidratación se marque como completado.
4. **P2-2:** preservar ParserSnapshot en las rutas de revisión.
5. **P2-1:** separar identidad de evento y deduplicación heurística.
6. **P2-3:** corregir retención, máximo de intentos y backoff de la cola.
7. **P2-5:** hacer observable y reintentable la entrega de alertas.
8. **P2-4:** completar la auditoría versionada y sanitizada.
9. **P2-6:** establecer límites y ciclo de vida del runtime.
10. Ejecutar toda la matriz de pruebas, compilación e instrumentación en el dispositivo objetivo.

**Recomendación final:** mantener el modo Sombra mientras se corrigen los hallazgos. No habilitar el modo Automático con notificaciones financieras reales hasta resolver los tres P1 y validar los escenarios de importe ambiguo, reembolsos y recuperación de fallos.
