package timber.log

open class Tree {
    open fun v(message: String?, vararg args: Any?) {}
    open fun v(t: Throwable?, message: String?, vararg args: Any?) {}
    open fun v(t: Throwable?) {}

    open fun d(message: String?, vararg args: Any?) {}
    open fun d(t: Throwable?, message: String?, vararg args: Any?) {}
    open fun d(t: Throwable?) {}

    open fun i(message: String?, vararg args: Any?) {}
    open fun i(t: Throwable?, message: String?, vararg args: Any?) {}
    open fun i(t: Throwable?) {}

    open fun w(message: String?, vararg args: Any?) {}
    open fun w(t: Throwable?, message: String?, vararg args: Any?) {}
    open fun w(t: Throwable?) {}

    open fun e(message: String?, vararg args: Any?) {}
    open fun e(t: Throwable?, message: String?, vararg args: Any?) {}
    open fun e(t: Throwable?) {}

    open fun tag(tag: String): Tree = this
}

object Timber : Tree() {
    open class DebugTree : Tree() {
        override fun d(message: String?, vararg args: Any?) {
            println("[DEBUG] " + format(message, args))
        }
        override fun i(message: String?, vararg args: Any?) {
            println("[INFO] " + format(message, args))
        }
        override fun w(message: String?, vararg args: Any?) {
            System.err.println("[WARN] " + format(message, args))
        }
        override fun w(t: Throwable?, message: String?, vararg args: Any?) {
            System.err.println("[WARN] " + format(message, args) + (t?.let { ": ${it.message}" } ?: ""))
        }
        override fun e(message: String?, vararg args: Any?) {
            System.err.println("[ERROR] " + format(message, args))
        }
        override fun e(t: Throwable?, message: String?, vararg args: Any?) {
            System.err.println("[ERROR] " + format(message, args) + (t?.let { ": ${it.message}" } ?: ""))
        }
        override fun e(t: Throwable?) {
            System.err.println("[ERROR] ${t?.message}")
        }

        private fun format(message: String?, args: Array<out Any?>): String {
            if (message == null) return ""
            return if (args.isEmpty()) message else try { String.format(message, *args) } catch (_: Exception) { message }
        }
    }

    private val trees = mutableListOf<Tree>()

    @JvmStatic
    fun plant(tree: Tree) {
        trees.add(tree)
    }

    @JvmStatic
    fun uproot(tree: Tree) {
        trees.remove(tree)
    }

    @JvmStatic
    fun uprootAll() {
        trees.clear()
    }

    override fun v(message: String?, vararg args: Any?) {
        trees.forEach { it.v(message, *args) }
    }
    override fun v(t: Throwable?, message: String?, vararg args: Any?) {
        trees.forEach { it.v(t, message, *args) }
    }
    override fun v(t: Throwable?) {
        trees.forEach { it.v(t) }
    }

    override fun d(message: String?, vararg args: Any?) {
        trees.forEach { it.d(message, *args) }
    }
    override fun d(t: Throwable?, message: String?, vararg args: Any?) {
        trees.forEach { it.d(t, message, *args) }
    }
    override fun d(t: Throwable?) {
        trees.forEach { it.d(t) }
    }

    override fun i(message: String?, vararg args: Any?) {
        trees.forEach { it.i(message, *args) }
    }
    override fun i(t: Throwable?, message: String?, vararg args: Any?) {
        trees.forEach { it.i(t, message, *args) }
    }
    override fun i(t: Throwable?) {
        trees.forEach { it.i(t) }
    }

    override fun w(message: String?, vararg args: Any?) {
        trees.forEach { it.w(message, *args) }
    }
    override fun w(t: Throwable?, message: String?, vararg args: Any?) {
        trees.forEach { it.w(t, message, *args) }
    }
    override fun w(t: Throwable?) {
        trees.forEach { it.w(t) }
    }

    override fun e(message: String?, vararg args: Any?) {
        trees.forEach { it.e(message, *args) }
    }
    override fun e(t: Throwable?, message: String?, vararg args: Any?) {
        trees.forEach { it.e(t, message, *args) }
    }
    override fun e(t: Throwable?) {
        trees.forEach { it.e(t) }
    }

    override fun tag(tag: String): Tree = this
}
