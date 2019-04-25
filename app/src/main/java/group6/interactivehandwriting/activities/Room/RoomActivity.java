package group6.interactivehandwriting.activities.Room;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import group6.interactivehandwriting.R;

import android.widget.SeekBar;
import android.widget.Toast;

import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;
import com.skydoves.colorpickerview.sliders.AlphaSlideBar;
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import group6.interactivehandwriting.activities.Room.views.DocumentView;
import group6.interactivehandwriting.activities.Room.views.RoomView;
import group6.interactivehandwriting.activities.Video.ScreenShareService;
import group6.interactivehandwriting.activities.Video.VideoMenuActivity;
import group6.interactivehandwriting.common.app.Permissions;
import group6.interactivehandwriting.common.network.NetworkLayer;
import group6.interactivehandwriting.common.network.NetworkLayerBinder;
import group6.interactivehandwriting.common.network.NetworkLayerService;
import group6.interactivehandwriting.common.network.nearby.connections.NCNetworkConnection;
import group6.interactivehandwriting.common.network.nearby.connections.NCNetworkLayerService;
import group6.interactivehandwriting.common.network.nearby.connections.message.NetworkMessageType;


public class RoomActivity extends AppCompatActivity {
    private RoomView roomView;
    private SeekBar seekbar;
    private ColorPickerView color_picker_view;
    private Context context;
    private DocumentView documentView;
    private ConstraintLayout roomLayout;

    private boolean resizeToggle;

    NetworkLayer networkLayer;
    ServiceConnection networkServiceConnection;
    private NCNetworkConnection ncNetworkConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        getRoomName(savedInstanceState);

        networkServiceConnection = getNetworkServiceConnection();

        setContentView(R.layout.room_layout);
        roomLayout = findViewById(R.id.roomView_layout);
        roomLayout.setBackgroundColor(Color.WHITE);

        roomView = new RoomView(context);
        roomLayout.addView(roomView);
        roomLayout.bringChildToFront(roomView);

        documentView = findViewById(R.id.documentView);

        roomView.setDocumentView(documentView);

        seekbar = findViewById(R.id.seekBar);
        seekbar.setOnSeekBarChangeListener(seekBarChangeListener);

        color_picker_view = findViewById(R.id.colorPickerLayout);
        AlphaSlideBar alphaSlideBar = findViewById(R.id.alphaSlideBar);
        BrightnessSlideBar brightnessSlideBar = findViewById(R.id.brightnessSlide);


        resizeToggle = findViewById(R.id.toggle_button).isActivated();

        // Add alpha and brightness sliders
        color_picker_view.attachAlphaSlider(alphaSlideBar);
        color_picker_view.attachBrightnessSlider(brightnessSlideBar);

