package com.nikon.transfer;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;
import androidx.core.view.WindowCompat;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        registerPlugin(NikonCameraPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
