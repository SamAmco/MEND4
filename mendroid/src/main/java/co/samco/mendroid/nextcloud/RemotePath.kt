package co.samco.mendroid.nextcloud

/** Decoded, account-relative DAV path. Encoding belongs exclusively to the HTTP client. */
@JvmInline
value class RemotePath private constructor(val value: String) {
    fun child(name: String): RemotePath {
        require(name.isNotBlank() && '/' !in name && name != "." && name != "..")
        return of("${value.trimEnd('/')}/$name")
    }
    val name: String get() = value.substringAfterLast('/')
    val parent: RemotePath get() = of(value.substringBeforeLast('/').ifEmpty { "/" })

    companion object {
        val ROOT = RemotePath("/")
        fun of(value: String): RemotePath {
            require(value.startsWith('/'))
            val segments = value.split('/').filter { it.isNotEmpty() }
            require(segments.none { it == "." || it == ".." })
            return RemotePath("/" + segments.joinToString("/"))
        }
    }
}

