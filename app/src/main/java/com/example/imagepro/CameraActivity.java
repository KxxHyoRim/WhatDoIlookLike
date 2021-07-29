package com.example.imagepro;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final String TAG="CameraActivity_";

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView flip_camera;     // call for image view of flip button
    private int mCameraId = 0;         // start with front camera // 0 : back, 1 : front
    private ImageView take_picture_button;
    private int take_image = 0;

    Interpreter interpreter;
    TextView textView;
    Handler handler1, handler2, handler3;

    /** mCameraId = 1로 시작
     * 문제 1 : mCameraId를 1로 지정했음에도 불구하고 후면으로 시작
     * 문제 2 : 전면 카메라가 반대로 뜬다 */
    // 후면 정상
    // 후면 정상
    // 전면 반대
    // 후면 정상


    private BaseLoaderCallback mLoaderCallback =new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface
                        .SUCCESS:{
                    Log.i(TAG,"OpenCv Is loaded");
                    mOpenCvCameraView.enableView();
                }
                default:
                {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public CameraActivity(){
        Log.i(TAG,"Instantiated new "+this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int MY_PERMISSIONS_REQUEST_CAMERA=0;
        // if camera permission is not given it will ask for it on device
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);

        interpreter = getTfliteInterpreter("converted_model.tflite");
        textView = (TextView) findViewById(R.id.textView);

        mOpenCvCameraView=(CameraBridgeViewBase) findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        flip_camera = findViewById(R.id.flip_camera);
        // when flip camera button is clicked
        flip_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                swapCamera();
            }
        });

        // if take_image == 1 then take a picture
        take_picture_button = findViewById(R.id.take_picture_button);
        take_picture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "pressed");
                if (take_image == 0){
                    take_image = 1;
                } else {
                    take_image = 0;
                }
            }
        });

        handler1 = new Handler(){
            public void handleMessage(Message msg){
                textView.setText("result : 고양이");
            }
        };

        handler2 = new Handler(){
            public void handleMessage(Message msg){
                textView.setText("result : 강아지");
            }
        };

        handler3 = new Handler(){
            public void handleMessage(Message msg){
                textView.setText("result : 여우");
            }
        };
    }

    private void swapCamera() {
        // first we will change mCameraId
        mCameraId = mCameraId^1;    // not operation
        // disable current camera view
        mOpenCvCameraView.disableView();
        // setCameraIndex
        mOpenCvCameraView.setCameraIndex(mCameraId);
        // enable view
        mOpenCvCameraView.enableView();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            //if load success
            Log.d(TAG,"Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else{
            //if not loaded
            Log.d(TAG,"Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this,mLoaderCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }
    }

    public void onDestroy(){
        super.onDestroy();
        if(mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }

    }

    public void onCameraViewStarted(int width ,int height){
        mRgba=new Mat(height,width, CvType.CV_8UC4);
        mGray =new Mat(height,width,CvType.CV_8UC1);
        Log.i(TAG,"onCameraViewStarted()    "  +  "mCameraId : " + mCameraId);


    }
    public void onCameraViewStopped() {
        Log.i(TAG,"onCameraViewStopped()    "  +  "mCameraId : " + mCameraId);
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        mRgba=inputFrame.rgba();
        mGray=inputFrame.gray();

        // when we change camera from back to front there is a rotation problem
        // front camera is rotated by 180
        // so when mCameraId is 1(front), rotate camera frame with 180 degree

        Log.i(TAG,"onCameraFrame()         " +  "mCameraId : " + mCameraId);



        if (mCameraId == 1){    // front camera
            Core.flip(mRgba, mRgba, -1);
            Core.flip(mGray, mGray, -1);
        }

        take_image = take_picture_function_rgb(take_image, mRgba);

        return mRgba;

    }

    private int take_picture_function_rgb(int take_image, Mat mRgba) {
        if (take_image == 1){
            // add permission on Manifest

            Mat save_mat = new Mat();

            // rotate img 90 degree
            Core.flip(mRgba.t(), save_mat, 1);

            // convert image from RGBA to BGRA
            Imgproc.cvtColor(save_mat, save_mat, Imgproc.COLOR_RGBA2BGRA);

            // create new folder
            File folder = new File(Environment.getExternalStorageDirectory().getPath() + "/WhatDoIlookLike" );
            Log.d(TAG, String.valueOf(folder));

//            boolean success = true;
            if (!folder.exists()){
                Log.d(TAG, "make Folder");
                folder.mkdirs();
//                success = folder.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String currentDateAndTime = sdf.format(new Date());
            String fileName = Environment.getExternalStorageDirectory().getPath() + "/WhatDoIlookLike/" + currentDateAndTime + ".jpg";
            Log.d(TAG, fileName);

            Imgcodecs.imwrite(fileName, save_mat);

            float result = doInference(save_mat);

            if(result == 0.0){
                Message msg = handler1.obtainMessage();
                handler1.sendMessage(msg);
            }
            else if(result == 1.0){
                Message msg = handler2.obtainMessage();
                handler2.sendMessage(msg);
            }
            else if(result == 2.0){
                Message msg = handler3.obtainMessage();
                handler3.sendMessage(msg);
            }

            take_image = 0;
        }

        return take_image;
    }

    private Interpreter getTfliteInterpreter(String modelPath) {
        try {
            return new Interpreter(loadModelFile(this, modelPath));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private float doInference(Mat image){
        float[][][][] input = new float[1][50][50][1];
        float[][] output = new float[1][3];

        Imgproc.resize(image, image, new Size(50, 50));

        for(int i=0;i<image.rows();i++)
            for(int j=0;j<image.cols();j++) {
                double pixel = image.get(i,j)[0] * 0.299 + image.get(i,j)[1] * 0.587 + image.get(i,j)[2] * 0.114;
                input[0][i][j][0] = (float) (pixel / 255.0);
            }

        interpreter.run(input, output);
        int out = 0;
        float out2 = 0;
        for(int i=0;i<3;i++) {
            if (output[0][i] > out2) {
                out2 = output[0][i];
                out = i;
            }
        }
        return out;
    }
}