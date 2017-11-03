package in.dc297.artisticstyletransfer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.mikepenz.iconics.context.IconicsContextWrapper;
import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraListener;
import com.wonderkiln.camerakit.CameraView;

import java.io.File;
import java.io.FileOutputStream;

public class CameraActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback{

    CameraView mCameraView;

    private static final int REQUEST_STORAGE_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_camera);

        mCameraView = findViewById(R.id.camera);

        mCameraView.setFocus(CameraKit.Constants.FOCUS_CONTINUOUS);

        mCameraView.setMethod(CameraKit.Constants.METHOD_STILL);

        mCameraView.setCropOutput(true);

        mCameraView.setPermissions(CameraKit.Constants.PERMISSIONS_PICTURE);

        mCameraView.setJpegQuality(70);

        mCameraView.setCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] picture) {
                super.onPictureTaken(picture);
                SavePhotoTask savePhotoTask = new SavePhotoTask();
                savePhotoTask.execute(picture);

            }
        });

        findViewById(R.id.picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.captureImage();
            }
        });

        findViewById(R.id.pickfromgallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), GalleryActivity.class);
                startActivity(i);
            }
        });

        findViewById(R.id.showfrontcam).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.setFacing(mCameraView.getFacing()^1);
            }
        });

        int navigationBarHeight = getNavigationBarHeight();
        RelativeLayout relativeLayout = findViewById(R.id.camera_controls);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) relativeLayout.getLayoutParams();
        layoutParams.setMargins(0,0,0,navigationBarHeight);
        relativeLayout.setLayoutParams(layoutParams);

        int statusBarHeight = getStatusBarHeight();

        final RelativeLayout relativeLayout1= findViewById(R.id.topControls);
        RelativeLayout.LayoutParams relativeLayoutLayoutParams = (RelativeLayout.LayoutParams) relativeLayout.getLayoutParams();
        Log.i(ShowImageActivity.class.getName(),String.valueOf(statusBarHeight));
        relativeLayoutLayoutParams.setMargins(0,statusBarHeight,0,0);
        relativeLayout1.setLayoutParams(relativeLayoutLayoutParams);

        ImageView navigationBar = findViewById(R.id.navigartionBarBg);
        navigationBar.getLayoutParams().height = navigationBarHeight;
        navigationBar.requestLayout();

        if(ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            requestStoragePermission();
            return;
        }

        File dir = new File(Environment.getExternalStorageDirectory(), getResources().getString(R.string.app_name));

        try{
            if(!dir.exists()) {
                if (dir.mkdir()) {
                    System.out.println("Directory created");
                } else {
                    System.out.println("Directory is not created");
                    Toast.makeText(getApplicationContext(), "Failed to create directory", Toast.LENGTH_SHORT).show();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),"Failed to create directory",Toast.LENGTH_SHORT).show();
        }

    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public int getNavigationBarHeight(){
        int result = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(CameraActivity.this,"Write permission required to share",Toast.LENGTH_SHORT).show();
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Camera2BasicFragment.ErrorDialog.newInstance(getString(R.string.request_permission_storage_capture)).show(getFragmentManager(),"dialog");
            }
            else{
                File dir = new File(Environment.getExternalStorageDirectory(), getResources().getString(R.string.app_name));
                try{
                    if(!dir.exists()) {
                        if (dir.mkdir()) {
                            System.out.println("Directory created");
                        } else {
                            System.out.println("Directory is not created");
                            Toast.makeText(getApplicationContext(), "Failed to create directory", Toast.LENGTH_SHORT).show();
                        }
                    }
                    mCameraView.start();
                }catch(Exception e){
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(),"Failed to create directory",Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        mCameraView.start();
    }

    @Override
    protected void onPause() {
        if(mCameraView.isStarted()) mCameraView.stop();
        super.onPause();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(IconicsContextWrapper.wrap(newBase));
    }

    class SavePhotoTask extends AsyncTask<byte[], Integer, File> {
        @Override
        protected File doInBackground(byte[]... jpeg) {

            File photo=new File(Environment.getExternalStorageDirectory(), getResources().getString(R.string.app_name)+"/"+System.currentTimeMillis()+".jpg");

            if (photo.exists()) {
                photo.delete();
            }

            try {
                FileOutputStream fos=new FileOutputStream(photo.getPath());

                fos.write(jpeg[0]);
                fos.close();
            }
            catch (java.io.IOException e) {
                Log.e("PictureDemo", "Exception in photoCallback", e);
            }

            return photo;
        }

        protected void onPostExecute(File result) {
            Intent mIntent = new Intent(getApplicationContext(),ShowImageActivity.class);
            mIntent.putExtra("filepath",result.toString());
            startActivity(mIntent);
        }
    }
}
