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

public class VideoActivity extends AppCompatActivity  {

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
        textView = (TextView)findViewById(R.id.textView);
        btnSelectVideo = (Button)findViewById(R.id.btnSelectVideo);
        btnProcessVideo = (Button)findViewById(R.id.btnProcessVideo);

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

                FFmpegMediaMetadataRetriever mFMMR = new FFmpegMediaMetadataRetriever();
                mFMMR.setDataSource(selectedFilePath);

                Bitmap bitmap = mFMMR.getFrameAtTime(0, FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap tempRotatedBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);


                String timeMs = mFMMR.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION); // video time in ms
                long totalVideoTime= 1000*Integer.valueOf(timeMs); // total video time, in uS

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
                if(resultCode == RESULT_OK) {
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
        return timestamp+".mp4";
    }


    private void extractMpegFrames(String path, FaceDetector detector, BitmapToVideoEncoder bitmapToVideoEncoder) throws IOException{
        MediaCodec decoder = null;
        MediaExtractor extractor = null;
        CodecOutputSurface outputSurface = null;
        int saveWidth = 640;
        int saveHeight = 480;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(path);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0 ) {
                throw new RuntimeException("No video track found in " + path);
            }

            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            if (VERBOSE) {
                Log.d("FRAME EXTRACT", "Video size is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                        format.getInteger(MediaFormat.KEY_HEIGHT));
            }

            // Could use width/height from the MediaFormat to get full-size frames;
            outputSurface = new CodecOutputSurface(saveWidth, saveHeight);

            // Create a MediaCodec decoder, and configure it with the MediaFormat from the
            // extractor.  It's very important to use the format from the extractor because
            // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, outputSurface.getSurface(), null, 0);
            decoder.start();

            doExtract(extractor, trackIndex, decoder, outputSurface, detector, bitmapToVideoEncoder);
        } finally {
            // release everything we grabbed
            if (outputSurface != null) {
                outputSurface.release();
                outputSurface = null;
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    /**
     * Selects the video track, if any.
     *
     * @return the track index, or -1 if no video track is found.
     */
    private int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d("FRAME EXTRACT", "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
    }

    /*
    * Work loop
    */
    void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder, CodecOutputSurface outputSurface, FaceDetector detector, BitmapToVideoEncoder bitmapToVideoEncoder) throws IOException {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int decodeCount = 0;
        long frameSaveTime = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d("Extract Frame", "loop");

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d("Extract Frame", "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            Log.w("Extract Frame", "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d("Extract Frame", "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);
                        }
                        inputChunk++;
                        extractor.advance();
                    }
                } else{
                        if (VERBOSE) Log.d("Extract Frame", "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d("Extract Frame", "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d("Extract Frame", "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d("Extract Frame", "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    Log.e("Extract Frame", "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.d("Extract Frame", "surface decoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d("Extract Frame", "output EOS");
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d("Extract Frame", "awaiting decode of frame " + decodeCount);
                        outputSurface.awaitNewImage();
                        outputSurface.drawImage(true);

                        Frame frame;
                        SparseArray<Face> faces;
                        final Paint rectPaint = new Paint();
                        rectPaint.setStrokeWidth(5);
                        rectPaint.setColor(Color.RED);
                        rectPaint.setStyle(Paint.Style.STROKE);

                        if (decodeCount < 10) {
//                            File outputFile = new File(FILES_DIR, String.format("frame-%02d.png", decodeCount));
                            long startWhen = System.nanoTime();
//                            outputSurface.saveFrame(outputFile.toString());
                            frameSaveTime += System.nanoTime() - startWhen;
                            Bitmap bmp = outputSurface.getFrame();

//                            frame = new Frame.Builder().setBitmap(bmp).build(); // generates a "Frame" object, which can be fed to a face detector
//                            faces = detector.detect(frame); // detect the faces (detector is a FaceDetector)
//
//                            Canvas canvas = new Canvas(bmp);
//                            canvas.drawBitmap(bmp, 0, 0, null);
//
//                            for(int i = 0; i < faces.size(); i++)
//                            {
//                                Face face = faces.valueAt(i);
//                                float x1 = face.getPosition().x;
//                                float y1 = face.getPosition().y;
//                                float x2 = x1 + face.getWidth();
//                                float y2 = y1 + face.getHeight();
//                                RectF rectF = new RectF(x1, y1, x2, y2);
//                                canvas.drawRect(rectF, rectPaint);
//                            }
//
                            bitmapToVideoEncoder.queueFrame(bmp);
                            Toast.makeText(getApplicationContext(), "Processing", Toast.LENGTH_SHORT).show();
                        }

                        decodeCount++;
                    }
                }
            }
        }
//
//        int numSaved = (60 < decodeCount) ? 60 : decodeCount;
//        Log.d("Extract Frame", "Saving " + numSaved + " frames took " +
//                (frameSaveTime / numSaved / 1000) + " us per frame");
    }
}
