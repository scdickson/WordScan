package com.qualcomm.vuforia.samples.VuforiaSamples.app.TextRecognition;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.support.v4.app.FragmentActivity;

import java.io.File;
import java.io.FileInputStream;

import com.qualcomm.vuforia.samples.VuforiaSamples.R;

/**
 * Created by sdickson on 8/6/13.
 */
public class FullScreenImageView extends FragmentActivity
{
    String caption, image_path;
    Bitmap raw_image;
    RelativeLayout fullscreenimagelayout;
    TouchImageView fullscreenimage;
    TextView fullscreencaption, fullscreenshare;
    ProgressDialog progressDialog;
    FullScreenImageView fsiv;

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fullscreenimageview);
        fsiv = this;
        Intent data = this.getIntent();
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle("");
        progressDialog.setMessage("Loading...");

        if(data != null)
        {
            image_path = data.getStringExtra("image");
            caption = data.getStringExtra("caption");
            //raw_image = data.getParcelableExtra("raw_image");
            fullscreenimagelayout = (RelativeLayout) findViewById(R.id.fullscreenimage_layout);
            fullscreenimage = (TouchImageView) findViewById(R.id.fullscreenimage);
            fullscreencaption = (TextView) findViewById(R.id.fullscreenimage_caption);
            fullscreenshare = (TextView) findViewById(R.id.fullscreenimage_share);
            fullscreenshare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view)
                {
                    try
                    {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("image/png");

                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(image_path)));
                        share.putExtra(Intent.EXTRA_TEXT, "Image from Muni");

                        startActivity(Intent.createChooser(share, "Share Image"));
                    }
                    catch(Exception e){}
                }
            });
        }
    }

    public void onResume()
    {
        super.onResume();

        if(image_path != null && fullscreenimage != null)
        {
            try
            {
                new loadImage().execute();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        if(caption != null && fullscreencaption != null)
        {
            fullscreencaption.setText(caption);
        }
    }

    private class loadImage extends AsyncTask<Object, Integer, Void>
    {
        Bitmap image;

        protected void onPreExecute()
        {
            //progressDialog.show();
        }

        protected Void doInBackground(Object... arg0)
        {
            try
            {
                File f = new File(image_path);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(new FileInputStream(f),null,o);
                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                int width = size.x;
                int height = size.y;

                    int width_tmp=o.outWidth, height_tmp=o.outHeight;
                    int scale=1;
                    while(true){
                        if(width_tmp/2<width || height_tmp/2<height)
                            break;
                        width_tmp/=2;
                        height_tmp/=2;
                        scale*=2;
                    }

                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize=scale;
                    image = BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
                    raw_image = image;
            }
            catch(Exception e)
            {}
            return null;
        }

        protected void onPostExecute(Void v)
        {
            if(image != null)
            {
                fullscreenimage.setImageBitmap(image);
                //progressDialog.dismiss();
            }
        }
    }
}
