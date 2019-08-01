package com.zhixin.alexleung.recordscreen;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScreenRecorderActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "ERR";
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private static final int RECORD_REQUEST_CODE = 100;
    private int mScreenDensity;
    private Button mBtnRecorder;
    private Button btn1;
    private Button btn2;
    boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private MediaProjectionCallback mMediaProjectionCallback;
    private static final SparseIntArray ORIENTTIONS = new SparseIntArray();

    //camera
    private final int maxCameraNumbers = 2;
    private SurfaceView[] mSurfaceView = new SurfaceView[maxCameraNumbers];
    private SurfaceHolder[] mSurfaceHolder = new SurfaceHolder[maxCameraNumbers];
    private Camera[] camera = new Camera[maxCameraNumbers];
    private Camera.Parameters[] parameters = new Camera.Parameters[maxCameraNumbers];
    private int maxCameraNumber = maxCameraNumbers;
    private boolean[] cameraStatus = new boolean[maxCameraNumbers];

    static {
        ORIENTTIONS.append(Surface.ROTATION_0, 90);
        ORIENTTIONS.append(Surface.ROTATION_90, 0);
        ORIENTTIONS.append(Surface.ROTATION_180, 270);
        ORIENTTIONS.append(Surface.ROTATION_270, 180);
    }

    //test
    public static List<Camera.Size> supportedVideoSizes;
    public static List<Camera.Size> previewSizes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            mScreenDensity = metrics.densityDpi;

            mMediaRecorder = new MediaRecorder();
            mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            mBtnRecorder = (Button) findViewById(R.id.id_btn_screen_recorder);
            mBtnRecorder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isStartRecordScreen();
                }
            });
            mSurfaceView[0] = findViewById(R.id.surfaceView01);
            mSurfaceView[1] = findViewById(R.id.surfaceView02);
            cameraStatus[0] = false;
            cameraStatus[1] = false;

            btn1 = findViewById(R.id.button);
            btn2 = findViewById(R.id.button2);
            btn1.setOnClickListener(this);
            btn2.setOnClickListener(this);
            initSurface();
            Log.i(TAG, "onCreate: " + getCameraInfo());
        } catch (Exception e) {
            Log.i(TAG, "onCreate: " + e.toString());
            Log.i(TAG, "onCreate: " + e.getStackTrace()[0].getLineNumber());
        }
    }

    public static void checkPermission(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            int checkPermission =
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                            + ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE)
                            + ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            + ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                //动态申请
                ActivityCompat.requestPermissions(activity, new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, 123);
                return;
            } else {
                return;
            }
        }
        return;
    }

    //是否开启录制
    private void isStartRecordScreen() {
        try {
            if (!isRecording) {
                initRecorder();
                recordScreen();
            } else {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                stopRecordScreen();
            }
        } catch (Exception e) {
            Log.i("ERR", e.toString());
        }
    }

    //初始化录制参数
    private void initRecorder() {
        try {
            if (mMediaRecorder == null) {
                Log.d(TAG, "initRecorder: MediaRecorder为空");
                return;
            }
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);// 音频源
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);// 视频源
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//视频输出格式
            //这里的路径我是直接写死了。。。
            mMediaRecorder.setOutputFile(this.getExternalFilesDir(null).getPath() + '/' + System.currentTimeMillis() + ".mp4");//存储路径
            mMediaRecorder.setVideoSize(1280, 800);// 设置分辨率
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);// 视频录制格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);// 音频格式
            mMediaRecorder.setVideoFrameRate(16);//帧率
            mMediaRecorder.setVideoEncodingBitRate(5242880);//视频清晰度
