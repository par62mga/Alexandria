package it.jaschke.alexandria.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.InputStream;

/**
 * Created by saj on 11/01/15.
 */
public class DownloadImage extends AsyncTask<String, Void, Bitmap> {
    private static final String LOG_TAG = DownloadImage.class.getSimpleName();

    ImageView mImage;

    public DownloadImage(ImageView bmImage) {
        this.mImage = bmImage;
    }

    protected Bitmap doInBackground(String... urls) {
        String urlDisplay = urls[0];
        Bitmap bookCover = null;
        try {
            // Log.d (LOG_TAG, "doInBackground: image URL ==> " + urlDisplay);
            InputStream in = new java.net.URL(urlDisplay).openStream();
            bookCover = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }
        return bookCover;
    }

    protected void onPostExecute(Bitmap result) {
        // this leaves the default image in place if the image can't be fetched
        if (result != null) {
            mImage.setImageBitmap(result);
        }
    }
}

