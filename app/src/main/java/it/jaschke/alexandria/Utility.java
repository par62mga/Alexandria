package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

/**
 * Utility class defined to support general and shared helper methods
 */
public class Utility {

    /**
     * convertISBN10toISBN13 -- code lifted from Stack Overflow that properly converts an ISBN10 to
     *     ISBN13. Original Alexandria code only added "978", but in many cases the MOD10 check
     *     digit was incorrect.
     *
     * @param ISBN10
     * @return ISBN13 string
     */
   public static String convertISBN10toISBN13( String ISBN10 ) {
        String ISBN13  = ISBN10;
        ISBN13 = "978" + ISBN13.substring(0,9);
        //if (LOG_D) Log.d(TAG, "ISBN13 without sum" + ISBN13);
        int d;

        int sum = 0;
        for (int i = 0; i < ISBN13.length(); i++) {
            d = ((i % 2 == 0) ? 1 : 3);
            sum += ((((int) ISBN13.charAt(i)) - 48) * d);
            //if (LOG_D) Log.d(TAG, "adding " + ISBN13.charAt(i) + "x" + d + "=" + ((((int) ISBN13.charAt(i)) - 48) * d));
        }
        sum = 10 - (sum % 10);
        return ISBN13 + String.valueOf(sum);
    }


    /**
     * hideSoftInput -- code used to hide input associated with any active view and also to make
     *     sure the keypad does not come up right away for the current view (such as ListOfBooks)
     * @param activity
     */
    public static void hideSoftInput (Activity activity) {
        // this is needed to make sure the keypad is hidden and does not come up automatically
        // I really don't know why Android makes managing the soft keypad so complicated...
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }


    /**
     * isNetworkAvailable -- return TRUE if network is available, code is from Udacity Advanced
     *     Android Development
     * @param context
     * @return - true if network is connected or connecting
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

}
