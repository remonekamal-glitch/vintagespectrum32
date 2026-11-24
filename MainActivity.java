package com.vintage.spectrum32;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 10;
    private static final int REQ_AUDIO = 11;
    private static final int REQ_CAPTURE = 12;
    private Spinner modeSpinner, inputSpinner;
    private SeekBar sensitivity, brightness;
    private TextView status;
    private int pendingInput = 0; // 0 microphone, 1 internal playback

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 24, 24, 24);
        page.setBackgroundColor(Color.rgb(18,18,22));
        scroll.addView(page);

        TextView title = label("VINTAGE SPECTRUM 32", 25, Color.WHITE);
        page.addView(title);
        page.addView(label("32-channel floating LED visualizer · Java 17", 14, 0xFFBBBBBB));

        page.addView(label("VISUALIZER MODE", 14, 0xFF66D9EF));
        modeSpinner = new Spinner(this);
        String[] modes = {"1 · Classic Winamp LED", "2 · Rainbow Mirror", "3 · Red & Blue Beat", "4 · Vintage Stereo VU", "5 · Retro Neon Fire"};
        modeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        page.addView(modeSpinner);

        page.addView(label("AUDIO INPUT", 14, 0xFF66D9EF));
        inputSpinner = new Spinner(this);
        inputSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Microphone", "Internal playback (Android 10+, capture-permitted apps)"}));
        page.addView(inputSpinner);

        page.addView(label("Sensitivity", 14, Color.WHITE));
        sensitivity = new SeekBar(this); sensitivity.setMax(200); sensitivity.setProgress(100); page.addView(sensitivity);
        page.addView(label("Brightness", 14, Color.WHITE));
        brightness = new SeekBar(this); brightness.setMax(100); brightness.setProgress(85); page.addView(brightness);

        Button start = button("START FLOATING VISUALIZER");
        start.setOnClickListener(v -> startSelected());
        page.addView(start);
        Button stop = button("STOP VISUALIZER");
        stop.setOnClickListener(v -> stopService(new Intent(this, SpectrumService.class)));
        page.addView(stop);
        status = label("Ready. Grant overlay permission when Android asks.", 13, 0xFFFFD166);
        page.addView(status);
        page.addView(label("Tip: drag the floating panel to move it. Pinch with two fingers to resize it.", 13, 0xFFBBBBBB));
        setContentView(scroll);
    }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, 14, 0, 8); return t;
    }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); return b;
    }

    private void startSelected() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return;
        }
        pendingInput = inputSpinner.getSelectedItemPosition();
        if (pendingInput == 1) {
            MediaProjectionManager mpm = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
        } else {
            launchService(0, null, null);
        }
    }

    private void launchService(int source, Intent projectionData, Integer resultCode) {
        Intent i = new Intent(this, SpectrumService.class);
        i.setAction(SpectrumService.ACTION_START);
        i.putExtra("source", source);
        i.putExtra("mode", modeSpinner.getSelectedItemPosition());
        i.putExtra("sensitivity", Math.max(20, sensitivity.getProgress()));
        i.putExtra("brightness", brightness.getProgress());
        if (projectionData != null) {
            i.putExtra("projection_data", projectionData);
            i.putExtra("projection_result", resultCode);
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("Visualizer started. Switch to JetAudio or another app.");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) startSelected();
            else status.setText("Overlay permission is required.");
        } else if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) launchService(1, data, resultCode);
            else status.setText("Internal audio capture was cancelled. Try microphone input.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startSelected();
        } else if (requestCode == REQ_AUDIO) status.setText("Microphone permission is required for audio analysis.");
    }
}
