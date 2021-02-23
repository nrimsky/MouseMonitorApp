package com.github.nrimsky.mousemonitor

// TODO: Allow user to pick the delimiter
fun floatListFromString(str: String): List<Float> {
    return str
        .split(",")
        .filter { it != "" }
        .map { it.toFloat() }
}