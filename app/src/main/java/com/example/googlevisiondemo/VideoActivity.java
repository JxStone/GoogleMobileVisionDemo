package com.example.googlevisiondemo;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

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

    VideoView videoView;
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

        videoView = (VideoView) findViewById(R.id.videoView);
        textView = (TextView) findViewById(R.id.textView);
        btnSelectVideo = (Button) findViewById(R.id.btnSelectVideo);
        btnProcessVideo = (Button) findViewById(R.id.btnProcessVideo);

        MediaController mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        mediaController.setAnchorView(videoView);


        // paint style
        final Paint rectPaint = new Paint();
        rectPaint.setStrokeWidth(5);
        rectPaint.setColor(Color.RED);
        rectPaint.setStyle(Paint.Style.STROKE);

        final Paint rectPaint2 = new Paint();
        rectPaint2.setStrokeWidth(5);
        rectPaint2.setColor(Color.YELLOW);
        rectPaint2.setStyle(Paint.Style.STROKE);

        final Paint rectPaint3 = new Paint();
        rectPaint3.setStrokeWidth(5);
        rectPaint3.setColor(Color.RED);
        rectPaint3.setStyle(Paint.Style.FILL_AND_STROKE);

        final Paint rectPaint4 = new Paint();
        rectPaint4.setStrokeWidth(5);
        rectPaint4.setColor(Color.YELLOW);
        rectPaint4.setStyle(Paint.Style.FILL_AND_STROKE);

        Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.RED);
        paintText.setTextSize(20);
        paintText.setStyle(Paint.Style.FILL);
        paintText.setShadowLayer(10f, 10f, 10f, Color.BLACK);

        Paint paintText2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText2.setColor(Color.BLACK);
        paintText2.setTextSize(25);
        paintText2.setStyle(Paint.Style.FILL);
        paintText2.setShadowLayer(10f, 10f, 10f, Color.BLACK);

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
                        .setMode(FaceDetector.ACCURATE_MODE)
                        .setProminentFaceOnly(true)
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

                // get framerate
                MediaExtractor extractor = new MediaExtractor();
                int frameRate = 24; //may be default
                try {
                    //Adjust data source as per the requirement if file, URI, etc.
                    extractor.setDataSource(selectedFilePath);
                    int numTracks = extractor.getTrackCount();
                    for (int i = 0; i < numTracks; ++i) {
                        MediaFormat format = extractor.getTrackFormat(i);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (mime.startsWith("video/")) {
                            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    //Release stuff
                    extractor.release();
                }

                Bitmap bitmap = mFMMR.getFrameAtTime(0, FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
                Matrix matrix = new Matrix();
                matrix.postRotate(0);
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

                Toast.makeText(getApplicationContext(), "Processing", Toast.LENGTH_SHORT).show();

                int faceCount = 0;
                int live_counter = 0;
                int occupancy_counter = 0;
                int rider_per_minute = 0;
                int daily_counter = 0;
                int max_count = 0;
                int bus_speed = 1;
                int sum = 0;
                int num_exit = 0;
                int time_counter = 0;
                int exit_counter = 0;
                int[] seat_usage = {0,0,0,0,0,0,0,0,0,0,0,0,0,0};

                boolean reset = false;

                //iterate through video frames
                for (int time_us = 0; time_us < totalVideoTime; time_us += (1000000/25)) { // 25 frames per second
                    bitmap = mFMMR.getFrameAtTime(time_us, FFmpegMediaMetadataRetriever.OPTION_CLOSEST); // extract a bitmap element from the closest key frame from the specified time_us
                    if (bitmap == null) {
                        Log.d("Bitmap", "Bitmap is null");
                        break;
                    }
                    Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    // rotate frame if needed
                    Bitmap rotatedBmp = Bitmap.createBitmap(mutableBitmap, 0, 0, mutableBitmap.getWidth(), mutableBitmap.getHeight(), matrix, true);

                    frame = new Frame.Builder().setBitmap(rotatedBmp).build(); // generates a "Frame" object, which can be fed to a face detector
                    faces = detector.detect(frame); // detect the faces (detector is a FaceDetector)

//                    if(faces.size() != 0) {
                    Canvas canvas = new Canvas(rotatedBmp);
                    canvas.drawBitmap(rotatedBmp, 0, 0, null);
                    float[][] faceXY = new float[faces.size()][2];

                    // draw rectangle around faces
                    for (int i = 0; i < faces.size(); i++) {
                        Face face = faces.valueAt(i);
                        float x1 = face.getPosition().x;
                        float y1 = face.getPosition().y;
                        float x2 = x1 + face.getWidth();
                        float y2 = y1 + face.getHeight();
                        RectF rectF = new RectF(x1, y1, x2, y2);
                        canvas.drawRect(rectF, rectPaint);

                        // canvas size 640 480
                        faceXY[i][0] = x1;
                        faceXY[i][1] = y1;
                        String coord = x1 + " " + y1;
                        Log.d("coordinate", coord);
                    }

                    //draw mini seat map on canvas
                    RectF seat_map1 = new RectF(500, 350, 515, 365);
                    canvas.drawRect(seat_map1, rectPaint3);
                    canvas.drawText(seat_usage[0]+"", 500, 365, paintText2);
                    RectF seat_map2 = new RectF(525, 350, 540, 365);
                    canvas.drawRect(seat_map2, rectPaint3);
                    canvas.drawText(seat_usage[1]+"", 525, 365, paintText2);
                    RectF seat_map3 = new RectF(560, 350, 575, 365);
                    canvas.drawRect(seat_map3, rectPaint3);
                    canvas.drawText(seat_usage[2]+"", 560, 365, paintText2);
                    RectF seat_map4 = new RectF(585, 350, 600, 365);
                    canvas.drawRect(seat_map4, rectPaint3);
                    canvas.drawText(seat_usage[3]+"", 585, 365, paintText2);

                    RectF seat_map5 = new RectF(500, 375, 515, 390);
                    canvas.drawRect(seat_map5, rectPaint3);
                    canvas.drawText(seat_usage[4]+"", 500, 390, paintText2);
                    RectF seat_map6 = new RectF(525, 375, 540, 390);
                    canvas.drawRect(seat_map6, rectPaint3);
                    canvas.drawText(seat_usage[5]+"", 525, 390, paintText2);
                    RectF seat_map7 = new RectF(560, 375, 575, 390);
                    canvas.drawRect(seat_map7, rectPaint3);
                    canvas.drawText(seat_usage[6]+"", 560, 390, paintText2);
                    RectF seat_map8 = new RectF(585, 375, 600, 390);
                    canvas.drawRect(seat_map8, rectPaint3);
                    canvas.drawText(seat_usage[7]+"", 585, 390, paintText2);

                    RectF seat_map9 = new RectF(500, 400, 515, 415);
                    canvas.drawRect(seat_map9, rectPaint3);
                    canvas.drawText(seat_usage[8]+"", 500, 415, paintText2);
                    RectF seat_map10 = new RectF(525, 400, 540, 415);
                    canvas.drawRect(seat_map10, rectPaint3);
                    canvas.drawText(seat_usage[9]+"", 525, 415, paintText2);
                    RectF seat_map11 = new RectF(560, 400, 575, 415);
                    canvas.drawRect(seat_map11, rectPaint3);
                    canvas.drawText(seat_usage[10]+"", 560, 415, paintText2);
                    RectF seat_map12 = new RectF(585, 400, 600, 415);
                    canvas.drawRect(seat_map12, rectPaint3);
                    canvas.drawText(seat_usage[11]+"", 585, 415, paintText2);

                    RectF seat_map13 = new RectF(500, 425, 515, 440);
                    canvas.drawRect(seat_map13, rectPaint3);
                    canvas.drawText(seat_usage[12]+"", 500, 440, paintText2);
                    RectF seat_map14 = new RectF(585, 425, 600, 440);
                    canvas.drawRect(seat_map14, rectPaint3);
                    canvas.drawText(seat_usage[13]+"", 585, 440, paintText2);

                    //associate detected face to seat map
                    for(int i = 0; i < faces.size(); i++) {
                        for(int j = i+1; j < faces.size(); j++) {
                            if(Math.abs(faceXY[i][0] - faceXY[j][0]) <= 50 && Math.abs(faceXY[i][1] - faceXY[j][1]) <= 7) {
                                for(int k = 0; k < faces.size(); k++) {
                                    Face face = faces.valueAt(k);
                                    float x1 = face.getPosition().x;
                                    float y1 = face.getPosition().y;
                                    float x2 = x1 + face.getWidth();
                                    float y2 = y1 + face.getHeight();
                                    RectF rect = new RectF(x1, y1, x2, y2);
                                    if((face.getPosition().x == faceXY[i][0] && face.getPosition().y == faceXY[i][1]) || (face.getPosition().x == faceXY[j][0] && face.getPosition().y == faceXY[j][1])) {
                                        canvas.drawRect(rect, rectPaint2);
                                        if(Math.abs(x1 - 219) <= 3 && Math.abs(y1 - 67) <= 3 || Math.abs(x1 - 247) <= 3 && Math.abs(y1 -73) <= 3) { // match face with seat mapcanvas.drawRect(seat_map1, rectPaint4);
                                                canvas.drawRect(seat_map2, rectPaint4);
                                        } else if (Math.abs(x1 - 340) <= 3 && Math.abs(y1 - 67) <= 3 || Math.abs(x1 - 399) <= 3 && Math.abs(y1 - 67) <= 3) {
                                            canvas.drawRect(seat_map3, rectPaint4);
                                            canvas.drawRect(seat_map4, rectPaint4);
                                        } else if (Math.abs(x1 - 201) <= 3 && Math.abs(y1 - 87) <= 5 || Math.abs(x1 - 245) <= 3 && Math.abs(y1 - 78) <= 5) {
                                            canvas.drawRect(seat_map5, rectPaint4);
                                            canvas.drawRect(seat_map6, rectPaint4);
                                        } else if (Math.abs(x1 - 340) <= 5 && Math.abs(y1 - 70) <= 5 || Math.abs(x1 - 399) <= 5 && Math.abs(y1 - 70) <= 5) {
                                            canvas.drawRect(seat_map7, rectPaint4);
                                            canvas.drawRect(seat_map8, rectPaint4);
                                        } else if (Math.abs(x1 - 180) <= 5 && Math.abs(y1 - 96) <= 5 || Math.abs(x1 - 234) <= 10 && Math.abs(y1 - 96) <= 10) {
                                            canvas.drawRect(seat_map9, rectPaint4);
                                            canvas.drawRect(seat_map10, rectPaint4);
                                        } else if (Math.abs(x1 - 336) <= 5 && Math.abs(y1 - 96) <= 5 || Math.abs(x1 - 426) <= 5 && Math.abs(y1 - 96) <= 5) {
                                            canvas.drawRect(seat_map11, rectPaint4);
                                            canvas.drawRect(seat_map12, rectPaint4);
                                        } else if (Math.abs(x1 - 166) <= 10 && Math.abs(y1 - 155) <= 10) {
                                            canvas.drawRect(seat_map13, rectPaint4);
                                        } else if (Math.abs(x1 - 462) <= 10 && Math.abs(y1 - 125) <= 10) {
                                            canvas.drawRect(seat_map14, rectPaint4);
                                        }
                                    }
                                }
                            }
                        }
                    }



                    if(time_us == 38000000 || time_us == 250000000) { //simulate bus stops
                            bus_speed = 0;
                        }
                        if(time_us == 61000000 || time_us == 616000000) { //simulate bus leaves the stop
                            bus_speed = 1;
                        }


                    if (faces.size() >= rider_per_minute || time_us % 60000000 <= 40000) {
                        rider_per_minute = faces.size();
                    }

                    live_counter = faces.size();

                        if(bus_speed != 0) {
                            exit_counter = 0;
                            sum = 0;

                            // Rear door count
                            if (faces.size() > occupancy_counter || time_us % 20000000 <= 50000) {
                                occupancy_counter = faces.size();
                                daily_counter = occupancy_counter + num_exit;
                            }
                        } else {
                            exit_counter += (1000000/25);
                            if(exit_counter < 5000000) { //5 seconds after bus stops
                                occupancy_counter = faces.size(); //reset occupancy_counter
                            }else if(exit_counter >= 5000000 && exit_counter < 8000000) { //3 seconds after ppl get off bus
                                sum += faces.size();
                                occupancy_counter = faces.size();
                            }else if(exit_counter == 8000000) {
                                int avg = sum / 75;
                                if (avg < daily_counter) {
                                    num_exit = daily_counter - avg;
                                }
                                occupancy_counter = faces.size();
                            }else {
                                if (faces.size() > occupancy_counter) {
                                    occupancy_counter = faces.size();
                                    daily_counter = occupancy_counter + num_exit;
                                }
                            }
                        }


//                        if (time_us % 100000000 <= 100000) { // update occupancy counter every 100 second
//                            occupancy_counter = live_counter;
//                        }

//                        // entry door sequential count
//                        if (faces.size() == 1 && time_us % 2000000 < (1000000/30)) { // update count every half second
//                            faceCount++;
//                        }

//                        String counter_text = "live count: " + live_counter
//                                        + " occupancy count: " + occupancy_counter
//                                        + " rider/minute: " + rider_per_minute
//                                        + " daily count(total): " + daily_counter;

                    // draw on canvas
                    String text = "Current Occupancy/Max Occupancy: " + occupancy_counter + "/8";
                        // draw count
                        Rect rectText = new Rect();
                        paintText.getTextBounds(text, 0, text.length(), rectText);

                        canvas.drawText(text, 0, rectText.height(), paintText);



//
//                    String temp = "live count: " + String.valueOf(live_counter)
//                            + "  Time: " + String.valueOf(time_us/1000000)
//                            + " Occupancy count: " + String.valueOf(occupancy_counter)
//                            + " Rider/minute: " + String.valueOf(rider_per_minute)
//                            + " Daily count: " + String.valueOf(daily_counter);
//                    Log.d("Video", temp);
                    bitmapToVideoEncoder.queueFrame(rotatedBmp);

//                    Toast.makeText(getApplicationContext(), "Processing", Toast.LENGTH_SHORT).show();
                }
                bitmapToVideoEncoder.stopEncoding();

                detector.release();
                Log.d("Video", "Queueing Complete");
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
                    videoView.setVideoURI(uri);
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
