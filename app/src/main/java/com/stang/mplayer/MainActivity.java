package com.stang.mplayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int FILE_SELECT_CODE = 1;

    PlayerService mPlayerService;
    Intent mServiceIntent;
    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private RecyclerAdapter mAdapter;

    private int playlistPosition = -1;

    LinearLayoutManager mLayoutManager;
    Button prevButton;
    Button nextButton;
    Button playButton;
    Button pauseButton;
    Button repeatButton;
    Button addButton;
    Button addFolderButton;
    Button deleteButton;
    Button clearButton;
    EditText searchText;
    ImageButton searchButton;
    ImageView albumImage;
    TextView artistTitle;
    TextView songTitle;
    RecyclerView mPlaylist;
    SeekBar seekBar;
    TextView duration;
    TextView remain;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlaylist = (RecyclerView) findViewById(R.id.listView_playlist);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(mPlayerService != null ){
                    mPlayerService.seekTo(seekBar.getProgress());
                }
            }
        });

        prevButton = (Button) findViewById(R.id.button_prev);
        nextButton = (Button) findViewById(R.id.button_next);
        playButton = (Button) findViewById(R.id.button_play);
        pauseButton = (Button) findViewById(R.id.button_pause);
        repeatButton = (Button) findViewById(R.id.button_repeat);
        addButton = (Button) findViewById(R.id.button_addFile);
        addFolderButton = (Button) findViewById(R.id.button_addFolder);
        deleteButton = (Button) findViewById(R.id.button_delete);
        clearButton = (Button) findViewById(R.id.button_clear);
        searchText = (EditText) findViewById(R.id.editText_search);
        searchButton = (ImageButton) findViewById(R.id.button_search);


        albumImage = (ImageView) findViewById(R.id.imageView_album);
        artistTitle = (TextView) findViewById(R.id.textView_artistTitle);
        songTitle = (TextView) findViewById(R.id.textView_songTitle);
        duration = (TextView) findViewById(R.id.textView_duration);
        remain = (TextView) findViewById(R.id.textView_remain);


        prevButton.setOnClickListener(this);
        nextButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        pauseButton.setOnClickListener(this);
        repeatButton.setOnClickListener(this);
        addButton.setOnClickListener(this);
        addFolderButton.setOnClickListener(this);
        deleteButton.setOnClickListener(this);
        clearButton.setOnClickListener(this);
        searchButton.setOnClickListener(this);


        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mPlaylist.setLayoutManager(mLayoutManager);

        mAdapter = new RecyclerAdapter(this, null);
        mAdapter.setOnItemClickListener(new RecyclerAdapter.OnClickListener() {
            @Override
            public void onClick(View view, int position) {
                play(position);
                Log.d(TAG,"playlist OnClick position=" + position + " Song=" + mAdapter.getSong(position).fileName);
            }
        });
        mAdapter.setOnQueueChangeListener(new RecyclerAdapter.OnQueueChangeListener() {
            @Override
            public void onChange(ArrayList<Integer> newQueue) {
                if(mPlayerService != null) {
                    mPlayerService.setQueue(newQueue);
                }
            }
        });
        mPlaylist.setAdapter(mAdapter);

        filter = new IntentFilter();
        filter.addAction(PlayerService.ACTION_PROGRESS_CHANGED);
        filter.addAction(PlayerService.ACTION_STATUS_CHANGED);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "ACTIVITY receiver action=" + action);
                switch (action){
                    case PlayerService.ACTION_PROGRESS_CHANGED :
                        seekBar.setProgress(Integer.valueOf(intent.getIntExtra("progress", 0)));
                        remain.setText(String.valueOf(intent.getIntExtra("remain", 0)));
                        break;
                    case PlayerService.ACTION_STATUS_CHANGED :
                        int position  = intent.getIntExtra("position", -1);
                        int dur = intent.getIntExtra("duration", 0);
                        if (position > -1) {
                            Song song = ((RecyclerAdapter)mPlaylist.getAdapter()).getSong(position);
                            songTitle.setText(song.songTitle);
                            artistTitle.setText(song.artistTitle);
                            albumImage.setImageDrawable(song.albumImage);
                            duration.setText(String.valueOf(dur));
                            //remain.setText("");
                            mAdapter.setCurrentPosition(position);
                            mPlaylist.scrollToPosition(position);
                            mAdapter.notifyDataSetChanged();
                        }
                        break;
                }
            }
        };

        mServiceIntent = new Intent(this, PlayerService.class);
        startService(mServiceIntent);

        Log.d(TAG,"OnCreate finish");
    }

    public ServiceConnection myConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            mPlayerService = ((PlayerService.MusicBinder) binder).getService();
            Log.d(TAG, "ServiceConnection " + "connected");
            //
            ArrayList<Song> list = mPlayerService.getPlaylist();
            if(list.size() == 0) {
                list = getPlayListFromURI(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                mPlayerService.setPlayList(list);
            }
            mAdapter.setPlaylist(mPlayerService.getPlaylist());
            mAdapter.setQueue(mPlayerService.getQueue());
            mAdapter.setCurrentPosition(mPlayerService.getCurrentPosition());
            mAdapter.notifyDataSetChanged();

            if (mAdapter.getCurrentPosition() > -1) {
                Song song = mAdapter.getPlaylist().get(mAdapter.getCurrentPosition());
                songTitle.setText(song.songTitle);
                artistTitle.setText(song.artistTitle);
                albumImage.setImageDrawable(song.albumImage);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "ServiceConnection " + "disconnected");
            mPlayerService = null;
        }
    };


    public Handler myHandler = new Handler() {
        public void handleMessage(Message message) {
            Bundle data = message.getData();
        }
    };


    public void doBindService() {
        // Create a new Messenger for the communication back
        // From the Service to the Activity
        Messenger messenger = new Messenger(myHandler);
        mServiceIntent.putExtra("MESSENGER", messenger);

        bindService(mServiceIntent, myConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, filter);
        doBindService();
    }


    @Override
    protected void onPause() {
        if(receiver != null) {
            unregisterReceiver(receiver);
            //receiver = null;
        }

        if(mPlayerService != null){
            unbindService(myConnection);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        //stopService(mServiceIntent);
        //mPlayerService = null;
        super.onDestroy();
    }


    private void showPlayerStatus(){
        int position  = mPlayerService.getCurrentPosition();
        if (position > -1) {
            Song song = mPlayerService.getPlaylist().get(position);
            songTitle.setText(song.songTitle);
            artistTitle.setText(song.artistTitle);
            albumImage.setImageDrawable(song.albumImage);
        }
    }


    public void play(int position) {
        if(position < 0) return;
        if(mPlayerService != null) {
            mPlayerService.play(position);
        }
        mAdapter.setCurrentPosition(position);
        Song song = mAdapter.getSong(position);
        Log.d(TAG, "play " + song.songTitle);
    }


    public void pause() {
        if(mPlayerService != null) {
            mPlayerService.pause();
        }
    }


    public void onPlaylistChanged() {
        mPlayerService.setPlayList(mAdapter.getPlaylist());
        mPlayerService.setQueue(mAdapter.getQueue());
        mPlayerService.setCurrentPosition(mAdapter.getCurrentPosition());
    }


    public String intToTime(int time) {
        int min = time/60;
        int sec = time - min * 60;
        return String.valueOf(min) + ":" + String.valueOf(sec);
    }


    public ArrayList<Song> getPlayListFromURI(Uri uri){
        Log.d(TAG, "getPlayListFromURI");
        ArrayList<Song> songs =new ArrayList<>();
        //Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor == null) {
            // query failed, handle error.
        } else if (!cursor.moveToFirst()) {
            // no media on the device
        } else {
            int titleColumn = cursor
                    .getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor
                    .getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int idColumn = cursor
                    .getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
//            int contentTypeColumn = cursor
//                    .getColumnIndexOrThrow(MediaStore.Audio.Media.CONTENT_TYPE);
            int durationColumn = cursor
                    .getColumnIndex(MediaStore.Audio.Media.DURATION);
            int displayNameColumn = cursor
                    .getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int filenameColumn = cursor
                    .getColumnIndex(MediaStore.Audio.Media.DATA);
            int albumId = cursor
                    .getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);

            do {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                String filename = "file://" + cursor.getString(filenameColumn);
                String duration = cursor.getString(durationColumn);
                Drawable album = null;

//                Cursor c = contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
//                        new String[] {MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART},
//                        MediaStore.Audio.Albums._ID+ "=?",
//                        new String[] {String.valueOf(albumId)},
//                        null);
//                if (c.moveToFirst()) {
//                    String path = c.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
//                    // do whatever you need to do
//                    album = Drawable.createFromPath(path);
//                }


                //title = "id:"+id+" "+title+" ("+duration+")";
                songs.add(new Song(title, artist, String.valueOf(filename), album));

                Log.d(TAG, "getPlayListFromURI:: " + title + " filename:: " + filename);
            } while (cursor.moveToNext());
            playlistPosition = 0;
        }
        return songs;
    }


    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.button_pause:
                pause();
                break;

            case R.id.button_play:
                play(mAdapter.getCurrentPosition());
                break;

            case R.id.button_prev:
                prevTrack();
                break;

            case R.id.button_next:
                nextTrack();
                break;

            case R.id.button_repeat:
                //
                break;

            case R.id.button_addFile:
                addFiles();
                break;

            case R.id.button_addFolder:
                addFolder();
                break;

            case R.id.button_search:
                killService();
        }
    }


    public void prevTrack(){
        if(mPlayerService != null) {
            mPlayerService.prevTrack();
        }
    }


    public void nextTrack(){
        if(mPlayerService != null) {
            mPlayerService.nextTrack();
        }
    }


    private void addFiles(){
        Log.d(TAG, "addFiles");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");      //all files
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a File to add"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "no FileManager", Toast.LENGTH_SHORT).show();
        }
    }


    private void addFolder(){
        mAdapter.setPlaylist(getPlayListFromURI(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI));
        mAdapter.notifyDataSetChanged();
        onPlaylistChanged();
    }


    private void killService() {
        if(mPlayerService != null) {
            //unbindService(myConnection);
            stopService(mServiceIntent);
        }
        this.finish();
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == FILE_SELECT_CODE) {
                String title = data.getDataString();;
                String artist = "";

                Uri selectedFileUri = data.getData();
                String path = data.getDataString();

                Cursor cursor = null;
                cursor = getApplicationContext().getContentResolver().query(selectedFileUri,  null, null, null, null);
                if(cursor != null) {
                    cursor.moveToFirst();
                    title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                    artist = "";//cursor.getString(cursor.getColumnIndex("summary"));
                }

                Log.e(TAG + "SELECT FILE:", title + " path:" + path + " artist: " + artist);

                mAdapter.addSong(new Song(title, artist, path, null));

                if(playlistPosition < 0) playlistPosition = 0;
                onPlaylistChanged();

                mAdapter.notifyDataSetChanged();

            }
        }
    }

}
