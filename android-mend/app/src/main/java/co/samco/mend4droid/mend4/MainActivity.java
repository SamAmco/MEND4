package co.samco.mend4droid.mend4;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.Settings;

public class MainActivity extends AppCompatActivity implements TextWatcher {
    private final int PERMISSIONS_REQUEST_READ_STORAGE = 1;
    private final int PERMISSIONS_REQUEST_WRITE_STORAGE = 2;

    AutoCompleteTextView autoCompleteTextView;
    EditText editText;
    private boolean initialized = false;

    private String mendDir = "";
    private String currentLog = "";

    private boolean checkSettingsPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_STORAGE);

            return false;
        }
        return true;
    }

    private boolean checkWritePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_STORAGE);

            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!initialized)
                        tryInitializeSettings();
                }
                return;
            }
            case PERMISSIONS_REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //just double check the mend dir is there.. although it should be because it needs
                    //a settings file to work anyway.
                    (new File(mendDir)).mkdirs();
                }
                return;
            }
        }
    }

    private void tryInitializeSettings() {
        if (!initialized && checkSettingsPermissions()) {
            try {
                Settings.InitializeSettings(new AndroidSettings(mendDir));
                indexLogFiles();
                currentLog = Settings.instance().getValue(Config.Settings.CURRENTLOG);
                autoCompleteTextView.setText(currentLog);
                initialized = true;
            } catch (Exception e) {
                Log.wtf("MainActivity", e.getMessage());
            }
        }
    }

    private void indexLogFiles() {
        File mendDirFile = new File(mendDir);
        File[] files = mendDirFile.listFiles();
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < files.length; ++i) {
            String name = files[i].getName();
            if (name.endsWith(".mend"))
                names.add(name.substring(0, name.length() - 5));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line,
                names);
        autoCompleteTextView.setAdapter(adapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setTitle("MEND4." + AndroidSettings.ANDROID_VERSION);
        autoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView);
        autoCompleteTextView.addTextChangedListener(this);
        editText = (EditText) findViewById(R.id.entryText);
        mendDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MEND4/";
        tryInitializeSettings();
    }

    //File button callback
    public void onFileButton(View view) {
        if (!doubleCheckPermissions(view))
            return;

        final Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        //to get image and videos, I used a */"
        fileIntent.setType("*/*");
        fileIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(fileIntent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Encrypt selected file
        if (requestCode == 1 && resultCode == RESULT_OK) {
            FileInputStream fis = null;
            try {
                FileOutputStream fos = null;
                try {
                    Uri uri = data.getData();
                    ContentResolver cR = getContentResolver();
                    MimeTypeMap mime = MimeTypeMap.getSingleton();
                    String fileExtension = mime.getExtensionFromMimeType(cR.getType(uri));
                    fis = (FileInputStream) cR.openInputStream(uri);
                    String name = new SimpleDateFormat("yyyyMMddHHmmssSS").format(new Date());
                    File file = new File(mendDir + "/Enc", name + ".enc");
                    file.getParentFile().mkdirs();
                    //TODO check if file exists (which probably needs doing when we enable encrypting multiple files
                    // at once)
                    fos = new FileOutputStream(file);
                    EncryptionUtils.encryptFileToStream(new AndroidEncodingProvider(), fis, fos, fileExtension);
                    Toast toast = Toast.makeText(this.getApplicationContext(), "File encryption complete", Toast
                            .LENGTH_SHORT);
                    toast.show();
                    editText.append(name);
                } catch (Exception e) {
                    Log.wtf("MainActivity", e.getMessage());
                    Toast toast = Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
                } finally {
                    try {
                        if (fos != null)
                            fos.close();
                    } catch (Exception e) {
                    }
                }
            } finally {
                try {
                    if (fis != null)
                        fis.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private boolean doubleCheckPermissions(View view) {
        if (!initialized) {
            Toast toast = Toast.makeText(view.getContext(), "Could not read from storage.", Toast.LENGTH_LONG);
            toast.show();
            tryInitializeSettings();
            return false;
        }
        if (!checkWritePermissions()) {
            Toast toast = Toast.makeText(view.getContext(), "Could not write to storage.", Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
        return true;
    }

    //Submit button callback
    public void onSubmitButton(View view) {
        if (!doubleCheckPermissions(view))
            return;

        String logText = editText.getText().toString();
        if (logText == null || logText.length() == 0) {
            Toast toast = Toast.makeText(view.getContext(), "Nothing to log.", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        FileOutputStream fos = null;
        try {
            Settings.instance().setValue(Config.Settings.CURRENTLOG, currentLog);
            File file = new File(mendDir, currentLog + ".mend");
            file.createNewFile();
            fos = new FileOutputStream(file, true);
            EncryptionUtils.encryptLogToStream(new AndroidEncodingProvider(), fos, logText.toCharArray(), false);
            editText.getText().clear();
            indexLogFiles();
        } catch (Exception e) {
            Log.wtf("MainActivity", e.getMessage());
            Toast toast = Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (Exception e) {
                Log.wtf("MainActivity", e.getMessage());
            }
        }
    }


    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        currentLog = autoCompleteTextView.getText().toString();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    private class AndroidEncodingProvider implements IBase64EncodingProvider {
        public byte[] decodeBase64(String s) {
            return Base64.decode(s, Base64.URL_SAFE);
        }

        public String encodeBase64URLSafeString(byte[] bytes) {
            return Base64.encodeToString(bytes, Base64.URL_SAFE);
        }
    }
}
