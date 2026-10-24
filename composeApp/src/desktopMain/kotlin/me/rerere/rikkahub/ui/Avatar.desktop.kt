package me.rerere.rikkahub.ui

actual fun avatarHue(name: String): Float = java.security.MessageDigest.getInstance("SHA-1")
    .digest(name.toByteArray(Charsets.UTF_8)).sumOf { it.toInt() and 0xFF }.rem(360).toFloat()

actual fun localGreeting(): String = when (java.time.LocalTime.now().hour) {
    in 0..5 -> "夜深了🌙"
    in 6..11 -> "早上好👋"
    in 12..17 -> "下午好👋"
    else -> "晚上好👋"
}
