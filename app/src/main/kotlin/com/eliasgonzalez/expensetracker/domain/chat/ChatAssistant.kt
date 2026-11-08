package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.analysis.NumericTokens
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import com.eliasgonzalez.expensetracker.domain.usecase.CatalogOutcome
import com.eliasgonzalez.expensetracker.domain.usecase.CatalogRejection
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteExpense
import com.eliasgonzalez.expensetracker.domain.usecase.EditExpense
import com.eliasgonzalez.expensetracker.domain.usecase.RegisterExpense
import com.eliasgonzalez.expensetracker.domain.usecase.isValidCatalogLabel
import com.eliasgonzalez.expensetracker.domain.usecase.normalizedCatalogLabel
import java.time.LocalDate
import java.time.ZoneId

/**
 * El asistente del chat. El modelo solo INTERPRETA ([ChatInterpreter]); todo lo demas lo hace la app:
 * - Lecturas (consultar, describir): se calculan aca con los datos reales; el modelo no ve la base.
 * - Escrituras (cargar, editar, borrar): NUNCA se ejecutan al interpretar. [ask] devuelve una propuesta
 *   completa y solo [confirm] (accion explicita del usuario) la ejecuta.
 * - Defensas: un solo gasto por operacion (no hay "borrar todos"), importes y fechas acotados, referencias
 *   ambiguas se preguntan, y [confirm] revalida que el gasto no haya cambiado desde la propuesta.
 */
