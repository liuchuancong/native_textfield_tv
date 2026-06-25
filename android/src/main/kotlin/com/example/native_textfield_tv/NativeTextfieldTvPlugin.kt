package com.example.native_textfield_tv

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

object ChannelConst {
    const val CHANNEL_NAME = "native_textfield_tv"
    const val VIEW_TYPE_ID = "native_textfield_tv"
    const val METHOD_GET_PLATFORM_VERSION = "getPlatformVersion"
    const val METHOD_SET_TEXT = "setText"
    const val METHOD_GET_TEXT = "getText"
    const val METHOD_REQUEST_FOCUS = "requestFocus"
    const val METHOD_CLEAR_FOCUS = "clearFocus"
    const val METHOD_SET_ENABLED = "setEnabled"
    const val METHOD_SET_HINT = "setHint"
    const val METHOD_MOVE_CURSOR = "moveCursor"
    const val METHOD_SET_OBSCURE_TEXT = "setObscureText"
    const val METHOD_ON_SUBMITTED = "onSubmitted"
    const val ARG_INSTANCE_ID = "instanceId"
    const val ARG_TEXT = "text"
    const val ARG_DIRECTION = "direction"
    const val ARG_ENABLED = "enabled"
    const val ARG_HINT = "hint"
    const val ARG_OBSCURE_TEXT = "obscureText"
    const val EVENT_SUBMITTED = "onSubmitted"
    const val CURSOR_LEFT = "left"
    const val CURSOR_RIGHT = "right"
}

object ErrorCode {
    const val INVALID_INSTANCE = "INVALID_INSTANCE"
    const val INVALID_CURSOR_DIRECTION = "INVALID_MOVE_CURSOR"
}

class NativeTvTextFieldPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var methodChannel: MethodChannel
    private lateinit var binaryMessenger: BinaryMessenger
    private val activeViewMap = mutableMapOf<Int, NativeTvTextFieldView>()

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        binaryMessenger = binding.binaryMessenger
        methodChannel = MethodChannel(binaryMessenger, ChannelConst.CHANNEL_NAME)
        methodChannel.setMethodCallHandler(this)
        binding.platformViewRegistry.registerViewFactory(
            ChannelConst.VIEW_TYPE_ID,
            NativeTvTextFieldFactory(binaryMessenger, activeViewMap, this)
        )
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == ChannelConst.METHOD_GET_PLATFORM_VERSION) {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
            return
        }
        val targetView = getTargetView(call, result) ?: return
        when (call.method) {
            ChannelConst.METHOD_SET_TEXT -> handleSetText(call, targetView, result)
            ChannelConst.METHOD_GET_TEXT -> handleGetText(targetView, result)
            ChannelConst.METHOD_REQUEST_FOCUS -> handleRequestFocus(targetView, result)
            ChannelConst.METHOD_CLEAR_FOCUS -> handleClearFocus(targetView, result)
            ChannelConst.METHOD_SET_ENABLED -> handleSetEnabled(call, targetView, result)
            ChannelConst.METHOD_SET_HINT -> handleSetHint(call, targetView, result)
            ChannelConst.METHOD_MOVE_CURSOR -> handleMoveCursor(call, targetView, result)
            ChannelConst.METHOD_SET_OBSCURE_TEXT -> handleSetObscureText(call, targetView, result)
            ChannelConst.METHOD_ON_SUBMITTED -> handleOnSubmitted(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        activeViewMap.clear()
    }

    private fun getTargetView(call: MethodCall, result: Result): NativeTvTextFieldView? {
        val instanceId = call.argument<Int>(ChannelConst.ARG_INSTANCE_ID)
        val view = activeViewMap[instanceId]
        if (view == null) {
            result.error(ErrorCode.INVALID_INSTANCE, "View instance not found", null)
            return null
        }
        return view
    }

    fun removeViewInstance(instanceId: Int) {
        activeViewMap.remove(instanceId)
    }

    private fun handleSetText(call: MethodCall, view: NativeTvTextFieldView, result: Result) {
        val content = call.argument<String>(ChannelConst.ARG_TEXT).orEmpty()
        view.setText(content)
        result.success(null)
    }

    private fun handleGetText(view: NativeTvTextFieldView, result: Result) {
        result.success(view.getText())
    }

    private fun handleRequestFocus(view: NativeTvTextFieldView, result: Result) {
        view.requestFocus()
        result.success(null)
    }

    private fun handleClearFocus(view: NativeTvTextFieldView, result: Result) {
        view.clearFocus()
        result.success(null)
    }

    private fun handleSetEnabled(call: MethodCall, view: NativeTvTextFieldView, result: Result) {
        val enable = call.argument<Boolean>(ChannelConst.ARG_ENABLED) ?: true
        view.setEnabled(enable)
        result.success(null)
    }

    private fun handleSetHint(call: MethodCall, view: NativeTvTextFieldView, result: Result) {
        val hintText = call.argument<String>(ChannelConst.ARG_HINT)
        view.setHint(hintText)
        result.success(null)
    }

    private fun handleMoveCursor(call: MethodCall, view: NativeTvTextFieldView, result: Result) {
        val direction = call.argument<String>(ChannelConst.ARG_DIRECTION)
        if (direction == ChannelConst.CURSOR_LEFT || direction == ChannelConst.CURSOR_RIGHT) {
            view.moveCursor(direction)
            result.success(null)
        } else {
            result.error(ErrorCode.INVALID_CURSOR_DIRECTION, "Invalid cursor direction", null)
        }
    }

    private fun handleSetObscureText(call: MethodCall, view: NativeTvTextFieldView, result: Result) {
        val obscure = call.argument<Boolean>(ChannelConst.ARG_OBSCURE_TEXT) ?: false
        view.setObscureText(obscure)
        result.success(null)
    }

    private fun handleOnSubmitted(call: MethodCall, result: Result) {
        val instanceId = call.argument<Int>(ChannelConst.ARG_INSTANCE_ID)
        val inputText = call.argument<String>(ChannelConst.ARG_TEXT).orEmpty()
        methodChannel.invokeMethod(
            ChannelConst.EVENT_SUBMITTED,
            mapOf(
                ChannelConst.ARG_INSTANCE_ID to instanceId,
                ChannelConst.ARG_TEXT to inputText
            )
        )
        result.success(null)
    }
}

class NativeTvTextFieldFactory(
    private val messenger: BinaryMessenger,
    private val viewCache: MutableMap<Int, NativeTvTextFieldView>,
    private val plugin: NativeTvTextFieldPlugin
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    @Suppress("UNCHECKED_CAST")
    override fun create(
        context: android.content.Context,
        viewId: Int,
        creationParams: Any?
    ): PlatformView {
        val params = creationParams as? Map<String, Any>
        val instanceId = params?.get(ChannelConst.ARG_INSTANCE_ID) as? Int ?: viewId
        val textFieldView = NativeTvTextFieldView(context, viewId, params, messenger, plugin)
        viewCache[instanceId] = textFieldView
        return textFieldView
    }
}
