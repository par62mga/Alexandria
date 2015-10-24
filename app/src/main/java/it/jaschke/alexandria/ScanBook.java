package it.jaschke.alexandria;


import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;


/**
 * ScanBook -- fragment that supports scanning using the ZBar library, with general usage coming
 *     straight from github: https://github.com/dm77/barcodescanner
 */
public class ScanBook extends Fragment  implements ZBarScannerView.ResultHandler {
    private final String LOG_TAG = ScanBook.class.getSimpleName();

    private ZBarScannerView mScannerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mScannerView = new ZBarScannerView(getActivity());
        return mScannerView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // make sure the pesky keypad is not showing
        Utility.hideSoftInput(getActivity());

        // per stackoverflow, this should speed up bar code reading since we only need these formats
        List<BarcodeFormat> barCodeFormats = new ArrayList<BarcodeFormat> ();
        barCodeFormats.add (BarcodeFormat.EAN13);
        barCodeFormats.add(BarcodeFormat.ISBN10);
        barCodeFormats.add(BarcodeFormat.ISBN13);
        mScannerView.setFormats(barCodeFormats);

        if ( getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS) ) {
            mScannerView.setAutoFocus(true);
        } else {
            Toast doneFeedback = Toast.makeText(
                    getActivity(), R.string.toast_no_auto_focus, Toast.LENGTH_LONG);
            // doneFeedback.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, 0, 0);
            doneFeedback.show();
        }

        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
        mScannerView.setFlash(true);
    }

    @Override
    public void handleResult(Result rawResult) {
        Log.d(LOG_TAG, "Contents = " + rawResult.getContents() +
                ", Format = " + rawResult.getBarcodeFormat().getName());

        // open up AddBook fragment and pass the results of the scan...
        NavigationDrawerFragment.NavigationDrawerCallbacks mCallbacks;
        try {
            mCallbacks = (NavigationDrawerFragment.NavigationDrawerCallbacks) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
        }
        mCallbacks.onNavigationDrawerItemSelected(
                NavigationDrawerFragment.NavigationDrawerCallbacks.SELECT_ADD_BOOK, rawResult.getContents());
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }
}
