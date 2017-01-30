package com.toxoidandroid.personalnotes;

import android.content.Context;
import android.os.AsyncTask;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class DropBoxImageUploadAsync extends AsyncTask<Void, Long, Boolean> {
    private DropboxAPI<?> mApi;
    private String mPath;
    private File mFile;
    private String mFilename;

    public DropBoxImageUploadAsync(Context context, DropboxAPI<?> mApi, File mFile, String mFilename) {
        this.mApi = mApi;
        this.mFile = mFile;
        this.mFilename = mFilename;
        this.mPath = AppSharedPreferences.getDropBoxUploadPath(context);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        String errorMessage;
        try {
            FileInputStream fis = new FileInputStream(mFile);
            String path = mPath + "/" + mFilename;
            DropboxAPI.UploadRequest request = mApi.putFileOverwriteRequest(path, fis, mFile.length(),
                    new ProgressListener() {

                        @Override
                        public long progressInterval() {
                            return 500;
                        }

                        @Override
                        public void onProgress(long bytes, long total) {
                            publishProgress(bytes);
                        }
                    });

            if(request != null) {
                request.upload();
                return true;
            }

        } catch(DropboxException e) {
            errorMessage = "Dropbox exception";
            // DropboxUnlinkedException, DropboxFileSizeException,DropboxPartialFileException,DropboxServerException
        } catch(FileNotFoundException e) {
            errorMessage = "File not found exception";
        }

        return false;
    }
}