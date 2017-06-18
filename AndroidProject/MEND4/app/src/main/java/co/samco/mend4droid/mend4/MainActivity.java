package co.samco.mend4droid.mend4;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    //Submit button callback
    public void onSubmit(View view)
    {
        EditText editText = (EditText) findViewById(R.id.entryText);
        String logText = editText.getText().toString();

    }
}
