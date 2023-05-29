package com.sample.osogo_app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_PERMISSION = 11;

    private Button btnOCR, btnImageCaption;
    private DialogActivity dialog;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission(); //권한체크

        btnImageCaption = findViewById(R.id.btnImageCaption); // Button for ImageCaptioning
        btnOCR = findViewById(R.id.btnOCR); // Button for OCR

        // 첫 촬영 이후 다음 촬영은 버튼 누르면 시작
        btnImageCaption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ImageCaptionActivity.class);
                startActivity(intent);
            }
        });
        btnOCR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, OCRActivity.class);
                startActivity(intent);
            }
        });

        // 최초 실행 여부 판단
        SharedPreferences pref = getSharedPreferences("isFirst", Activity.MODE_PRIVATE);
        boolean first = pref.getBoolean("isFirst", false);
        if (!first) {
            Log.d("Is first Time?", "first");
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean("isFirst", true);
            editor.commit();
            // 최초 실행 시 안내 문구
            String info = "시그널의 안내 문구";
            onPostExecute_Info(info);
        } else {
            Log.d("Is first Time?", "not first");
        }
    }

    protected void onPostExecute_Info(String result) {
        if (result != null) {
            // 텍스트 내용을 팝업 메세지로 출력
            dialog = new DialogActivity(MainActivity.this, result);
            dialog.show();
        } else {
            result = "안내 문구가 없습니다.";
            dialog = new DialogActivity(MainActivity.this, result);
            dialog.show();
        }

        // 텍스트 내용을 음성 메세지로 출력
        String finalResult = result;
        tts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != android.speech.tts.TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.0f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts.speak(finalResult, TextToSpeech.QUEUE_FLUSH, null, null);
                    } else {
                        tts.speak(finalResult, TextToSpeech.QUEUE_FLUSH, null);
                    }
                }
            }
        });
    }

    //권한 확인
    public void checkPermission() {
        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        //권한이 없으면 권한 요청
        if (permissionCamera != PackageManager.PERMISSION_GRANTED
                || permissionRead != PackageManager.PERMISSION_GRANTED
                || permissionWrite != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "이 앱을 실행하기 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);

        }
    }
}