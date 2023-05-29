package com.sample.osogo_app;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OCRActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";

    public static final int REQUEST_TAKE_PHOTO = 10;
    public static final int REQUEST_PERMISSION = 11;

    private String mCurrentPhotoPath;
    private DialogActivity dialog;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        captureCamera();

    }

    private void captureCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // 인텐트를 처리 할 카메라 액티비티가 있는지 확인
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            // 촬영한 사진을 저장할 파일 생성
            File photoFile = null;

            try {
                //임시로 사용할 파일이므로 경로는 캐시폴더로
                File tempDir = getCacheDir();

                //임시촬영파일 세팅
                String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
                String imageFileName = "Capture_" + timeStamp + "_"; //ex) Capture_20201206_

                File tempImage = File.createTempFile(
                        imageFileName,  /* 파일이름 */
                        ".jpg",         /* 파일형식 */
                        tempDir      /* 경로 */
                );

                // ACTION_VIEW 인텐트를 사용할 경로 (임시파일의 경로)
                mCurrentPhotoPath = tempImage.getAbsolutePath();

                photoFile = tempImage;

            } catch (IOException e) {
                //에러 로그는 이렇게 관리하는 편이 좋다.
                Log.w(TAG, "파일 생성 에러!", e);
            }

            //파일이 정상적으로 생성되었다면 계속 진행
            if (photoFile != null) {
                //Uri 가져오기
                Uri photoURI = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider",
                        photoFile);
                //인텐트에 Uri담기
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                //인텐트 실행
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Bitmap rotatedBitmap = null;
        try {
            //after capture
            switch (requestCode) {
                case REQUEST_TAKE_PHOTO: {
                    if (resultCode == RESULT_OK) {

                        File file = new File(mCurrentPhotoPath);
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));

                        if (bitmap != null) {
                            ExifInterface ei = new ExifInterface(mCurrentPhotoPath);
                            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);

                            switch (orientation) {

                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    rotatedBitmap = rotateImage(bitmap, 90);
                                    break;

                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    rotatedBitmap = rotateImage(bitmap, 180);
                                    break;

                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotatedBitmap = rotateImage(bitmap, 270);
                                    break;

                                case ExifInterface.ORIENTATION_NORMAL:
                                default:
                                    rotatedBitmap = bitmap;
                            }
                        }
                    }
                    break;
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "onActivityResult Error !", e);
        }

        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            // 찍은 사진을 가져옴
            Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);

            // 텍스트 변환 작업 실행
            OCRActivity.CaptionTask captionTask = new OCRActivity.CaptionTask();
            captionTask.execute(rotatedBitmap);
        }
    }

    //카메라에 맞게 이미지 로테이션
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermission(); //권한체크
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION: {
                // 권한이 취소되면 result 배열은 비어있다.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "권한 확인", Toast.LENGTH_LONG).show();

                } else {
                    Toast.makeText(this, "권한 없음", Toast.LENGTH_LONG).show();
                    finish(); //권한이 없으면 앱 종료
                }
            }
        }
    }

    private class CaptionTask extends AsyncTask<Bitmap, Void, String> {
        // REST API
        private static final String API_URL = "https://oshmos.pythonanywhere.com/api/v2/ocr_kr";

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
                AlertDialog.Builder builder = new AlertDialog.Builder(OCRActivity.this);
                builder.setTitle("OCR").setMessage(result);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            } else {
                result = "서버와의 통신에 문제가 발생했습니다.";
                AlertDialog.Builder builder = new AlertDialog.Builder(OCRActivity.this);
                builder.setTitle("OCR").setMessage(result);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }

            // 텍스트 내용을 음성 메세지로 출력
            String finalResult = result;
            tts = new TextToSpeech(OCRActivity.this, new TextToSpeech.OnInitListener() {
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

        // multipart/form-data에서 사용하는 boundary 문자열을 생성합니다.
        private final String BOUNDARY = "---------------------------" + System.currentTimeMillis();
    }
}
