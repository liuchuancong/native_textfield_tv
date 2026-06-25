package com.example.native_textfield_tv

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class NativeTvTextFieldView(
    private val context: Context,
    private val viewId: Int,
    private val creationParams: Map<String, Any>?,
    private val messenger: BinaryMessenger,
    private val plugin: NativeTextfieldTvPlugin
) : PlatformView {
    private val tag = "NativeTvTextFieldView"
    private val editText: EditText
    private val methodChannel: MethodChannel
    private val instanceId: Int
    private var textWatcher: TextWatcher? = null

    init {
        instanceId = creationParams?.get(ChannelConst.ARG_INSTANCE_ID) as? Int ?: viewId
        editText = buildEditText()
        methodChannel = MethodChannel(messenger, ChannelConst.CHANNEL_NAME)
        attachTextWatcher()
        attachFocusListener()
    }

    private fun buildEditText(): EditText {
        return EditText(context).apply {
            val initialText = creationParams?.get("initialText") as? String
            initialText?.let { setText(it) }
            hint = creationParams?.get(ChannelConst.ARG_HINT) as? String ?: ""
            val textColor = parseColor(creationParams?.get("textColor"), Color.WHITE)
            setTextColor(textColor)
            setHintTextColor(textColor)
            val bgColor = parseColor(creationParams?.get("backgroundColor"), Color.BLACK)
            setBackgroundColor(bgColor)
            val obscureText = creationParams?.get(ChannelConst.ARG_OBSCURE_TEXT) as? Boolean ?: false
            applyObscureModeInternal(this, obscureText)
            val maxLines = creationParams?.get("maxLines") as? Int ?: 1
            setLines(maxLines)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener(::handleEditorSubmit)
        }
    }

    private fun parseColor(rawValue: Any?, defaultColor: Int): Int {
        return when (rawValue) {
            is Int -> rawValue
            is Long -> rawValue.toInt()
            else -> defaultColor
        }
    }

    private fun handleEditorSubmit(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            methodChannel.invokeMethod(
                ChannelConst.EVENT_SUBMITTED,
                mapOf(
                    ChannelConst.ARG_INSTANCE_ID to instanceId,
                    ChannelConst.ARG_TEXT to editText.text.toString()
                )
            )
            return true
        }
        return false
    }

    private fun attachTextWatcher() {
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                methodChannel.invokeMethod(
                    "onTextChanged",
                    mapOf(
                        ChannelConst.ARG_INSTANCE_ID to instanceId,
                        ChannelConst.ARG_TEXT to s.toString()
                    )
                )
            }
        }
        editText.addTextChangedListener(textWatcher)
    }

    private fun attachFocusListener() {
        editText.setOnFocusChangeListener { _, hasFocus ->
            methodChannel.invokeMethod(
                "onFocusChanged",
                mapOf(
                    ChannelConst.ARG_INSTANCE_ID to instanceId,
                    "hasFocus" to hasFocus
                )
            )
        }
    }

    private fun applyObscureModeInternal(targetEditText: EditText, obscure: Boolean) {
        val cursorPos = targetEditText.selectionStart
        val inputBase = android.text.InputType.TYPE_CLASS_TEXT
        val passwordFlag = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        targetEditText.transformationMethod = if (obscure) PasswordTransformationMethod.getInstance() else null
        targetEditText.inputType = if (obscure) inputBase or passwordFlag else inputBase
        if (cursorPos >= 0 && cursorPos <= targetEditText.text.length) {
            targetEditText.setSelection(cursorPos)
        }
    }

    fun setText(text: String) {
        editText.setText(text)
    }

    fun getText(): String = editText.text.toString()

    fun requestFocus() {
        editText.requestFocus()
    }

    fun clearFocus() {
        editText.clearFocus()
    }

    fun setEnabled(enabled: Boolean) {
        editText.isEnabled = enabled
    }

    fun setHint(hint: String?) {
        editText.hint = hint
    }

    fun setTextColorFlutter(color: Int) {
        editText.setTextColor(color)
        editText.setHintTextColor(color)
    }

    fun setBackgroundColorFlutter(color: Int) {
        editText.setBackgroundColor(color)
    }

    fun setObscureText(obscure: Boolean) {
        editText.post { applyObscureModeInternal(editText, obscure) }
    }

    fun moveCursor(direction: String) {
        val pos = editText.selectionStart
        when (direction) {
            ChannelConst.CURSOR_LEFT -> if (pos > 0) editText.setSelection(pos - 1)
            ChannelConst.CURSOR_RIGHT -> if (pos < editText.text.length) editText.setSelection(pos + 1)
        }
    }

    override fun getView(): View = editText

    override fun dispose() {
        plugin.removeViewInstance(instanceId)
        editText.setOnEditorActionListener(null)
        editText.setOnFocusChangeListener(null)
        textWatcher?.let { editText.removeTextChangedListener(it) }
        methodChannel.setMethodCallHandler(null)
    }
}
