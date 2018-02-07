package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Lock;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import helper.FakeLazy;
import helper.TestUtils;
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

   @Before
   public void setup() {
      err = mock(PrintStream.class);
      out = mock(PrintStream.class);
      printStreamProvider = mock(PrintStreamProvider.class);
      when(printStreamProvider.err()).thenReturn(err);
      when(printStreamProvider.out()).thenReturn(out);
      settings = mock(Settings.class);
      osDao = mock(OSDao.class);
      shredHelper = new ShredHelper(osDao, new FakeLazy<>(settings), printStreamProvider);
      lock = new Lock(printStreamProvider, osDao, shredHelper);
   }

   private ArgumentCaptor<String> getOutputOfLockTest() throws IOException, SettingsImpl.InvalidSettingNameException,
           SettingsImpl.CorruptSettingsException {
      when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("");
      Process process = mock(Process.class);
      when(process.getInputStream()).thenReturn(TestUtils.getEmptyInputStream());
      ArgumentCaptor<String> stdErr = ArgumentCaptor.forClass(String.class);
      when(osDao.executeCommand(any(String[].class))).thenReturn(process);
      lock.execute(Collections.emptyList());
      return stdErr;
   }

   @Test
   public void testKeyNotFound() throws SettingsImpl.InvalidSettingNameException,
           SettingsImpl.CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(false);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(4)).println(stdErr.capture());
      Assert.assertEquals("MEND did not appear to be unlocked.", stdErr.getAllValues().get(0));
      Assert.assertTrue(stdErr.getAllValues().get(1).contains("Cleaning"));
      Assert.assertTrue(stdErr.getAllValues().get(2).contains("Cleaning"));
      Assert.assertEquals("MEND Locked.", stdErr.getAllValues().get(3));
   }

   @Test
   public void testKeyFoundAndLockFailed() throws SettingsImpl.InvalidSettingNameException,
           SettingsImpl.CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenReturn(true);
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(3)).println(stdErr.capture());
      Assert.assertTrue(stdErr.getAllValues().get(0).contains("Cleaning"));
      Assert.assertTrue(stdErr.getAllValues().get(1).contains("Cleaning"));
      Assert.assertEquals("Locking may have failed, your private key file still exists.", stdErr.getAllValues().get(2));
   }

   @Test
   public void testKeyFoundAndLockPassed() throws SettingsImpl.InvalidSettingNameException,
           SettingsImpl.CorruptSettingsException, IOException {
      when(osDao.fileExists(any(File.class))).thenAnswer(new Answer<Boolean>() {
         int count = 0;
         @Override
         public Boolean answer(InvocationOnMock invocation) throws Throwable {
            if (count++ > 0) return false;
            else return true;
         }
      });
      ArgumentCaptor<String> stdErr = getOutputOfLockTest();
      verify(err, times(3)).println(stdErr.capture());
      Assert.assertTrue(stdErr.getAllValues().get(0).contains("Cleaning"));
      Assert.assertTrue(stdErr.getAllValues().get(1).contains("Cleaning"));
      Assert.assertEquals("MEND Locked.", stdErr.getAllValues().get(2));
   }
}
