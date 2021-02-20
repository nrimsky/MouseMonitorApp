package com.example.mousemonitor.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

class AppAlert(private  val context: Context, private val title: String, private val message: String) {

    fun show(text: String, buttonFunc: ((DialogInterface, Int) -> Unit)) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(text, buttonFunc)
        builder.setCancelable(false)
        val alertDialog = builder.show()
        alertDialog.setCanceledOnTouchOutside(false)
    }

}