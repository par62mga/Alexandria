package it.jaschke.alexandria;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import it.jaschke.alexandria.api.Callback;

/**
 * Changes made as part of the Alexandria project:
 *
 * Required Components in the Rubric:
 * 1) Alexandria has barcode scanning -- Implemented ISBN scan feature using ZBar library (see
 *    ScanBook and MainActivity changes)
 * 2) Alexandria does not crash -- Fixed various problems with BookService to address crashes when
 *    server or internet connectivity was not available. There were also a few other areas with
 *    null pointer crashes that were addressed.
 *
 * Optional Components:
 * 1) Bar code scanning does not require a separate app as ZBar library is compiled with the
 *    application.
 * 2) Extra error cases found and addressed (see list below)
 * 3) Included all "UI" strings into strings.xml, included "translatable=false" where needed
 *
 *  Issues found/fixed in original Alexandria application include:
 *  - In AddBook, when entering ISBN-13 in mEditTextEAN field, DONE incorrectly advanced to the
 *    book title TextView, added error toast that book was not found and kept user in the
 *    mEditTextEAN field.
 *  - In AddBook, ISBN-10 entry was not handled properly by only adding "978", pulled in sample
 *    code from stack overflow to properly convert ISBN-10 to ISBN-13.
 *  - In AddBook, multiple "findViewById" searches were inefficient, moved all of these to
 *    onCreateView
 *  - Various errors were not handled by BookService (crashes, etc.), updated the broadcast
 *    message to identify exactly what when wrong to provide better feedback to the user. Moved
 *    broadcast receiver to Addbook to used fixed message in TextView rather than Toast to
 *    make it easier for user to view the exact problem.
 *  - Back navigation was a mess. Fixed MainActivity to only put one instance of a fragment on
 *    the backstack. The wrong title was often shown on the navigation bar and fixed this by
 *    by changing the title update to onResume() in ListOfBooks, AddBook and Settings.
 *  - Fixed problem with disappearing book detail and only allow one book detail fragment to be
 *    pushed on back stack
 *  - Tablet rotation was also broken especially when book detail was shown. Fixed to properly
 *    handle changing view between single and multi-panel views.
 *  - In ListOfBooks, when book image is not available from google, a blank image was shown and
 *    the book list was misaligned, updated to show default Alexandria image
 *  - Fixed race when book deleted where sometimes the book would still show up in ListOfBooks
 *    by adding a new broadcast message that tells ListOfBooks when a delete operation is done
 *  - Remove unused classes/variables/strings that were placeholders for SCAN feature
 *  - Fixed cases where soft input kept popping up such as during drawer navigation and before
 *    the user selected the search field
 *  - Updated settings to properly show summary for the selected start screen
 *  - Corrected issues with RTL support and checked RTL behavior correct
 *  - Corrected accessibility issues and content descriptions and checked using TalkBack
 */

/**
 * MainActivity -- activity that handles the navigation drawer and all other active fragments.
 */
