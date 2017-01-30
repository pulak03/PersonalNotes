package com.toxoidandroid.personalnotes;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

import java.util.ArrayList;
import java.util.List;

public class DropBoxDirectoryListenerAsync extends AsyncTask<Void, Long, Boolean> {
    private Context mContext;
    private DropboxAPI<?> mApi;
    private List<String> mDirectories = new ArrayList<>();
    private String mErrorMessage;
    private String mCurrentDirectory;
    private OnLoadFinished mListener;

    public DropBoxDirectoryListenerAsync(Context context, DropboxAPI<?> api, String currentDirectory, OnLoadFinished listener) {
        mContext = context;
        mApi = api;
        mCurrentDirectory = currentDirectory;
        mListener = listener;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            mErrorMessage = null;
            DropboxAPI.Entry directoryEntry = mApi.metadata(mCurrentDirectory, 1000, null, true, null);
            if(!directoryEntry.isDir || directoryEntry.contents == null) {
                mErrorMessage = "File or empty directory";
                return false;
            }
            for (DropboxAPI.Entry entry : directoryEntry.contents) {
                if(entry.isDir) {
                    mDirectories.add(entry.fileName());
                }
            }


        } catch(DropboxUnlinkedException e) {
            mErrorMessage = "Authentication dropbox error!";
        } catch(DropboxPartialFileException e) {
            mErrorMessage = "Download canceled";
        } catch(DropboxServerException e) {
            mErrorMessage = "Network error, try again";
        } catch(DropboxParseException e) {
            mErrorMessage = "Dropbox Parse excepton, try again";
        } catch(DropboxException e) {
            mErrorMessage = "Unknown Dropbox error, try again";

        }

        if(mErrorMessage != null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if(result) {
            mListener.onLoadFinished(mDirectories);
        } else {
            showToast(mErrorMessage);
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        error.show();
    }



    public interface OnLoadFinished {
        void onLoadFinished(List<String> values);
    }

}
