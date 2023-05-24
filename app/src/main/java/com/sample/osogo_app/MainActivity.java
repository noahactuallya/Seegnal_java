package com.sample.osogo_app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static CameraManager cameraManager;
    private static boolean flashOn = false;

    Button btnCamera;
    ImageView ivCapture;
    DialogActivity dialog;
    TextToSpeech tts;
    CameraSurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivCapture = findViewById(R.id.ivCapture); //ImageView 선언
        btnCamera = findViewById(R.id.btnCapture); //Button 선언
        surfaceView = findViewById(R.id.surfaceView); //SurfaceView 선언

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

        // 첫 촬영 이후 다음 촬영은 버튼 누르면 시작
        btnCamera.setOnClickListener(v -> capture());

        /* 버튼 누르면 촬영한 이미지 저장
        btnSave.setOnClickListener(v -> {
            try {
                BitmapDrawable drawable = (BitmapDrawable) ivCapture.getDrawable();
                Bitmap bitmap = drawable.getBitmap();

                // 찍은 사진이 없으면
                if (bitmap == null) {
                    Toast.makeText(this, "저장할 사진이 없습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    // 저장
                    saveImg();
                }
            } catch (Exception e) {
                Log.w(TAG, "SAVE ERROR!", e);
            }
        }); */
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 101:
                if(grantResults.length > 0){
                    if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                        Toast.makeText(this, "카메라 권한 사용자가 승인함",Toast.LENGTH_LONG).show();
                    }
                    else if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                        Toast.makeText(this, "카메라 권한 사용자가 허용하지 않음.",Toast.LENGTH_LONG).show();
                    }
                    else{
                        Toast.makeText(this, "수신권한 부여받지 못함.",Toast.LENGTH_LONG).show();
                    }
                }
        }
    }

    public void capture(){
        surfaceView.capture((data, camera) -> {
            //bytearray 형식으로 전달
            //이걸이용해서 이미지뷰로 보여주거나 파일로 저장
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8; // 1/8사이즈로 보여주기
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length); //data 어레이 안에 있는 데이터 불러와서 비트맵에 저장

            int targetWidth = ivCapture.getWidth();
            int targetHeight = ivCapture.getHeight();

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            float scaleRatio = Math.min((float) targetWidth / width, (float) targetHeight / height); // 비율 계산

            Matrix matrix = new Matrix();
            matrix.postScale(scaleRatio, scaleRatio);
            matrix.postRotate(90);

            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0,0,width,height,matrix,true);
            BitmapDrawable bmd = new BitmapDrawable(getResources(), resizedBitmap);

            ivCapture.setImageDrawable(bmd);//이미지뷰에 사진 보여주기
            camera.startPreview();

            // 텍스트 변환 작업 실행
            CaptionTask captionTask = new CaptionTask();
            captionTask.execute(resizedBitmap);
        });
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

    //이미지저장 메소드
    private void saveImg() {
        try {
            //저장할 파일 경로
            File storageDir = new File(getFilesDir() + "/capture");
            if (!storageDir.exists()) //폴더가 없으면 생성.
                storageDir.mkdirs();

            // 사진을 찍은 날짜를 파일 이름으로 사용
            String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String filename = "Capture_" + timeStamp + ".jpeg";

            // 기존에 있다면 삭제
            File file = new File(storageDir, filename);
            boolean deleted = file.delete();
            Log.w(TAG, "Delete Dup Check : " + deleted);
            FileOutputStream output = null;

            try {
                output = new FileOutputStream(file);
                BitmapDrawable drawable = (BitmapDrawable) ivCapture.getDrawable();
                Bitmap bitmap = drawable.getBitmap();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output); //해상도에 맞추어 Compress
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    assert output != null;
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Log.e(TAG, "Captured Saved");
            Toast.makeText(this, "Capture Saved ", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "Capture Saving Error!", e);
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private class CaptionTask extends AsyncTask<Bitmap, Void, String> {
        // REST API
        private static final String API_URL = "https://seegnal.pythonanywhere.com/api/v2/caption_kr";

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            Bitmap bitmap = bitmaps[0];
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] byteArray = stream.toByteArray();

            try {
                // API 서버로 POST 요청을 보냄
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
                conn.setDoOutput(true);

                // 바이너리 데이터 전송을 위한 파트를 생성하고 전송
                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                out.writeBytes("--" + BOUNDARY + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"image\";filename=\"" + "image.jpeg" + "\"" + "\r\n");
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                out.write(byteArray);
                out.writeBytes("\r\n");
                out.writeBytes("--" + BOUNDARY + "--\r\n");
                out.flush();
                out.close();

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK && conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                    throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
                }

                // 응답을 받아옴
                InputStream inputStream = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                // JSON 파싱
                JSONObject jsonObject = new JSONObject(response.toString());
                String caption = jsonObject.getString("text");
                return caption;
            } catch (IOException e) {
                Log.e(TAG, "Error occurred while sending POST request: " + e.getMessage());
                return null;
            } catch (JSONException e) {
            Log.e(TAG, "Error occurred while parsing JSON response", e);
            return null;
            } catch (RuntimeException e) {
            Log.e(TAG, "Error occurred while parsing JSON response: " + e.getMessage());
            return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // 텍스트 내용을 팝업 메세지로 출력
                dialog = new DialogActivity(MainActivity.this, result);
                dialog.show();
            } else {
                result = "서버와의 통신에 문제가 발생했습니다.";
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
            ivCapture.setVisibility(View.GONE);
        }

        // multipart/form-data에서 사용하는 boundary 문자열을 생성합니다.
        private final String BOUNDARY = "---------------------------" + System.currentTimeMillis();
    }
}