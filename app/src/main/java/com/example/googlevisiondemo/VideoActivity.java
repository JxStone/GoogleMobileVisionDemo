package com.example.googlevisiondemo;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Rational;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VideoActivity extends AppCompatActivity {

    private static final int REQUEST_FILE_SELECT = 0;

    private String selectedFilePath;

    VideoView videoView;
    TextView textView;
    Button btnSelectVideo;
    Button btnProcessVideo;

    Canvas canvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        videoView = (VideoView)findViewById(R.id.videoView);
        textView = (TextView)findViewById(R.id.textView);
        btnSelectVideo = (Button)findViewById(R.id.btnSelectVideo);
        btnProcessVideo = (Button)findViewById(R.id.btnProcessVideo);

        MediaController mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        mediaController.setAnchorView(videoView);

        final Paint rectPaint = new Paint();
        rectPaint.setStrokeWidth(5);
        rectPaint.setColor(Color.RED);
        rectPaint.setStyle(Paint.Style.FILL_AND_STROKE);

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
                        .setTrackingEnabled(false)
                        .setMode(FaceDetector.FAST_MODE)
                        .build();

                if(!detector.isOperational())
                {
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
                Bitmap bitmap = bitmap = mMMR.getFrameAtTime(1, MediaMetadataRetriever.OPTION_CLOSEST);

                String timeMs = mMMR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); // video time in ms
                int totalVideoTime= 1000*Integer.valueOf(timeMs); // total video time, in uS

//                BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
//                    @Override
//                    public void onEncodingComplete(File outputFile) {
//                        Toast.makeText(VideoActivity.this,  "Encoding complete!", Toast.LENGTH_SHORT).show();
//                    }
//                });

                File fileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                String pictureName = getFileName();
                File file = new File(fileDir, pictureName);

                org.jcodec.common.io.SeekableByteChannel out = null;

//                bitmapToVideoEncoder.startEncoding(bitmap.getWidth(), bitmap.getHeight(), filedir);
                try {
                    out = NIOUtils.writableFileChannel(String.valueOf(file));
                    AndroidSequenceEncoder encoder = new AndroidSequenceEncoder(out, Rational.R(15,1));

                    for (int time_us = 1; time_us < totalVideoTime; time_us += 40000){ //frames per second
                        bitmap = mMMR.getFrameAtTime(time_us, MediaMetadataRetriever.OPTION_CLOSEST); // extract a bitmap element from the closest key frame from the specified time_us
                        if (bitmap==null) {
                            Log.d("Bitmap", "Bitmap is null");
                            break;
                        }
                        frame = new Frame.Builder().setBitmap(bitmap).build(); // generates a "Frame" object, which can be fed to a face detector
                        faces = detector.detect(frame); // detect the faces (detector is a FaceDetector)

                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawBitmap(bitmap, 0, 0, null);

                        for(int i = 0; i < faces.size(); i++)
                        {
                            Face face = faces.valueAt(i);
                            float x1 = face.getPosition().x;
                            float y1 = face.getPosition().y;
                            float x2 = x1 + face.getWidth();
                            float y2 = y1 + face.getHeight();
                            RectF rectF = new RectF(x1, y1, x2, y2);
                            canvas.drawRect(rectF, rectPaint);
                        }

//                    bitmapToVideoEncoder.queueFrame(bitmap);
                        encoder.encodeImage(bitmap);


                        Toast.makeText(getApplicationContext(), "Processing", Toast.LENGTH_SHORT).show();

//                    File pictureDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
//                    String pictureName = getPictureName();
//                    File image = new File(pictureDir, pictureName);
//                    Uri pictureUri = Uri.fromFile(image);
//
//                    boolean success = false;
//
//                    // Encode the file as a PNG image.
//                    FileOutputStream outStream;
//                    try {
//
//                        outStream = new FileOutputStream(image);
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
//                        /* 100 to keep full quality of the image */
//
//                        outStream.flush();
//                        outStream.close();
//                        success = true;
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//
//                    if (success) {
//                        Toast.makeText(getApplicationContext(), "Image saved success",
//                                Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(getApplicationContext(),
//                                "Error during image saving", Toast.LENGTH_SHORT).show();
//                    }
                    }
//                   bitmapToVideoEncoder.stopEncoding();
                    encoder.finish();

                    Toast.makeText(getApplicationContext(), "Process Complete", Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    NIOUtils.closeQuietly(out);
                }


//
            }
        });


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_FILE_SELECT:
                if(resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    selectedFilePath = getPath(uri);
                    textView.setText(selectedFilePath);
                    System.out.println(selectedFilePath);

                    videoView.setVideoURI(uri);
                    videoView.start();
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
        return timestamp+".mp4";
    }

}
