package com.toxoidandroid.personalnotes;



import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;


public class NotesActivity extends BaseActivity implements
        LoaderManager.LoaderCallbacks<List<Note>>, ConnectionCallbacks,
        OnConnectionFailedListener{

    private List<Note> mNotes;
    private RecyclerView mRecyclerView;
    private NotesAdapter mNotesAdapter;
    private ContentResolver mContentResolver;
    private static Boolean mIsInAuth;
    public static Bitmap mSendingImage = null;
    private boolean mIsImageNotFound = false;
    private DropboxAPI<AndroidAuthSession> mDropboxAPI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_layout);
        activateToolbar();
        setUpForDropbox();
        setUpNavigationDrawer();
        setUpRecyclerView();
        setUpActions();
    }

    private void setUpForDropbox() {
        AndroidAuthSession session = DropBoxActions.buildSession(getApplicationContext());
        mDropboxAPI = new DropboxAPI<>(session);
    }

    private void setUpRecyclerView() {
        mContentResolver = getContentResolver();
        mNotesAdapter = new NotesAdapter(NotesActivity.this, new ArrayList<Note>());
        int LOADER_ID = 1;
        getSupportLoaderManager().initLoader(LOADER_ID, null, NotesActivity.this);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view_home);
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getApplicationContext(), 1);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setAdapter(mNotesAdapter);
        mRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(this,
                mRecyclerView, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                edit(view);
            }

            @Override
            public void onItemLongClick(View view, int position) {
                PopupMenu popupMenu = new PopupMenu(NotesActivity.this, view);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.action_notes, popupMenu.getMenu());
                popupMenu.show();
                final View v = view;
                final int pos = position;
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if(item.getItemId() == R.id.action_delete) {
                            moveToTrash();
                            delete(v, pos);
                        } else if(item.getItemId() == R.id.action_archive) {
                            moveToArchive(v, pos);
                        } else if(item.getItemId() == R.id.action_edit) {
                            edit(v);
                        }

                        return false;
                    }
                });
            }
        }));
    }

    @Override
    public Loader<List<Note>> onCreateLoader(int id, Bundle args) {
        mContentResolver = getContentResolver();
        return new NotesLoader(NotesActivity.this, mContentResolver, BaseActivity.mType);
    }

    @Override
    public void onLoadFinished(Loader<List<Note>> loader, List<Note> data) {
        this.mNotes = data;
        Thread[] thread = new Thread[mNotes.size()];
        int threadCounter = 0;
        for (final Note aNote: mNotes) {
            if (AppConstant.GOOGLE_DRIVE_SELECTION == aNote.getStorageSelection()) {
                GDUT.init(this);
                if(checkPlayServices() && checkUserAccount()) {
                    GDActions.init(this, GDUT.AM.getActiveEmil());
                    GDActions.connect(true);
                }
                thread[threadCounter] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        do {
                            ArrayList<GDActions.GF> gfs = GDActions.search(AppSharedPreferences.getGoogleDriveResourceId(getApplicationContext()),
                                    aNote.getImagePath(), GDUT.MIME_JPEG);
                            if(gfs.size()>0) {
                                byte[] imageBytes = GDActions.read(gfs.get(0).id, 0);
                                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                aNote.setBitmap(bmp);
                                mIsImageNotFound = false;
                                mNotesAdapter.setData(mNotes);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mNotesAdapter.notifyImageObtained();
                                    }
                                });
                            } else {
                                aNote.setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_loading));
                                mIsImageNotFound = true;
                                try {
                                    Thread.sleep(500);
                                } catch(InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } while(mIsImageNotFound);
                    }
                });
                thread[threadCounter].start();
                threadCounter++;

            } else if(AppConstant.DROP_BOX_SELECTION == aNote.getStorageSelection()) {
                thread[threadCounter] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        do {
                            Drawable drawable = getImageFromDropbox(mDropboxAPI,
                                    AppSharedPreferences.getDropBoxUploadPath(getApplicationContext()),
                                    aNote.getImagePath());
                            if(drawable != null) {
                                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                aNote.setBitmap(bitmap);
                            }
                            if(!mIsImageNotFound) {
                                mNotesAdapter.setData(mNotes);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mNotesAdapter.notifyImageObtained();
                                    }
                                });
                            }
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } while(mIsImageNotFound);
                    }
                });
                thread[threadCounter].start();
                threadCounter++;
            } else {
                aNote.setHasNoImage(true);
            }
        }
        mNotesAdapter.setData(mNotes);
        changeNoItemTag();
    }

    private Drawable getImageFromDropbox(DropboxAPI<?> mApi, String mPath, String filename) {
        FileOutputStream fos;
        Drawable drawable;
        String cachePath = getApplicationContext().getCacheDir().getAbsolutePath() + "/" + filename;
        File cacheFile = new File(cachePath);
        if(cacheFile.exists()) {
            mIsImageNotFound = false;
            return Drawable.createFromPath(cachePath);
        } else {
            try {
                DropboxAPI.Entry dirEnt = mApi.metadata(mPath, 1000, null, true, null);
                if(!dirEnt.isDir || dirEnt.contents == null) {
                    mIsImageNotFound = true;
                }
                ArrayList<DropboxAPI.Entry> thumbs = new ArrayList<DropboxAPI.Entry>();
                for (DropboxAPI.Entry ent : dirEnt.contents) {
                    if(ent.thumbExists) {
                        if(ent.fileName().startsWith(filename)) {
                            thumbs.add(ent);
                        }
                    }
                }
                if(thumbs.size() == 0) {
                    mIsImageNotFound = true;
                } else {
                    DropboxAPI.Entry ent = thumbs.get(0);
                    String path = ent.path;
                    try {
                        fos = new FileOutputStream(cachePath);
                    } catch (FileNotFoundException e) {
                        return getResources().getDrawable(R.drawable.ic_image_deleted);
                    }
                    mApi.getThumbnail(path, fos, DropboxAPI.ThumbSize.BESTFIT_960x640,
                            DropboxAPI.ThumbFormat.JPEG, null);
                    drawable = Drawable.createFromPath(cachePath);
                    mIsImageNotFound = false;
                    return drawable;
                }
            } catch (DropboxException e) {
                e.printStackTrace();
                mIsImageNotFound = true;
            }

            drawable = getResources().getDrawable(R.drawable.ic_loading);
            return drawable;
        }
    }

    private void changeNoItemTag() {
        TextView noItemTextView = (TextView) findViewById(R.id.no_item_textview);
        if(mNotesAdapter.getItemCount() !=0) {
            noItemTextView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            noItemTextView.setText(AppConstant.EMPTY);
            noItemTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Note>> loader) {
        mNotesAdapter.setData(null);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if(!mIsInAuth) {
            if(connectionResult.hasResolution()) {
                try {
                    mIsInAuth = true;
                    connectionResult.startResolutionForResult(this, AppConstant.REQ_AUTH);
                } catch(IntentSender.SendIntentException e) {
                    e.printStackTrace();
                    finish();
                }
            } else {
                finish();
            }
        }
    }


    @Override
    public void onConnected(Bundle bundle) {

    }

    private boolean checkUserAccount() {
        String email = GDUT.AM.getActiveEmil();
        Account account = GDUT.AM.getPrimaryAccnt(true);
        if(email == null) {
            if(account == null) {
                account = GDUT.AM.getPrimaryAccnt(false);
                Intent accountIntent = AccountPicker.newChooseAccountIntent(account, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true,
                        null, null, null, null);
                startActivityForResult(accountIntent, AppConstant.REQ_ACCPICK);
                return false;
            } else {
                GDUT.AM.setEmil(account.name);
            }
            return true;
        }
        account = GDUT.AM.getActiveAccnt();
        if(account == null) {
            Intent accountIntent = AccountPicker.newChooseAccountIntent(account, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true,
                    null, null, null, null);
            startActivityForResult(accountIntent, AppConstant.REQ_ACCPICK);
            return false;
        }

        return true;

    }


    private boolean checkPlayServices() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(status != ConnectionResult.SUCCESS) {
            if(GooglePlayServicesUtil.isUserRecoverableError(status)) {
                errorDialog(status, AppConstant.REQ_RECOVER);
            } else {
                finish();
            }
            return false;
        }

        return true;
    }

    private void errorDialog(int errorCode, int requestCode) {
        Bundle args = new Bundle();
        args.putInt(AppConstant.DIALOG_ERROR, errorCode);
        args.putInt(AppConstant.REQUEST_CODE, requestCode);
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), AppConstant.DIALOG_ERROR);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_notes, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void moveToTrash() {
        ContentValues values = new ContentValues();
        TextView title = (TextView) findViewById(R.id.title_note_custom_home);
        TextView description = (TextView) findViewById(R.id.description_note_custom_home);
        TextView dateTime = (TextView) findViewById(R.id.date_time_note_custom_home);
        values.put(TrashContract.TrashColumns.TRASH_TITLE, title.getText().toString());
        values.put(TrashContract.TrashColumns.TRASH_DESCRIPTION, description.getText().toString());
        values.put(TrashContract.TrashColumns.TRASH_DATE_TIME, dateTime.getText().toString());
        ContentResolver cr = this.getContentResolver();
        Uri uri = TrashContract.URI_TABLE;
        cr.insert(uri, values);
    }

    private void moveToArchive(View view, int position) {
        ContentValues values = new ContentValues();
        TextView title = (TextView) findViewById(R.id.title_note_custom_home);
        TextView description = (TextView) findViewById(R.id.description_note_custom_home);
        TextView dateTime = (TextView) findViewById(R.id.date_time_note_custom_home);

        LinearLayout linearLayout = (LinearLayout) view.findViewById(R.id.home_list);
        int isList = linearLayout.getVisibility();
        String listDescription = "";
        if(isList == View.VISIBLE) {
            NoteCustomList noteCustomList = (NoteCustomList) linearLayout.getChildAt(0);
            listDescription = noteCustomList.getLists();
//            for(int i=0; i<noteCustomList.getChildCount(); i++) {
//                LinearLayout first = (LinearLayout) noteCustomList.getChildAt(i);
//                CheckBox bx = (CheckBox) first.getChildAt(0);
//                TextView cx = (TextView) first.getChildAt(1);
//                listDescription = description + cx.getText().toString() + bx.isChecked() + "%";
//            }
            values.put(ArchivesContract.ArchivesColumns.ARCHIVES_TYPE, AppConstant.LIST);
        } else {
            listDescription = description.getText().toString();
            values.put(ArchivesContract.ArchivesColumns.ARCHIVES_TYPE, AppConstant.NORMAL);
        }

        values.put(ArchivesContract.ArchivesColumns.ARCHIVES_DESCRIPTION, listDescription);
        values.put(ArchivesContract.ArchivesColumns.ARCHIVES_TITLE, title.getText().toString());
        values.put(ArchivesContract.ArchivesColumns.ARCHIVES_DATE_TIME, dateTime.getText().toString());
        values.put(ArchivesContract.ArchivesColumns.ARCHIVES_CATEGORY, mTitle);

        ContentResolver cr = this.getContentResolver();
        Uri uri = ArchivesContract.URI_TABLE;
        cr.insert(uri, values);
        delete(view, position);
    }

    private void delete(View view, int position) {
        ContentResolver cr = this.getContentResolver();
        String _ID = ((TextView) view.findViewById(R.id.id_note_custom_home)).getText().toString();
        Uri uri = NotesContract.Notes.buildNoteUri(_ID);
        cr.delete(uri, null, null);
        mNotesAdapter.delete(position);
        changeNoItemTag();
    }

    private void edit(View view) {
        Intent intent = new Intent(NotesActivity.this, NoteDetailActivity.class);
        String id = ((TextView) view.findViewById(R.id.id_note_custom_home)).getText().toString();
        intent.putExtra(AppConstant.ID, id);
        LinearLayout linearLayout = (LinearLayout) view.findViewById(R.id.home_list);
        int isList = linearLayout.getVisibility();
        if (isList == View.VISIBLE) {
            intent.putExtra(AppConstant.LIST, AppConstant.TRUE);
        }
        ImageView tempImageView = (ImageView) view.findViewById(R.id.image_note_custom_home);
        if(tempImageView.getDrawable() != null) {
            mSendingImage = ((BitmapDrawable) tempImageView.getDrawable()).getBitmap();
        }
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case AppConstant.REQ_ACCPICK: {
                if(resultCode == Activity.RESULT_OK && data != null) {
                    String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if(GDUT.AM.setEmil(email) == GDUT.AM.CHANGED) {
                        GDActions.init(this, GDUT.AM.getActiveEmil());
                        GDActions.connect(true);
                    }
                } else if(GDUT.AM.getActiveEmil() == null) {
                    GDUT.AM.removeActiveAccnt();
                    finish();
                }
                break;
            }

            case AppConstant.REQ_AUTH:

            case AppConstant.REQ_RECOVER: {
                mIsInAuth = false;
                if(resultCode == Activity.RESULT_OK) {
                    GDActions.connect(true);
                } else if(resultCode == RESULT_CANCELED) {
                    GDUT.AM.removeActiveAccnt();
                    finish();
                }
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
