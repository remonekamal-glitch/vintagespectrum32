package com.vintage.spectrum32;

import android.app.*;\nimport android.app.Activity;
import android.content.*;
import android.graphics.PixelFormat;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpectrumService extends Service {
    public static final String ACTION_START = "com.vintage.spectrum32.START";
    private static final int NOTIFICATION_ID = 3201;
    private WindowManager wm;
    private SpectrumView visualizer;
    private WindowManager.LayoutParams params;
    private AudioRecord recorder;
    private Thread audioThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MediaProjection projection;
    private int sensitivity = 100, brightness = 85;\n    private int inputChannels = 1;

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_START.equals(intent.getAction())) {
            startForeground(NOTIFICATION_ID, notification());
            int mode = intent.getIntExtra("mode",0);
            sensitivity = intent.getIntExtra("sensitivity",100);
            brightness = intent.getIntExtra("brightness",85);
            showOverlay(mode);
            int source = intent.getIntExtra("source",0);
            stopAudio();
            if (source == 1) {
                Intent data = intent.getParcelableExtra("projection_data");
                int result = intent.getIntExtra("projection_result", Activity.RESULT_CANCELED);
                if (data != null && result == Activity.RESULT_OK) startPlaybackCapture(result, data);
            } else startMicrophone();
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("spectrum32", "Vintage Spectrum", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this,"spectrum32") : new Notification.Builder(this);
        return b.setContentTitle("Vintage Spectrum 32")
                .setContentText("Floating 32-channel visualizer is running")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).build();
    }

    private void showOverlay(int mode) {
        if (visualizer != null) { try { wm.removeView(visualizer); } catch(Exception ignored) {} }
        visualizer = new SpectrumView(this);
        visualizer.setMode(mode); visualizer.setBrightness(brightness);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(340, 145, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 0; params.y = 180;
        visualizer.setWindowGestureListener(new SpectrumView.WindowGestureListener() {
            @Override public void onDrag(float dx, float dy) {
                params.x += (int)dx; params.y += (int)dy;
                try { wm.updateViewLayout(visualizer, params); } catch (Exception ignored) {}
            }
            @Override public void onResize(float factor) {
                params.width = Math.max(220, Math.min(900, (int)(params.width * factor)));
                params.height = Math.max(90, Math.min(420, (int)(params.height * factor)));
                try { wm.updateViewLayout(visualizer, params); } catch (Exception ignored) {}
            }
        });
        try { wm.addView(visualizer, params); } catch (Exception ignored) {}
    }

    private void startMicrophone() {
        int min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return;
        inputChannels = 1;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min,4096));
        startAnalyzer();
    }

    private void startPlaybackCapture(int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(resultCode, data);
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build();
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            int min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            inputChannels = 2;
            recorder = new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(min,8192))
                    .setAudioPlaybackCaptureConfig(config).build();
            startAnalyzer();
        } catch (Exception ignored) { stopSelf(); }
    }

    private void startAnalyzer() {
        if (recorder == null || recorder.getState()!=AudioRecord.STATE_INITIALIZED) { stopSelf(); return; }
        running.set(true);
        recorder.startRecording();
        audioThread = new Thread(() -> {
            short[] buffer = new short[2048];
            float[] bands = new float[32];
            while (running.get()) {
                int n = recorder.read(buffer,0,buffer.length);
                if (n > 0) {
                    calculateFftBands(buffer,n,bands,inputChannels);
                    if (visualizer != null) visualizer.setLevels(bands);
                }
            }
        }, "SpectrumAudio");
        audioThread.start();
    }

    // 512-point radix-2 FFT mapped into 32 logarithmically spaced frequency bands.
    private void calculateFftBands(short[] pcm, int count, float[] out, int channels) {
        final int n = 512;
        double[] re = new double[n];
        double[] im = new double[n];
        int available = Math.min(n, count / Math.max(1, channels));
        for (int i=0; i<available; i++) {
            double sample = pcm[i * channels] / 32768.0;
            double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
            re[i] = sample * window;
        }
        // Bit-reversal permutation.
        int j=0;
        for (int i=1; i<n; i++) {
            int bit=n>>1;
            while ((j & bit)!=0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i<j) {
                double tr=re[i]; re[i]=re[j]; re[j]=tr;
            }
        }
        for (int len=2; len<=n; len<<=1) {
            double angle = -2.0 * Math.PI / len;
            double wLenRe=Math.cos(angle), wLenIm=Math.sin(angle);
            for (int i=0; i<n; i+=len) {
                double wr=1, wi=0;
                for (int k=0; k<len/2; k++) {
                    int even=i+k, odd=i+k+len/2;
                    double vr=re[odd]*wr-im[odd]*wi;
                    double vi=re[odd]*wi+im[odd]*wr;
                    re[odd]=re[even]-vr; im[odd]=im[even]-vi;
                    re[even]+=vr; im[even]+=vi;
                    double nextWr=wr*wLenRe-wi*wLenIm;
                    wi=wr*wLenIm+wi*wLenRe; wr=nextWr;
                }
            }
        }
        for (int band=0; band<32; band++) {
            int low=Math.max(1,(int)Math.pow(n/2.0, band/32.0));
            int high=Math.max(low+1,(int)Math.pow(n/2.0, (band+1)/32.0));
            high=Math.min(n/2,high);
            double peak=0;
            for (int bin=low; bin<=high; bin++) {
                double mag=Math.sqrt(re[bin]*re[bin]+im[bin]*im[bin]) / (n/2.0);
                if (mag>peak) peak=mag;
            }
            float level=(float)Math.min(1.0, peak * sensitivity * 2.8);
            out[band]=Math.max(out[band]*0.76f, level);
        }
    }

    private void stopAudio() {
        running.set(false);
        if (audioThread != null) { try { audioThread.join(250); } catch(InterruptedException ignored) {} audioThread=null; }
        if (recorder != null) { try { recorder.stop(); } catch(Exception ignored) {} recorder.release(); recorder=null; }
        if (projection != null) { try { projection.stop(); } catch(Exception ignored) {} projection=null; }
    }

    @Override public void onDestroy() {
        stopAudio();
        if (visualizer != null) { try { wm.removeView(visualizer); } catch(Exception ignored) {} visualizer=null; }
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
