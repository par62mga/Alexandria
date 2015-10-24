package it.jaschke.alexandria.services;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import it.jaschke.alexandria.Utility;
import it.jaschke.alexandria.data.AlexandriaContract;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class BookService extends IntentService {

    private final String LOG_TAG = BookService.class.getSimpleName();

    // define Intent actions and keys
    public static final String FETCH_BOOK  = "it.jaschke.alexandria.services.action.FETCH_BOOK";
    public static final String DELETE_BOOK = "it.jaschke.alexandria.services.action.DELETE_BOOK";
    public static final String EAN         = "it.jaschke.alexandria.services.extra.EAN";

    // define Broadcast Message event and key
    public static final String MESSAGE_FETCH_EVENT  = "MESSAGE_EVENT";
    public static final String MESSAGE_DELETE_EVENT = "MESSAGE_EVENT";
    public static final String MESSAGE_KEY          = "MESSAGE_EXTRA";

    // failures returned in MESSAGE_KEY
    public static final String FETCH_ALREADY_PRESENT = "present";
    public static final String FETCH_NOT_FOUND       = "not found";
    public static final String FETCH_NETWORK_FAILURE = "network";
    public static final String FETCH_SERVER_FAILURE  = "server";
    public static final String FETCH_OTHER_FAILURE   = "other";

    public BookService() {
        // use class as thread name for debugging
        super(BookService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (FETCH_BOOK.equals(action)) {
                final String ean = intent.getStringExtra(EAN);
                fetchBook(ean);
            } else if (DELETE_BOOK.equals(action)) {
                final String ean = intent.getStringExtra(EAN);
                deleteBook(ean);
            }
        }
    }

    /**
     * deleteBook -- Handle action deleteBook in the provided background thread with the provided
     *     parameters.
     * @param ean
     */
    private void deleteBook(String ean) {
        if ((ean!=null) && (! ean.isEmpty())) {
            getContentResolver().delete(AlexandriaContract.BookEntry.buildBookUri(Long.parseLong(ean)), null, null);
        }
        broadcastEvent (MESSAGE_DELETE_EVENT, ean);
    }

    /**
     * fetchBook -- Handle action fetchBook in the provided background thread with the provided
     *     parameters.
     * @param ean
     */
    private void fetchBook(String ean) {
        // check if ean is valid and book is not already in our library...
        if (! fetchRequired(ean) ) {
            // already present, the loader should find it so no error broadcast is needed.
            return;
        }

        // try to open a connection to Google to fetch the book
        HttpURLConnection urlConnection = createConnection(ean);
        if (urlConnection == null) {
            if (Utility.isNetworkAvailable(this))
                broadcastEvent (MESSAGE_FETCH_EVENT, FETCH_SERVER_FAILURE);
            else
                broadcastEvent (MESSAGE_FETCH_EVENT, FETCH_NETWORK_FAILURE);
            return;
        }

        String bookJsonString = readStream (urlConnection);
        urlConnection.disconnect();

        if (bookJsonString == null) {
            broadcastEvent (MESSAGE_FETCH_EVENT, FETCH_OTHER_FAILURE);
            return;
        }

        final String ITEMS = "items";

        final String VOLUME_INFO = "volumeInfo";

        final String TITLE = "title";
        final String SUBTITLE = "subtitle";
        final String AUTHORS = "authors";
        final String DESC = "description";
        final String CATEGORIES = "categories";
        final String IMG_URL_PATH = "imageLinks";
        final String IMG_URL = "thumbnail";

        try {
            // fixed null pointer exception here as bookJsonString may have been null
            JSONObject bookJson = new JSONObject(bookJsonString);
            JSONArray bookArray;
            if(bookJson.has(ITEMS)){
                bookArray = bookJson.getJSONArray(ITEMS);
            }else{
                broadcastEvent(MESSAGE_FETCH_EVENT, FETCH_NOT_FOUND);
                return;
            }

            JSONObject bookInfo = ((JSONObject) bookArray.get(0)).getJSONObject(VOLUME_INFO);

            String title = bookInfo.getString(TITLE);

            String subtitle = "";
            if(bookInfo.has(SUBTITLE)) {
                subtitle = bookInfo.getString(SUBTITLE);
            }

            String desc="";
            if(bookInfo.has(DESC)){
                desc = bookInfo.getString(DESC);
            }

            String imgUrl = "";
            if(bookInfo.has(IMG_URL_PATH) && bookInfo.getJSONObject(IMG_URL_PATH).has(IMG_URL)) {
                imgUrl = bookInfo.getJSONObject(IMG_URL_PATH).getString(IMG_URL);
            }

            writeBackBook(ean, title, subtitle, desc, imgUrl);

            if(bookInfo.has(AUTHORS)) {
                writeBackAuthors(ean, bookInfo.getJSONArray(AUTHORS));
            }
            if(bookInfo.has(CATEGORIES)){
                writeBackCategories(ean,bookInfo.getJSONArray(CATEGORIES) );
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error ", e);
            broadcastEvent (MESSAGE_FETCH_EVENT, FETCH_OTHER_FAILURE);
        }
    }

    /**
     * broadcastEvent -- send broadcast event with given details
     * @param eventString
     * @param eventDetails
     */
    private void broadcastEvent (String eventString, String eventDetails) {
        Intent messageIntent = new Intent(eventString);
        messageIntent.putExtra(MESSAGE_KEY, eventDetails);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(messageIntent);
    }

    /**
     * fetchRequired -- check to see if ean should be fetched
     * @param ean
     * @return true if ean length valid and book does not exist in library
     */
    private boolean fetchRequired (String ean) {
        if(ean.length()!=13){
            return false;
        }

        // see if the book is already in the library
        Cursor bookEntry = getContentResolver().query(
                AlexandriaContract.BookEntry.buildBookUri(Long.parseLong(ean)),
                null, // leaving "columns" null just returns all the columns.
                null, // cols for "where" clause
                null, // values for "where" clause
                null  // sort order
        );

        if(bookEntry.getCount()>0){
            bookEntry.close();
            return false;
        }

        bookEntry.close();
        return true;
    }

    /**
     * createConnection -- creates connection to Google API using the ean to identify the book
     * @param ean
     * @return active connection when successful, null when connection fails
     */
    private HttpURLConnection createConnection (String ean) {
        final String BASE_URL = "https://www.googleapis.com/books/v1/volumes?";
        final String QUERY_PARAM = "q";
        final String ISBN_PARAM = "isbn:" + ean;

        try {
            Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, ISBN_PARAM)
                    .build();
            URL url = new URL(builtUri.toString());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            return urlConnection;

        } catch (Exception e) {
            Log.e(LOG_TAG, "Error ", e);
        }

        return null;
    }

    /**
     * readString -- reads the connection stream to get JSON book data
     * @param urlConnection
     * @return JSON string if successful, otherwise null
     */
    private String readStream (HttpURLConnection urlConnection) {

        try {
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }

            reader.close ();
            if (buffer.length() == 0) {
                return null;
            }
            return buffer.toString();

        } catch (Exception e) {
            Log.e(LOG_TAG, "Error ", e);
        }
        return null;
    }

    /**
     * writeBackBook -- original Alexandria code to write book to database
     * @param ean
     * @param title
     * @param subtitle
     * @param desc
     * @param imgUrl
     */
    private void writeBackBook(String ean, String title, String subtitle, String desc, String imgUrl) {
        ContentValues values= new ContentValues();
        values.put(AlexandriaContract.BookEntry._ID, ean);
        values.put(AlexandriaContract.BookEntry.TITLE, title);
        values.put(AlexandriaContract.BookEntry.IMAGE_URL, imgUrl);
        values.put(AlexandriaContract.BookEntry.SUBTITLE, subtitle);
        values.put(AlexandriaContract.BookEntry.DESC, desc);
        getContentResolver().insert(AlexandriaContract.BookEntry.CONTENT_URI,values);
    }

    /**
     * writeBackAuthors -- original Alexandria code to write authors to database
     * @param ean
     * @param jsonArray
     * @throws JSONException
     */
    private void writeBackAuthors(String ean, JSONArray jsonArray) throws JSONException {
        ContentValues values= new ContentValues();
        for (int i = 0; i < jsonArray.length(); i++) {
            values.put(AlexandriaContract.AuthorEntry._ID, ean);
            values.put(AlexandriaContract.AuthorEntry.AUTHOR, jsonArray.getString(i));
            getContentResolver().insert(AlexandriaContract.AuthorEntry.CONTENT_URI, values);
            values= new ContentValues();
        }
    }

    /**
     * writeBackCategories -- original Alexandria code to write category database
     * @param ean
     * @param jsonArray
     * @throws JSONException
     */
    private void writeBackCategories(String ean, JSONArray jsonArray) throws JSONException {
        ContentValues values= new ContentValues();
        for (int i = 0; i < jsonArray.length(); i++) {
            values.put(AlexandriaContract.CategoryEntry._ID, ean);
            values.put(AlexandriaContract.CategoryEntry.CATEGORY, jsonArray.getString(i));
            getContentResolver().insert(AlexandriaContract.CategoryEntry.CONTENT_URI, values);
            values= new ContentValues();
        }
    }
 }