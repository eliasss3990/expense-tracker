package com.eliasgonzalez.expensetracker.data.analysis

import android.content.Context
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationRequest
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.LocalModelManager
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpreter
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

private const val TAG = "LiteRtInterpreter"

/** Tope de tokens de salida: la respuesta es un JSON chico; mas que esto es un modelo desbocado. */
private const val MAX_OUTPUT_TOKENS = 400

/** Cuanto se espera a que el motor confirme una cancelacion antes de cerrar la conversacion igual. */
private const val CANCEL_GRACE_MILLIS = 5_000L

/**
 * Adaptador de [TransactionInterpreter] sobre LiteRT-LM. Es la UNICA clase que conoce a la libreria y a
 * su formato de archivo; cambiar de runtime es escribir otro adaptador.
 *
 * - **Salida restringida:** la respuesta se limita por JSON Schema (decodificacion restringida del runtime),
 *   con las categorias del catalogo del usuario como unico valor posible de `category`. Si el motor rechaza la
 *   restriccion se reintenta una vez sin ella y se recuerda (el parser sigue siendo tolerante).
 * - **Cancelacion real:** la inferencia es asincrona y un timeout o una cancelacion llaman a
 *   `Conversation.cancelProcess()`; la cola no queda congelada detras de una llamada colgada.
 * - **Ciclo de vida del motor** (los pesos ocupan RAM): perezoso, un solo hilo para crear/cerrar, serializado
 *   por un mutex, cerrado tras [idleCloseMillis] sin uso. GPU primero, CPU si no inicializa o si falla al inferir.
 */
class LiteRtTransactionInterpreter(
    context: Context,
    private val descriptor: ModelDescriptor,
    private val models: LocalModelManager,
    private val idleCloseMillis: Long = 2 * 60_000L,
    private val timeoutMillis: Long = 120_000L,
) : TransactionInterpreter {

    private val appContext = context.applicationContext
    private val modelPath = ModelFiles.fileFor(appContext, descriptor).absolutePath

    // Solo para crear y cerrar el motor (initialize() puede tardar ~10 s); la inferencia en si es asincrona.
    private val engineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "expense-transaction-ai") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var usingGpu = false
    private var constrainedDecoding = true
    private var idleJob: Job? = null

    override suspend fun interpret(request: InterpretationRequest): InterpretationResult = mutex.withLock {
        check(models.isReady()) { "El modelo local no esta instalado" }
        idleJob?.cancel()
        val started = System.nanoTime()
        try {
            val raw = withTimeout(timeoutMillis) { generate(request) }
            val interpretation = ModelOutputParser.parse(raw)
            InterpretationResult(interpretation, request.pass, descriptor.id, (System.nanoTime() - started) / 1_000_000)
        } catch (timeout: TimeoutCancellationException) {
            // La inferencia ya se cancelo (cancelProcess); se recicla el motor por si quedo en mal estado.
            AppLog.w(TAG, "la inferencia supero ${timeoutMillis}ms y se cancelo; se recicla el motor")
            closeEngine()
            throw IllegalStateException("Timeout de inferencia", timeout)
        } finally {
            scheduleIdleClose()
        }
    }

    private suspend fun generate(request: InterpretationRequest): String {
        val first = engine ?: withContext(engineDispatcher) { createEngine() }.also { engine = it }
        return try {
            respond(first, request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!usingGpu) throw error
            // GPU inicializo pero fallo al inferir (ej. OpenCL ausente): una vez en CPU.
            AppLog.w(TAG, "fallo la inferencia en GPU, se reintenta en CPU: ${error.javaClass.simpleName}")
            closeEngine()
            val cpu = withContext(engineDispatcher) { createEngine(cpuOnly = true) }.also { engine = it }
            respond(cpu, request)
        }
    }

    /** Con restriccion de salida; si el motor la rechaza, una vez sin ella, y se recuerda para las siguientes. */
    private suspend fun respond(engine: Engine, request: InterpretationRequest): String {
        if (!constrainedDecoding) return ask(engine, request, constrained = false)
        return try {
            ask(engine, request, constrained = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.w(TAG, "decodificacion restringida fallo (${error.javaClass.simpleName}); se reintenta sin restriccion")
            val plain = ask(engine, request, constrained = false)
            constrainedDecoding = false // el reintento funciono: la restriccion es lo que el motor no acepta
            AppLog.w(TAG, "decodificacion restringida desactivada para esta sesion")
            plain
        }
    }

    private suspend fun ask(engine: Engine, request: InterpretationRequest, constrained: Boolean): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(InterpretationPrompts.system(request.pass)),
            samplerConfig = SamplerConfig(topK = 1, topP = 0.1, temperature = 0.0),
            maxOutputToken = MAX_OUTPUT_TOKENS,
            enableResponseFormat = constrained,
        )
        val format = if (constrained) ResponseFormat.json(InterpretationSchema.build(request.categories)) else null
        val conversation = withContext(engineDispatcher) { engine.createConversation(config) }
        try {
            return conversation.complete(Contents.of(InterpretationPrompts.user(request)), format)
        } finally {
            runCatching { conversation.close() }
        }
    }

    /**
     * Envia el mensaje de forma asincrona y espera la respuesta completa. Si la corrutina se cancela (timeout
     * incluido) se cancela la inferencia en el motor y se espera su confirmacion antes de devolver el control.
     */
    private suspend fun Conversation.complete(contents: Contents, format: ResponseFormat?): String {
        val text = StringBuilder()
        val done = CompletableDeferred<String>()
        sendMessageAsync(
            contents,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    text.append(message.toString())
                }

                override fun onDone() {
                    done.complete(text.toString())
                }

                override fun onError(throwable: Throwable) {
                    done.completeExceptionally(throwable)
                }
            },
            responseFormat = format,
        )
        try {
            return done.await()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching { cancelProcess() }
                // Hasta que el motor confirme, no se cierra la conversacion: cerrarla en medio puede crashear.
                runCatching { withTimeout(CANCEL_GRACE_MILLIS) { done.join() } }
            }
            throw cancelled
        }
    }

    private fun createEngine(cpuOnly: Boolean = false): Engine {
        val cache = appContext.cacheDir.absolutePath
        if (!cpuOnly) {
            try {
                val gpu = Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = cache))
                gpu.initialize()
                usingGpu = true
                AppLog.i(TAG, "motor listo backend=GPU")
                return gpu
            } catch (gpuError: Exception) {
                // No se llama close() a una instancia cuyo initialize() fallo: puede lanzar.
                AppLog.w(TAG, "GPU no disponible, se usa CPU: ${gpuError.javaClass.simpleName}")
            }
        }
        val cpu = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cache))
        cpu.initialize()
        usingGpu = false
        AppLog.i(TAG, "motor listo backend=CPU")
        return cpu
    }

    private suspend fun closeEngine() {
        withContext(engineDispatcher) {
            engine?.let { runCatching { it.close() } }
            engine = null
            usingGpu = false
        }
    }

    private fun scheduleIdleClose() {
        idleJob = scope.launch {
            delay(idleCloseMillis)
            mutex.withLock {
                if (engine != null) {
                    closeEngine()
                    AppLog.i(TAG, "motor cerrado por inactividad")
                }
            }
        }
    }
}
