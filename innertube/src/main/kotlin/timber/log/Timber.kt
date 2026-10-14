package timber.log

object Timber {
    @JvmStatic
    fun d(message: String?) {
        // Debug logging
    }

    @JvmStatic
    fun w(message: String?) {
        System.err.println("[WARN] $message")
    }

    @JvmStatic
    fun e(t: Throwable?, message: String?) {
        System.err.println("[ERROR] $message: ${t?.message}")
    }

    @JvmStatic
    fun e(message: String?) {
        System.err.println("[ERROR] $message")
    }

    @JvmStatic
    fun tag(tag: String): Timber = this
}
