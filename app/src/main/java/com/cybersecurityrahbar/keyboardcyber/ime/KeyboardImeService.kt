package com.cybersecurityrahbar.keyboardcyber.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.cybersecurityrahbar.keyboardcyber.MainActivity
import com.cybersecurityrahbar.keyboardcyber.data.ClipStore
import com.cybersecurityrahbar.keyboardcyber.data.KeyboardPreferences
import java.util.concurrent.Executors

class KeyboardImeService : InputMethodService() {
    private lateinit var clipboardManager: ClipboardManager
    private val io = Executors.newSingleThreadExecutor()
    private var privateInput = false
    private var language = "ar"
    private var symbolsMode = false
    private var emojiMode = false
    private var shiftEnabled = false
    private var suggestionsRow: LinearLayout? = null
    private lateinit var keyboardContainer: LinearLayout

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        privateInput = isPasswordField(attribute?.inputType ?: InputType.TYPE_NULL)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        privateInput = isPasswordField(info?.inputType ?: InputType.TYPE_NULL)
        refreshSuggestions()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(7, 15, 27), Color.rgb(31, 67, 82), 18)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "CYBER"
            setTextColor(Color.rgb(53, 224, 190))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(38), 1f))
        toolbar.addView(actionButton("▤", "الأرشيف") { openArchive() })
        toolbar.addView(actionButton("⇄", "ترجمة") {
            Toast.makeText(
                this,
                "سيتم ربط الترجمة السحابية الآمنة في المرحلة التالية",
                Toast.LENGTH_SHORT
            ).show()
        })
        toolbar.addView(actionButton(if (language == "ar") "EN" else "ع", "تغيير اللغة") {
            language = if (language == "ar") "en" else "ar"
            rebuildKeyboard()
        })
        root.addView(toolbar)

        suggestionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(5))
        }
        root.addView(
            suggestionsRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        )

        keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(keyboardContainer)

        rebuildSuggestions()
        rebuildKeyboard()
        return root
    }

    private fun rebuildSuggestions() {
        val row = suggestionsRow ?: return
        row.removeAllViews()
        if (privateInput || !KeyboardPreferences.archiveSuggestionsEnabled(this)) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        val recent = ClipStore.get(this).recent(3)
        if (recent.isEmpty()) {
            val empty = TextView(this).apply {
                text = "اقتراحاتك من الأرشيف ستظهر هنا"
                setTextColor(Color.rgb(112, 139, 161))
                textSize = 10f
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(empty)
            return
        }
        recent.forEach { record ->
            val preview = record.content.replace("\n", " ").take(18)
            val chip = TextView(this).apply {
                text = preview
                setTextColor(Color.rgb(206, 226, 239))
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                background = rounded(Color.rgb(19, 39, 56), Color.rgb(36, 80, 91), 10)
                setPadding(dp(8), dp(5), dp(8), dp(5))
                setOnClickListener { currentInputConnection?.commitText(record.content, 1) }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginEnd = dp(5)
            })
        }
    }

    private fun refreshSuggestions() {
        rebuildSuggestions()
    }

    private fun rebuildKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        keyboardContainer.removeAllViews()
        if (emojiMode) {
            addRow(listOf("😀", "😂", "🥹", "😍", "👍", "🔥", "❤️", "✅", "🚀", "🔒")) { commit(it) }
            addRow(listOf("😊", "😉", "🤔", "😭", "💡", "⚡", "🎯", "🙏")) { commit(it) }
            addBottomRow()
            return
        }
        if (symbolsMode) {
            addRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) { commit(it) }
            addRow(listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")) { commit(it) }
            addRow(listOf("*", "\"", "'", ":", ";", "!", "?", "_", "=", "٫")) { commit(it) }
            addBottomRow()
            return
        }

        if (language == "ar") {
            addRow("ض ص ث ق ف غ ع ه خ ح ج".split(" ")) { commit(it) }
            addRow("ش س ي ب ل ا ت ن م ك".split(" ")) { commit(it) }
            addSpecialRow(left = "⇧", letters = "ئ ء ؤ ر ى ة و ز ظ", right = "⌫")
        } else {
            addRow("q w e r t y u i o p".split(" ")) {
                commit(if (shiftEnabled) it.uppercase() else it)
            }
            addRow("a s d f g h j k l".split(" ")) {
                commit(if (shiftEnabled) it.uppercase() else it)
            }
            addSpecialRow(left = "⇧", letters = "z x c v b n m", right = "⌫")
        }
        addBottomRow()
    }

    private fun addBottomRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(keyButton("?123", 1.25f, true) {
            symbolsMode = !symbolsMode
            emojiMode = false
            rebuildKeyboard()
        })
        row.addView(keyButton("😊", 0.9f, true) {
            emojiMode = !emojiMode
            symbolsMode = false
            rebuildKeyboard()
        })
        row.addView(keyButton("،", 0.8f, false) { commit(if (language == "ar") "،" else ",") })
        row.addView(keyButton("ع/EN", 1.1f, true) {
            language = if (language == "ar") "en" else "ar"
            symbolsMode = false
            emojiMode = false
            rebuildKeyboard()
        })
        row.addView(keyButton("مسافة", 3.5f, false) { commit(" ") })
        row.addView(keyButton(".", 0.8f, false) { commit(".") })
        row.addView(keyButton("↵", 1.0f, true) { sendEnter() })
        keyboardContainer.addView(row)
    }

    private fun addSpecialRow(left: String, letters: String, right: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(keyButton(left, 1.1f, true) {
            shiftEnabled = !shiftEnabled
            rebuildKeyboard()
        })
        letters.split(" ").forEach { letter ->
            row.addView(keyButton(letter, 1f, false) {
                commit(if (shiftEnabled && language == "en") letter.uppercase() else letter)
                if (shiftEnabled) {
                    shiftEnabled = false
                    rebuildKeyboard()
                }
            })
        }
        row.addView(keyButton(right, 1.2f, true) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        keyboardContainer.addView(row)
    }

    private fun addRow(keys: List<String>, action: (String) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        keys.forEach { key ->
            row.addView(keyButton(key, 1f, false) { action(key) })
        }
        keyboardContainer.addView(row)
    }

    private fun keyButton(
        label: String,
        weight: Float,
        special: Boolean,
        onClick: () -> Unit
    ): TextView {
        val view = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(if (special) Color.rgb(91, 231, 202) else Color.rgb(229, 239, 248))
            textSize = when {
                label.length > 5 -> 9f
                label.length > 2 -> 11f
                else -> 18f
            }
            typeface = Typeface.DEFAULT
            background = rounded(
                if (special) Color.rgb(18, 57, 69) else Color.rgb(20, 34, 51),
                if (special) Color.rgb(48, 124, 131) else Color.rgb(36, 58, 77),
                9
            )
            setOnClickListener { onClick() }
            isFocusable = true
            contentDescription = label
        }
        view.layoutParams = rowLayout(weight)
        return view
    }

    private fun actionButton(symbol: String, description: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(Color.rgb(84, 224, 197))
            background = rounded(Color.rgb(14, 34, 49), Color.rgb(37, 83, 94), 10)
            layoutParams = LinearLayout.LayoutParams(dp(43), dp(34)).apply {
                marginStart = dp(5)
            }
            setOnClickListener { action() }
        }

    private fun rowLayout(weight: Float) = LinearLayout.LayoutParams(0, dp(45), weight).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun sendEnter() {
        val connection: InputConnection = currentInputConnection ?: return
        val eventDown = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER
        )
        val eventUp = android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_ENTER
        )
        connection.sendKeyEvent(eventDown)
        connection.sendKeyEvent(eventUp)
    }

    private fun openArchive() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isPasswordField(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (inputClass == InputType.TYPE_CLASS_TEXT &&
            variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun captureClipboard() {
        if (privateInput || !::clipboardManager.isInitialized) return
        val clip = clipboardManager.primaryClip ?: return
        val description = clip.description ?: return
        if (description.label?.toString() == INTERNAL_CLIP_LABEL) return

        val extras = description.extras
        val markedSensitive = if (android.os.Build.VERSION.SDK_INT >= 33) {
            extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        }
        if (markedSensitive) return

        val payloads = (0 until clip.itemCount).mapNotNull { index ->
            runCatching { clip.getItemAt(index).coerceToText(this)?.toString() }.getOrNull()
        }.filter { it.isNotEmpty() }

        if (payloads.isEmpty()) return
        io.execute {
            val store = ClipStore.get(applicationContext)
            payloads.forEach { value -> runCatching { store.insert(value) } }
        }
    }

    override fun onDestroy() {
        if (::clipboardManager.isInitialized) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        }
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val INTERNAL_CLIP_LABEL = "Keyboard Cyber internal"
    }
}
