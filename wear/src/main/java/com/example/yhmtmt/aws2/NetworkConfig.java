package com.example.yhmtmt.aws2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class NetworkConfig extends WearableActivity implements View.OnClickListener{

    EditText editAddr;
    EditText editPort;

    Button btnConfig;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_config);


        editAddr = (EditText) findViewById(R.id.editAddress);
        editAddr.setText(AppData.getInstance().addr, TextView.BufferType.NORMAL);
        editPort = (EditText) findViewById(R.id.editPort);
        editPort.setText(Integer.toString(AppData.getInstance().port), TextView.BufferType.NORMAL);

        btnConfig = (Button) findViewById(R.id.config);
        btnConfig.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        Button btn = (Button) v;
        switch(btn.getId()){
            case R.id.config:
                SpannableStringBuilder editor = (SpannableStringBuilder) editAddr.getText();
                AppData.getInstance().addr = editor.toString();
                editor = (SpannableStringBuilder) editPort.getText();
                AppData.getInstance().port = Integer.parseInt(editor.toString());
                finish();
        }
    }

}
