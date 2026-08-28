package com.eliasgonzalez.expensetracker.quicksettings

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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
 */
class QuickAddOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var rootView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var selectedCategory: Category = Category.OTHER

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        rootView = buildCardView()
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 400
        }
        windowManager.addView(rootView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { windowManager.removeView(rootView) }
        super.onDestroy()
    }

    private fun buildCardView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }

        val header = TextView(this).apply {
            text = "☰  Nuevo gasto"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        }
        attachDragHandling(header)
        card.addView(header)

        val amountInput = EditText(this).apply {
            hint = "Monto (₲)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        card.addView(amountInput)

        val merchantInput = EditText(this).apply {
            hint = "Comercio"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        card.addView(merchantInput)

        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryChips = mutableMapOf<Category, TextView>()
        Category.entries.forEach { category ->
            val chip = TextView(this).apply {
                text = category.label
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setTextColor(Color.DKGRAY)
                background = chipBackground(dp(16), selected = category == selectedCategory)
                setOnClickListener {
                    val previous = selectedCategory
                    selectedCategory = category
                    categoryChips[previous]?.background = chipBackground(dp(16), selected = false)
                    background = chipBackground(dp(16), selected = true)
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
            setPadding(0, dp(12), 0, dp(12))
            addView(categoryRow)
        }
        card.addView(categoryScroll)

        val buttonsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelButton = Button(this).apply {
            text = "Cancelar"
            setOnClickListener { stopSelf() }
        }
        val saveButton = Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                val amount = amountInput.text.toString().toLongOrNull() ?: 0
                val merchant = merchantInput.text.toString().trim()
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
                            occurredAt = now,
                            createdAt = now,
                            source = ExpenseSource.QUICK_TILE,
                        )
                    )
                    stopSelf()
                }
            }
        }
        buttonsRow.addView(cancelButton)
        buttonsRow.addView(saveButton)
        card.addView(buttonsRow)

        return card
    }

    private fun chipBackground(radius: Int, selected: Boolean) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(if (selected) Color.parseColor("#D8CCFF") else Color.parseColor("#EEEEEE"))
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
