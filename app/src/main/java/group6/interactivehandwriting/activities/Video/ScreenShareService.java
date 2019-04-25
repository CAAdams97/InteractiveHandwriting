package group6.interactivehandwriting.activities.Video;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import group6.interactivehandwriting.R;
import group6.interactivehandwriting.common.network.NetworkLayer;
import group6.interactivehandwriting.common.network.nearby.connections.message.NetworkMessageType;

public class ScreenShareService extends Service {

    public static NetworkLayer networkLayer;
    public static MediaProjection mediaProjection;
    public static ImageReader imageReader;
    public static boolean isStreaming;
    public static boolean otherUserStreaming;

    private static NotificationManager notificationManager;

    private static int streamQuality = 10;

    private static final int MAX_QUALITY = 50;
    private static final int MIN_QUALITY = 1;
    private static final float MIN_QUALITY_FACTOR = 0.65f;
    private static final float DESIRED_QUALITY_FACTOR = 0.9f;

    private static int frameCount;
    private static long curStartTime;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {

        startForeground(1338, createNotification());

        System.out.println("Screen Share Service Started");

        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        Point size = new Point();
        display.getRealSize(size);
        final int width = size.x;
        final int height = size.y;
        int density = metrics.densityDpi;
        frameCount = 0;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        final Handler handler = new Handler();

        int vdFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        mediaProjection.createVirtualDisplay("screen-mirror", width, height, density, vdFlags, imageReader.getSurface(), null, null);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                reader.setOnImageAvailableListener(this, handler);

                Image image = imageReader.acquireLatestImage();

                if (image == null) {
                    return;
                }

                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();

                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * metrics.widthPixels;

                Bitmap bmp = Bitmap.createBitmap(metrics.widthPixels + (int) ((float) rowPadding / (float) pixelStride), metrics.heightPixels, Bitmap.Config.ARGB_8888);

                bmp.copyPixelsFromBuffer(buffer);

                Bitmap realSizeBitmap = Bitmap.createBitmap(bmp, 0, 0, metrics.widthPixels, bmp.getHeight());

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);

                    int bitmapHeight = realSizeBitmap.getHeight();
                    int bitmapWidth = realSizeBitmap.getWidth();
                    float aspectRatio = (float) realSizeBitmap.getHeight() / realSizeBitmap.getWidth();
                    int newHeight = (int) Math.ceil(realSizeBitmap.getWidth() / aspectRatio);


                    realSizeBitmap = Bitmap.createBitmap(realSizeBitmap, 0, (bitmapHeight - newHeight) / 2, bitmapWidth, newHeight, matrix, true);
                }

                ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
                realSizeBitmap.compress(Bitmap.CompressFormat.JPEG, streamQuality, bitmapStream);
                System.out.println("Sharing:  "  + bitmapStream.size());
                byte[] bitmapByteArray = bitmapStream.toByteArray();
                networkLayer.sendBytes(bitmapByteArray, NetworkMessageType.VIDEO_STREAM);
                frameCount++;
                System.out.println(getFPS());

                image.close();

                bmp.recycle();
                buffer.clear();

            }
        }, null);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        System.out.println("Screen Share Service Stopped");
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                return;
            }
        }, null);
        super.onDestroy();
    }

    private static float getFPS() {
        return (float) (frameCount / ((java.lang.System.currentTimeMillis() - curStartTime) / 1000.0));
    }

    public static void setCurStartTime(long curStartTimeIn) {
        curStartTime = curStartTimeIn;
    }

    private Notification createNotification() {
        notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "screen_sharing");

        Intent notificationIntent = new Intent(this, VideoMenuActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentTitle("Screen Sharing")
                .setContentText("Click to return to screen share menu")
                .setContentIntent(pendingIntent)
                .setTicker("Screen Sharing")
                .setSmallIcon(R.drawable.ic_screen_share_white_24dp)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        String channelId = "screen_sharing";
        NotificationChannel channel = new NotificationChannel(channelId,
                "Screen Sharing Channel", NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channelId);

        return builder.build();
    }

    private static void adjustStreamQuality(boolean increaseQuality) {
        if (increaseQuality) {
            int newQuality = streamQuality + 5;
            if (newQuality > MAX_QUALITY) {
                newQuality = MAX_QUALITY;
            }
            streamQuality = newQuality;
        }
        else {
            int newQuality = streamQuality - 5;
            if (newQuality < MIN_QUALITY) {
                newQuality = MIN_QUALITY;
            }
            streamQuality = newQuality;
        }
    }

    public static void compareFPS(float receiverFPS) {
        float senderFPS = getFPS();
        if (senderFPS * MIN_QUALITY_FACTOR > receiverFPS) {
            adjustStreamQuality(false);
        }
        else if (receiverFPS > senderFPS * DESIRED_QUALITY_FACTOR) {
            adjustStreamQuality(true);
        }

        curStartTime = java.lang.System.currentTimeMillis();
        frameCount = 0;
    }

}
