package fjdrodrigues.securemanager;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class MonitorActivity extends MapsActivity {

    private FrameLayout frameLayout;
    private View monitorView;

    private GoogleMap mMap;
    private SupportMapFragment mapFragment;

    private ListView agentListView;


    final private ConcurrentHashMap<String, DataSnapshot> agents = new ConcurrentHashMap<>();
    final private ConcurrentHashMap<String, Marker> markers = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add the Name and Type to the Drawer Header
        NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
        navView.inflateMenu(R.menu.monitor_drawer);
        View navHeaderMain = navView.getHeaderView(0);
        TextView title = (TextView) navHeaderMain.findViewById(R.id.nav_title);
        title.setText(FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
        TextView subtitle = (TextView) navHeaderMain.findViewById(R.id.nav_sub_title);
        subtitle.setText("Monitor");

        //Get the frame Layout from Drawer
        frameLayout = (FrameLayout) findViewById(R.id.activity_frame);
        // inflate the Monitor Activity FrameLayout
        monitorView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.activity_monitor, null, false);
        // add the Monitor Activity FrameLayout to the Drawer's FrameLayout.
        frameLayout.addView(monitorView);

        //Initialize the MapFragment
        initializeMap();

        //Initialize the ListView
        initializeAgentList();

        getAgents();
//        (new Timer()).schedule(new TimerTask() {
//            @Override
//            public void run() {
//                getAgents();
//            }
//        }, 0, 1000);

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_map) {
            hideAgentList();
            showMap();
        }else if (id == R.id.nav_agents) {
            // List the Agents
            hideMap();
            showAgentList();

        } else if (id == R.id.nav_logout) {
            signOut();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        return super.onOptionsItemSelected(item);
    }

    private void initializeMap() {
        //Load the MapFragment from the Layout
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        //Load the MapFragment from the Layout and retrieve it
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setIndoorEnabled(true);
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setIndoorLevelPickerEnabled(true);
        uiSettings.setZoomControlsEnabled(true);
        uiSettings.setAllGesturesEnabled(true);
        //Centrar na UA
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(40.6344675,-8.6587745), 17));
    }

    private void showMap() {
        mapFragment.getView().setVisibility(VISIBLE);
    }

    private void hideMap() {
        mapFragment.getView().setVisibility(GONE);
    }

    private void initializeAgentList() {
        agentListView = (ListView) findViewById(R.id.agent_list_view) ;
        agentListView.setVisibility(GONE);

        //private List<ConcurrentHashMap.Entry<String, DataSnapshot>> list = new ArrayList(agents.entrySet());
        //ArrayAdapter adapter = new ConcurrentHashMapArrayAdapter(this, R.layout.agent_view, new ArrayList(agents.entrySet()));
    }

    private void showAgentList() {
        agentListView.setVisibility(VISIBLE);
        ConcurrentHashMapArrayAdapter adapter = new ConcurrentHashMapArrayAdapter(this, R.layout.agent_view, new ArrayList(agents.entrySet()));
        agentListView.setAdapter(adapter);
        adapter.notifyDataSetChanged();

    }

    private void hideAgentList() {
        agentListView.setVisibility(GONE);
    }

    private void getAgents() {

        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot child, String string) {
                if (!agents.containsKey(child.getKey())) {
                    agents.put(child.getKey(), child);
                    if(child.hasChild("latLng") && child.hasChild("timeStamp")) {
                        if (child.child("timeStamp").getValue(long.class) > System.currentTimeMillis()-30000) {
                            if (!markers.containsKey(child.getKey()))
                                setAgentMarker(child.getKey(), child);
                            else
                                updateAgentMarker(child.getKey(), child);
                        }else {
                            if(markers.containsKey(child.getKey()))
                                removeAgentMarker(child.getKey());
                        }
                    }
                }else {
                    agents.replace(child.getKey(), child);
                    if(child.hasChild("latLng") && child.hasChild("timeStamp")) {
                        if (child.child("timeStamp").getValue(long.class) > System.currentTimeMillis()-30000) {
                            if (!markers.containsKey(child.getKey()))
                                setAgentMarker(child.getKey(), child);
                            else
                                updateAgentMarker(child.getKey(), child);
                        }else {
                            if(markers.containsKey(child.getKey()))
                                removeAgentMarker(child.getKey());
                        }
                    }
                }
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot child, String string) {
                agents.replace(child.getKey(), child);
                if(child.hasChild("latLng") && child.hasChild("timeStamp")) {
                    if (child.child("timeStamp").getValue(long.class) > System.currentTimeMillis()-30000) {
                        if (!markers.containsKey(child.getKey()))
                            setAgentMarker(child.getKey(), child);
                        else
                            updateAgentMarker(child.getKey(), child);
                    }else {
                        if(markers.containsKey(child.getKey()))
                            removeAgentMarker(child.getKey());
                    }
                }
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot child, String string) {
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot child) {
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void setAgentMarker(String uId, DataSnapshot agent) {
        System.out.println("Set Marker: "+uId);
        MarkerOptions m = new MarkerOptions();
        m.icon(BitmapDescriptorFactory.fromResource(R.drawable.agent_icon_24));
        m.position(new LatLng(agent.child("latLng").child("latitude").getValue(double.class),agent.child("latLng").child("longitude").getValue(double.class)));
        m.title(agent.child("displayName").getValue(String.class));
        m.snippet("Email: " + agent.child("email").getValue(String.class) + "\nLatitude: "+
                agent.child("latLng").child("latitude").getValue(long.class)+ "\nLongitude: "+agent.child("latLng").child("longitude").getValue(double.class)+"\nLast Position: "+(System.currentTimeMillis()-agent.child("timeStamp").getValue(Long.class))/1000+" seconds Old.");
        markers.put(uId, mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.agent_icon_24))
                .position(new LatLng(agent.child("latLng").child("latitude").getValue(double.class),agent.child("latLng").child("longitude").getValue(double.class)))
                .snippet("Email: " + agent.child("email").getValue(String.class) +
                        "\nLatitude: "+agent.child("latLng").child("latitude").getValue(long.class)+
                        "\nLongitude: "+agent.child("latLng").child("longitude").getValue(long.class)+
                        "\nLast Position: "+(System.currentTimeMillis()-agent.child("timeStamp").getValue(Long.class))/1000+" seconds Old.")));

    }

    private void updateAgentMarker(String uId, DataSnapshot agent) {
        //Marker aux = markers.get(uId);
        markers.get(uId).setPosition(new LatLng(agent.child("latLng").child("latitude").getValue(double.class),agent.child("latLng").child("longitude").getValue(double.class)));
        markers.get(uId).setSnippet("Email: " + agent.child("email").getValue(String.class) + "\nLatitude: "+agent.child("latLng").child("latitude").getValue(double.class)+ "\nLongitude: "+agent.child("latLng").child("longitude").getValue(double.class)+"\nLast Position: "+(System.currentTimeMillis()-agent.child("timeStamp").getValue(Long.class))/1000+" seconds old.");
    }

    private void removeAgentMarker(String uId) {
        markers.remove(uId);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void signOut() {
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        startActivity(new Intent(MonitorActivity.this, LoginActivity.class));
                    }
                });
        // [END auth_fui_signout]
    }

    class MultimediaRecyclerViewAdapter extends RecyclerView.Adapter<MultimediaRecyclerViewAdapter.CustomViewHolder> {

        class CustomViewHolder extends RecyclerView.ViewHolder{
            ImageButton audio;
            VideoView video;
            ImageView image;
            //View view;

            public CustomViewHolder(View view) {
                super(view);
                /*
                this.view = (ImageView) view.findViewById(R.id.thumbnail);
                this.textView = (TextView) view.findViewById(R.id.title);*/
            }
        }

        class ImageViewHolder extends CustomViewHolder{
            ImageView image;

            public ImageViewHolder(View view) {
                super(view);
                this.image = view.findViewById(R.id.image_view);
            }
        }
        class VideoViewHolder extends CustomViewHolder {
            VideoView video;

            public VideoViewHolder(View view) {
                super(view);
                this.video = view.findViewById(R.id.video_view);
            }
        }
        class AudioViewHolder extends CustomViewHolder {
            ImageButton audio;

            public AudioViewHolder(View view) {
                super(view);
                this.audio = view.findViewById(R.id.image_button);
            }
        }

        final static int  IMAGE_VIEW_TYPE = 0;
        final static int  VIDEO_VIEW_TYPE = 1;
        final static int  AUDIO_VIEW_TYPE = 2;


        private Context mContext;
        private String uId;
        private List<DataSnapshot> multimedia;

        public MultimediaRecyclerViewAdapter(Context context, List<DataSnapshot> multimedia, String uId) {
            System.out.println("MultimediaListAdapter Called");
            System.out.println("Multimedia Count: "+multimedia.size());
            this.multimedia = multimedia;
            this.mContext = context;
            this.uId = uId;
        }


        @Override
        public CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            if(viewType == IMAGE_VIEW_TYPE) {
                View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.image_layout, null);
                CustomViewHolder viewHolder = new ImageViewHolder(view);
                return viewHolder;

            }else if(viewType == VIDEO_VIEW_TYPE) {
                View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.video_layout, null);
                CustomViewHolder viewHolder = new VideoViewHolder(view);
                return viewHolder;

            }else {
                View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.audio_layout, null);
                CustomViewHolder viewHolder = new AudioViewHolder(view);
                return viewHolder;

            }
        }

        @Override
        public int getItemViewType(int position) {
            DataSnapshot media = multimedia.get(position);
            if (media.getValue(String.class).contains("image"))
                return IMAGE_VIEW_TYPE;
            else if (media.getValue(String.class).contains("video"))
                return VIDEO_VIEW_TYPE;
            else
                return AUDIO_VIEW_TYPE;
        }

        @Override
        public void onBindViewHolder(CustomViewHolder customViewHolder, int position) {
            final DataSnapshot media = multimedia.get(position);

            Uri imgUri;

            if (media.getValue(String.class).contains("image")) {
                final ImageViewHolder imageViewHolder = (ImageViewHolder) customViewHolder;
                System.out.println("Image Found, Key: "+media.getKey());
                imgUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
                Cursor cursor = null;
                String[] projection = {MediaStore.MediaColumns.DATA};
                try {
                    cursor = getContentResolver().query(imgUri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        final File imgFile = new File(cursor.getString(0));
                        FirebaseStorage.getInstance().getReference("users").child("security_users").child("security_agents").child(uId).child(media.getKey()).getFile(imgFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                System.out.println("Image Downloaded");
                                if (imgFile.exists()) {

                                    //Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                                    //viewHolder.image.setImageBitmap(myBitmap);
                                    imageViewHolder.image.setImageURI(Uri.fromFile(imgFile));
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                System.out.println("Image Not Downloaded");
                            }
                        });
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }
            }else if(media.getValue(String.class).contains("video")) {
                final VideoViewHolder videoViewHolder = (VideoViewHolder) customViewHolder;

                System.out.println("Video Found, Key: "+media.getKey());
                imgUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new ContentValues());
                Cursor cursor = null;
                String[] projection = {MediaStore.MediaColumns.DATA};
                try {
                    cursor = getContentResolver().query(imgUri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        final File imgFile = new File(cursor.getString(0));
                        FirebaseStorage.getInstance().getReference("users").child("security_users").child("security_agents").child(uId).child(media.getKey()).getFile(imgFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                videoViewHolder.video.setVideoURI(Uri.fromFile(imgFile));
                                videoViewHolder.video.setMediaController(new MediaController(MonitorActivity.this));
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {

                            }
                        });
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }
            }else {
                final AudioViewHolder audioViewHolder = (AudioViewHolder) customViewHolder;

                System.out.println(media.getValue(String.class));
                long bytes = 1024*1024*1024;
                FirebaseStorage.getInstance().getReference("users").child("security_users").child("security_agents").child(uId).child(media.getKey()).getBytes(bytes).addOnCompleteListener(new OnCompleteListener<byte[]>() {
                    @Override
                    public void onComplete(@NonNull Task<byte[]> task) {
                        try {

                            String root = Environment.getExternalStorageDirectory().toString();
                            File myDir = new File(root + "/audio");
                            if (!myDir.exists())
                                myDir.mkdirs();


                            File audioFile = new File (myDir, media.getKey());
                            if (audioFile.exists ())
                                audioFile.delete ();

                            FileOutputStream fos = new FileOutputStream(audioFile);
                            fos.write(task.getResult());
                            fos.close();

                            final MediaPlayer mediaPlayer = new MediaPlayer();
                            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                            FileInputStream fis = new FileInputStream(audioFile);
                            mediaPlayer.setDataSource(fis.getFD());
                            mediaPlayer.prepare();

                            audioViewHolder.audio.setImageResource(android.R.drawable.ic_media_play);
                            audioViewHolder.audio.setSelected(false);
                            audioViewHolder.audio.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (!audioViewHolder.audio.isSelected()) {
                                        audioViewHolder.audio.setSelected(true);
                                        audioViewHolder.audio.setImageResource(android.R.drawable.ic_media_pause);
                                        mediaPlayer.start();
                                        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                            @Override
                                            public void onCompletion(MediaPlayer mp) {
                                                audioViewHolder.audio.setSelected(false);
                                                audioViewHolder.audio.setImageResource(android.R.drawable.ic_media_play);
                                                //mediaPlayer.release();

                                            }
                                        });
                                    } else {
                                        mediaPlayer.pause();
                                        audioViewHolder.audio.setSelected(false);
                                        audioViewHolder.audio.setImageResource(android.R.drawable.ic_media_play);
                                    }
                                }
                            });

                        } catch (Exception e) {
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return (null != multimedia ? multimedia.size() : 0);
        }
    }

    class ConcurrentHashMapArrayAdapter extends ArrayAdapter {

        private class ViewHolder {
            TextView name;
            TextView eMail;
            TextView latLng;
            TextView online;
            TextView timeStamp;
            TextView evidenceCollected;
            RecyclerView evidence;
        }

        public ConcurrentHashMapArrayAdapter(Context context, int textViewResourceId, List<ConcurrentHashMap<String, DataSnapshot>> agents) {
            super(context, textViewResourceId, agents);
            System.out.println("ConcurrentHashMapArrayAdapter Called");
            System.out.println("Agents size: "+agents.size());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder viewHolder;

            if (convertView == null) {
                convertView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.agent_view, parent, false);//LayoutInflater.from(MonitorActivity.this).inflate(R.layout.agent_view, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.name);
                viewHolder.eMail = (TextView) convertView.findViewById(R.id.eMail);
                viewHolder.latLng = (TextView) convertView.findViewById(R.id.latLng);
                viewHolder.online = (TextView) convertView.findViewById(R.id.online);
                viewHolder.timeStamp = (TextView) convertView.findViewById(R.id.timeStamp);
                viewHolder.evidenceCollected = (TextView) convertView.findViewById(R.id.evidenceCollected);
                viewHolder.evidence = (RecyclerView) convertView.findViewById(R.id.evidence);
                convertView.setTag(viewHolder);
            } else
                viewHolder = (ViewHolder) convertView.getTag();

            ConcurrentHashMap.Entry<String, DataSnapshot> entry = (ConcurrentHashMap.Entry<String, DataSnapshot>) this.getItem(position);

            viewHolder.name.setText("Agent name: " + entry.getValue().child("displayName").getValue(String.class));
            viewHolder.eMail.setText("Email: " + entry.getValue().child("email").getValue(String.class));
            viewHolder.latLng.setText("Last Position\nLatitude: " + entry.getValue().child("latLng").child("latitude").getValue(double.class) + "\nLongitude: " + entry.getValue().child("latLng").child("longitude").getValue(double.class));
            if (System.currentTimeMillis() - 30000 > entry.getValue().child("timeStamp").getValue(Long.class))
                viewHolder.online.setText("State: Online");
            else
                viewHolder.online.setText("State: Offline");
            viewHolder.timeStamp.setText("Last Update: " + ((String) DateFormat.format("yyyy/MM/dd hh:mm:ss", entry.getValue().child("timeStamp").getValue(Long.class))));

            viewHolder.evidenceCollected.setText("Number of Evidence Collected: " + entry.getValue().child("multimedia").getChildrenCount());

            MultimediaRecyclerViewAdapter adapter = new MultimediaRecyclerViewAdapter(MonitorActivity.this, iteratorToList(entry.getValue().child("multimedia").getChildren().iterator()), entry.getKey());
            if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
                viewHolder.evidence.setLayoutManager(new GridLayoutManager(MonitorActivity.this, 2));
            }
            else{
                viewHolder.evidence.setLayoutManager(new GridLayoutManager(MonitorActivity.this, 4));
            }
            viewHolder.evidence.setAdapter(adapter);
            adapter.notifyDataSetChanged();


            System.out.println("Convert View HashMap Count: "+((LinearLayout)convertView).getChildCount()+" Visible: "+((LinearLayout)convertView).getVisibility());

            System.out.println("ViewHolder: "+convertView.getTag().toString());
            return convertView;
        }

    }

    private <T> List<T> iteratorToList(Iterator<T> iter) {
        List<T> list= new ArrayList<>();
        while (iter.hasNext()) {
            list.add(iter.next());
        }
        return list;
    }
}

