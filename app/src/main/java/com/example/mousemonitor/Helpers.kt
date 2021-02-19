package com.example.mousemonitor

fun floatListFromString(str: String): List<Float> {
    return str
        .split(",")
        .filter { it != "" }
        .map { it.toFloat() }
}