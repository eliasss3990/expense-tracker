package com.eliasgonzalez.expensetracker.data.analysis

/**
 * Todo lo que identifica a un modelo concreto, en un solo lugar. Cambiar de modelo es definir otro
 * descriptor (y, si usa otro runtime, otro TransactionInterpreter); el instalador, la cola, la
 * auditoria y la UI no cambian.
 */
data class ModelDescriptor(
    /** Estable: se guarda en la auditoria para comparar modelos entre si. */
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val approximateSize: String,
)

/**
 * Gemma 4 E2B Instruct en formato LiteRT-LM (repo oficial litert-community). La URL esta fijada a un
 * commit inmutable; tamano y SHA-256 se contrastaron con los metadatos LFS de Hugging Face de esa
 * revision. Licencia reportada: Apache 2.0.
 */
val Gemma4E2B = ModelDescriptor(
    id = "gemma-4-e2b-it",
    displayName = "Gemma 4 E2B",
    fileName = "gemma-4-E2B-it.litertlm",
    url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
        "9262660a1676eed6d0c477ab1a86344430854664/gemma-4-E2B-it.litertlm",
    sizeBytes = 2_588_147_712L,
    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    approximateSize = "2,6 GB",
)