        // Create listener for changing color
        color_picker_view.setColorListener(new ColorEnvelopeListener() {
            @Override
            public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                RoomViewActionUtility.ChangeColorHex(envelope.getHexCode());
            }
        });

        set_initial_color();
    }

    private void getRoomName(Bundle savedInstanceState) {
        String roomName;
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                roomName = null;
            } else {
                roomName = extras.getString("ROOM_NAME");
            }
        } else {
            roomName = (String) savedInstanceState.getSerializable("ROOM_NAME");
        }
        if (roomName != null) {
            Toast.makeText(getApplicationContext(), "Joined " + roomName, Toast.LENGTH_LONG).show();
        } else {
            Log.e("RoomActivity", "room name was null");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        NetworkLayerService.startNetworkService(this);
        NetworkLayerService.bindNetworkService(this, networkServiceConnection);
        roomLayout.bringChildToFront(roomView);
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
            public void onServiceDisconnected (ComponentName name){

            }
        };
    }

    private void handleNetworkStarted() {
        roomView.setNetworkLayer(networkLayer);
        networkLayer.setRoomActivity(this);
        ncNetworkConnection = networkLayer.getNCNetworkConnection();
        ncNetworkConnection.stopDiscovering();
        ncNetworkConnection.advertise();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(networkServiceConnection);
    }

    public void showDocument(View view) {
        new MaterialFilePicker()
                .withActivity(this)
                .withRequestCode(Permissions.REQUEST_CODE_FILEPICKER)
                .withHiddenFiles(true)
                .start();
    }

    // Modified by Kyle Ehlers on 1/17/19
    // Added the try/catch to handle the NullPointerException
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Permissions.REQUEST_CODE_FILEPICKER) {
            try {
                String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
                showPDF(new File(filePath));
            }
            catch (NullPointerException e) {
                e.printStackTrace();
            }

        }
    }

    public void openStreamView(View view) {
        // Go to stream view to either start streaming or view streaming
        Intent video_activity = new Intent(this, VideoMenuActivity.class);
        RoomActivity.this.startActivity(video_activity);
    }

    public void showPDF(File file) {
        try {

            PdfiumCore pdfiumCore = new PdfiumCore(context);

            ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfDocument pdfDocument = pdfiumCore.newDocument(fd);

            //Get current screen size
            DisplayMetrics metrics = getBaseContext().getResources().getDisplayMetrics();
            int screen_width = metrics.widthPixels;
            int screen_height = metrics.heightPixels;

            int pageCount = pdfiumCore.getPageCount(pdfDocument);

            // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
            // RGB_565 - little worse quality, twice less memory usage

            Bitmap bitmapArr[] = new Bitmap[pageCount];

            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                Bitmap bitmap = Bitmap.createBitmap(screen_width, screen_height, Bitmap.Config.ARGB_8888);
                pdfiumCore.openPage(pdfDocument, pageNum);
                pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageNum, 0, 0, screen_width, screen_height, true);
                bitmapArr[pageNum] = bitmap;
            }

            documentView.setPDF(bitmapArr);

            if (networkLayer != null) {
                networkLayer.sendFile(fd);
            }

            pdfiumCore.closeDocument(pdfDocument); // important!


            findViewById(R.id.decPageBtn).setVisibility(View.VISIBLE);
            findViewById(R.id.incPageBtn).setVisibility(View.VISIBLE);

        } catch(IOException ex) {
            Toast.makeText(context, "File corrupted", Toast.LENGTH_SHORT).show();
            ex.printStackTrace();
        }
    }

    public void toggleToolbox(View view) {
        ConstraintLayout toolboxLayout = findViewById(R.id.toolbox_view);

        if (toolboxLayout.getVisibility() == View.VISIBLE) {
            toolboxLayout.setVisibility(View.GONE);
        }
        else {
            toolboxLayout.setVisibility(View.VISIBLE);
        }
    }

    public void undo(View view) {
        roomView.undo();
    }

    public void incPDFPage(View view) {
        documentView.incPDFPage();
    }

    public void decPDFPage(View view) {
        documentView.decPDFPage();
    }


    private void set_initial_color() {
        // Wait for color_picker_view to load to get width and height
        color_picker_view.post(new Runnable() {
            public void run() {

                int width = color_picker_view.getWidth();
                int radius = width / 2;
                int center_y = color_picker_view.getHeight() / 2;

                // Makes sure the initial color is not too light to see
                int min_dist_from_center = radius / 2;

                // Generate random angle and distance from center of color wheel
                double rand_angle = new Random().nextDouble() * Math.PI*2;
                double rand_dist = new Random().nextInt(radius - min_dist_from_center) + min_dist_from_center;

                // Use random values to set initial color
                int rand_x =(int)(Math.cos(rand_angle) * rand_dist) + radius;
                int rand_y =(int)(Math.sin(rand_angle) * rand_dist) + center_y;
                color_picker_view.setSelectorPoint(rand_x, rand_y);
            }
        }
        );
    }

    public void toggleColorPickerView(View view) {

        ConstraintLayout colorPickerLayout = findViewById(R.id.color_picker_view);
        ConstraintLayout toolboxLayout = findViewById(R.id.toolbox_view);

        if (colorPickerLayout.getVisibility() == View.VISIBLE) {
            colorPickerLayout.setVisibility(View.GONE);
            toolboxLayout.setVisibility(View.VISIBLE);
        }
        else {
            colorPickerLayout.setVisibility(View.VISIBLE);
            toolboxLayout.setVisibility(View.INVISIBLE);
        }


    }

    public void changeColor(View view) {
        RoomViewActionUtility.ChangeColorHex(view.getTag().toString());
    }

    public void colorErase(View view) {
        RoomViewActionUtility.setEraser();
        view.setPressed(true);
    }

    public void saveCanvas(View view) {

    }

    public void toggleDraw(View view) {
        if (roomView.getTouchState() == roomView.getDrawState()) {
            roomView.setTouchState(roomView.getResizeState());
        }
        else {
            roomView.setTouchState(roomView.getDrawState());
        }
    }

    // Used for the SeekBar to change pen width
    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int seekbar_progress, boolean fromUser) {
            RoomViewActionUtility.ChangeWidth((float)seekbar_progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            RoomViewActionUtility.ChangeWidth((float)seekbar.getProgress());
        }
    };

    @Override
    public void onBackPressed() {
        ncNetworkConnection.stopAdvertising();
        ncNetworkConnection.discover();
        Intent killService = new Intent(this, ScreenShareService.class);
        stopService(killService);
        if (ScreenShareService.isStreaming) {
            Toast.makeText(RoomActivity.this, "Screen share ending", Toast.LENGTH_LONG).show();
            networkLayer.sendBytes(new byte[]{}, NetworkMessageType.STREAM_ENDED);
            ScreenShareService.isStreaming = false;
        }
        super.onBackPressed();
    }

}