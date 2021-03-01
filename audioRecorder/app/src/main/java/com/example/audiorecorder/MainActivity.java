package com.example.audiorecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.ibm.cloud.sdk.core.http.HttpMediaType;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
//import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
//import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

public class MainActivity extends AppCompatActivity {
    private static final String API_KEY = "JEZoDtAZDpSBxGJ4RcmyRfoI3HzCHRjQf3JELr-uTISs";
    private static final String URL = "https://api.us-south.speech-to-text.watson.cloud.ibm.com/instances/661f3e57-7e35-42b5-bd58-70cb1502fdcb";
    Button buttonStart, buttonStop, buttonConvert;
    TextView showData;
    String AudioSavePathInDevice = null,threeGptoMp3 = null;
    MediaRecorder mediaRecorder;
    public static final int RequestPermissionCode = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //used for ibm watson networking else error occur
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setContentView(R.layout.activity_main);
        showData = findViewById(R.id.showTrans);
        buttonStart = findViewById(R.id.startRecord);
        buttonStop = findViewById(R.id.stopRecord);
        buttonConvert = findViewById(R.id.playRecord);
        buttonConvert.setOnClickListener(v -> {
            if(AudioSavePathInDevice != null){
                new Thread(() -> {
                    convertSpeechToText();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,"converted to text successfully",Toast.LENGTH_SHORT).show());
                }).start();
            }
            else{
                Toast.makeText(getApplicationContext(),"Record audio to convert",Toast.LENGTH_SHORT).show();
            }
        });
        //start recording
        buttonStart.setOnClickListener(v -> {
            try {
                if(checkPermission()) {
                    MediaRecorderReady();
                    Toast.makeText(MainActivity.this, "recording", Toast.LENGTH_LONG).show();
                }
                else {
                    requestPermission();
                }
            }
            catch (Exception e){
                Toast.makeText(MainActivity.this, "recorder start exception", Toast.LENGTH_LONG).show();
            }

        });
        //stop recording
        buttonStop.setOnClickListener(v -> {
            try {
                mediaRecorder.stop();
                Toast.makeText(MainActivity.this, "recorded", Toast.LENGTH_LONG).show();
            } catch(RuntimeException stopException) {
                Toast.makeText(MainActivity.this, "recorder stop exception", Toast.LENGTH_LONG).show();
            }

        });
    }
    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
    }
    public void MediaRecorderReady(){
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            UUID uuid=UUID.randomUUID();
            AudioSavePathInDevice = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "sailorsAudioRecording"+ uuid +".3gp";
            threeGptoMp3 = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "sailorsAudioRecordingM"+ uuid +".mp3";
            mediaRecorder=new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
            mediaRecorder.setOutputFile(AudioSavePathInDevice);
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaRecorder.start();
        }
    }
    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }
    public void convertSpeechToText(){
        FFmpeg.execute(String.format("-i %s -c:a libmp3lame %s",AudioSavePathInDevice,threeGptoMp3));
        if(threeGptoMp3 != null && AudioSavePathInDevice != null){
            IamAuthenticator authenticator = new IamAuthenticator(API_KEY);
            SpeechToText speechToText = new SpeechToText(authenticator);
            speechToText.setServiceUrl(URL);
            File audioFile = new File(threeGptoMp3);
            RecognizeOptions options = null;
            try {
                options = new RecognizeOptions.Builder().audio(audioFile).contentType(HttpMediaType.AUDIO_MP3).model("en-US_NarrowbandModel").timestamps(true).wordConfidence(true).build();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            final SpeechRecognitionResults transcript = speechToText.recognize(options).execute().getResult();
            System.out.println(transcript);
            try {
                JSONObject jsobj = new JSONObject(transcript.toString());
                JSONArray jsonArray = jsobj.getJSONArray("results");
                if(jsonArray.length() > 0){
                    JSONArray alternativesArray = jsonArray.getJSONObject(0).getJSONArray("alternatives");
                    if(alternativesArray.length() > 0){
                        //for (int j = 0; j < alternativesArray.length(); j++) {
                        //}
                        JSONObject resultObject = alternativesArray.getJSONObject(0);
                        runOnUiThread(() -> {
                            try {
                                showData.setText(resultObject.getString("transcript"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        });


                    }else{
                        runOnUiThread(() -> showData.setText(R.string.response));
                    }

                }
                else{
                    runOnUiThread(() -> showData.setText(R.string.response));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        else{
            runOnUiThread(() -> Toast.makeText(MainActivity.this,"mp3 file not created",Toast.LENGTH_SHORT).show());
        }

    }
}