package com.example.googlevisiondemo;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageActivity extends AppCompatActivity {

    ImageView imageView;
    Button btnProcessImage;
    Button btnSave;
    Canvas canvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        imageView = (ImageView)findViewById(R.id.imageView);
        btnProcessImage = (Button)findViewById(R.id.btnProcessImage);
        btnSave = (Button)findViewById(R.id.btnSave);

        final Bitmap myBitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.image);
        imageView.setImageBitmap(myBitmap);

        final Paint rectPaint = new Paint();
        rectPaint.setStrokeWidth(5);
        rectPaint.setColor(Color.RED);
        rectPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        final Bitmap tempBitmap = Bitmap.createBitmap(myBitmap.getWidth(), myBitmap.getHeight(), Bitmap.Config.RGB_565);
        canvas = new Canvas(tempBitmap);
        canvas.drawBitmap(myBitmap, 0, 0, null);

        btnProcessImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FaceDetector faceDetector = new FaceDetector.Builder(getApplicationContext())
                        .setTrackingEnabled(false)
                        .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                        .setMode(FaceDetector.FAST_MODE)
                        .build();
                if(!faceDetector.isOperational())
                {
                    Toast.makeText(ImageActivity.this, "Face Detector could not be set up on your device", Toast.LENGTH_SHORT).show();

                    IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
                    boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;
                    if (hasLowStorage) {
                        Toast.makeText(ImageActivity.this, "Low Storage", Toast.LENGTH_SHORT).show();
                    }
                }

                Frame frame = new Frame.Builder().setBitmap(myBitmap).build();
                SparseArray<Face> sparseArray = faceDetector.detect(frame);

                for(int i = 0; i < sparseArray.size(); i++)
                {
                    Face face = sparseArray.valueAt(i);
                    float x1 = face.getPosition().x;
                    float y1 = face.getPosition().y;
                    float x2 = x1 + face.getWidth();
                    float y2 = y1 + face.getHeight();
                    RectF rectF = new RectF(x1, y1, x2, y2);
                    canvas.drawRect(rectF, rectPaint);

                    detectLandmasks(face);


                }
                imageView.setImageDrawable(new BitmapDrawable(getResources(), tempBitmap));

            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BitmapDrawable bdrawable = (BitmapDrawable) imageView.getDrawable();
                Bitmap bitmap = bdrawable.getBitmap();

                File pictureDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                String pictureName = getPictureName();
                File image = new File(pictureDir, pictureName);
                Uri pictureUri = Uri.fromFile(image);

                boolean success = false;

                // Encode the file as a PNG image.
                FileOutputStream outStream;
                try {

                    outStream = new FileOutputStream(image);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                    /* 100 to keep full quality of the image */

                    outStream.flush();
                    outStream.close();
                    success = true;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (success) {
                    Toast.makeText(getApplicationContext(), "Image saved success",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error during image saving", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    private String getPictureName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        return timestamp+".jpg";
    }

    private void detectLandmasks(Face face) {
        for (Landmark landmark:face.getLandmarks()) {
            int cx = (int) (landmark.getPosition().x);
            int cy = (int) (landmark.getPosition().y);

            drawOnImageView(landmark.getType(), cx, cy);
        }
    }

    private void drawOnImageView(int type, int cx, int cy) {
        //Draw
    }
}
