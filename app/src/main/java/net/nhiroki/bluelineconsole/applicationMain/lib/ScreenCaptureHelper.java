package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.nhiroki.bluelineconsole.applicationMain.MainActivity;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureHelper {
    public interface CaptureCallback {
        void onScreenCaptured(@Nullable Bitmap bitmap);
        void onError(String message);
    }

    private static Intent sResultData = null;
    private static int sResultCode = 0;

    public static void setProjectionResult(int resultCode, Intent resultData) {
        sResultCode = resultCode;
        sResultData = resultData;
    }

    public static boolean hasPermission() {
        return sResultCode == android.app.Activity.RESULT_OK && sResultData != null;
    }

    public static void captureScreenBehindActivity(final MainActivity activity, final CaptureCallback callback) {
        if (!hasPermission()) {
            callback.onError("MediaProjection permission not granted.");
            return;
        }

        final WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            callback.onError("WindowManager not available.");
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;
        final int densityDpi = metrics.densityDpi;

        // Start Foreground Service required by Android 10+ (API 29+) for MediaProjection
        Intent serviceIntent = new Intent(activity, ScreenCaptureService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(activity, serviceIntent);
        } else {
            activity.startService(serviceIntent);
        }

        ScreenCaptureService.setServiceReadyListener(new ScreenCaptureService.ServiceReadyListener() {
            @Override
            public void onReady() {
                // Hide launcher window so it doesn't obstruct background app
                final View decorView = activity.getWindow().getDecorView();
                decorView.setVisibility(View.INVISIBLE);

                // Allow 220ms for window compositor to draw background application cleanly
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        MediaProjectionManager projectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                        if (projectionManager == null) {
                            decorView.setVisibility(View.VISIBLE);
                            ScreenCaptureService.stopService(activity);
                            callback.onError("MediaProjectionManager not available.");
                            return;
                        }

                        MediaProjection projection = null;
                        try {
                            projection = projectionManager.getMediaProjection(sResultCode, (Intent) sResultData.clone());
                        } catch (Exception e) {
                            decorView.setVisibility(View.VISIBLE);
                            ScreenCaptureService.stopService(activity);
                            callback.onError("Failed to obtain MediaProjection: " + e.getMessage());
                            return;
                        }

                        if (projection == null) {
                            decorView.setVisibility(View.VISIBLE);
                            ScreenCaptureService.stopService(activity);
                            callback.onError("MediaProjection is null.");
                            return;
                        }

                        final ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
                        final VirtualDisplay virtualDisplay = projection.createVirtualDisplay(
                                "BlueLineScreenCapture",
                                width, height, densityDpi,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                imageReader.getSurface(), null, null
                        );

                        final MediaProjection activeProjection = projection;

                        // Wait 150ms for frame buffer to arrive
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Bitmap finalBitmap = null;
                                try {
                                    Image image = imageReader.acquireLatestImage();
                                    if (image != null) {
                                        Image.Plane[] planes = image.getPlanes();
                                        ByteBuffer buffer = planes[0].getBuffer();
                                        int pixelStride = planes[0].getPixelStride();
                                        int rowStride = planes[0].getRowStride();
                                        int rowPadding = rowStride - pixelStride * width;

                                        Bitmap tempBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                                        tempBitmap.copyPixelsFromBuffer(buffer);
                                        finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height);
                                        image.close();
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    try { virtualDisplay.release(); } catch (Exception ignored) {}
                                    try { imageReader.close(); } catch (Exception ignored) {}
                                    try { activeProjection.stop(); } catch (Exception ignored) {}
                                    ScreenCaptureService.stopService(activity);
                                    decorView.setVisibility(View.VISIBLE);
                                }

                                if (finalBitmap != null) {
                                    callback.onScreenCaptured(finalBitmap);
                                } else {
                                    callback.onError("Could not capture frame buffer.");
                                }
                            }
                        }, 150);
                    }
                }, 220);
            }
        });
    }

    public static String bitmapToBase64Jpeg(Bitmap bitmap, int maxDimension, int quality) {
        if (bitmap == null) return null;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = Math.min(1.0f, (float) maxDimension / Math.max(width, height));
            Bitmap scaled = (scale < 1.0f)
                    ? Bitmap.createScaledBitmap(bitmap, Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), true)
                    : bitmap;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}
