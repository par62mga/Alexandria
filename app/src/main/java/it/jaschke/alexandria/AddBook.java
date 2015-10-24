package it.jaschke.alexandria;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;

/**
 * AddBook -- fragment used to support add books.
 *
 */
public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String  SCAN_ISBN_KEY = "isbnScan";

    private static final int    LOADER_ID   = 1;
    private static final String EAN_CONTENT = "eanContent";

    private EditText    mEditTextEAN;
    private Button      mButtonScan;
    private TextView    mTextViewBookTitle;
    private TextView    mTextViewBookSubTitle;
    private TextView    mTextViewAuthors;
    private TextView    mTextViewCategories;
    private ImageView   mImageViewBookCover;
    private ImageButton mButtonSave;
    private ImageButton mButtonDelete;

    // moved broadcast receiver to AddBook to more gracefully handle fetch errors
    private BroadcastReceiver mBroadcastReceiver;

    private String mFetchEAN = null;

    /**
     * MessageReceiver -- used to handle broadcast sent from BookService when the provided EAN
     *      cannot be fetched
     */
    private class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive (Context context, Intent intent) {
            String errorString = intent.getStringExtra(BookService.MESSAGE_KEY);
            if ( (mFetchEAN != null) && (errorString != null) ) {
                int textResource = R.string.error_add_book_other;
                if (errorString.contentEquals (BookService.FETCH_NOT_FOUND)) {
                    textResource = R.string.error_add_book_not_found;
                } else if (errorString.contentEquals (BookService.FETCH_ALREADY_PRESENT)) {
                    textResource = R.string.error_add_book_already_present;
                } else if (errorString.contentEquals (BookService.FETCH_NETWORK_FAILURE)) {
                    textResource = R.string.error_add_book_network;
                } else if (errorString.contentEquals (BookService.FETCH_SERVER_FAILURE)) {
                    textResource = R.string.error_add_book_server;
                }

                // show failure message below the ISBN entered/scanned by the user
                mTextViewBookSubTitle.setText (getString(textResource));
                mFetchEAN = null;
            }
        }
    }

    public AddBook () {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mEditTextEAN != null) {
            outState.putString(EAN_CONTENT, mEditTextEAN.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_add_book, container, false);

        mEditTextEAN          = (EditText)rootView.findViewById(R.id.ean);
        mButtonScan           = (Button)rootView.findViewById(R.id.scan_button);
        mTextViewBookTitle    = (TextView)rootView.findViewById(R.id.bookTitle);
        mTextViewBookSubTitle = (TextView)rootView.findViewById(R.id.bookSubTitle);
        mTextViewAuthors      = (TextView)rootView.findViewById(R.id.authors);
        mTextViewCategories   = (TextView)rootView.findViewById(R.id.categories);
        mImageViewBookCover   = (ImageView)rootView.findViewById(R.id.bookCover);
        mButtonSave           = (ImageButton)rootView.findViewById(R.id.save_button);
        mButtonDelete         = (ImageButton)rootView.findViewById(R.id.delete_button);

        // make detail fields and buttons invisible until a book is loaded
        clearFields();

        mEditTextEAN.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                String ean = s.toString();
                //catch isbn10 numbers
                if (ean.length() == 10 && !ean.startsWith("978")) {
                    ean = Utility.convertISBN10toISBN13(ean);
                    // ean="978"+ean; (this did not work)
                }
                if (ean.length() < 13) {
                    clearFields();
                    return;
                }

                fetchEAN(ean);
            }
        });

        // handle "DONE" press during EAN entry
        mEditTextEAN.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int editorAction, KeyEvent keyEvent) {

                // if user pressed done, let them know they need to enter a 10 or 13 digit ISBN
                if ( (editorAction == EditorInfo.IME_ACTION_DONE) ||
                        (editorAction == EditorInfo.IME_ACTION_NEXT)) {
                    Toast doneFeedback = Toast.makeText(
                            getActivity(), R.string.toast_done_pressed, Toast.LENGTH_SHORT);
                    doneFeedback.setGravity(Gravity.CENTER, 0, 0);
                    doneFeedback.show();

                    // clear entry and let user try again
                    enableEdits();
                    return true;
                }
                return false;
            }
        });

        // handle click on EAN entry to make sure soft keypad displays
        mEditTextEAN.setOnClickListener (new EditText.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)
                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
            }
        });

        mButtonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This is the callback method that the system will invoke when your button is
                // clicked.
                // The below leverages navigation drawer handling to replace the AddBook fragment
                // with the ScanBook fragment.
                NavigationDrawerFragment.NavigationDrawerCallbacks mCallbacks;
                try {
                    mCallbacks = (NavigationDrawerFragment.NavigationDrawerCallbacks) getActivity();
                } catch (ClassCastException e) {
                    throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
                }
                mCallbacks.onNavigationDrawerItemSelected(
                        NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_SCAN_BOOK, null);
            }
        });

        mButtonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // book is already in database so "save" just clears the current book
                mFetchEAN = null;
                enableEdits();
            }
        });

        mButtonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // delete the current book, no need to check for NULL fetch EAN as it SHOULD
                // exist at this point
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, mFetchEAN);
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                mFetchEAN = null;
                enableEdits();
            }
        });

        if (savedInstanceState != null) {
            mEditTextEAN.setText(savedInstanceState.getString(EAN_CONTENT));
            // not sure why original author fiddled with the hint...seems to work fine without
            // mEditTextEAN.setHint("");
        } else {
            // see if fragment launched with scanned EAN
            Bundle arguments = getArguments ();
            String ean = ( (arguments == null) ? null : arguments.getString (SCAN_ISBN_KEY, null));
            if (ean != null) {
                if (ean.length() == 10 && !ean.startsWith("978")) {
                    ean = Utility.convertISBN10toISBN13(ean);
                }
                if (ean.length() == 13) {
                    fetchEAN (ean);
                    mEditTextEAN.setText(ean);
                    // mEditTextEAN.setHint("");
                }
            }
        }

        if (! getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) ) {
            mButtonScan.setEnabled(false);
            mButtonScan.setClickable(false);
        }

        return rootView;
    }

    @Override
    public void onPause () {
        // added broadcast receiver handling to AddBook since this is the best place to deal
        // with fetch failures
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    @Override
    public void onResume () {
        // added broadcast receiver handling to AddBook since this is the best place to deal
        // with fetch failures
        mBroadcastReceiver  = new MessageReceiver ();
        IntentFilter filter = new IntentFilter(BookService.MESSAGE_FETCH_EVENT);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastReceiver,filter);
        getActivity().setTitle(R.string.menu_scan);
        super.onResume();
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mFetchEAN == null) {
            return null;
        }

        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(mFetchEAN)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        // ok, we found a book, show details and ONLY enable Save and Delete actions
        // this fixes the problem where the user could change the EAN and cause the book to
        // disappear
        disableEdits();
        showTextView(mTextViewBookTitle,
                data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE)));
        showTextView(mTextViewBookSubTitle,
                data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE)));
        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        if (authors != null) {
            String[] authorsArr = authors.split(",");
            mTextViewAuthors.setLines(authorsArr.length);
            mTextViewAuthors.setText(authors.replace(",", "\n"));
        } else {
            mTextViewAuthors.setText("");
        }
        mTextViewAuthors.setVisibility(View.VISIBLE);
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if(Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage(mImageViewBookCover).execute(imgUrl);
            mImageViewBookCover.setVisibility(View.VISIBLE);
        }
        showTextView(mTextViewCategories,
                data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY)));
        mButtonSave.setVisibility(View.VISIBLE);
        mButtonDelete.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    /**
     * fetchEAN -- initiate fetch of the given EAN by invoking the BookService and loader
     * @param ean
     */
    private void fetchEAN (String ean) {
        // save EAN we're searching for...
        mFetchEAN = ean;

        // hide keypad
        mEditTextEAN.clearFocus();
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditTextEAN.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        // show searching...
        mTextViewBookSubTitle.setVisibility(View.VISIBLE);
        mTextViewBookSubTitle.setText(getString(R.string.search_for_isbn) + mFetchEAN);

        //Once we have an ISBN, start a book intent
        Intent bookIntent = new Intent(getActivity(), BookService.class);
        bookIntent.putExtra(BookService.EAN, mFetchEAN);
        bookIntent.setAction(BookService.FETCH_BOOK);
        getActivity().startService(bookIntent);
        restartLoader();
    }

    /**
     * restartLoader -- restart loader action (requires mFetchEAN to be non-null
     */
    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    /**
     * showTextView -- make sure text is non-null and update the TextView making it visible
     * @param textView
     * @param textToShow
     */
    private void showTextView (TextView textView, String textToShow) {
        if (textToShow != null) {
            textView.setText (textToShow);
        } else {
            textView.setText ("");
        }
        textView.setVisibility(View.VISIBLE);
    }

    /**
     * clearFields -- make all fields invisible, this fixes an issue where the soft keypad
     *     could come up when a user "advanced" to a empty TextView
     */
    private void clearFields(){
        // using invisible rather than blank text to not allow DONE to move focus to other fields
        mTextViewBookTitle.setVisibility(View.INVISIBLE);
        mTextViewBookSubTitle.setVisibility(View.INVISIBLE);
        mTextViewAuthors.setVisibility(View.INVISIBLE);
        mTextViewCategories.setVisibility(View.INVISIBLE);
        mImageViewBookCover.setVisibility(View.INVISIBLE);
        mButtonSave.setVisibility(View.INVISIBLE);
        mButtonDelete.setVisibility(View.INVISIBLE);
    }

    /**
     *  disableEdits -- this keeps the user from being able to click/update the EAN or other
     *     fields before pressing save or delete
     */
    private void disableEdits () {
        mEditTextEAN.setEnabled(false);
        mEditTextEAN.setClickable(false);
        mButtonScan.setEnabled(false);
        mButtonScan.setClickable(false);

        // this is needed to make sure the keypad is hidden when book details shown
        Utility.hideSoftInput(getActivity());
    }

    /**
     *  enableEdits -- after a book is saved or deleted, this allows the user to update the
     *     EAN
     */
    private void enableEdits () {
        mEditTextEAN.setEnabled(true);
        mEditTextEAN.setClickable(true);
        mEditTextEAN.setText("");
        // mEditTextEAN.setHint(getString(R.string.hint_input));
        if ( getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) ) {
            mButtonScan.setEnabled(true);
            mButtonScan.setClickable(true);
        }
        // this is needed to make the keypad come back after re-enabling the EditText
        // why does Android make managing the soft keypad so complicated...
        if (mEditTextEAN.requestFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mEditTextEAN, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