class ChatAssistant(
    private val interpreter: ChatInterpreter,
    private val expenses: ExpenseRepository,
    private val register: RegisterExpense,
    private val edit: EditExpense,
    private val delete: DeleteExpense,
    private val categories: CategoryGateway,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun ask(request: ChatRequest): ChatReply {
        if (request.phrase.isBlank()) return ChatReply.Clarify(ClarifyReason.EMPTY_PHRASE)
        return when (val intent = interpreter.interpret(request)) {
            is ChatIntent.Query -> query(intent, request.today)
            is ChatIntent.Describe -> resolve(intent.ref).fold({ ChatReply.Detail(it) }, { it })
            is ChatIntent.Create -> create(intent, request)
            is ChatIntent.Edit -> edit(intent, request)
            is ChatIntent.Delete -> resolve(intent.ref).fold({ ChatReply.Confirm(ChatProposal.Delete(it)) }, { it })
            is ChatIntent.SmallTalk -> ChatReply.Talk(intent.reply.take(MAX_REPLY))
            is ChatIntent.Revise -> revise(intent, request)
            ChatIntent.Help -> ChatReply.Help
            is ChatIntent.CategoryCreate -> categoryCreate(intent)
            is ChatIntent.CategoryRename -> categoryRename(intent)
            is ChatIntent.CategoryDelete -> categoryDelete(intent)
            is ChatIntent.ReviseName -> reviseName(intent, request)
            ChatIntent.Cancel -> if (request.pending != null) ChatReply.Cancelled else ChatReply.Clarify(ClarifyReason.NOTHING_TO_REVISE)
            is ChatIntent.Unreadable -> ChatReply.Clarify(ClarifyReason.UNREADABLE)
        }
    }

    /** Ejecuta una propuesta que el usuario YA vio y acepto. Un gasto que cambio desde entonces no se toca. */
    suspend fun confirm(proposal: ChatProposal): ConfirmResult = try {
        when (proposal) {
            is ChatProposal.Create -> { register(proposal.expense); ConfirmResult.DONE }
            is ChatProposal.Edit -> if (expenses.findById(proposal.before.id) != proposal.before) {
                ConfirmResult.STALE
            } else {
                edit(proposal.after.id, proposal.after.amount, proposal.after.merchant, proposal.after.categoryId, proposal.after.description)
                ConfirmResult.DONE
            }
            is ChatProposal.Delete -> if (expenses.findById(proposal.expense.id) != proposal.expense) {
                ConfirmResult.STALE
            } else {
                delete(proposal.expense.id); ConfirmResult.DONE
            }
            is ChatProposal.CategoryCreate -> outcome(categories.create(proposal.label, proposal.color))
            is ChatProposal.CategoryRename ->
                if (categories.find(proposal.before.id) != proposal.before) ConfirmResult.STALE
                else outcome(categories.rename(proposal.before.id, proposal.newLabel))
            is ChatProposal.CategoryDelete ->
                if (categories.find(proposal.entry.id) != proposal.entry) ConfirmResult.STALE else outcome(categories.delete(proposal.entry.id))
            is ChatProposal.CategoryDeactivate ->
                if (categories.find(proposal.entry.id) != proposal.entry) ConfirmResult.STALE else outcome(categories.setActive(proposal.entry.id, false))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ConfirmResult.FAILED
    }

    /** Lo que cambio entre la propuesta y la confirmacion (otro dato, otro nombre repetido, ya en uso) es STALE, no un error. */
    private fun outcome(result: CatalogOutcome): ConfirmResult = when (result) {
        is CatalogOutcome.Success -> ConfirmResult.DONE
        is CatalogOutcome.Rejected -> when (result.reason) {
            CatalogRejection.NOT_FOUND, CatalogRejection.DUPLICATE_LABEL, CatalogRejection.IN_USE -> ConfirmResult.STALE
            else -> ConfirmResult.FAILED
        }
    }

    private fun categoryCreate(c: ChatIntent.CategoryCreate): ChatReply {
        val label = c.name.normalizedCatalogLabel()
        if (!label.isValidCatalogLabel()) return ChatReply.Clarify(ClarifyReason.INVALID_CATEGORY_NAME)
        if (labelTaken(label, exceptId = null)) return ChatReply.Clarify(ClarifyReason.DUPLICATE_CATEGORY, label)
        return ChatReply.Confirm(ChatProposal.CategoryCreate(label, categories.nextColor()))
    }

    private fun categoryRename(r: ChatIntent.CategoryRename): ChatReply {
        val entry = resolveCategory(r.name).fold({ it }, { return it })
        val label = r.newName.normalizedCatalogLabel()
        if (!label.isValidCatalogLabel()) return ChatReply.Clarify(ClarifyReason.INVALID_CATEGORY_NAME)
        if (label == entry.label || labelTaken(label, exceptId = entry.id)) return ChatReply.Clarify(ClarifyReason.DUPLICATE_CATEGORY, label)
        return ChatReply.Confirm(ChatProposal.CategoryRename(entry, label))
    }

    private fun categoryDelete(d: ChatIntent.CategoryDelete): ChatReply {
        val entry = resolveCategory(d.name).fold({ it }, { return it })
        if (entry.protected) return ChatReply.Clarify(ClarifyReason.PROTECTED_CATEGORY, entry.label)
        if (!categories.isInUse(entry.id)) return ChatReply.Confirm(ChatProposal.CategoryDelete(entry))
        if (!entry.active) return ChatReply.Clarify(ClarifyReason.ALREADY_INACTIVE, entry.label)
        return ChatReply.Confirm(ChatProposal.CategoryDeactivate(entry))
    }

    /** Corrige el nombre de una propuesta de categoria pendiente. */
    private fun reviseName(r: ChatIntent.ReviseName, request: ChatRequest): ChatReply {
        val label = r.newName.normalizedCatalogLabel()
        return when (val pending = request.pending) {
            is ChatProposal.CategoryCreate -> when {
                !label.isValidCatalogLabel() -> ChatReply.Clarify(ClarifyReason.INVALID_CATEGORY_NAME)
                labelTaken(label, exceptId = null) -> ChatReply.Clarify(ClarifyReason.DUPLICATE_CATEGORY, label)
                else -> ChatReply.Confirm(pending.copy(label = label))
            }
            is ChatProposal.CategoryRename -> when {
                !label.isValidCatalogLabel() -> ChatReply.Clarify(ClarifyReason.INVALID_CATEGORY_NAME)
                label == pending.before.label || labelTaken(label, exceptId = pending.before.id) ->
                    ChatReply.Clarify(ClarifyReason.DUPLICATE_CATEGORY, label)
                else -> ChatReply.Confirm(pending.copy(newLabel = label))
            }
            null -> ChatReply.Clarify(ClarifyReason.NOTHING_TO_REVISE)
            else -> ChatReply.Clarify(ClarifyReason.WRONG_PENDING_KIND)
        }
    }

    private fun labelTaken(label: String, exceptId: String?): Boolean {
        val needle = NumericTokens.normalizeForMatch(label)
        return categories.categories().any { it.id != exceptId && NumericTokens.normalizeForMatch(it.label) == needle }
    }

    /** Resuelve una categoria POR NOMBRE contra el catalogo real (el modelo no ve ni elige ids): exacto primero, luego "contiene". */
    private fun resolveCategory(name: String): CategoryResolution {
        val needle = NumericTokens.normalizeForMatch(name)
        if (needle.isBlank()) return CategoryResolution.Missing(ChatReply.Clarify(ClarifyReason.CATEGORY_NOT_FOUND))
        val all = categories.categories()
        // El prompt le muestra al modelo "ID (Nombre)": si devuelve el id en vez del nombre, es la misma categoria.
        val exact = all.filter { it.id.equals(name.trim(), ignoreCase = true) || NumericTokens.normalizeForMatch(it.label) == needle }
        val hits = exact.ifEmpty { all.filter { NumericTokens.normalizeForMatch(it.label).contains(needle) } }
        return when {
            hits.isEmpty() -> CategoryResolution.Missing(ChatReply.Clarify(ClarifyReason.CATEGORY_NOT_FOUND, name.take(MAX_MERCHANT)))
            hits.size == 1 -> CategoryResolution.Found(hits.single())
            else -> CategoryResolution.Missing(ChatReply.Clarify(ClarifyReason.AMBIGUOUS_CATEGORY, name.take(MAX_MERCHANT)))
        }
    }

    private sealed interface CategoryResolution {
        data class Found(val entry: CatalogEntry) : CategoryResolution
        data class Missing(val reply: ChatReply) : CategoryResolution
    }

    private inline fun <T> CategoryResolution.fold(found: (CatalogEntry) -> T, missing: (ChatReply) -> T): T = when (this) {
        is CategoryResolution.Found -> found(entry)
        is CategoryResolution.Missing -> missing(reply)
    }

    private fun query(q: ChatIntent.Query, today: LocalDate): ChatReply {
        val (from, to) = q.period.resolve(today, q.from, q.to)
        // Un periodo propio sin ninguna fecha no es "todo el historial": es una consulta que no se entendio.
        if (q.period == ChatPeriod.CUSTOM && from == null && to == null) return ChatReply.Clarify(ClarifyReason.INVALID_RANGE)
        if (from != null && to != null && from.isAfter(to)) return ChatReply.Clarify(ClarifyReason.INVALID_RANGE)
        val fromMs = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val toMs = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        val matching = expenses.expenses.value
            .filter { it.occurredAt in fromMs until toMs && (q.categoryId == null || it.categoryId == q.categoryId) }
            .sortedWith(compareByDescending<Expense> { it.occurredAt }.thenByDescending { it.id })
        val total = matching.sumOf { it.amount }
        return when (q.metric) {
            ChatMetric.LIST -> ChatReply.Listing(matching.take(MAX_LIST), matching.size, total)
            ChatMetric.COUNT -> ChatReply.Summary(q.metric, total, matching.size, from, to, q.categoryId)
            ChatMetric.SUM -> ChatReply.Summary(q.metric, total, matching.size, from, to, q.categoryId)
            ChatMetric.AVERAGE ->
                ChatReply.Summary(q.metric, if (matching.isEmpty()) 0 else total / matching.size, matching.size, from, to, q.categoryId)
        }
    }

    private fun create(c: ChatIntent.Create, request: ChatRequest): ChatReply {
        if (c.amount !in 1..MAX_AMOUNT) return ChatReply.Clarify(ClarifyReason.INVALID_AMOUNT)
        val date = c.date ?: request.today
        if (date.isAfter(request.today) || date.isBefore(MIN_DATE)) return ChatReply.Clarify(ClarifyReason.INVALID_DATE)
        val category = c.categoryId?.takeIf { id -> request.categories.any { it.id == id } } ?: CategoryIds.OTHER
        val occurredAt = instantOf(date, request.today)
        val draft = Expense(
            amount = c.amount,
            merchant = c.merchant.trim().take(MAX_MERCHANT).replaceFirstChar { it.uppercase() }.ifBlank { DEFAULT_MERCHANT },
            description = c.description.trim().take(MAX_DESCRIPTION).replaceFirstChar { it.uppercase() },
            categoryId = category,
            occurredAt = occurredAt,
            createdAt = now(),
            source = ExpenseSource.MANUAL,
        )
        return ChatReply.Confirm(ChatProposal.Create(draft))
    }

    private fun edit(e: ChatIntent.Edit, request: ChatRequest): ChatReply {
        if (e.field == EditableField.DATE) return ChatReply.Clarify(ClarifyReason.DATE_NOT_EDITABLE)
        val target = resolve(e.ref).fold({ it }, { return it })
        val after = applyField(target, e.field, e.newValue, request) ?: return invalidField(e.field, e.newValue)
        return ChatReply.Confirm(ChatProposal.Edit(target, after))
    }

    /** Corrige la propuesta pendiente: el borrador de un gasto nuevo o el "despues" de una edicion. */
    private fun revise(r: ChatIntent.Revise, request: ChatRequest): ChatReply = when (val pending = request.pending) {
        is ChatProposal.Create -> {
            val fixed = if (r.field == EditableField.DATE) reviseDate(pending.expense, r.newValue, request) else applyField(pending.expense, r.field, r.newValue, request)
            if (fixed == null) invalidField(r.field, r.newValue) else ChatReply.Confirm(ChatProposal.Create(fixed))
        }
        is ChatProposal.Edit -> if (r.field == EditableField.DATE) {
            ChatReply.Clarify(ClarifyReason.DATE_NOT_EDITABLE)
        } else {
            val fixed = applyField(pending.after, r.field, r.newValue, request)
            if (fixed == null) invalidField(r.field, r.newValue) else ChatReply.Confirm(ChatProposal.Edit(pending.before, fixed))
        }
        is ChatProposal.Delete -> ChatReply.Clarify(ClarifyReason.PENDING_IS_DELETE)
        is ChatProposal.CategoryCreate, is ChatProposal.CategoryRename, is ChatProposal.CategoryDelete,
        is ChatProposal.CategoryDeactivate -> ChatReply.Clarify(ClarifyReason.WRONG_PENDING_KIND)
        null -> ChatReply.Clarify(ClarifyReason.NOTHING_TO_REVISE)
    }

    private fun reviseDate(expense: Expense, value: String, request: ChatRequest): Expense? {
        val date = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: return null
        if (date.isAfter(request.today) || date.isBefore(MIN_DATE)) return null
        return expense.copy(occurredAt = instantOf(date, request.today))
    }

    /** Un unico criterio para "fecha -> instante": hoy es AHORA (nunca el futuro); otro dia, a mediodia. */
    private fun instantOf(date: LocalDate, today: LocalDate): Long =
        if (date == today) now() else date.atTime(NOON).atZone(zone).toInstant().toEpochMilli()

    private fun invalidField(field: EditableField, value: String): ChatReply = when (field) {
        EditableField.AMOUNT -> ChatReply.Clarify(ClarifyReason.INVALID_AMOUNT)
        EditableField.CATEGORY -> ChatReply.Clarify(ClarifyReason.UNKNOWN_CATEGORY, value.take(MAX_MERCHANT))
        EditableField.DATE -> ChatReply.Clarify(ClarifyReason.INVALID_DATE)
        EditableField.MERCHANT, EditableField.DESCRIPTION -> ChatReply.Clarify(ClarifyReason.UNREADABLE)
    }

    /** null = el valor no es valido para ese campo. */
    private fun applyField(base: Expense, field: EditableField, value: String, request: ChatRequest): Expense? = when (field) {
        EditableField.AMOUNT -> ChatAmount.parse(value)?.takeIf { it in 1..MAX_AMOUNT }?.let { base.copy(amount = it) }
        EditableField.MERCHANT -> value.trim().take(MAX_MERCHANT).takeIf(String::isNotBlank)?.let { base.copy(merchant = it) }
        EditableField.DESCRIPTION -> value.trim().take(MAX_DESCRIPTION).takeIf(String::isNotBlank)?.let { base.copy(description = it.replaceFirstChar { c -> c.uppercase() }) }
        EditableField.CATEGORY -> request.categories.firstOrNull { it.id == value || NumericTokens.normalizeForMatch(it.label) == NumericTokens.normalizeForMatch(value) }?.let { base.copy(categoryId = it.id) }
        EditableField.DATE -> null
    }

    /** Right = no se pudo resolver (respuesta para el usuario). Left = el gasto. */
    private fun resolve(ref: ExpenseRef): Resolution {
        val all = expenses.expenses.value
        if (all.isEmpty()) return Resolution.Missing(ChatReply.Clarify(ClarifyReason.NOT_FOUND))
        return when (ref) {
            ExpenseRef.Last -> Resolution.Found(all.maxWith(compareBy<Expense> { it.occurredAt }.thenBy { it.id }))
            ExpenseRef.MostExpensive -> Resolution.Found(all.maxWith(compareBy<Expense> { it.amount }.thenBy { it.occurredAt }))
            is ExpenseRef.ByText -> {
                val needle = NumericTokens.normalizeForMatch(ref.text)
                if (needle.isBlank()) return Resolution.Missing(ChatReply.Clarify(ClarifyReason.NOT_FOUND))
                val hits = all.filter { NumericTokens.normalizeForMatch("${it.merchant} ${it.description}").contains(needle) }
                    .sortedWith(compareByDescending<Expense> { it.occurredAt }.thenByDescending { it.id })
                when {
                    hits.isEmpty() -> Resolution.Missing(ChatReply.Clarify(ClarifyReason.NOT_FOUND, ref.text.take(MAX_MERCHANT)))
                    hits.size == 1 -> Resolution.Found(hits.single())
                    else -> Resolution.Missing(ChatReply.Clarify(ClarifyReason.AMBIGUOUS_REFERENCE, ref.text.take(MAX_MERCHANT)))
                }
            }
        }
    }

    private sealed interface Resolution {
        data class Found(val expense: Expense) : Resolution
        data class Missing(val reply: ChatReply) : Resolution
    }

    private inline fun <T> Resolution.fold(found: (Expense) -> T, missing: (ChatReply) -> T): T = when (this) {
        is Resolution.Found -> found(expense)
        is Resolution.Missing -> missing(reply)
    }

    private companion object {
        const val MAX_AMOUNT = 999_999_999L
        const val MAX_LIST = 20
        const val MAX_MERCHANT = 80
        const val MAX_REPLY = 300
        const val DEFAULT_MERCHANT = "Sin comercio"
        const val MAX_DESCRIPTION = 120
        val NOON: java.time.LocalTime = java.time.LocalTime.NOON
        val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
