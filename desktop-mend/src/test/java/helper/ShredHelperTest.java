package helper;

import co.samco.mend4.desktop.helper.ShredHelper;
import commands.TestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class ShredHelperTest extends TestBase {

    private String fileNameRef;
    @Before
    public void setup() {
        super.setup();
        fileNameRef = strings.get("Shred.fileName");
        shredHelper = new ShredHelper(strings, osDao, settings, log);
    }

    private void assertShredCommand(String filename, String command, String[] result) {
        Assert.assertTrue(Arrays.equals(shredHelper.generateShredCommandArgs(filename, command), result));
    }

    @Test
    public void testGenerateShredCommandArgs() {
        String filename = "sam";
        String command = "some " + fileNameRef + " to shred";
        String[] result = new String[]{"some", filename, "to", "shred"};
        assertShredCommand(filename, command, result);
    }

    @Test
    public void testGenerateShredCommandArgsMultipleFilenameInstances() {
        String filename = "sam";
        String command = "some " + fileNameRef + " to shred " + fileNameRef;
        String[] result = new String[]{"some", filename, "to", "shred", filename};
        assertShredCommand(filename, command, result);
    }

    @Test
    public void testGenerateShredCommandRealisticExample() {
        String filename = "some-file.txt";
        String command = "shred -u " + fileNameRef;
        String[] result = new String[]{"shred", "-u", filename};
        assertShredCommand(filename, command, result);
    }
}
