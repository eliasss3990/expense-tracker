package com.eliasgonzalez.expensetracker.quicksettings

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Ventanita flotante y movible para el alta rápida desde el Quick
 * Settings Tile, mismo espíritu visual que la Now Bar de Samsung (tarjeta
 * chica de bordes redondeados) pero implementada como overlay propio
 * (SYSTEM_ALERT_WINDOW) en vez de Android Bubbles: Bubbles exige simular
 * una "conversación" (Person + shortcut de Direct Share) y aun así este
 * Samsung nunca la promovía a burbuja real.
 *
 * Vistas nativas de Android en vez de Compose a propósito: alojar un
 * ComposeView dentro de un Service (sin Activity) exige fabricar a mano
 * un LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner propio -
 * mucho más código que este formulario chico no justifica.
 *
 * La ventana es FLAG_NOT_TOUCH_MODAL: los toques fuera de la tarjeta
 * pasan de largo a lo que sea que esté abajo (home, otra app), así se
 * puede seguir navegando con la ventanita flotando encima. Es focusable
 * desde que se crea (no NOT_FOCUSABLE) para que el teclado responda al
 * primer toque - alternarla dinámicamente sonaba mejor en teoría, pero
 * `updateViewLayout` tarda un viaje de ida y vuelta al sistema en
 * aplicarse, y pedir el foco en el mismo instante siempre perdía esa
 * carrera la primera vez. Focusable no bloquea navegar otras apps
 * (eso lo controla FLAG_NOT_TOUCH_MODAL, es independiente) - como mucho,
 * si estabas escribiendo en otra app en el momento exacto en que se abre
 * esta ventanita, esa app pierde el foco de teclado, un caso de borde
 * aceptable frente al bug real del doble toque.
 */
/** Paleta de la ventanita para un tema (claro u oscuro) - vistas nativas
 * no pueden leer el ExpenseTrackerTheme de Compose, así que se repiten
 * acá los mismos hex que ui/theme/Color.kt para los dos modos. */
private class OverlayPalette(
    val surface: Int,
    val onSurface: Int,
    val fieldBg: Int,
    val fieldStroke: Int,
    val hint: Int,
    val chipUnselectedBg: Int,
    val chipUnselectedText: Int,
    val closeBg: Int,
    val closeIcon: Int,
    val primary: Int,
    val onPrimary: Int,
    val dragHandle: Int,
)

private val LIGHT_PALETTE = OverlayPalette(
    surface = Color.WHITE,
    onSurface = Color.parseColor("#1E1B4B"),
    fieldBg = Color.parseColor("#F7F7FB"),
    fieldStroke = Color.parseColor("#E2E1EC"),
    hint = Color.parseColor("#9691A8"),
    chipUnselectedBg = Color.parseColor("#F1F0F7"),
    chipUnselectedText = Color.parseColor("#6B7280"),
    closeBg = Color.parseColor("#E0E7FF"),
    closeIcon = Color.parseColor("#4338CA"),
    primary = Color.parseColor("#4338CA"),
    onPrimary = Color.WHITE,
    dragHandle = Color.parseColor("#D1D5DB"),
)

private val DARK_PALETTE = OverlayPalette(
    surface = Color.parseColor("#1D1B24"),
    onSurface = Color.parseColor("#F1F0F7"),
    fieldBg = Color.parseColor("#26232F"),
    fieldStroke = Color.parseColor("#37333F"),
    hint = Color.parseColor("#8B879B"),
    chipUnselectedBg = Color.parseColor("#26232F"),
    chipUnselectedText = Color.parseColor("#9CA3AF"),
    closeBg = Color.parseColor("#332F55"),
    closeIcon = Color.parseColor("#A5B4FC"),
    primary = Color.parseColor("#A5B4FC"),
    onPrimary = Color.parseColor("#1E1B4B"),
    dragHandle = Color.parseColor("#4B4758"),
)

/** Mismos hex que CategoryPalette (ui/theme/Color.kt) - Vistas nativas no
 * pueden usar androidx.compose.ui.graphics.Color, así que se repiten acá. */
private fun categoryColor(category: Category): Int = when (category) {
    Category.FOOD -> Color.parseColor("#F59E0B")
    Category.FUEL -> Color.parseColor("#EF4444")
    Category.GROCERIES -> Color.parseColor("#10B981")
    Category.ENTERTAINMENT -> Color.parseColor("#8B5CF6")
    Category.SUBSCRIPTIONS -> Color.parseColor("#EC4899")
    Category.TRANSPORT -> Color.parseColor("#3B82F6")
    Category.SHOPPING -> Color.parseColor("#F97316")
    Category.HEALTH -> Color.parseColor("#14B8A6")
    Category.EDUCATION -> Color.parseColor("#0EA5E9")
    Category.MECHANIC -> Color.parseColor("#78716C")
    Category.OTHER -> Color.parseColor("#6B7280")
}

class QuickAddOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var rootView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var amountInput: EditText
    private lateinit var merchantInput: EditText
    private lateinit var descriptionInput: EditText
    private var selectedCategory: Category = Category.OTHER
    private val palette: OverlayPalette
        get() {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (nightMode == Configuration.UI_MODE_NIGHT_YES) DARK_PALETTE else LIGHT_PALETTE
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Ancho y posición dinámicos según la pantalla real del celular, no
        // valores en dp fijos - se ven bien en cualquier tamaño.
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val cardWidthPx = (screenWidth * 0.82f).toInt()
        val topOffsetPx = (screenHeight * 0.08f).toInt()

        rootView = buildCardView()
        layoutParams = WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topOffsetPx
        }
        windowManager.addView(rootView, layoutParams)
    }

    // El Service no se recrea con un cambio de tema (a diferencia de una
    // Activity), así que sin esto la tarjeta se queda pintada con la
    // paleta de cuando se abrió si el sistema cambia de claro a oscuro
    // (o viceversa) mientras sigue flotando en pantalla.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::rootView.isInitialized) return

        val previousAmount = amountInput.text.toString()
        val previousMerchant = merchantInput.text.toString()
        val previousDescription = descriptionInput.text.toString()
        // Sin esto, si el usuario estaba escribiendo justo cuando el
        // sistema cambia de tema (ej. modo oscuro automatico por horario
        // a mitad de tipeo), el teclado se cerraba solo y el foco se
        // perdia - las instancias nuevas de EditText no heredan el foco
        // de las viejas, hay que restaurarlo a mano.
        val wasEditingAmount = amountInput.isFocused
        val wasEditingMerchant = merchantInput.isFocused
        val wasEditingDescription = descriptionInput.isFocused

        runCatching { windowManager.removeView(rootView) }
        rootView = buildCardView()
        amountInput.setText(previousAmount)
        merchantInput.setText(previousMerchant)
        descriptionInput.setText(previousDescription)
        windowManager.addView(rootView, layoutParams)

        val fieldToRefocus = when {
            wasEditingAmount -> amountInput
            wasEditingMerchant -> merchantInput
            wasEditingDescription -> descriptionInput
            else -> null
        }
        fieldToRefocus?.let { field ->
            field.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { windowManager.removeView(rootView) }
        super.onDestroy()
    }

    private fun allowKeyboardFocus(editText: EditText) {
        editText.setOnClickListener {
            editText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeOverlay() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(rootView.windowToken, 0)
        stopSelf()
    }

    private fun buildCardView(): View {
        val density = resources.displayMetrics.density
        val colors = palette
        fun dp(value: Int) = (value * density).toInt()
        fun matchWidth() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(colors.surface)
            }
            elevation = dp(8).toFloat()
        }

        // Manija de arrastre - la barrita visible es angosta (mismo lenguaje
        // visual que el drag handle de los ModalBottomSheet de Material3),
        // pero el área táctil real es un contenedor más alto y ancho que la
        // envuelve: tocar/arrastrar justo esos 4dp de línea era muy difícil
        // al primer intento.
        val dragHandleBar = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(colors.dragHandle)
            }
        }
        val dragHandleTouchArea = FrameLayout(this).apply {
            addView(
                dragHandleBar,
                FrameLayout.LayoutParams(dp(36), dp(4)).apply {
                    gravity = Gravity.CENTER
                },
            )
        }
        attachDragHandling(dragHandleTouchArea)
        card.addView(
            dragHandleTouchArea,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)).apply {
                bottomMargin = dp(6)
            },
        )

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val headerTitle = TextView(this).apply {
            text = "Nuevo gasto"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors.onSurface)
        }
        val closeButtonCircle = TextView(this).apply {
            text = "✕"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(colors.closeIcon)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colors.closeBg)
            }
        }
        // El circulo visual queda en 32dp (no cambia el diseño), pero el
        // area tactil real es el FrameLayout que lo envuelve, de 48dp -
        // el minimo recomendado de Android para no quedar por debajo del
        // target de accesibilidad. Mismo patron ya usado para la manija
        // de arrastre mas abajo en este archivo.
        val closeButton = FrameLayout(this).apply {
            addView(closeButtonCircle, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            isClickable = true
            isFocusable = true
            contentDescription = "Cerrar"
            setOnClickListener { closeOverlay() }
        }
        headerRow.addView(
            headerTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        headerRow.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(headerRow, matchWidth())

        fun styleField(editText: EditText) {
            editText.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(colors.fieldBg)
                setStroke(dp(1), colors.fieldStroke)
            }
            editText.setPadding(dp(14), dp(12), dp(14), dp(12))
            editText.setTextColor(colors.onSurface)
            editText.setHintTextColor(colors.hint)
            editText.textSize = 16f
        }

        amountInput = EditText(this).apply {
            hint = "Monto (₲)"
            // El hint solo lo lee TalkBack hasta que hay texto cargado -
            // contentDescription queda como label persistente despues de
            // eso, algo que ninguna vista nativa de este overlay tenia.
            contentDescription = "Monto en guaraníes"
            inputType = InputType.TYPE_CLASS_NUMBER
            styleField(this)
        }
        allowKeyboardFocus(amountInput)
        card.addView(amountInput, matchWidth().apply { bottomMargin = dp(10) })

        merchantInput = EditText(this).apply {
            hint = "Comercio"
            contentDescription = "Comercio"
            inputType = InputType.TYPE_CLASS_TEXT
            styleField(this)
        }
        allowKeyboardFocus(merchantInput)
        card.addView(merchantInput, matchWidth().apply { bottomMargin = dp(10) })

        descriptionInput = EditText(this).apply {
            hint = "Descripción"
            contentDescription = "Descripción"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            styleField(this)
        }
        allowKeyboardFocus(descriptionInput)
        card.addView(descriptionInput, matchWidth())

        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryChips = mutableMapOf<Category, TextView>()
        Category.entries.forEach { category ->
            val chip = TextView(this).apply {
                text = category.label
                textSize = 13f
                setPadding(dp(12), dp(7), dp(12), dp(7))
                applyChipStyle(this, category, selected = category == selectedCategory)
                // Sin esto TalkBack anuncia el chip como texto plano, no
                // como algo tocable/seleccionable con estado propio.
                isClickable = true
                isFocusable = true
                contentDescription = "Categoría ${category.label}"
                setOnClickListener {
                    val previous = selectedCategory
                    selectedCategory = category
                    categoryChips[previous]?.let { applyChipStyle(it, previous, selected = false) }
                    applyChipStyle(this, category, selected = true)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
            categoryRow.addView(chip, params)
            categoryChips[category] = chip
        }
        val categoryScroll = HorizontalScrollView(this).apply {
            setPadding(0, dp(12), 0, dp(14))
            isHorizontalScrollBarEnabled = false
            addView(categoryRow)
        }
        card.addView(categoryScroll, matchWidth())

        val buttonsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelButton = Button(this).apply {
            text = "Cancelar"
            isAllCaps = false
            textSize = 15f
            setTextColor(colors.primary)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), colors.primary)
            }
            stateListAnimator = null
            setOnClickListener { closeOverlay() }
        }
        val saveButton = Button(this).apply {
            text = "Guardar"
            isAllCaps = false
            textSize = 15f
            setTextColor(colors.onPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(colors.primary)
            }
            stateListAnimator = null
            setOnClickListener {
                val amount = amountInput.text.toString().toLongOrNull() ?: 0
                val merchant = merchantInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                if (amount <= 0 || merchant.isBlank()) {
                    Toast.makeText(this@QuickAddOverlayService, "Completá monto y comercio", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                scope.launch {
                    val now = System.currentTimeMillis()
                    ServiceLocator.get().registerExpense(
                        Expense(
                            amount = amount,
                            merchant = merchant,
                            categoryId = selectedCategory.id,
                            description = description,
                            occurredAt = now,
                            createdAt = now,
                            source = ExpenseSource.QUICK_TILE,
                        )
                    )
                    closeOverlay()
                }
            }
        }
        buttonsRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            },
        )
        buttonsRow.addView(saveButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(buttonsRow)

        return card
    }

    private fun applyChipStyle(chip: TextView, category: Category, selected: Boolean) {
        val color = categoryColor(category)
        val colors = palette
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        chip.setTextColor(if (selected) color else colors.chipUnselectedText)
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (selected) Color.argb(46, Color.red(color), Color.green(color), Color.blue(color)) else colors.chipUnselectedBg)
        }
    }

    private fun attachDragHandling(header: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }
}
