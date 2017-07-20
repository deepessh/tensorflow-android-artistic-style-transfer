package in.dc297.artisticstyletransfer;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

public class ShowImageActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback{

    private boolean debug = false;
    private static final String MODEL_FILE = "file:///android_asset/stylize_quantized.pb";
    private static final String INPUT_NODE = "input";
    private static final String STYLE_NODE = "style_num";
    private static final String OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid";
    private static final int NUM_STYLES = 26;

    private static final String TAG = "ShowImageActivity";

    private final float[] styleVals = new float[NUM_STYLES];
    private int[] intValues;
    private float[] floatValues;

    private TensorFlowInferenceInterface inferenceInterface;
    private ImageView mPreviewImage = null;
    private ImageView mOriginalImage = null;
    private String mImagePath = "";
    private Bitmap mImgBitmap = null;
    private Bitmap mOrigBitmap = null;

    private ProgressDialog progress =null;
    private Handler handler;
    private HandlerThread handlerThread;
    private Button shareButton;

    private RecyclerView mRecyclerView;
    private HorizontalListAdapter mHrAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private ArrayList<Bitmap> myStylesBmList = new ArrayList<Bitmap>();

    private int mSelectedStyle = 0;

    private static final int REQUEST_STORAGE_PERMISSION = 1;

