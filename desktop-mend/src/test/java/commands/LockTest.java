package commands;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.commands.Lock;
import co.samco.mend4.desktop.helper.ShredHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import testutils.TestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class LockTest extends TestBase {
   private Lock lock;
   private String privateKeyPath = "privpath";
   private String publicKeyPath = "pubpath";

   @Before
   public void setup() {
      super.setup();
      shredHelper = new ShredHelper(strings, osDao, settings, log);
      when(fileResolveHelper.getPrivateKeyPath()).thenReturn(privateKeyPath);
      when(fileResolveHelper.getPublicKeyPath()).thenReturn(publicKeyPath);
      lock = new Lock(strings, log, osDao, shredHelper, fileResolveHelper);
   }

   private ArgumentCaptor<String> getOutputOfLockTest() throws IOException, CorruptSettingsException {
      when(settings.getValue(Settings.Name.SHREDCOMMAND)).thenReturn("");
      Process process = mock(Process.class);
      when(process.getInputStream()).thenReturn(TestUtils.getEmptyInputStream());
      ArgumentCaptor<String> stdErr = ArgumentCaptor.forClass(String.class);
      when(osDao.executeCommand(any(String[].class))).thenReturn(process);
      lock.execute(Collections.emptyList());
      return stdErr;
   }

   private void assertCleaning(ArgumentCaptor<String> stdErr, int startInd) {
      Assert.assertEquals(strings.getf("Shred.cleaning", privateKeyPath), stdErr.getAllValues().get(startInd));
      Assert.assertEquals(strings.getf("Shred.cleaning", publicKeyPath), stdErr.getAllValues().get(startInd + 1));
   }

   @Test
   public void testKeyNotFound() throws CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(false);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(4)).println(stdErr.capture());
      Assert.assertEquals(strings.get("Lock.notUnlocked"), stdErr.getAllValues().get(0));
      assertCleaning(stdErr, 1);
      Assert.assertEquals(strings.get("Lock.locked"), stdErr.getAllValues().get(3));
   }

   @Test
   public void testKeyFoundAndLockFailed() throws CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(true);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(3)).println(stdErr.capture());
      assertCleaning(stdErr, 0);
      Assert.assertEquals(strings.get("Lock.lockFailed"), stdErr.getAllValues().get(2));
   }

   @Test
   public void testKeyFoundAndLockPassed() throws CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenAnswer(new Answer<Boolean>() {
         int count = 0;
         @Override
         public Boolean answer(InvocationOnMock invocation) {
            if (count++ > 0) return false;
            else return true;
         }
      });
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(3)).println(stdErr.capture());
      assertCleaning(stdErr, 0);
      Assert.assertEquals(strings.get("Lock.locked"), stdErr.getAllValues().get(2));
   }
}
