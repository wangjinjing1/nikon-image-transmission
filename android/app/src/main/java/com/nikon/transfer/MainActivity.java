package com.nikon.transfer;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NikonCameraPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
