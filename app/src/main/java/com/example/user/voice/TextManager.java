package com.example.user.voice;

import android.os.Handler;
import android.os.Message;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class TextManager {

    static final String TAG = "TextManager Thread";

    private final int MAX_BUFFER_SIZE = 1024;

    private Socket socket_recv;

    private final String IP = "163.239.22.105";
    private final int RECV = 6000;


    private Handler handler;

    TextManager(Handler handler) {
        this.handler = handler;
    }

    void setText(String res) {
        Message message = handler.obtainMessage();
        message.obj = res;
        handler.sendMessage(message);
    }
}
