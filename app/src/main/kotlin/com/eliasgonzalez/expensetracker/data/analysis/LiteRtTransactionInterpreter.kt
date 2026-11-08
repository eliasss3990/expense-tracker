package com.eliasgonzalez.expensetracker.data.analysis

import android.content.Context
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationRequest
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.LocalModelManager
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpreter
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

private const val TAG = "LiteRtInterpreter"

/**
 * Adaptador de [TransactionInterpreter] sobre LiteRT-LM. Es la UNICA clase que conoce a la
 * libreria y a su formato de archivo; cambiar de runtime es escribir otro adaptador.
 *
 * Ciclo de vida del Engine (los pesos ocupan RAM):
 * - se crea perezosamente en la primera inferencia, nunca en el arranque;
 * - todo corre en un unico hilo y bajo un mutex: nunca dos inferencias a la vez ni un cierre en
 *   medio de una;
 * - se cierra solo tras [idleCloseMillis] sin uso y se vuelve a crear cuando hace falta;
 * - GPU primero, CPU si no inicializa o si falla en la primera inferencia.
 *
 * Limite conocido: una llamada nativa en curso no se puede interrumpir. Si supera [timeoutMillis]
 * la corrutina se cancela y el Engine se recicla, pero el hilo sigue ocupado hasta que la
 * llamada vuelva; las siguientes esperan en la misma cola.
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
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "expense-transaction-ai") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var usingGpu = false
    private var idleJob: Job? = null

    override suspend fun interpret(request: InterpretationRequest): InterpretationResult = mutex.withLock {
        check(models.isReady()) { "El modelo local no esta instalado" }
        idleJob?.cancel()
        val started = System.nanoTime()
        try {
            val raw = withTimeout(timeoutMillis) { withContext(dispatcher) { generate(request) } }
            val interpretation = ModelOutputParser.parse(raw)
            InterpretationResult(interpretation, request.pass, descriptor.id, (System.nanoTime() - started) / 1_000_000)
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            AppLog.w(TAG, "la inferencia supero ${timeoutMillis}ms, se recicla el motor")
            closeEngine()
            throw IllegalStateException("Timeout de inferencia", timeout)
        } finally {
            scheduleIdleClose()
        }
    }

    private fun generate(request: InterpretationRequest): String {
        val first = engine ?: createEngine().also { engine = it }
        return try {
            run(first, request)
        } catch (error: Exception) {
            if (!usingGpu) throw error
            // GPU inicializo pero fallo al inferir (ej. OpenCL ausente): una vez en CPU.
            AppLog.w(TAG, "fallo la inferencia en GPU, se reintenta en CPU: ${error.javaClass.simpleName}")
            first.close()
            engine = null
            usingGpu = false
            val cpu = createEngine(cpuOnly = true).also { engine = it }
            try {
                run(cpu, request)
            } catch (cpuError: Exception) {
                cpuError.addSuppressed(error)
                throw cpuError
            }
        }
    }

    private fun run(engine: Engine, request: InterpretationRequest): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(InterpretationPrompts.system(request.pass)),
            samplerConfig = SamplerConfig(topK = 1, topP = 0.1, temperature = 0.0),
        )
        return engine.createConversation(config).use { it.sendMessage(InterpretationPrompts.user(request)).toString() }
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
        withContext(dispatcher) {
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
