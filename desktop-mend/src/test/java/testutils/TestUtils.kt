package testutils

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object TestUtils {
    val emptyInputStream: InputStream
        get() = ByteArrayInputStream("".toByteArray(StandardCharsets.UTF_8))
}