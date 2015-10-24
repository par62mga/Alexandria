package it.jaschke.alexandria;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import it.jaschke.alexandria.api.BookListAdapter;
import it.jaschke.alexandria.api.Callback;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;

/**
 * ListOfBooks -- fragment used to support the book list
 *
 */
public class ListOfBooks extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private final String LOG_TAG = ListOfBooks.class.getSimpleName();

    private EditText mEditTextSearch;
    private ListView mListViewBooks;

    private BookListAdapter mBookListAdapter;

    private int mListViewPosition = ListView.INVALID_POSITION;

    private final int LOADER_ID = 10;

    private BroadcastReceiver mBroadcastReceiver;



    /**
     * MessageReceiver -- used to handle broadcast sent from BookService when a book has been
     *     deleted, tells us to refresh the list of books
     */
    private class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive (Context context, Intent intent) {
            String deletedEAN = intent.getStringExtra(BookService.MESSAGE_KEY);
            Log.d (LOG_TAG, "onReceive() -- deletedEAN ==> " + deletedEAN);
            ListOfBooks.this.restartLoader();
        }
    }

    public ListOfBooks() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d (LOG_TAG, "onCreateView()");
        View rootView = inflater.inflate(R.layout.fragment_list_of_books, container, false);
        mEditTextSearch = (EditText) rootView.findViewById(R.id.searchText);

        // implement click listener to make sure soft keypad comes up when user clicks on the field
        mEditTextSearch.setOnClickListener (new EditText.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)
                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
            }
        });

        rootView.findViewById(R.id.searchButton).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ListOfBooks.this.restartLoader();
                    }
                }
        );

        mListViewBooks = (ListView) rootView.findViewById(R.id.listOfBooks);

        // moved query here as there seemed to be a possible race where query returned before
        //     the view was inflated
        Cursor cursor = getActivity().getContentResolver().query(
                AlexandriaContract.BookEntry.CONTENT_URI,
                null, // leaving "columns" null just returns all the columns.
                null, // cols for "where" clause
                null, // values for "where" clause
                null  // sort order
        );

        mBookListAdapter = new BookListAdapter(getActivity(), cursor, 0);
        mListViewBooks.setAdapter(mBookListAdapter);

        mListViewBooks.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Cursor cursor = mBookListAdapter.getCursor();
                if (cursor != null && cursor.moveToPosition(position)) {
                    // hide keypad if showing
                    Utility.hideSoftInput(getActivity());
                    ((Callback) getActivity())
                            .onItemSelected(cursor.getString(cursor.getColumnIndex(AlexandriaContract.BookEntry._ID)));

                }
            }
        });

        return rootView;
    }

    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        final String selection = AlexandriaContract.BookEntry.TITLE +" LIKE ? OR " + AlexandriaContract.BookEntry.SUBTITLE + " LIKE ? ";
        String searchString = mEditTextSearch.getText().toString();

        if(searchString.length()>0){
            searchString = "%"+searchString+"%";
            return new CursorLoader(
                    getActivity(),
                    AlexandriaContract.BookEntry.CONTENT_URI,
                    null,
                    selection,
                    new String[]{searchString,searchString},
                    null
            );
        }

        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mBookListAdapter.swapCursor(data);
        if (mListViewPosition != ListView.INVALID_POSITION) {
            mListViewBooks.smoothScrollToPosition(mListViewPosition);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mBookListAdapter.swapCursor(null);
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
        // fixed but where "onAttach" would not always set up the title after back pressed...
        super.onResume();

        Log.d(LOG_TAG, "onResume, registering broadcast receiver and updating title...");

        // make sure the pesky keypad is not showing
        Utility.hideSoftInput(getActivity());

        // used to receive notice when book deleted from library
        mBroadcastReceiver  = new MessageReceiver ();
        IntentFilter filter = new IntentFilter(BookService.MESSAGE_DELETE_EVENT);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastReceiver, filter);

        getActivity().setTitle(R.string.menu_books);

        super.onResume();
    }
}
