package com.example.googlevisiondemo;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.io.NIOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class VideoActivity extends AppCompatActivity {

    private static final boolean VERBOSE = false; // lots of logging
    private static final int REQUEST_FILE_SELECT = 0;

    private String selectedFilePath;

    TextureView textureView;
    TextView textView;
    Button btnSelectVideo;
    Button btnProcessVideo;

    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;

    Canvas canvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        textureView = (TextureView) findViewById(R.id.textureView);
        textView = (TextView) findViewById(R.id.textView);
        btnSelectVideo = (Button) findViewById(R.id.btnSelectVideo);
        btnProcessVideo = (Button) findViewById(R.id.btnProcessVideo);

//        surfaceHolder = surfaceView.getHolder();
//        surfaceHolder.addCallback(VideoActivity.this);

        final Paint rectPaint = new Paint();
        rectPaint.setStrokeWidth(5);
        rectPaint.setColor(Color.RED);
        rectPaint.setStyle(Paint.Style.STROKE);

        btnSelectVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                galleryIntent.setType("*/*");
                startActivityForResult(
                        Intent.createChooser(galleryIntent, "Select File"),
                        REQUEST_FILE_SELECT);
            }
        });

        btnProcessVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                        .setTrackingEnabled(true)
                        .setMode(FaceDetector.FAST_MODE)
                        .build();

                if (!detector.isOperational()) {
                    Toast.makeText(VideoActivity.this, "Face Detector could not be set up on your device", Toast.LENGTH_SHORT).show();

                    IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
                    boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;
                    if (hasLowStorage) {
                        Toast.makeText(VideoActivity.this, "Low Storage", Toast.LENGTH_SHORT).show();
                    }
                }

                Frame frame;
                SparseArray<Face> faces;
                MediaMetadataRetriever mMMR = new MediaMetadataRetriever();
                mMMR.setDataSource(selectedFilePath);

                FFmpegMediaMetadataRetriever mFMMR = new FFmpegMediaMetadataRetriever();
                mFMMR.setDataSource(selectedFilePath);

                Bitmap bitmap = mFMMR.getFrameAtTime(0, FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap tempRotatedBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);


                String timeMs = mFMMR.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION); // video time in ms
                long totalVideoTime = 1000 * Integer.valueOf(timeMs); // total video time, in uS

                BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
                    @Override
                    public void onEncodingComplete(File outputFile) {
                        //Toast.makeText(VideoActivity.this,  "Encoding complete!", Toast.LENGTH_SHORT).show();
                    }
                });

                File fileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                String fileName = getFileName();
                File file = new File(fileDir, fileName);

                org.jcodec.common.io.SeekableByteChannel out = null;

                bitmapToVideoEncoder.startEncoding(tempRotatedBmp.getWidth(), tempRotatedBmp.getHeight(), file);

//                try {
//                    extractMpegFrames(selectedFilePath, detector, bitmapToVideoEncoder);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

//                    out = NIOUtils.writableFileChannel(String.valueOf(file));
//                    AndroidSequenceEncoder encoder = new AndroidSequenceEncoder(out, Rational.R(40,1));

                for (int time_us = 0; time_us < totalVideoTime; time_us += 40000) { // 25 frames per second
                    bitmap = mFMMR.getFrameAtTime(time_us, FFmpegMediaMetadataRetriever.OPTION_CLOSEST); // extract a bitmap element from the closest key frame from the specified time_us
                    if (bitmap == null) {
                        Log.d("Bitmap", "Bitmap is null");
                        break;
                    }
                    Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    Bitmap rotatedBmp = Bitmap.createBitmap(mutableBitmap, 0, 0, mutableBitmap.getWidth(), mutableBitmap.getHeight(), matrix, true);

                    frame = new Frame.Builder().setBitmap(rotatedBmp).build(); // generates a "Frame" object, which can be fed to a face detector
                    faces = detector.detect(frame); // detect the faces (detector is a FaceDetector)

                    Canvas canvas = new Canvas(rotatedBmp);
                    canvas.drawBitmap(rotatedBmp, 0, 0, null);

                    for (int i = 0; i < faces.size(); i++) {
                        Face face = faces.valueAt(i);
                        float x1 = face.getPosition().x;
                        float y1 = face.getPosition().y;
                        float x2 = x1 + face.getWidth();
                        float y2 = y1 + face.getHeight();
                        RectF rectF = new RectF(x1, y1, x2, y2);
                        canvas.drawRect(rectF, rectPaint);
                    }

                    bitmapToVideoEncoder.queueFrame(rotatedBmp);
//                       encoder.encodeImage(bitmap);

                    Toast.makeText(getApplicationContext(), "Processing", Toast.LENGTH_SHORT).show();
                }
                bitmapToVideoEncoder.stopEncoding();
//                    encoder.finish();

                Toast.makeText(getApplicationContext(), "Process Complete", Toast.LENGTH_SHORT).show();

            }
        });


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_FILE_SELECT:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    selectedFilePath = getPath(uri);
                    textView.setText(selectedFilePath);
                }
                break;
        }

    }


    /**
     * helper to retrieve the path of an video URI
     */
    public String getPath(Uri uri) {
        // just some safety built in
        if (uri == null) {
            // TODO perform some logging or show user feedback
            return null;
        }
        // try to retrieve the image from the media store first
        // this will only work for images selected from gallery
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }

        // this is our fallback here
        return uri.getPath();
    }

    private String getFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        return timestamp + ".mp4";
    }
}
