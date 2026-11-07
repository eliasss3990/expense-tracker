package com.eliasgonzalez.expensetracker.domain.analysis

data class AnalyzedTransaction(
    val modelResult: TransactionAnalysisResult,
    val reconciled: ReconciledTransaction,
)

/** Orquesta inferencia y reconciliacion sin acoplar el dominio al runtime Android. */
class AnalyzeTransaction(
    private val analyzer: TransactionAnalyzer,
    private val reconcile: ReconcileTransaction,
) {
    suspend operator fun invoke(input: TransactionAnalysisInput): AnalyzedTransaction {
        val result = analyzer.analyze(input)
        return AnalyzedTransaction(
            modelResult = result,
            reconciled = reconcile(input, result),
        )
    }
}
