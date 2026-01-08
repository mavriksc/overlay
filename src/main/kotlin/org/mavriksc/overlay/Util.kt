package org.mavriksc.overlay

import okhttp3.Request

fun String.toRequest(): Request = Request.Builder().url(this).build()