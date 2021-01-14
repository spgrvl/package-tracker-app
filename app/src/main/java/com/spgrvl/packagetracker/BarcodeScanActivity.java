package com.spgrvl.packagetracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class BarcodeScanActivity extends AppCompatActivity{

    private SurfaceView surfaceView;
    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private ToneGenerator toneGen;

    public static final int REQUEST_CAMERA_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scan);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        surfaceView = findViewById(R.id.surface_view);
        initialiseDetectorsAndSources();
    }

    private void initialiseDetectorsAndSources() {

        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build();

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true)
                .build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (ContextCompat.checkSelfPermission(BarcodeScanActivity.this, Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED) {

                        // Show information if Camera Permission got declined
                        if (ActivityCompat.shouldShowRequestPermissionRationale(BarcodeScanActivity.this,
                                Manifest.permission.CAMERA)) {
                            Toast.makeText(BarcodeScanActivity.this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            // Request Camera Permission
                            ActivityCompat.requestPermissions(BarcodeScanActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    } else {
                        cameraSource.start(surfaceView.getHolder());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if(barcodes.size() > 0){

                    // play sound
                    toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 50);

                    // stop scanning for new barcodes
                    barcodeDetector.release();

                    // create array and store barcodes
                    String[] barcodesArray = new String[barcodes.size()];

                    for (int index = 0; index < barcodes.size(); index++) {
                        barcodesArray[index] = barcodes.valueAt(index).displayValue;
                    }

                    Intent myIntent = new Intent(BarcodeScanActivity.this, MainActivity.class);
                    myIntent.putExtra("barcodesArray", barcodesArray);
                    startActivity(myIntent);

                    finish();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSupportActionBar().hide();
        cameraSource.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportActionBar().hide();
        initialiseDetectorsAndSources();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission granted, recreating activity
                recreate();
            }
        }
    }
}
