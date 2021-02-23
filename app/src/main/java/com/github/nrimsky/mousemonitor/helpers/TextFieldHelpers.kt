package com.github.nrimsky.mousemonitor.helpers

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

private fun modifyText(numberText: String, editText: EditText) {
    editText.setText(numberText)
    editText.setSelection(numberText.length)
}

fun setupNumberTextField(editText: EditText, defaultString: String) {
    editText.setText(defaultString)
    editText.doAfterTextChanged {
        if (!it.isNullOrBlank()) {
            val originalText = it.toString()
            try {
                val numberText = originalText.toInt().toString()
                if (originalText != numberText) {
                    modifyText(numberText, editText)
                }
            } catch (e: Exception) {
                modifyText(defaultString, editText)
            }
        }
    }
}