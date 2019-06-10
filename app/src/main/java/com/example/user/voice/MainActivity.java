package com.example.user.voice;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

public class MainActivity extends Activity {

    /* For Debug */
    static final String TAG = "MainActivity";

    RecordManager recordManager;
    TextManager textManager;

    boolean isRecording = false;

    int THRESHOLD = 1000000;
    int THRESHOLD_TIME = 30;

    Thread audioThread;

    private static final int REQUEST_RECORD_AUDIO = 13;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ImageView toolTip = (ImageView) findViewById(R.id.toolTipImageView);

        toolTip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ToolTipActivity.class);
                startActivity(intent);
            }
        });

        final ImageView button1 = (ImageView) findViewById(R.id.calibrateImageView);
        final ImageView button2 = (ImageView) findViewById(R.id.micImageView);
        final TextView textView = (TextView) findViewById(R.id.mainTextView);
        final BoardManager boardManager = (BoardManager) findViewById(R.id.boardManagerView);

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                super.handleMessage(message);
                textView.setText(message.obj.toString());
            }
        };
//
//
//        /* RECORDER  permission */
        requestMicrophonePermission();
//
//        textView.setTextSize(40);
        textView.setMovementMethod(new ScrollingMovementMethod());
//
//        button1.setImageResource(R.drawable.button_default);
//        button2.setImageResource(R.drawable.button_threshold);
//
        try {
            recordManager = new RecordManager(handler, boardManager, THRESHOLD, MainActivity.this);
        } catch (IOException e) {
            Log.e(TAG, "Create Model Failed");
        }
//
        audioThread = new Thread(recordManager.getDispatcher(), "Audio Thread");
//
        button1.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRecording) {
                    isRecording = true;
                    textManager = new TextManager(handler);

                    button1.setImageResource(R.drawable.button_pressed);
                    Toast.makeText(getApplicationContext(), "Start recording", Toast.LENGTH_SHORT).show();

                    audioThread.start();
                }
                else {
                    isRecording = false;
                    button1.setImageResource(R.drawable.button_default);
                    Toast.makeText(getApplicationContext(), "Stop recording", Toast.LENGTH_SHORT).show();

                    recordManager.stop();
                }
                view.invalidate();
            }
        });
//
        button2.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View view) {

                AudioRecord audioRecord = null;

                audioRecord =  new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RecordManager.SAMPLING_RATE, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, RecordManager.FRAME_SIZE);

                byte[] data = new byte[RecordManager.FRAME_SIZE];

                audioRecord.startRecording();

                double totalEnergy = 0;

                for (int i = 0; i < THRESHOLD_TIME; i++) {
                    audioRecord.read(data, 0, RecordManager.FRAME_SIZE);

                    // Convert byte array to short array
                    short[] shorts = new short[data.length / 2];
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

                    ShortBuffer sendBuffer = ShortBuffer.allocate(RecordManager.FRAME_SIZE / 2);
                    sendBuffer.position(0);
                    sendBuffer.put(shorts, 0, RecordManager.FRAME_SIZE / 2);

                    if (i > RecordManager.SAMPLING_RATE * 2 / RecordManager.FRAME_SIZE) {
                        double energy = CalculateEnergy(sendBuffer);
                        totalEnergy += energy;

                        audioRecord.release();
                    }
                }

                THRESHOLD = (int) (totalEnergy / 20);

                Toast.makeText(getApplicationContext(), "Threshold " +
                        (int) (Math.sqrt(THRESHOLD)), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestMicrophonePermission() {
        requestPermissions(
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        }
    }

    private double CalculateEnergy(ShortBuffer buffer) {
        double sum = 0;

        for (int i = 0; i < buffer.capacity(); i++) {
            sum += buffer.get(i) * buffer.get(i);
        }

        return sum / buffer.capacity();
    }
}