//            int rotation = getWindowManager().getDefaultDisplay().getRotation();
//            int orientataion = ORIENTTIONS.get(rotation + 90);
//            mMediaRecorder.setOrientationHint(orientataion);//设置旋转方向
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //开始录制
    private void recordScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), RECORD_REQUEST_CODE);
            return;
        }

        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
        changeText();
    }

    //停止录制
    private void stopRecordScreen() {
        if (mVirtualDisplay == null) {
            return;
        }

        mVirtualDisplay.release();
        destroyMediaProjection();
        isRecording = false;
        changeText();
    }

    //释放录制的资源
    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenRecorder", DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(), null, null);
    }

    private void changeText() {
        if (isRecording) {
            mBtnRecorder.setText("停止录屏");
        } else {
            mBtnRecorder.setText("开始录屏");
        }
    }

    @Override
    public void onClick(View view) {
        try {
            switch (view.getId()) {
                case R.id.button:
                    openCamera0();
                    break;
                case R.id.button2:
                    openCamera1();
                    break;
            }
        } catch (Exception e) {
            Log.i(TAG, "onClick: " + e.toString());
            Log.i(TAG, "onClick: " + e.getStackTrace()[0].getLineNumber());
        }
    }

    private void initSurface() {
        for (int i = 0; i < maxCameraNumber; i++) {
            mSurfaceHolder[i] = mSurfaceView[i].getHolder();
            mSurfaceHolder[i].addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                }

                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                }
            });
        }
    }

    private void stopCamera(int cameraId) {
        if (camera[cameraId] != null) {
            camera[cameraId].stopPreview();
            camera[cameraId].release();
            camera[cameraId] = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < maxCameraNumber; i++) {
            stopCamera(i);
        }
    }

    private void startCamera0(int cameraId, SurfaceHolder holder) {
        if (camera != null) {
            try {
                parameters[cameraId] = camera[cameraId].getParameters();
                parameters[cameraId].setPictureFormat(ImageFormat.JPEG);
                parameters[cameraId].setPreviewSize(960, 720);
                parameters[cameraId].setPictureSize(1280, 720);
//                parameters[cameraId].setPreviewFrameRate(30);
                //如果硬件设备不支持所填参数则会报错
                camera[cameraId].setParameters(parameters[cameraId]);
                camera[cameraId].setPreviewDisplay(holder);
                camera[cameraId].startPreview();
            } catch (IOException e) {
                Log.i(TAG, "startCamera: " + e.toString());
            }
        }
    }

    private void startCamera1(int cameraId, SurfaceHolder holder) {
        if (camera != null) {
            try {
                parameters[cameraId] = camera[cameraId].getParameters();
                parameters[cameraId].setPictureFormat(ImageFormat.JPEG);
                parameters[cameraId].setPreviewSize(1280, 720);
                parameters[cameraId].setPictureSize(1280, 720);
//                parameters[cameraId].setPreviewFrameRate(30);
                //如果硬件设备不支持所填参数则会报错
                camera[cameraId].setParameters(parameters[cameraId]);
                camera[cameraId].setPreviewDisplay(holder);
                camera[cameraId].startPreview();
                //camera.unlock();
            } catch (IOException e) {
                Log.i(TAG, "startCamera: " + e.toString());
//                e.printStackTrace();
            }
        }
    }

    private void openCamera1() {
        int i = 1;
        try {
            if (!cameraStatus[i]) {
                camera[i] = Camera.open(i);
                startCamera1(i, mSurfaceHolder[i]);
                cameraStatus[i] = true;
            }
        } catch (Exception e) {
            camera[i] = null;
            Log.i(TAG, "openCamera0: " + e.toString());
        }
    }

    private void openCamera0() {
        int i = 0;
        try {
            if (!cameraStatus[i]) {
                camera[i] = Camera.open(i);
                startCamera0(i, mSurfaceHolder[i]);
                cameraStatus[i] = true;
            }else{

            }
        } catch (Exception e) {
            camera[i] = null;
            Log.i(TAG, "openCamera1: " + e.toString());
        }
    }

    //录制回调
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (isRecording) {
                isRecording = false;
                changeText();
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            }
            mMediaProjection = null;
            stopRecordScreen();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECORD_REQUEST_CODE) {

            if (resultCode != RESULT_OK) {
                Toast.makeText(ScreenRecorderActivity.this, "录屏权限被禁止了啊", Toast.LENGTH_SHORT).show();
                isRecording = false;
                changeText();
                return;
            }

            mMediaProjectionCallback = new MediaProjectionCallback();
            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            mMediaProjection.registerCallback(mMediaProjectionCallback, null);
            mVirtualDisplay = createVirtualDisplay();
            mMediaRecorder.start();
            isRecording = true;
            changeText();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        destroyMediaProjection();
    }

    @Override
    public void onBackPressed() {
        if (isRecording) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("确定停止录屏吗？")
                    .setPositiveButton("停止", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mMediaRecorder.stop();
                            mMediaRecorder.reset();
                            stopRecordScreen();
                            finish();
                        }
                    }).setNegativeButton("继续", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            }).create().show();

        } else {
            finish();

        }
    }

    public String getCameraInfo() {

        int cameracount = 0;//摄像头数量
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();  //获取摄像头信息
        cameracount = Camera.getNumberOfCameras();
        Log.i("MainActivity", "摄像头数量" + String.valueOf(cameracount));
        for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
            Camera.getCameraInfo(cameraId, cameraInfo);
            Camera camera = Camera.open(cameraId); //开启摄像头获得一个Camera的实例
            Camera.Parameters params = camera.getParameters();  //通过getParameters获取参数
            supportedVideoSizes = params.getSupportedPictureSizes();
            previewSizes = params.getSupportedPreviewSizes();
            camera.release();//释放摄像头

            //重新排列后设下摄像头预设分辨率在所有分辨率列表中的地址，用以选择最佳分辨率（保证适配不出错）
            int index = bestVideoSize(previewSizes.get(0).width);
            Log.i("MainActivity", "预览分辨率地址: " + index);
            if (null != previewSizes && previewSizes.size() > 0) {  //判断是否获取到值，否则会报空对象
                Log.i("MainActivity", "摄像头最高分辨率宽: " + String.valueOf(supportedVideoSizes.get(0).width));  //降序后取最高值，返回的是int类型
                Log.i("MainActivity", "摄像头最高分辨率高: " + String.valueOf(supportedVideoSizes.get(0).height));
                Log.i("MainActivity", "摄像头分辨率全部: " + cameraSizeToSting(supportedVideoSizes));
            } else {
                Log.i("MainActivity", "没取到值啊什么鬼");
                Log.i("MainActivity", "摄像头预览分辨率: " + String.valueOf(previewSizes.get(0).width));
            }
        }
        return cameraSizeToSting(supportedVideoSizes);
    }

    public static int bestVideoSize(int _wid) {

        //降序排列
        Collections.sort(supportedVideoSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                if (lhs.width > rhs.width) {
                    return -1;
                } else if (lhs.width == rhs.width) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        for (int i = 0; i < supportedVideoSizes.size(); i++) {
            if (supportedVideoSizes.get(i).width < _wid) {
                return i;
            }
        }
        return 0;
    }


    public String cameraSizeToSting(Iterable<Camera.Size> sizes) {
        StringBuilder s = new StringBuilder();
        for (Camera.Size size : sizes) {
            if (s.length() != 0)
                s.append(",\n");
            s.append(size.width).append('x').append(size.height);
        }
        return s.toString();
    }
}