public class MainActivity
        extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, Callback {

    private final String LOG_TAG = MainActivity.class.getSimpleName();

    // tags used to identify and better manage fragments on the backstack
    private static final String TAG_FRAGMENT_ADD    = "fragment_add";
    private static final String TAG_FRAGMENT_ABOUT  = "fragment_about";
    private static final String TAG_FRAGMENT_SCAN   = "fragment_scan";
    private static final String TAG_FRAGMENT_LIST   = "fragment_list";
    private static final String TAG_FRAGMENT_DETAIL = "fragment_detail";

    // used to see if configuration changed to/from portrait and landscape mode and restore book
    // detail
    private static boolean mTwoPanels  = false;
    private static String  mRestoreEAN = null;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment navigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence title;

    // No longer used, moved to AddBook ==> private BroadcastReceiver messageReciever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean foundDetail = false;
        if (isTablet()) {
            // showing book detail during rotation is problematic, so pop it off the stack
            foundDetail = removeFragmentFromBackStack(TAG_FRAGMENT_DETAIL);

            // TODO: could make app better by remembering fragments popped off and push back
            //     which would put things in the correct container

            setContentView(R.layout.activity_main_tablet);
            mTwoPanels = (findViewById(R.id.right_container) != null);
            Log.d (LOG_TAG, "onCreate: twoPanels ==> " + mTwoPanels +
                            " foundDetail ==> " + foundDetail);
        } else {
            setContentView(R.layout.activity_main);
        }

        // messageReciever = new MessageReciever();
        // IntentFilter filter = new IntentFilter(MESSAGE_EVENT);
        // LocalBroadcastManager.getInstance(this).registerReceiver(messageReciever,filter);
        navigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        title = getTitle();

        // Set up the drawer.
        navigationDrawerFragment.setUp(R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        if (foundDetail) {
            onItemSelected (mRestoreEAN);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int selection, String scanISBN) {
        // updated to launch scan book from add book and to launch add book with the scanned EAN
        // after a successful scan. Also call addFragmentToContainer to better handle the backstack.
        switch (selection){
            case NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_ADD_BOOK:
                Fragment addFragment = new AddBook();
                if (scanISBN != null) {
                    Bundle arguments = new Bundle ();
                    arguments.putString (AddBook.SCAN_ISBN_KEY, scanISBN);
                    addFragment.setArguments (arguments);
                }
                addFragmentToContainer (R.id.container, addFragment, TAG_FRAGMENT_ADD);
                break;

            case NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_ABOUT:
                addFragmentToContainer (R.id.container, new About(), TAG_FRAGMENT_ABOUT);
                break;

            case NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_SCAN_BOOK:
                addFragmentToContainer (R.id.container, new ScanBook(), TAG_FRAGMENT_SCAN);
                break;

            case NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_LIST_OF_BOOKS:
            default:
                addFragmentToContainer (R.id.container, new ListOfBooks(), TAG_FRAGMENT_LIST);
                break;
        }
    }

    // updated to not only save the title, but to make sure this is the title shown on the nav bar
    public void setTitle(int titleId) {
        // save title for future "restore", and also make sure current title is updated
        title = getString(titleId);
        getSupportActionBar().setTitle(title);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(title);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!navigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReciever);
        super.onDestroy();
    }

    @Override
    public void onItemSelected(String ean) {
        Bundle args = new Bundle();
        args.putString(BookDetail.EAN_KEY, ean);

        BookDetail fragment = new BookDetail();
        fragment.setArguments(args);

        int id = mTwoPanels ? R.id.right_container : R.id.container;
        addFragmentToContainer(id, fragment, TAG_FRAGMENT_DETAIL);

        // save EAN for book detail restore on screen rotation
        mRestoreEAN = ean;
    }

    @Override
    public void onBackPressed() {
        // see if drawer is open and handle back by closing it
        if (navigationDrawerFragment.isDrawerOpen () ) {
            navigationDrawerFragment.closeDrawer();
            return;
        }

        if(getSupportFragmentManager().getBackStackEntryCount()<2){
            Log.d (LOG_TAG, "onBackPressed: calling finish()");
            finish();
        }
        super.onBackPressed();
    }

    /**
     * code removed as the broadcast receiver was moved into AddBook where the error result
     * could be handled more gracefully
     *
    private class MessageReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getStringExtra(MESSAGE_KEY)!=null){
                Toast.makeText(MainActivity.this, intent.getStringExtra(MESSAGE_KEY), Toast.LENGTH_LONG).show();
            }
        }
    }
    */

    public void goBack(View view){
        getSupportFragmentManager().popBackStack();
    }

    /**
     * addFragmentToContainer -- replaces the fragment in the given container and adds the given
     *     fragment to the backstack. Before the fragment is added, the backstack is cleaned up
     *     to make sure that only one instance of a fragment is on the stack to keep it from
     *     growing too deep and confuse the user.
     * @param containerId
     * @param fragment
     * @param fragmentTag
     */
    private void addFragmentToContainer (int containerId, Fragment fragment, String fragmentTag) {
        Log.d(LOG_TAG, "addFragmentToContainer: Adding fragment ==> " + fragmentTag);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // make sure this fragment is only on the backstack once...
        removeFragmentFromBackStack(fragmentTag);

        // make sure we only save detail on the back stack when we have book list
        if ( (! fragmentTag.contentEquals(TAG_FRAGMENT_LIST)   ) &&
             (! fragmentTag.contentEquals(TAG_FRAGMENT_DETAIL)))    {
            removeFragmentFromBackStack(TAG_FRAGMENT_DETAIL);
        }

        fragmentTransaction.replace(containerId, fragment);
        fragmentTransaction.addToBackStack(fragmentTag);
        fragmentTransaction.commit();
    }

    /**
     * removeFragmentFromBackStack -- used to find a fragment on the backstack and that fragment
     *     and others above it from the top of the backstack
     * @param fragmentTag
     * @return boolean, true when the fragment was found and popped off the backstack
     */
    private boolean removeFragmentFromBackStack (String fragmentTag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        int numberToPop = 0;
        for (int i = 0; i < fragmentManager.getBackStackEntryCount(); i++) {
            String backStackTag = fragmentManager.getBackStackEntryAt(i).getName();
            Log.d(LOG_TAG, "removeFragmentFromBackStack: Tag found ==> " + backStackTag +
                    " @ " + String.valueOf(i));
            if (backStackTag.contentEquals(fragmentTag)) {
                numberToPop = fragmentManager.getBackStackEntryCount() - i;
            }
        }
        Log.d(LOG_TAG,
                "removeFragmentFromBackStack: Number to pop ==> " + String.valueOf(numberToPop));
        if (numberToPop == 0) {
            return false;
        }
        while (numberToPop-- > 0) {
            // popBackStackImmediate is needed since we can perform remove operations back to back
            fragmentManager.popBackStackImmediate ();
        }
        return true;
    }

    private boolean isTablet() {
        return (getApplicationContext().getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

}