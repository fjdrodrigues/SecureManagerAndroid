package fjdrodrigues.securemanager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 * ____________
 * security1@agent.com
 * abc123
 * ___________
 * monitor1@monitor.com
 * def456
 */
public class LoginActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 123;

    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        createSignInIntent();
    }

    public void createSignInIntent() {
        // [START auth_fui_create_intent]
        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList( new AuthUI.IdpConfig.EmailBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
        // [END auth_fui_create_intent]
    }

    // [START auth_fui_result]
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                user = FirebaseAuth.getInstance().getCurrentUser();

                processUser();
                // ...
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }
    // [END auth_fui_result]

    private void processUser() {
        final Intent i = null;
        if(isMonitor()) {
            FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_monitors").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(!dataSnapshot.hasChild(user.getUid())) {
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_monitors").child(user.getUid()).child("displayName").setValue(user.getDisplayName());
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_monitors").child(user.getUid()).child("email").setValue(user.getEmail());
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_monitors").child(user.getUid()).child("uid").setValue(user.getUid());
                    }
                    startActivity(new Intent(LoginActivity.this, MonitorActivity.class));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });


        }
        else if (isAgent()){
            FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(!dataSnapshot.hasChild(user.getUid())){
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").child(user.getUid()).child("displayName").setValue(user.getDisplayName());
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").child(user.getUid()).child("email").setValue(user.getEmail());
                        FirebaseDatabase.getInstance().getReference().child("users").child("security_users").child("security_agents").child(user.getUid()).child("uid").setValue(user.getUid());
                    }
                    startActivity(new Intent(LoginActivity.this, AgentActivity.class));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });

        }else {
            Toast.makeText(this, "User Domain not recognized!", Toast.LENGTH_LONG).show();
            signOut();
            createSignInIntent();
        }
    }

    private boolean isAgent() {
        if(user.getEmail().endsWith("@agent.com"))
            return true;
        return false;
    }

    private boolean isMonitor() {
        if(user.getEmail().endsWith("@monitor.com"))
            return true;
        return false;
    }

    public void signOut() {
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                    }
                });
        // [END auth_fui_signout]
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
        signOut();
    }
}