    private SeekBar mSeekBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_image);
        mPreviewImage = (ImageView) findViewById(R.id.image_preview);
        mOriginalImage = (ImageView) findViewById(R.id.image_orig);
        Intent recvdIntent = getIntent();
        mImagePath = recvdIntent.getStringExtra("filepath");
        getPreview();
        Button applyButton = (Button) findViewById(R.id.apply_button);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progress = new ProgressDialog(ShowImageActivity.this);
                progress.setTitle("Loading");
                progress.setMessage("Wait while loading...");
                progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
                progress.show();
                runInBackground(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    stylizeImage();
                                }
                                catch(Exception e){
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(),"Oops! Seems like your phone can't handle the pressure :P",Toast.LENGTH_SHORT).show();
                                            if(progress!=null){
                                                progress.dismiss();
                                            }
                                        }
                                    });
                                }
                            }
                        });
            }
        });

        intValues = new int[mImgBitmap.getWidth() * mImgBitmap.getHeight()];
        floatValues = new float[mImgBitmap.getWidth() * mImgBitmap.getHeight() * 3];

        shareButton = (Button) findViewById(R.id.share_button);

        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ContextCompat.checkSelfPermission(ShowImageActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)
                {
                    requestStoragePermission();
                    return;
                }
                if(mImgBitmap!=null) {
                    try{
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        mImgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                        Bitmap newBitmap = Bitmap.createBitmap(mImgBitmap.getWidth(), mImgBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        // create a canvas where we can draw on
                        Canvas canvas = new Canvas(newBitmap);
                        // create a paint instance with alpha
                        canvas.drawBitmap(mOrigBitmap,0,0,null);
                        Paint alphaPaint = new Paint();
                        alphaPaint.setAlpha(mSeekBar.getProgress()*255/100);
                        // now lets draw using alphaPaint instance
                        canvas.drawBitmap(mImgBitmap, 0, 0, alphaPaint);

                        String path = MediaStore.Images.Media.insertImage(ShowImageActivity.this.getContentResolver(), newBitmap, "Title", null);
                        final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
                        intent.setType("image/png");
                        startActivity(intent);
                    }
                    catch(Exception e){
                        e.printStackTrace();
                        Toast.makeText(ShowImageActivity.this,"Error occurred while trying to share",Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });
        loadStyleBitmaps();
        mRecyclerView = (RecyclerView) findViewById(R.id.my_rec_vw);
        mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mHrAdapter = new HorizontalListAdapter(myStylesBmList);
        mRecyclerView.setAdapter(mHrAdapter);

        mRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getApplicationContext(),mRecyclerView,new RecyclerItemClickListener.OnItemClickListener(){

            @Override
            public void onItemClick(View view, int position) {
                mSelectedStyle = position;
            }

            @Override
            public void onLongItemClick(View view, int position) {
            }
        }));
        mSeekBar = (SeekBar) findViewById(R.id.seekBar);
        mSeekBar.setProgress(100);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mPreviewImage.setAlpha(seekBar.getProgress()/100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mOriginalImage.setImageBitmap(mOrigBitmap);
        mPreviewImage.setImageBitmap(mImgBitmap);
    }

    private void loadStyleBitmaps(){
        for(int i=0;i<NUM_STYLES;i++){
            try{
                myStylesBmList.add(i,BitmapFactory.decodeStream(getAssets().open("thumbnails/style"+i+".jpg")));
            }
            catch(IOException e){
                e.printStackTrace();
                Toast.makeText(ShowImageActivity.this,"Oops! Some error occurred!",Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public synchronized void onResume(){
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        progress = new ProgressDialog(ShowImageActivity.this);
        progress.setTitle("Loading");
        progress.setMessage("Wait while loading...");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();
        runInBackground(new Runnable() {
            @Override
            public void run() {
                inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.cancel();
                    }
                });
            }
        });
        //mOriginalImage.setImageBitmap((getPreview()));
        //mPreviewImage.setImageBitmap(getPreview());
    }
    private Bitmap getPreview() {

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();

        // Decode the image file into a Bitmap sized to fill the View
        //bmOptions.inJustDecodeBounds = false;
        bmOptions.inMutable = true;

        mImgBitmap = BitmapFactory.decodeFile(mImagePath, bmOptions);
        int photoW = mImgBitmap.getWidth();
        int photoH = mImgBitmap.getHeight();

        // Get the dimensions of the View
        int targetW = getWindowManager().getDefaultDisplay().getWidth();
        if(targetW<photoW) {
            int targetH = targetW * photoH / photoW;
            mImgBitmap = Bitmap.createScaledBitmap(mImgBitmap, targetW, targetH, false);
            mOrigBitmap = Bitmap.createScaledBitmap(mImgBitmap, targetW, targetH, false);
        }
        else{
            mOrigBitmap = BitmapFactory.decodeFile(mImagePath, bmOptions);
        }
        return mImgBitmap;
    }

    public boolean isDebug() {
        return debug;
    }

    private void stylizeImage() {
        mImgBitmap = Bitmap.createBitmap(mOrigBitmap);
        for(int i = 0;i<NUM_STYLES;i++){
            if(i==mSelectedStyle) {
                styleVals[i] = 1.0f;
            }
            else styleVals[i] = 0.0f;
        }
        mImgBitmap.getPixels(intValues, 0, mImgBitmap.getWidth(), 0, 0, mImgBitmap.getWidth(), mImgBitmap.getHeight());

            for (int i = 0; i < intValues.length; ++i) {
                final int val = intValues[i];
                floatValues[i * 3] = ((val >> 16) & 0xFF) / 255.0f;
                floatValues[i * 3 + 1] = ((val >> 8) & 0xFF) / 255.0f;
                floatValues[i * 3 + 2] = (val & 0xFF) / 255.0f;
            }

        // Copy the input data into TensorFlow.
        inferenceInterface.feed(
                INPUT_NODE, floatValues, 1, mImgBitmap.getHeight(), mImgBitmap.getWidth(), 3);
        inferenceInterface.feed(STYLE_NODE, styleVals, NUM_STYLES);
        inferenceInterface.run(new String[] {OUTPUT_NODE}, isDebug());
        floatValues = new float[mImgBitmap.getWidth()*(mImgBitmap.getHeight()+10)*3];
        inferenceInterface.fetch(OUTPUT_NODE, floatValues);

        for (int i = 0; i < intValues.length; ++i) {
            intValues[i] =
                    0xFF000000
                            | (((int) (floatValues[i * 3] * 255)) << 16)
                            | (((int) (floatValues[i * 3 + 1] * 255)) << 8)
                            | ((int) (floatValues[i * 3 + 2] * 255));
        }
        floatValues = new float[mImgBitmap.getWidth()*(mImgBitmap.getHeight())*3];
        mImgBitmap.setPixels(intValues, 0, mImgBitmap.getWidth(), 0, 0, mImgBitmap.getWidth(), mImgBitmap.getHeight());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mPreviewImage!=null){
                    mPreviewImage.setImageBitmap(mImgBitmap);
                    if(progress!=null){
                        progress.dismiss();
                    }
                }
            }
        });
    }
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(ShowImageActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(ShowImageActivity.this,"Write permission required to share",Toast.LENGTH_SHORT).show();
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Camera2BasicFragment.ErrorDialog.newInstance(getString(R.string.request_permission_storage)).show(getFragmentManager(),"dialog");
            }
        } else {
            shareButton.performClick();
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
