package com.example.googlevisiondemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.googlevisiondemo.client.CryptoException;
import com.example.googlevisiondemo.client.CryptoUtils;
import com.example.googlevisiondemo.server.Server;

import java.io.File;
import java.net.SocketException;

public class ServerActivity extends AppCompatActivity {
    public static final String ENCRYPTION_KEY = "This is a keyyyy";
    private static int SERVER_PORT = 5000;
    private static final int REQUEST_FILE_SELECT = 0;

    private String selectedFilePath;

    TextView textView;
    Button bStartServer;
    Button bSelect;
    Button bDecrypt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAPTURE_AUDIO_OUTPUT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAPTURE_AUDIO_OUTPUT}, 10);
        } else {
            // code here
        }

        bStartServer = (Button) findViewById(R.id.bStartServer);
        bSelect = (Button) findViewById(R.id.bSelect);
        bDecrypt = (Button) findViewById(R.id.bDecrypt);
        textView = (TextView) findViewById(R.id.textView);

        bStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String filepath = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
                File dir = new File(filepath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                runServer(dir, SERVER_PORT);
            }
        });
        bSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(
                        Intent.createChooser(intent, "Select File"),
                        REQUEST_FILE_SELECT);
            }
        });
        bDecrypt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File fin;
                File fout;

                fin = new File(selectedFilePath);
                fout = new File(selectedFilePath);
                if (!fin.exists() || !fin.isFile()) {
                    System.out.print("*** Error: not a valid file\n");
                }

                try {
                    CryptoUtils.decrypt(ENCRYPTION_KEY, fout, fin);
                } catch (CryptoException e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
            }
        });

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_FILE_SELECT: // select file
                if (resultCode == RESULT_OK) {
                    selectedFilePath = data.getData().getPath();
                    textView.setText(selectedFilePath);
                    System.out.println(selectedFilePath);
                }
                break;
        }
    }

    public void runServer(File directory, int server_port){
        Server socket;

        try {
            socket = new Server(server_port, directory);
        } catch (IllegalArgumentException err) {
            System.err.printf("*** Error: %s", err.getMessage());
            return;
        } catch (SocketException err) {
            System.err.println("*** Error: could not create socket");
            return;
        }
        Toast.makeText(ServerActivity.this, "Server Running...", Toast.LENGTH_SHORT).show();
        System.out.println("Server Running...");

        // infinite loop
        socket.RunServer();
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

}
