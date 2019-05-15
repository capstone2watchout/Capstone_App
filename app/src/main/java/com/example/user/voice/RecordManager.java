package com.example.user.voice;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

public class RecordManager {

    static final String TAG = "RecordManager Thread";

    static final int SAMPLING_RATE = 16000;
    static final int FRAME_SIZE = 640; // 40 msec
    static final int FRAME_OVERLAP = 480;  // 30 msec
    static final int FRAME_MOVE = FRAME_SIZE - FRAME_OVERLAP;
    static final int FEATURE_PER_FRAME = 40; // 40 Frame -> 1 feature input
    static final int NUM_MEL_FILTER_BANK = 40;  // 40 dimension Filter bank
    static final int MFCC_PER_FRAME = 40;   // only for mfcc
    static final float LOWER_FILTER_FREQ = 25.0f;
    static final float UPPER_FILTER_FREQ = SAMPLING_RATE / 2;


    private final int SILENCE_THRESHOLD = 3;

    final int ENERGY_THRESHOLD;

    private Handler handler;
    private Model model = null;
    private BoardManager boardManager;
    private MFCC mfcc = null;
    private AudioDispatcher dispatcher = null;

    private int currentFrame = 0;
    private float [] inp = null;
    //private float [] audioFloatBuffer;
    //private byte [] audioByteBuffer;

    RecordManager(Handler handler, final BoardManager boardManager, int threshold, Activity activity) throws IOException {
        this.handler = handler;
        this.boardManager = boardManager;
        ENERGY_THRESHOLD = threshold;
        mfcc = new MFCC(FRAME_SIZE, SAMPLING_RATE, MFCC_PER_FRAME, NUM_MEL_FILTER_BANK, LOWER_FILTER_FREQ, UPPER_FILTER_FREQ);
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(TarsosDSPAudioFormat.Encoding.PCM_SIGNED, SAMPLING_RATE,
                16, 1, 2, SAMPLING_RATE, ByteOrder.BIG_ENDIAN.equals(ByteOrder.nativeOrder()));

        /* 파일로 부터 오디오 데이터 읽을 때 */
        //AssetFileDescriptor afd = activity.getAssets().openFd("bird_000019_08.wav");
        //InputStream is = afd.createInputStream();
        //dispatcher = new AudioDispatcher(new UniversalAudioInputStream(is, format), FRAME_SIZE, FRAME_OVERLAP);

        /* 마이크로 부터 오디오 데이터 읽을 때 */
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLING_RATE,FRAME_SIZE ,FRAME_OVERLAP);

        model = new Model(activity, handler);
        inp = new float[FEATURE_PER_FRAME * NUM_MEL_FILTER_BANK];
        //audioByteBuffer = new byte[FRAME_SIZE];

        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float [] audioFloatBuffer = audioEvent.getFloatBuffer().clone();
                //Log.d("AudioDataFloat1", "Length: " + FRAME_MOVE + " Data: " + Arrays.toString(Arrays.copyOfRange(audioFloatBuffer, 0, FRAME_MOVE)));
                //Log.d("AudioDataFloat2", "Length: " + FRAME_MOVE + " Data: " + Arrays.toString(Arrays.copyOfRange(audioFloatBuffer, FRAME_MOVE, FRAME_MOVE * 2)));
                //Log.d("AudioDataFloat3", "Length: " + FRAME_MOVE + " Data: " + Arrays.toString(Arrays.copyOfRange(audioFloatBuffer, FRAME_MOVE * 2, FRAME_MOVE * 3)));
                //Log.d("AudioDataFloat4", "Length: " + FRAME_MOVE + " Data: " + Arrays.toString(Arrays.copyOfRange(audioFloatBuffer, FRAME_MOVE * 3, FRAME_MOVE * 4)));

                FloatBuffer floatBuffer = FloatBuffer.allocate(FRAME_MOVE);
                floatBuffer.put(audioFloatBuffer, FRAME_OVERLAP, FRAME_MOVE);
                boardManager.setData(floatBuffer, FRAME_MOVE, true);
                //Log.d("AudioDrawData", "Length: " + FRAME_MOVE + " Data: " + Arrays.toString(floatBuffer.array()));

                float bin[] = mfcc.magnitudeSpectrum(audioFloatBuffer);
                //Log.d("Spectrum", "Length: " + bin.length + " Data: " + Arrays.toString(bin));

                float fbank[] = mfcc.melFilter(bin, mfcc.getCenterFrequencies());
                //Log.d("MelFBank", "Length: " + fbank.length + " Data: " + Arrays.toString(fbank));

                //float f[] = mfcc.nonLinearTransformation(fbank).clone();
                //Log.d("FbankLinear", "Length: " + f.length + " Data: " + Arrays.toString(f));

                //float m[] = mfcc.cepCoefficients(f);
                //Log.d("MFCC", "Length: " + m.length + " Data: " + Arrays.toString(m));

                System.arraycopy(fbank, 0, inp, currentFrame * NUM_MEL_FILTER_BANK, NUM_MEL_FILTER_BANK);

                currentFrame++;

                if (currentFrame == FEATURE_PER_FRAME && model != null) {
                    Log.d("MelFBank", "Length: " + inp.length + " Data: " + Arrays.toString(inp));
                    currentFrame = 0;
                    model.runModel(inp);
                    Arrays.fill(inp, 0);
                }

                return false;
            }

            @Override
            public void processingFinished() { }
        });
    }

    AudioDispatcher getDispatcher() {
        return dispatcher;
    }

    public void stop() {
        dispatcher.stop();
        dispatcher = null;
    }
   /* @Override
    public void run() {

        Thread audioThread = new Thread(dispatcher, "Audio Thread");
        audioThread.start();
        int silence = 0;
        boolean isActivated = false;

        while (!Thread.currentThread().isInterrupted()) {

            // Convert byte array to short array
            short[] shorts = new short[data.length / 2];
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

            ShortBuffer sendBuffer = ShortBuffer.allocate(BUFFER_SIZE / 2);
            sendBuffer.position(0);
            sendBuffer.put(shorts, 0, BUFFER_SIZE / 2);

            double energy = CalculateEnergy(sendBuffer);


            if (energy > ENERGY_THRESHOLD) {

                if (!isActivated) {
                    isActivated = true;

                }


                silence = 0;
            }

            else {

                silence++;

                if (silence > SILENCE_THRESHOLD && isActivated) {
                    isActivated = false;
                    silence = 0;

                }
                else if (silence <= SILENCE_THRESHOLD && isActivated) {

                }
            }

            boardManager.setData(sendBuffer, BUFFER_SIZE / 2, isActivated);
        }

        recorder.release();

    }*/

    private double CalculateEnergy(ShortBuffer buffer) {
        double sum = 0;

        for (int i=0; i<buffer.capacity(); i++) {
            sum += buffer.get(i) * buffer.get(i);
        }

        return sum / buffer.capacity();
    }
}

