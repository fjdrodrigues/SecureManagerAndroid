package fjdrodrigues.securemanager;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

public class AgentActivity extends DrawerActivity implements OnMyLocationClickListener, OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mLocationPermissionDenied = false;

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;

    private static final int REQUEST_WRITE_STORAGE_PERMISSION = 4;

    private boolean permissionToWriteToStorageAccepted = false;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 2;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;

    private static final int MICRO_REQUEST_WRITE_STORAGE_PERMISSION =3;

    private Uri mPhotoUri;
    private Uri mVideoUri;

    private FloatingActionButton shootButton;
    private FloatingActionButton emergencyButton;
    private FloatingActionButton recordSoundButton;

    static final int CAPTURE_MEDIA_RESULT_CODE = 1;
    static final int CAPTURE_AUDIO_RESULT_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add the Name and Type to the Drawer Header
        NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
        navView.inflateMenu(R.menu.agent_drawer);
        View navHeaderMain = navView.getHeaderView(0);
        TextView title = (TextView) navHeaderMain.findViewById(R.id.nav_title);
        title.setText(FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
        TextView subtitle = (TextView) navHeaderMain.findViewById(R.id.nav_sub_title);
        subtitle.setText("Agent");

        //Get the frame Layout from Drawer
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.activity_frame);
        // inflate the Agent Activity FrameLayout
        View agentView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.activity_agent, null, false);
        // add the Agent Activity FrameLayout to the Drawer's FrameLayout.
        frameLayout.addView(agentView);

        grantLocationPermissions();

        //Configure Camara button
        shootButton = (FloatingActionButton) findViewById(R.id.shoot);
        shootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                grantWriteStoragePermissions();
            }
        });
        //Check if Camera is accessible and We have permissions to write
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            shootButton.hide();
        }

        emergencyButton = (FloatingActionButton) findViewById(R.id.emergency);
        emergencyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                emergency();
            }
        });
        //Configure Sound Recording Button
        recordSoundButton = (FloatingActionButton) findViewById(R.id.recordSound);
        recordSoundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableMicro();
            }
        });

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_gallery) {
            // Handle the gallery action

        } else if (id == R.id.nav_evidence) {
            // Collect Evidence
            if(permissionToWriteToStorageAccepted)
                dispatchTakePictureIntent();
            else
                grantWriteStoragePermissions();
        } else if (id == R.id.nav_microphone) {
            if(permissionToRecordAccepted && permissionToWriteToStorageAccepted)
                recordSound();
            else
                enableMicro();
        } else if (id == R.id.nav_emergency) {
            emergency();
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
        // as you specify a parent actiMICRO_REQUEST_WRITE_STORAGE_PERMISSIONvity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    private void grantLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION_REQUEST_CODE);
        }else {
            initializeMap();
        }
    }

    private void initializeMap() {
        //Initialize the Location Provider
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //Load the MapFragment from the Layout and retrieve it
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /*
     *  MAP and LOCATION
     *
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        System.out.println("onMapReady: Agent");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000); // 1 sec interval
        mLocationRequest.setFastestInterval(500); // 0.5 sec interval
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mMap.setIndoorEnabled(true);
        mMap.setMinZoomPreference(18);
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setAllGesturesEnabled(false);
        uiSettings.setIndoorLevelPickerEnabled(true);
        mMap.setOnMyLocationClickListener(this);
        enableMyLocation();
    }

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location lastLocation = locationResult.getLastLocation();
            if (lastLocation != null) {
                LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("latLng").setValue(latLng);
                FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("timeStamp").setValue(Calendar.getInstance().getTime().getTime());
                //move map camera
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11));
            }
        }
    };
//1538526683016
//1538649768557.7568

    private void enableMyLocation() {
        if (mMap != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)  {
            // Access to the location has been granted to the app.
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }

    private void grantWriteStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_WRITE_STORAGE_PERMISSION);
        }else
            dispatchTakePictureIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Enable the my location layer if the permission has been granted.
                    initializeMap();
                } else {
                    // Display the missing permission error dialog when the fragments resume.
                    mLocationPermissionDenied = true;
                    Toast.makeText(AgentActivity.this, "LOCATION PERMISSIONS DENIED!", Toast.LENGTH_LONG).show();
                }
                break;
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionToRecordAccepted = true;
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        // Permission to access the External Storage is missing.
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MICRO_REQUEST_WRITE_STORAGE_PERMISSION);
                    }
                    else
                        recordSound();
                }else {
                    recordSoundButton.hide();
                    NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
                    navView.getMenu().removeItem(R.id.nav_evidence);
                    permissionToRecordAccepted = false;
                }
                break;
            case MICRO_REQUEST_WRITE_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionToWriteToStorageAccepted = true;
                    recordSound();
                }else {
                    recordSoundButton.hide();
                    NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
                    navView.getMenu().removeItem(R.id.nav_evidence);
                    permissionToRecordAccepted = false;
                }
                break;
            case REQUEST_WRITE_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionToWriteToStorageAccepted = true;
                    dispatchTakePictureIntent();
                }else {
                    shootButton.hide();
                    NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
                    navView.getMenu().removeItem(R.id.nav_evidence);
                    permissionToWriteToStorageAccepted = false;
                }
        }
    }

    /*
     *  MULTIMEDIA
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPhotoUri);
        mVideoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mVideoUri);
        Intent chooserIntent = Intent.createChooser(takePictureIntent, "Capture Image or Video");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takeVideoIntent});
        if (chooserIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooserIntent, CAPTURE_MEDIA_RESULT_CODE);
        }
    }

    /*
     *  RECORD SOUND
     */
    private void recordSound() {
        try {
            Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
            startActivityForResult(intent, CAPTURE_AUDIO_RESULT_CODE);
        }catch(ActivityNotFoundException e){
            Toast.makeText(AgentActivity.this, "No Application available to record sound.", Toast.LENGTH_LONG).show();
            recordSoundButton.hide();
            NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
            navView.getMenu().removeItem(R.id.nav_microphone);
        }
    }

    /*
     *  EMERGENCY
     */
    private void emergency() {
        Toast.makeText(AgentActivity.this, "EMERGENCY SITUATION ACTIVATED!", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:112"));
        startActivity(intent);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_MEDIA_RESULT_CODE && resultCode == RESULT_OK) {
            Cursor cursor = null;
            String[] projection = {MediaStore.MediaColumns.DATA};
            try {
                cursor = getContentResolver().query(mPhotoUri, projection,null,null,null);
                if (cursor != null && cursor.moveToFirst()) {
                    if(new File(cursor.getString(0)).exists())
                        addMultimediaToDatabase(mPhotoUri, "image");
                    else
                        addMultimediaToDatabase(data.getData(), "video");
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }

            mPhotoUri = null;
            mVideoUri = null;
        }else if (requestCode == CAPTURE_AUDIO_RESULT_CODE && resultCode == RESULT_OK) {
            addMultimediaToDatabase(data.getData(), "audio");
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void addMultimediaToDatabase(Uri file, String mediaType) {
        final String fileName = mediaType+"_"+((String) DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance().getTime()));
        UploadTask uploadTask = FirebaseStorage.getInstance().getReference("users").child("security_users").child("security_agents").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child(fileName).putFile(file);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                FirebaseDatabase.getInstance().getReference("users").child("security_users").child("security_agents").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("multimedia").child(fileName).setValue(taskSnapshot.getMetadata().getContentType());
            }
        });
    }

    private void enableMicro() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the Micro is missing.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }else
            recordSound();
    }

    /*
     *  Activity Lifecycle
     */
    @Override
    protected void onStart() {
        super.onStart();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mLocationPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mLocationPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }
    @Override
    protected void onPause() {
        super.onPause();
        //stop location updates when Activity is no longer active
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /*
     *  Sign Out
     */
    public void signOut() {
        final String uId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        startActivity(new Intent(AgentActivity.this, LoginActivity.class));
                    }
                });
        // [END auth_fui_signout]
    }
}