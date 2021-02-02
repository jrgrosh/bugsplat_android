package xyz.bugsplat.app;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import xyz.bugsplat.app.R;

import xyz.bugsplat.app.env.Logger;
import xyz.bugsplat.app.env.Utils;
import xyz.bugsplat.app.tflite.Classifier;
import xyz.bugsplat.app.tflite.YoloV4Classifier;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Minimum detection confidence to track a detection.
    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.05f;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int PICK_IMAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        detectionCountTextView = findViewById(R.id.detectionCountTextView);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        initDetector();
    }

    private static final Logger LOGGER = new Logger();

    public static final int TF_OD_API_INPUT_SIZE = 416;

    private static final boolean TF_OD_API_IS_QUANTIZED = false;

    private static final String TF_OD_API_MODEL_FILE = "bugsplat-yolov4-tiny-416.tflite";

    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/bugsplat.labels";

    private Classifier detector;

    private ImageView imageView;
    private TextView detectionCountTextView;
    private TextView instructionsTextView;

    private void initDetector(){
        try {
            detector =
                    YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
    }

    private void updateNumberOfConfidentDetectionsDisplayed(int numberOfConfidentDetections){
        String count = String.valueOf(numberOfConfidentDetections);
        String plural = "";
        if(numberOfConfidentDetections != 1){
            plural = "s";
        }
        String displayText = count + " bugsplat" + plural + " detected";
        detectionCountTextView.setText(displayText);
        detectionCountTextView.setVisibility(View.VISIBLE);
    }

    private void handleResult(Bitmap bitmap, List<Classifier.Recognition> results) {
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        int numberOfConfidentDetections = 0;
        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);
                numberOfConfidentDetections++;
            }
        }
        imageView.setImageBitmap(bitmap);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, imageFileName, "bugs");
        updateNumberOfConfidentDetectionsDisplayed(numberOfConfidentDetections);
    }

    private void hideText(){
        //detectionCountTextView.setText("...");
        instructionsTextView.setVisibility(View.GONE);
    }

    public void cameraButtonTap(View v){
        dispatchTakePictureIntent();
    }

    public void uploadButtonTap(View v){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException e) {
            // display error state to the user
        }
    }

    protected void doInferenceOnBitmap(Bitmap bitmap){
        final Bitmap  croppedBitmap = Utils.processBitmap(bitmap, TF_OD_API_INPUT_SIZE);
        imageView.setImageBitmap(croppedBitmap);
        Log.d("bugsplat", "hello");

        Handler handler = new Handler();
        new Thread(() -> {
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    handleResult(croppedBitmap, results);
                }
            });
        }).start();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        detectionCountTextView.setText("....");
        hideText();
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            doInferenceOnBitmap(imageBitmap);

        } else if (requestCode == PICK_IMAGE && resultCode == RESULT_OK){
            if (data == null) {
                //Display an error
                return;
            }
            try {
                InputStream inputStream = this.getContentResolver().openInputStream(data.getData());
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                doInferenceOnBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void aboutIconTap(View view) {
        Log.d("bugsplat", "aboutIconTap pressed");
        new AlertDialog.Builder(this)
                .setTitle("Open Source Acknowledgement")
                .setMessage("This application includes software licensed under the Apache License 2.0")
                .show();
    }
}
