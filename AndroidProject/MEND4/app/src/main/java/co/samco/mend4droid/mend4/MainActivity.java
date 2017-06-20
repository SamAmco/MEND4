package co.samco.mend4droid.mend4;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.Settings;

public class MainActivity extends AppCompatActivity
{
    private final int PERMISSIONS_REQUEST_READ_STORAGE = 1;
    private final int PERMISSIONS_REQUEST_WRITE_STORAGE = 2;

    private boolean initialized = false;

    private String mendDir = "";

    private boolean checkSettingsPermissions()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_STORAGE);

            return false;
        }
        return true;
    }

    private boolean checkWritePermissions()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_STORAGE);

            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        Log.wtf("MainActivity", "onRequestPermissionsResult");
        switch (requestCode)
        {
            case PERMISSIONS_REQUEST_READ_STORAGE:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.wtf("MainActivity", "granted");
                    if (!initialized)
                        tryInitializeSettings();
                }
                return;
            }
            case PERMISSIONS_REQUEST_WRITE_STORAGE:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //just double check the mend dir is there.. although it should be because it needs
                    //a settings file to work anyway.
                    (new File(mendDir)).mkdirs();
                    Log.wtf("MainActivity", "granted");
                }
                else
                    Log.wtf("MainActivity", "failed");

                return;
            }
        }
    }

    private void tryInitializeSettings()
    {
        if (!initialized && checkSettingsPermissions())
        {
            try
            {
                Settings.InitializeSettings(new AndroidSettings(mendDir));
                initialized = true;
            }
            catch (Exception e)
            {
                Log.wtf("MainActivity", e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mendDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MEND4/";
        tryInitializeSettings();
    }

    //Submit button callback
    public void onSubmit(View view)
    {
        if (!initialized)
        {
            Toast toast = Toast.makeText(view.getContext(), "Could not read from storage.", Toast.LENGTH_LONG);
            toast.show();
            tryInitializeSettings();
            return;
        }
        if (!checkWritePermissions())
        {
            Toast toast = Toast.makeText(view.getContext(), "Could not write to storage.", Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        EditText editText = (EditText) findViewById(R.id.entryText);
        String logText = editText.getText().toString();
        if (logText == null || logText.length() == 0)
        {
            Toast toast = Toast.makeText(view.getContext(), "Nothing to log.", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        FileOutputStream fos = null;
        try
        {
            File file = new File(mendDir, "Log.mend");
            file.createNewFile();
            fos = new FileOutputStream(file, true);
            EncryptionUtils.encryptLogToStream(new AndroidEncodingProvider(), fos, logText.toCharArray(), false);
            editText.getText().clear();
        }
        catch (Exception e)
        {
            Log.wtf("MainActivity", e.getMessage());
            Toast toast = Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
        finally
        {
            try
            {
                if (fos != null)
                    fos.close();
            }
            catch (Exception e)
            {
                Log.wtf("MainActivity", e.getMessage());
            }
        }
    }

    private class AndroidEncodingProvider implements IBase64EncodingProvider
    {
        public byte[] decodeBase64(String s)
        {
            return Base64.decode(s, Base64.URL_SAFE);
        }

        public String encodeBase64URLSafeString(byte[] bytes)
        {
            return Base64.encodeToString(bytes, Base64.URL_SAFE);
        }
    }
}
