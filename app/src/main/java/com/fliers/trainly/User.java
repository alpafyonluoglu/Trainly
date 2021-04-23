package com.fliers.trainly;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

/**
 * Abstract User class to be extended by Customer and Company classes
 * @author Alp Afyonluoğlu
 * @version 23.04.2021
 */
abstract class User {
    // Properties
    protected static final String SERVER_KEY = "KEY_Tr21iwuS3obrslfL4";
    protected final String NAME = "userName";
    protected final String EMAIL = "userEmail";
    protected final String DEFAULT_NAME = "DEFAULT";

    private SharedPreferences preferences;
    private String name;
    private String email;
    private String id;
    private boolean isLoggedIn;
    private boolean isNewAccount;

    // Constructors
    /**
     * Initializes a new user
     */
    public User( Context context) {
        name = DEFAULT_NAME;
        email = "";
        id = "";
        isLoggedIn = false;
        isNewAccount = true;

        preferences = context.getSharedPreferences(String.valueOf(R.string.app_name), 0);
    }

    // Methods
    /**
     * Sends login email to the given email address
     * @param email email address of the user
     * @param listener EmailListener interface that is called
     *                when verification email is sent
     */
    public void login( String email, EmailListener listener) {
        // Variables
        ActionCodeSettings actionCodeSettings;
        FirebaseAuth auth;

        // Code
        if ( !isLoggedIn && checkEmailValidity( email)) {
            this.email = email;

            actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl( "https://trainly-app.web.app/registration_successful")
                    .setHandleCodeInApp( true)
                    .setAndroidPackageName( "com.fliers.trainly", false, "19")
                    .build();

            auth = FirebaseAuth.getInstance();
            auth.sendSignInLinkToEmail( email, actionCodeSettings)
                    .addOnCompleteListener( new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete( @NonNull Task<Void> task) {
                            if ( task.isSuccessful()) {
                                listener.onEmailSent( email, true);
                            }
                            else {
                                listener.onEmailSent( email, false);
                            }
                        }
                    });
        }
        else {
            listener.onEmailSent( email, false);
        }
    }

    /**
     * Completes login process by using the link that is sent via email
     * @param email email address of the user
     * @param emailLink link sent to the email address of the user
     * @param listener LoginListener interface that is called
     *                when email link is verified
     */
    public void completeLogin( String email, String emailLink, LoginListener listener) {
        // Variables
        FirebaseAuth auth;

        // Code
        auth = FirebaseAuth.getInstance();
        if ( auth.isSignInWithEmailLink( emailLink)) {
            auth.signInWithEmailLink( email, emailLink).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete( @NonNull Task<AuthResult> task) {
                            if ( task.isSuccessful()) {
                                // Email link verified
                                id = email.replace( "@", "_at_");
                                id = id.replace( ".", "_dot_");

                                // Check whether user is registering a new account or logging in to an existing account
                                checkEmailAvailability( email, new EmailAvailabilityCheckListener() {
                                    @Override
                                    public void onEmailAvailabilityCheck(String email, boolean isAvailable) {
                                        isNewAccount = isAvailable;

                                        if ( isNewAccount) {
                                            // Register

                                            // Save user data to server
                                            saveToServer( new ServerSyncListener() {
                                                @Override
                                                public void onSync( boolean isSynced) {

                                                    preferences.edit().putString( NAME, name).apply();
                                                    preferences.edit().putString( EMAIL, email).apply();

                                                    isLoggedIn = isSynced;
                                                    listener.onLogin( isLoggedIn);
                                                }
                                            });
                                        }
                                        else {
                                            // Log in

                                            // Retrieve user data from server
                                            getFromServer( new ServerSyncListener() {
                                                @Override
                                                public void onSync( boolean isSynced) {

                                                    preferences.edit().putString( NAME, name).apply();
                                                    preferences.edit().putString( EMAIL, email).apply();

                                                    isLoggedIn = isSynced;
                                                    listener.onLogin( isLoggedIn);
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                            else {
                                // Email link could not be verified
                                listener.onLogin( false);
                            }
                        }
                    });
        }
        else {
            listener.onLogin( false);
        }
    }

    /**
     * Logs in the current user account
     * @return whether a user is currently logged in or not
     */
    public boolean getCurrentUser() {
        if ( !preferences.getString( EMAIL, "").equals( "")) {
            name = preferences.getString( NAME, DEFAULT_NAME);
            email = preferences.getString( EMAIL, "");

            id = email.replace( "@", "_at_");
            id = id.replace( ".", "_dot_");

            isLoggedIn = true;
            isNewAccount = false;

            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Checks whether email is registered before or not
     * @param email email address to check
     * @param listener EmailAvailabilityCheckListener interface that is called
     *                when data is retrieved from server
     */
    public void checkEmailAvailability( String email, EmailAvailabilityCheckListener listener) {
        // Variables
        FirebaseDatabase database;
        DatabaseReference reference;
        String id;

        // Code
        id = email.replace( "@", "_at_");
        id = id.replace( ".", "_dot_");

        database = FirebaseDatabase.getInstance();
        reference = database.getReference( SERVER_KEY + "/Users/" + id);

        // Retrieve data of user with given email address from server
        reference.addValueEventListener( new ValueEventListener() {
            @Override
            public void onDataChange( DataSnapshot dataSnapshot) {
                reference.removeEventListener( this);

                // Check if email entry exists on server or not
                if ( dataSnapshot.exists()) {
                    listener.onEmailAvailabilityCheck( email, false);
                }
                else {
                    listener.onEmailAvailabilityCheck( email, true);
                }
            }

            @Override
            public void onCancelled( DatabaseError error) {
                // Database error occurred
                reference.removeEventListener( this);

                listener.onEmailAvailabilityCheck( email, false);
            }
        });
    }

    /**
     * Saves local user data to server
     * @param listener ServerSyncListener interface that is called
     *                when data is sent to server
     */
    protected void saveToServer( ServerSyncListener listener) {
        //TODO: Check network connection

        // Variables
        FirebaseDatabase database;
        DatabaseReference reference;
        HashMap<String, String> userData;

        // Code
        if ( isLoggedIn) {
            database = FirebaseDatabase.getInstance();
            reference = database.getReference( SERVER_KEY + "/Users/" + id);

            // Create hash map with given user data
            userData = new HashMap<>();
            userData.put( "name", name);

            // Save map to server
            reference.setValue( userData);
            listener.onSync( true);
        }
    }

    /**
     * Updates local user data with data retrieved from server
     * @param listener ServerSyncListener interface that is called
     *                when data is retrieved from server
     */
    protected void getFromServer( ServerSyncListener listener) {
        //TODO: Check network connection

        // Variables
        FirebaseDatabase database;
        DatabaseReference reference;

        // Code
        database = FirebaseDatabase.getInstance();
        reference = database.getReference( SERVER_KEY + "/Users/" + id);

        // Retrieve data of the user with given email address from server
        reference.addValueEventListener( new ValueEventListener() {
            @Override
            public void onDataChange( DataSnapshot dataSnapshot) {
                // Variables
                HashMap<String, String> userData;

                // Code
                reference.removeEventListener( this);

                // Check if username entry exists on server or not
                if ( dataSnapshot.exists()) {
                    userData = (HashMap<String, String>) dataSnapshot.getValue();


                    // Check whether passwords match or not
                    name = userData.get( "name");

                    listener.onSync( true);
                }
            }

            @Override
            public void onCancelled( DatabaseError error) {
                // Database error occurred
                reference.removeEventListener( this);

                listener.onSync( false);
            }
        });
    }

    /**
     * Checks validity of email address
     * @param email email address to be checked
     * @return whether email address is valid or not
     */
    public boolean checkEmailValidity( String email) {
        // Variables
        String[] temp;
        String username;
        String domainName;
        String domain;

        // Code
        // Split email address into its parts
        if ( email.contains( "@")) {
            temp = email.split( "@");
            username = temp[0];

            if ( temp[1].contains( ".")) {
                temp = temp[1].split( ".");
                domainName = temp[0];
                domain = temp[1];

                return 6 <= username.length() && username.length() <= 30 && 2 <= domainName.length() && 2 <= domain.length();
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }

    /**
     * Getter method for name
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Getter method for email
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Getter method for id
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Getter method for isLoggedIn
     * @return isLoggedIn
     */
    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    /**
     * Getter method for isNewAccount
     * @return isNewAccount
     */
    public boolean isNewAccount() {
        return isNewAccount;
    }

    /**
     * Setter method for name
     * @param name new name
     */
    public void setName( String name) {
        this.name = name;
    }

    public interface EmailAvailabilityCheckListener {
        void onEmailAvailabilityCheck( String email, boolean isAvailable);
    }

    public interface EmailListener {
        void onEmailSent( String email, boolean isSent);
    }

    public interface LoginListener {
        void onLogin( boolean isLoggedIn);
    }

    private interface ServerSyncListener {
        void onSync( boolean isSynced);
    }
}