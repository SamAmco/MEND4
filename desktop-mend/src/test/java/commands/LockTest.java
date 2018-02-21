package commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.desktop.commands.Lock;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import testutils.FakeLazy;
import testutils.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class LockTest {
   private Lock lock;
   private OSDao osDao;
   private Settings settings;
   private PrintStreamProvider printStreamProvider;
   private PrintStream err;
   private PrintStream out;
   private ShredHelper shredHelper;
   private I18N strings;
   private FileResolveHelper fileResolveHelper;
   private String privateKeyPath = "privpath";
   private String publicKeyPath = "pubpath";

   @Before
   public void setup() {
      strings = new I18N("en", "UK");
      err = mock(PrintStream.class);
      out = mock(PrintStream.class);
      printStreamProvider = mock(PrintStreamProvider.class);
      when(printStreamProvider.err()).thenReturn(err);
      when(printStreamProvider.out()).thenReturn(out);
      settings = mock(Settings.class);
      osDao = mock(OSDao.class);
      fileResolveHelper = mock(FileResolveHelper.class);
      shredHelper = new ShredHelper(strings, osDao, new FakeLazy<>(settings), printStreamProvider);

      when(fileResolveHelper.getPrivateKeyPath()).thenReturn(privateKeyPath);
      when(fileResolveHelper.getPublicKeyPath()).thenReturn(publicKeyPath);
      lock = new Lock(strings, printStreamProvider, osDao, shredHelper, fileResolveHelper);
   }

   private ArgumentCaptor<String> getOutputOfLockTest() throws IOException, InvalidSettingNameException,
           CorruptSettingsException {
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
   public void testKeyNotFound() throws InvalidSettingNameException,
           CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(false);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(4)).println(stdErr.capture());
      Assert.assertEquals(strings.get("Lock.notUnlocked"), stdErr.getAllValues().get(0));
      assertCleaning(stdErr, 1);
      Assert.assertEquals(strings.get("Lock.locked"), stdErr.getAllValues().get(3));
   }

   @Test
   public void testKeyFoundAndLockFailed() throws InvalidSettingNameException,
           CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(true);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(3)).println(stdErr.capture());
      assertCleaning(stdErr, 0);
      Assert.assertEquals(strings.get("Lock.lockFailed"), stdErr.getAllValues().get(2));
   }

   @Test
   public void testKeyFoundAndLockPassed() throws InvalidSettingNameException,
           CorruptSettingsException, IOException {
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
