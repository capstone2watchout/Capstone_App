package com.example.user.voice;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.util.Log;

public class Model {
    private static String TAG = "TF_Model";

    private static final String LABEL_PATH = "labels.txt";
    private static final String MODEL_PATH = "frozen_saved_model.tflite";

    // 캡스톤 디자인용으로 30개 class중에서 선택해서 사용
    private String[] usedClass = {"car_horn", "dog_bark", "jackhammer", "siren", "baby_cry", "street_music"};
    private Interpreter tflite;

    private List<String> labelList = null;
    private float labelProbArray[][] = new float[1][30];

    private TextManager textManager;

    Model(Activity activity, Handler handler) throws IOException {
        tflite = new Interpreter(loadModelFile(activity, MODEL_PATH));
        labelList = loadLabelList(activity);
        textManager = new TextManager(handler);
    }


    public void runModel(float[] inp) {

        tflite.run(inp, labelProbArray);
        boolean flag = false;
        String result = null;
        float prob = 0;

        for (int i=0; i<labelList.size(); i++) {
            if (prob < labelProbArray[0][i]) {
                prob = labelProbArray[0][i];
                result = labelList.get(i);
            }
        }
        Log.d("", prob + ":" + result);

        for (int i=0; i<usedClass.length; i++) {
            if (usedClass[i].equals(result)) {
                if (usedClass[i].equals("dog_bark") && prob > 0.5) {
                    textManager.setText(result);
                    flag = true;
                }
                else if (!usedClass[i].equals("dog_bark")) {
                    textManager.setText(result);
                    flag = true;
                }
                break;
            }
        }

        if (!flag)
            textManager.setText("");

    }
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(Activity activity) throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    public void close() {
        tflite.close();
        tflite = null;
    }
}
