package testutils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestUtils {
    public static InputStream getEmptyInputStream() {
        return new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    }
}
