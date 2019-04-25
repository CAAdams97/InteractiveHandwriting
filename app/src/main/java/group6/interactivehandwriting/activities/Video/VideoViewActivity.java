package group6.interactivehandwriting.activities.Video;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import group6.interactivehandwriting.R;
import group6.interactivehandwriting.common.app.Permissions;
import group6.interactivehandwriting.common.network.NetworkLayer;
import group6.interactivehandwriting.common.network.NetworkLayerBinder;
import group6.interactivehandwriting.common.network.NetworkLayerService;
import group6.interactivehandwriting.common.network.nearby.connections.message.NetworkMessageType;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.SerialMessageHeader;


public class VideoViewActivity extends AppCompatActivity {

    NetworkLayer networkLayer;
    ServiceConnection networkServiceConnection;
    public ImageView imageView;
    ArrayList<Byte> byteArrayList;

    private int frameCount = 0;
    private long curStartTime = -1;
    private final float CHECK_FPS_INTERVAL = 4.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.video_view_layout);

        imageView = findViewById(R.id.imageView);

        networkServiceConnection = getNetworkServiceConnection();
        byteArrayList = new ArrayList<>();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Permissions.requestPermissions(this);
        NetworkLayerService.startNetworkService(this);
        NetworkLayerService.bindNetworkService(this, networkServiceConnection);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(networkServiceConnection);
    }

    private ServiceConnection getNetworkServiceConnection() {
        return new ServiceConnection()
        {
            @Override
            public void onServiceConnected (ComponentName name, IBinder service){
                NetworkLayerBinder binder = (NetworkLayerBinder) service;
                networkLayer = binder.getNetworkLayer();
                handleNetworkStarted();
            }

            @Override
            public void onServiceDisconnected (ComponentName name) {
            }
        };
    }

    private void handleNetworkStarted() {
        networkLayer.setVideoViewActivity(this);
    }

    public void showVideo(SerialMessageHeader header, byte[] frameBytes, String username) {
        if (curStartTime == -1) {
            curStartTime = java.lang.System.currentTimeMillis();
        }

        if (header.getBigData() == (byte) 0) {
            Bitmap bmp = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
            imageView.setImageBitmap(bmp);
            frameCount++;
            checkTimeSendFPS();
        }
        else if (header.getBigData() == (byte) 1) {
            for (int i = 0; i < frameBytes.length; i++) {
                byteArrayList.add(frameBytes[i]);
            }
        }
        else if (header.getBigData() == (byte) 2) {
            for (int i = 0; i < frameBytes.length; i++) {
                byteArrayList.add(frameBytes[i]);
            }
            byte[] b = new byte[byteArrayList.size()];
            for (int i = 0; i < b.length; i++) {
                b[i] = byteArrayList.get(i);
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
            imageView.setImageBitmap(bmp);
            frameCount++;
            checkTimeSendFPS();
            byteArrayList.clear();
        }

    }

    public void endViewing() {
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        curStartTime = -1;
    }

    private float getFPS() {
        return (float) (frameCount / ((java.lang.System.currentTimeMillis() - curStartTime) / 1000.0));
    }

    private void checkTimeSendFPS() {
        float timeDifference = (float) ((java.lang.System.currentTimeMillis() - curStartTime) / 1000.0);
        if (timeDifference >= CHECK_FPS_INTERVAL) {
            networkLayer.sendBytes(new byte[] {(byte) getFPS()}, NetworkMessageType.RECEIVER_FPS);
            curStartTime = java.lang.System.currentTimeMillis();
            frameCount = 0;
        }
    }

}