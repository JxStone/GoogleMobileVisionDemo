package com.example.googlevisiondemo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

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

public class MainActivity extends AppCompatActivity {

    Button bClient;
    Button bServer;
    Button bImage;
    Button bVideo;
    Button bFaceTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bClient = (Button)findViewById(R.id.bClient);
        bClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVideoActivity();
            }
        });
        bServer = (Button)findViewById(R.id.bServer);
        bServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVideoActivity();
            }
        });
        bImage = (Button)findViewById(R.id.bImage);
        bImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageActivity();
            }
        });
        bVideo = (Button)findViewById(R.id.bVideo);
        bVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVideoActivity();
            }
        });
        bFaceTracker = (Button)findViewById(R.id.bFaceTracker);
        bFaceTracker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFaceTrackerActivity();
            }
        });




    }


    private void openVideoActivity() {
        Intent intent = new Intent(this, VideoActivity.class);
        startActivity(intent);
    }

    private void openFaceTrackerActivity() {
        Intent intent = new Intent(this, FaceTrackerActivity.class);
        startActivity(intent);
    }

    private void openImageActivity() {
        Intent intent = new Intent(this, ImageActivity.class);
        startActivity(intent);
    }
}
