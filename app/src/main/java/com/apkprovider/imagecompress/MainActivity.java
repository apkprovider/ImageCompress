package com.apkprovider.imagecompress;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.apkprovider.imagecompress.imagecompression.Functions;
import com.apkprovider.imagecompress.imagecompression.ImageLoadingUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private Button btnGallery,btnCamera;
    private ImageView imgMain;
    private ImageLoadingUtils utils;

    private Cursor cursor;
    private LruCache<String, Bitmap> memoryCache;

    private final int REQUEST_CODE_FROM_GALLERY = 01;
    private final int REQUEST_CODE_CLICK_IMAGE = 02;

    Uri imageUri= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        utils = new ImageLoadingUtils(this);
        int cachesize = 60*1024*1024;

        memoryCache	 = new LruCache<String, Bitmap>(cachesize){
            @SuppressLint("NewApi")
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if(android.os.Build.VERSION.SDK_INT>=12){
                    return value.getByteCount();
                }
                else{
                    return value.getRowBytes()*value.getHeight();
                }
            }
        };

        init();
    }

    private void init(){
        btnGallery = (Button)findViewById(R.id.btnGallery);
        btnCamera = (Button)findViewById(R.id.btnCamera);
        imgMain = (ImageView)findViewById(R.id.imgMain);
        btnGallery.setOnClickListener(this);
        btnCamera.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnGallery:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CODE_FROM_GALLERY);
                break;
            case R.id.btnCamera:

                /*************************** Camera Intent Start ************************/

                // Define the file-name to save photo taken by Camera activity

                String fileName = "Camera_Example.jpg";

                // Create parameters for Intent with filename

                ContentValues values = new ContentValues();

                values.put(MediaStore.Images.Media.TITLE, fileName);

                values.put(MediaStore.Images.Media.DESCRIPTION,"Image capture by camera");

                // imageUri is the current activity attribute, define and save it for later usage

                imageUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                /**** EXTERNAL_CONTENT_URI : style URI for the "primary" external storage volume. ****/


                // Standard Intent action that can be sent to have the camera
                // application capture an image and return it.

                Intent intent1 = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );

                intent1.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

                intent1.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

                startActivityForResult( intent1, REQUEST_CODE_CLICK_IMAGE);

                /*************************** Camera Intent End ************************/


                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            switch(requestCode){
                case REQUEST_CODE_FROM_GALLERY:
                    new ImageCompressionAsyncTask().execute(data.getDataString());
                    break;
                case REQUEST_CODE_CLICK_IMAGE:

                    String imageId = convertImageUriToFile( imageUri,MainActivity.this);

                    new ImageCompressionAsyncTask().execute(imageId);



                    break;
            }
        }
    }

    /************ Convert Image Uri path to physical path **************/

    public static String convertImageUriToFile ( Uri imageUri, Activity activity )  {

        Cursor cursor = null;
        int imageID = 0;

        try {

            /*********** Which columns values want to get *******/
            String [] proj={
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Thumbnails._ID,
                    MediaStore.Images.ImageColumns.ORIENTATION
            };

            cursor = activity.managedQuery(
                    imageUri,         //  Get data for specific image URI
                    proj,             //  Which columns to return
                    null,             //  WHERE clause; which rows to return (all rows)
                    null,             //  WHERE clause selection arguments (none)
                    null              //  Order-by clause (ascending by name)

            );

            //  Get Query Data

            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int columnIndexThumb = cursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID);
            int file_ColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

            //int orientation_ColumnIndex = cursor.
            //    getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION);

            int size = cursor.getCount();

            /*******  If size is 0, there are no images on the SD Card. *****/

            if (size == 0) {


                Log.e("#######", "No Image");
            }
            else
            {

                int thumbID = 0;
                if (cursor.moveToFirst()) {

                    /**************** Captured image details ************/

                    /*****  Used to show image on view in LoadImagesFromSDCard class ******/
                    imageID     = cursor.getInt(columnIndex);

                    thumbID     = cursor.getInt(columnIndexThumb);

                    String Path = cursor.getString(file_ColumnIndex);

                    //String orientation =  cursor.getString(orientation_ColumnIndex);

                    String CapturedImageDetails = " CapturedImageDetails : \n\n"
                            +" ImageID :"+imageID+"\n"
                            +" ThumbID :"+thumbID+"\n"
                            +" Path :"+Path+"\n";
                    Log.e("#######",CapturedImageDetails);

                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Return Captured Image ImageID ( By this ImageID Image will load from sdcard )

        return ""+imageID;
    }


    public String getRealPathfromURI(Uri uri){
        Cursor cursor = getContentResolver().query(uri,null,null,null,null);
        cursor.moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        return cursor.getString(idx);
    }



    class ImageCompressionAsyncTask extends AsyncTask<String, Void, Bitmap> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            imgMain.setImageResource(R.mipmap.ic_launcher);
        }


        @Override
        protected Bitmap doInBackground(String... params) {

            Bitmap bmp = Functions.compressImage(MainActivity.this, params[0]);
            return bmp;
        }


        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            imgMain.setImageBitmap(result);

        }
    }



    //end of main class
}
