package com.cybersecurityrahbar.keyboardcyber.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.cybersecurityrahbar.keyboardcyber.MainActivity
import com.cybersecurityrahbar.keyboardcyber.data.ClipStore
import com.cybersecurityrahbar.keyboardcyber.data.KeyboardPreferences
import java.util.concurrent.Executors

private enum class KeyKind { LETTER, FUNCTION, ACCENT, PRIMARY }

private data class KeySpec(
    val label: String,
    val hint: String = "",
    val weight: Float = 1f,
    val kind: KeyKind = KeyKind.LETTER,
    val alternates: List<String> = emptyList(),
    val onTap: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null
)

class KeyboardImeService : InputMethodService() {
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var keyboardContainer: LinearLayout
    private var suggestionsRow: LinearLayout? = null

    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var privateInput = false
    private var language = "ar"
    private var symbolsMode = false
    private var emojiMode = false
    private var shiftEnabled = false

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
        rebuildKeyboard()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(7), dp(8), dp(7), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(7, 16, 29), Color.rgb(10, 25, 42), Color.rgb(5, 12, 22))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(30, 74, 91))
            }
        }

        root.addView(createToolbar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42)
        ))

        suggestionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(3), dp(2), dp(4))
        }
        root.addView(suggestionsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(37)
        ))

        keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.addView(keyboardContainer)

        refreshSuggestions()
        rebuildKeyboard()
        return root
    }

    private fun createToolbar(): LinearLayout {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER
        }

        val tools = listOf(
            Triple("⚙", "الإعدادات") { openArchive() },
            Triple("GIF", "صور متحركة") {
                toast("قسم GIF قيد التطوير")
            },
            Triple("☺", "الإيموجي") {
                emojiMode = !emojiMode
                symbolsMode = false
                rebuildKeyboard()
            },
            Triple("⌘", "الرموز") {
                symbolsMode = !symbolsMode
                emojiMode = false
                rebuildKeyboard()
            },
            Triple("A⇄文", "الترجمة") {
                toast("الترجمة عبر الإنترنت قيد الربط")
            },
            Triple("▤", "أرشيف النسخ") { openArchive() },
            Triple("◎", "تبديل اللغة") { toggleLanguage() },
            Triple("EN", "الإنجليزية") {
                language = "en"
                symbolsMode = false
                emojiMode = false
                rebuildKeyboard()
            },
            Triple("ع", "العربية") {
                language = "ar"
                symbolsMode = false
                emojiMode = false
                rebuildKeyboard()
            }
        )

        tools.forEach { tool ->
            val button = TextView(this).apply {
                text = tool.first
                contentDescription = tool.second
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(116, 220, 229))
                textSize = if (tool.first.length > 2) 10f else 19f
                typeface = Typeface.DEFAULT_BOLD
                background = toolBackground()
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    tool.third.invoke()
                }
            }
            toolbar.addView(button, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                setMargins(dp(1), 0, dp(1), 0)
            })
        }
        return toolbar
    }

    private fun refreshSuggestions() {
        val row = suggestionsRow ?: return
        if (privateInput || !KeyboardPreferences.archiveSuggestionsEnabled(this)) {
            row.removeAllViews()
            row.visibility = View.GONE
            return
        }

        row.visibility = View.VISIBLE
        io.execute {
            val recent = runCatching {
                ClipStore.get(applicationContext).recent(4)
            }.getOrDefault(emptyList())

            mainHandler.post {
                val target = suggestionsRow ?: return@post
                target.removeAllViews()
                if (privateInput || !KeyboardPreferences.archiveSuggestionsEnabled(this)) {
                    target.visibility = View.GONE
                    return@post
                }

                if (recent.isEmpty()) {
                    val label = TextView(this).apply {
                        text = "CLIP ARCHIVE  •  ستظهر هنا مقتطفاتك المنسوخة"
                        setTextColor(Color.rgb(91, 130, 151))
                        textSize = 9f
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    target.addView(label)
                    return@post
                }

                recent.forEach { record ->
                    val chip = TextView(this).apply {
                        text = record.content.replace("\n", " ").take(23)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(213, 236, 243))
                        textSize = 10f
                        background = roundedBackground(
                            intArrayOf(Color.rgb(20, 48, 65), Color.rgb(16, 36, 52)),
                            Color.rgb(40, 105, 119),
                            9
                        )
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        setOnClickListener {
                            currentInputConnection?.commitText(record.content, 1)
                        }
                    }
                    target.addView(chip, LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    })
                }
            }
        }
    }

    private fun rebuildKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        keyboardContainer.removeAllViews()

        when {
            emojiMode -> buildEmojiLayout()
            symbolsMode -> buildSymbolsLayout()
            language == "ar" -> buildArabicLayout()
            else -> buildEnglishLayout()
        }
    }

    /**
     * The visual left-to-right positions intentionally follow the user's
     * reference keyboard. Keep these arrays in visual order.
     */
    private fun buildArabicLayout() {
        addKeyRow(
            listOf(
                key("ض", "١", alternatives = listOf("ظ")),
                key("ص", "٢"),
                key("ق", "٣", alternatives = listOf("ث", "ڤ")),
                key("ف", "٤", alternatives = listOf("ڤ")),
                key("غ", "٥"),
                key("ع", "٦"),
                key("ه", "٧", alternatives = listOf("ة", "ۀ")),
                key("خ", "٨"),
                key("ح", "٩"),
                key("ج", "٠")
            )
        )

        addKeyRow(
            listOf(
                key("ش", alternatives = listOf("شّ")),
                key("س"),
                key("ي", "ى", alternatives = listOf("ى", "ئ", "ې")),
                key("ب"),
                key("ل", "لا", alternatives = listOf("لا")),
                key("ا", "أ", alternatives = listOf("أ", "إ", "آ", "ٱ")),
                key("ت", alternatives = listOf("ة")),
                key("ن", alternatives = listOf("ً", "ٌ", "ٍ")),
                key("م"),
                key("ك", alternatives = listOf("گ", "ڪ"))
            ),
            insetStart = 2,
            insetEnd = 2
        )

        addKeyRow(
            listOf(
                key("ظ"),
                key("ط"),
                key("ذ"),
                key("د", alternatives = listOf("ذ")),
                key("ز"),
                key("ر", alternatives = listOf("ڑ")),
                key("و", "ؤ", alternatives = listOf("ؤ")),
                key("ة", "ۀ", alternatives = listOf("ه", "ۀ")),
                key("ث"),
                functionKey("⌫", 1.15f, onTap = { backspace() }, onLongPress = { deleteWord() })
            )
        )

        addBottomRow()
    }

    private fun buildEnglishLayout() {
        addKeyRow(
            "q w e r t y u i o p".split(" ").mapIndexed { index, letter ->
                key(letter, (index + 1).toString(), alternatives = englishAlternates(letter))
            }
        )
        addKeyRow(
            "a s d f g h j k l".split(" ").map { letter ->
                key(letter, alternatives = englishAlternates(letter))
            },
            insetStart = 10,
            insetEnd = 10
        )
        addKeyRow(
            listOf(
                functionKey("⇧", 1.25f, onTap = { shiftEnabled = !shiftEnabled; rebuildKeyboard() }),
                *"z x c v b n m".split(" ").map { letter ->
                    key(if (shiftEnabled) letter.uppercase() else letter, alternatives = englishAlternates(letter))
                }.toTypedArray(),
                functionKey("⌫", 1.25f, onTap = { backspace() }, onLongPress = { deleteWord() })
            )
        )
        addBottomRow()
    }

    private fun englishAlternates(letter: String): List<String> = when (letter) {
        "a" -> listOf("á", "à", "ä", "â", "æ")
        "e" -> listOf("é", "è", "ë", "ê")
        "i" -> listOf("í", "ì", "ï", "î")
        "o" -> listOf("ó", "ò", "ö", "ô", "œ")
        "u" -> listOf("ú", "ù", "ü", "û")
        "n" -> listOf("ñ")
        "c" -> listOf("ç")
        else -> emptyList()
    }

    private fun buildSymbolsLayout() {
        addKeyRow(
            listOf("!", "@", "#", 36.toChar().toString(), "%", "&", "-", "+", "(", ")").map {
                key(it, kind = KeyKind.ACCENT)
            }
        )
        addKeyRow(
            listOf("/", ":", ";", "'", 34.toChar().toString(), "?", "¿", "=", "_", "\\\\").map {
                key(it, kind = KeyKind.ACCENT)
            },
            insetStart = 4,
            insetEnd = 4
        )
        addKeyRow(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶").map {
                key(it, kind = KeyKind.ACCENT)
            } + functionKey("⌫", 1.15f, onTap = { backspace() }, onLongPress = { deleteWord() })
        )
        addBottomRow()
    }

    private fun buildEmojiLayout() {
        addKeyRow(listOf("😀", "😂", "🥹", "😍", "😘", "😎", "🤔", "😭", "😊", "😉").map {
            key(it, kind = KeyKind.ACCENT)
        })
        addKeyRow(listOf("👍", "👎", "👏", "🙏", "💪", "🔥", "❤️", "💯", "✅", "🚀").map {
            key(it, kind = KeyKind.ACCENT)
        })
        addKeyRow(listOf("🎯", "🔒", "💻", "🛡️", "⚡", "🌐", "📌", "⭐", "💡").map {
            key(it, kind = KeyKind.ACCENT)
        } + functionKey("⌫", 1.15f, onTap = { backspace() }))
        addBottomRow()
    }

    private fun addBottomRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            createKeyView(functionKey("@?!", 1.05f, onTap = {
                symbolsMode = true
                emojiMode = false
                rebuildKeyboard()
            })),
            keyLayout(1.05f, 54)
        )
        row.addView(
            createKeyView(functionKey("123", 1.0f, onTap = {
                symbolsMode = !symbolsMode
                emojiMode = false
                rebuildKeyboard()
            })),
            keyLayout(1.0f, 54)
        )
        row.addView(
            createKeyView(
                key(if (language == "ar") "،" else ",", kind = KeyKind.ACCENT,
                    alternatives = listOf("؛", ":", "!", "؟", "."))
            ),
            keyLayout(0.82f, 54)
        )
        row.addView(createSpaceCluster(), keyLayout(4.25f, 54))
        row.addView(
            createKeyView(
                key(".", kind = KeyKind.ACCENT, alternatives = listOf("؟", "!", "…", "؟", "。"))
            ),
            keyLayout(0.82f, 54)
        )
        row.addView(
            createKeyView(functionKey("↵", 1.15f, KeyKind.PRIMARY, onTap = { sendEnter() })),
            keyLayout(1.15f, 54)
        )

        keyboardContainer.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(1)
        })
    }

    private fun createSpaceCluster(): FrameLayout {
        val root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = keyBackground(KeyKind.PRIMARY)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                currentInputConnection?.commitText(" ", 1)
            }
        }

        val center = TextView(this).apply {
            text = if (language == "ar") "مسافة" else "SPACE"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(238, 250, 255))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(center, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            leftMargin = dp(30)
            rightMargin = dp(30)
        })

        val left = TextView(this).apply {
            text = "◀"
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.rgb(83, 223, 210))
            setOnClickListener { moveCursor(-1) }
        }
        root.addView(left, FrameLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

        val right = TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            textSize = 11f
            setTextColor(Color.rgb(83, 223, 210))
            setOnClickListener { moveCursor(1) }
        }
        root.addView(right, FrameLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))

        return root
    }

    private fun addKeyRow(keys: List<KeySpec>, insetStart: Int = 0, insetEnd: Int = 0) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }

        keys.forEach { spec ->
            row.addView(createKeyView(spec), keyLayout(spec.weight, 50))
        }

        keyboardContainer.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply {
            leftMargin = dp(insetStart)
            rightMargin = dp(insetEnd)
            bottomMargin = dp(2)
        })
    }

    private fun keyLayout(weight: Float, heightDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun key(
        label: String,
        hint: String = "",
        weight: Float = 1f,
        kind: KeyKind = KeyKind.LETTER,
        alternatives: List<String> = emptyList(),
        onTap: (() -> Unit)? = null
    ) = KeySpec(label, hint, weight, kind, alternatives, onTap)

    private fun functionKey(
        label: String,
        weight: Float = 1f,
        kind: KeyKind = KeyKind.FUNCTION,
        onTap: (() -> Unit)? = null,
        onLongPress: (() -> Unit)? = null
    ) = KeySpec(label, weight = weight, kind = kind, onTap = onTap, onLongPress = onLongPress)

    private fun createKeyView(spec: KeySpec): FrameLayout {
        val root = FrameLayout(this).apply {
            background = keyBackground(spec.kind)
            elevation = dp(if (spec.kind == KeyKind.PRIMARY) 3 else 2).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = spec.label
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                spec.onTap?.invoke() ?: when (spec.label) {
                    "⌫" -> backspace()
                    "↵" -> sendEnter()
                    "⇧" -> { shiftEnabled = !shiftEnabled; rebuildKeyboard() }
                    else -> commit(if (language == "en" && shiftEnabled && spec.label.length == 1) {
                        spec.label.uppercase()
                    } else {
                        spec.label
                    })
                }
            }
            setOnLongClickListener {
                if (spec.alternates.isNotEmpty()) {
                    showAlternates(this, spec.alternates)
                    true
                } else if (spec.onLongPress != null) {
                    spec.onLongPress.invoke()
                    true
                } else {
                    false
                }
            }
        }

        val label = TextView(this).apply {
            text = spec.label
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = when {
                spec.label.length > 3 -> 11f
                spec.label.length > 1 -> 15f
                else -> 23f
            }
            typeface = if (spec.kind == KeyKind.LETTER) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            setTextColor(
                if (spec.kind == KeyKind.LETTER) Color.rgb(235, 245, 251)
                else Color.rgb(90, 232, 216)
            )
        }
        root.addView(label, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (spec.hint.isNotBlank()) {
            val hint = TextView(this).apply {
                text = spec.hint
                textSize = 8f
                gravity = Gravity.TOP or Gravity.END
                setTextColor(Color.rgb(100, 192, 202))
                setPadding(0, dp(3), dp(5), 0)
            }
            root.addView(hint, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        return root
    }

    private fun showAlternates(anchor: View, alternatives: List<String>) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = roundedBackground(
                intArrayOf(Color.rgb(14, 34, 51), Color.rgb(8, 20, 34)),
                Color.rgb(69, 201, 199),
                12
            )
        }

        var popup: PopupWindow? = null
        alternatives.distinct().forEach { alternative ->
            val option = TextView(this).apply {
                text = alternative
                gravity = Gravity.CENTER
                textSize = 21f
                setTextColor(Color.WHITE)
                background = roundedBackground(
                    intArrayOf(Color.rgb(29, 59, 77), Color.rgb(18, 42, 59)),
                    Color.rgb(47, 102, 120),
                    8
                )
                setOnClickListener {
                    commit(alternative)
                    popup?.dismiss()
                }
            }
            content.addView(option, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }

        popup = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, dp(52), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(roundedBackground(
                intArrayOf(Color.rgb(10, 24, 39), Color.rgb(7, 16, 27)),
                Color.rgb(55, 148, 157),
                12
            ))
            elevation = dp(8).toFloat()
            showAsDropDown(anchor, 0, -dp(58))
        }
    }

    private fun keyBackground(kind: KeyKind): StateListDrawable {
        val normal = when (kind) {
            KeyKind.LETTER -> intArrayOf(Color.rgb(35, 57, 77), Color.rgb(21, 39, 58))
            KeyKind.FUNCTION -> intArrayOf(Color.rgb(22, 73, 87), Color.rgb(13, 48, 65))
            KeyKind.ACCENT -> intArrayOf(Color.rgb(27, 66, 79), Color.rgb(17, 46, 62))
            KeyKind.PRIMARY -> intArrayOf(Color.rgb(22, 132, 143), Color.rgb(13, 83, 105))
        }
        val pressed = when (kind) {
            KeyKind.LETTER -> intArrayOf(Color.rgb(53, 101, 125), Color.rgb(33, 75, 99))
            KeyKind.FUNCTION -> intArrayOf(Color.rgb(39, 111, 124), Color.rgb(21, 77, 94))
            KeyKind.ACCENT -> intArrayOf(Color.rgb(40, 111, 122), Color.rgb(22, 74, 91))
            KeyKind.PRIMARY -> intArrayOf(Color.rgb(35, 170, 168), Color.rgb(17, 111, 134))
        }

        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                roundedBackground(pressed, Color.rgb(104, 237, 221), 9)
            )
            addState(
                intArrayOf(),
                roundedBackground(normal, Color.rgb(52, 91, 112), 9)
            )
        }
    }

    private fun toolBackground() = roundedBackground(
        intArrayOf(Color.rgb(15, 34, 52), Color.rgb(10, 25, 41)),
        Color.rgb(31, 69, 86),
        9
    )

    private fun roundedBackground(colors: IntArray, stroke: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
    }

    private fun deleteWord() {
        currentInputConnection?.deleteSurroundingTextInCodePoints(32, 0)
    }

    private fun moveCursor(direction: Int) {
        val keyCode = if (direction < 0) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        }
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        )
    }

    private fun sendEnter() {
        val connection: InputConnection = currentInputConnection ?: return
        if (sendDefaultEditorAction(true)) return

        connection.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
        )
        connection.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
        )
    }

    private fun toggleLanguage() {
        language = if (language == "ar") "en" else "ar"
        symbolsMode = false
        emojiMode = false
        shiftEnabled = false
        rebuildKeyboard()
    }

    private fun openArchive() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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

        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        val description = clip.description ?: return
        if (description.label?.toString() == INTERNAL_CLIP_LABEL) return

        val sensitiveExtra = if (Build.VERSION.SDK_INT >= 33) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        val markedSensitive = runCatching {
            description.extras?.getBoolean(sensitiveExtra, false) == true
        }.getOrDefault(false)
        if (markedSensitive) return

        // A single clipboard operation becomes one archive record. If Android
        // provides multiple text items, preserve them together rather than
        // creating a separate row for each item.
        val parts = (0 until clip.itemCount).mapNotNull { index ->
            runCatching {
                clip.getItemAt(index).coerceToText(this)?.toString()
            }.getOrNull()
        }.filter { it.isNotEmpty() }.distinct()

        if (parts.isEmpty()) return
        val completeText = parts.joinToString("\n")
        val copiedAt = description.timestamp

        io.execute {
            val saved = runCatching {
                ClipStore.get(applicationContext).insertClipboard(completeText, copiedAt)
            }.getOrDefault(-1L)

            if (saved > 0L) {
                mainHandler.post { refreshSuggestions() }
            }
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
