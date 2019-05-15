package com.example.user.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class BoardManager extends SurfaceView implements SurfaceHolder.Callback {

    final static String TAG = "BoardManager";

    final int MAX_16BIT = 1;            // Max value of short
    final int SAMPLING_RATE = RecordManager.SAMPLING_RATE;        // Use 16000Hz sample
    final int CHUNK = SAMPLING_RATE / 100;   // CHUNK = 10 msec
    final int MAX_SIZE = 50;                // Max size of graph = 0.5sec

    int screenWidth, screenHeight;
    int boardWidth, boardHeight;
    int boardMiddleWidth, boardMiddleHeight;
    int boardStartX, boardStartY;
    int boardEndX, boardEndY;

    Canvas canvas;

    // Audio Data
    double ratioX, ratioY;
    int timeDiv, maxHeight;
    int dataLength;
    int count = 0;

    FloatBuffer mBuffer, tempBuffer, readBuffer;
    boolean[] activation;

    SurfaceHolder mHolder;

    public BoardManager(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    public BoardManager(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);
        init();
    }

    public BoardManager(Context context) {
        super(context);
        init();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mHolder = holder;
        getScreenInfo();
        canvas = holder.lockCanvas();
        drawBoard();
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { }

    void init() {
        SurfaceHolder mHolder = getHolder();
        mHolder.addCallback(this);

        mBuffer = FloatBuffer.allocate(CHUNK * MAX_SIZE);
        tempBuffer = FloatBuffer.allocate(CHUNK * MAX_SIZE);
        readBuffer = FloatBuffer.allocate(CHUNK);

        timeDiv = MAX_SIZE * 100;
        maxHeight = MAX_16BIT;

        activation = new boolean[MAX_SIZE];
    }

    void getScreenInfo() {
        screenWidth = getWidth();
        screenHeight = getHeight();

        boardWidth = (int) (screenWidth * 0.9);
        boardHeight = (int) (screenHeight * 0.9);

        boardStartX = (screenWidth - boardWidth) / 2;
        boardStartY = (screenHeight - boardHeight) / 2;

        boardEndX = boardStartX + boardWidth;
        boardEndY = boardStartY + boardHeight;

        boardMiddleWidth = boardWidth / 2;
        boardMiddleHeight = boardHeight / 2;

        ratioY = (boardHeight) / (maxHeight * 2.0);
        ratioX = (boardWidth) * MAX_SIZE * 100 / (double) (timeDiv);
    }

    void drawBoard() {
        Paint paint = new Paint();

        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, screenWidth, screenHeight, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
    }

    void setData(FloatBuffer buffer, int length, boolean isActivated) {
        readBuffer = buffer;
        dataLength = length;

        canvas = mHolder.lockCanvas();

        drawBoard();
        drawData(isActivated);

        mHolder.unlockCanvasAndPost(canvas);
    }

    void drawData(boolean isActivated) {
        double data, sTime, tTime;

        Paint paint = new Paint();

        paint.setStrokeWidth(3);

        count++;

        sTime = 1.0 / (CHUNK * MAX_SIZE);
        tTime = timeDiv / (sTime * MAX_SIZE * 100);

        if (count < MAX_SIZE) {
            mBuffer.position(count * CHUNK);
            mBuffer.put(readBuffer.array(), 0, CHUNK);
            activation[count] = isActivated;
        }

        else {
            count--;

            // Copy mBuffer (0.1sec ~ 5.0sec) to tempBuffer (0.0sec ~ 4.9sec)
            tempBuffer.position(0);
            tempBuffer.put(mBuffer.array(), CHUNK * 1, mBuffer.capacity() - CHUNK);

            // Copy readBuffer(new CHUNK) to tempBuffer (4.9sec ~ 5.0sec)
            tempBuffer.position(count * CHUNK);
            tempBuffer.put(readBuffer.array(), 0, CHUNK);

            // Copy tempBuffer to mBuffer
            mBuffer.position(0);
            mBuffer.put(tempBuffer.array(), 0, tempBuffer.capacity());

            System.arraycopy(activation, 1, activation, 0, MAX_SIZE - 1);
            activation[MAX_SIZE-1] = isActivated;
        }

        for (int i=0; i<tTime-1; i++) {

            if (activation[i/CHUNK])
                paint.setColor(Color.RED);
            else
                paint.setColor(Color.YELLOW);

            int prevX = (int) ((i + 1) * sTime * ratioX) + boardStartX;
            int nextX = (int) ((i + 2) * sTime * ratioX) + boardStartX;

            data = -ratioY * mBuffer.get(i);
            int prevY = boardMiddleHeight + (int) data + boardStartY;

            data = -ratioY * mBuffer.get(i + 1);
            int nextY = boardMiddleHeight + (int) data + boardStartY;

            canvas.drawLine(prevX, prevY, nextX, nextY, paint);
        }
    }
}
