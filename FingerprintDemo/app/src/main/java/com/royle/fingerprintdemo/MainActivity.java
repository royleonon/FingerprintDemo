package com.royle.fingerprintdemo;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {

    //region widget
    private android.support.v7.widget.Toolbar toolbar;
    private android.support.design.widget.TabLayout tabLayout;
    private android.support.design.widget.AppBarLayout appBarLayout;
    private android.widget.EditText editTextPassword;
    private android.support.design.widget.TextInputLayout textInputLayout;
    private android.widget.TextView txtTitle;
    //endregion

    /**
     * Alias for our key in the Android Key Store
     */
    private static final String KEY_NAME = "my_key";

    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;
    private KeyStore keyStore;
    private KeyGenerator keyGenerator;
    private Cipher cipher;
    private FingerprintManager.CryptoObject cryptoObject;
    private FingerprintHelper fingerprintHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.txtTitle = (TextView) findViewById(R.id.txtTitle);
        this.textInputLayout = (TextInputLayout) findViewById(R.id.textInputLayout);
        this.editTextPassword = (EditText) findViewById(R.id.editTextPassword);
        this.appBarLayout = (AppBarLayout) findViewById(R.id.appBarLayout);
        this.tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        this.toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("FingerPrint & Password Unlock");
        toolbar.setTitleTextColor(getColor(android.R.color.white));

        tabLayout.addTab(tabLayout.newTab().setText("Cancel"));
        tabLayout.addTab(tabLayout.newTab().setText("Ok"));

        //เช็คเวอร์ชั่นของเครื่องต้อง >= 6.0 M จึงจะใช้งาน fingerprintManager ได้
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            if (fingerprintManager.isHardwareDetected()) {
                // Device support fingerprint authentication
                setupFingerPrint();
            } else {
                txtTitle.setText("Please fill password");
            }

        } else {
            txtTitle.setText("Please fill password");
        }

        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                checkPassword(tab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                checkPassword(tab);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fingerprintHelper != null) {
            fingerprintHelper.startAuth(fingerprintManager, cryptoObject);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fingerprintHelper != null) {
            fingerprintHelper.stopListening();
        }
    }

    private void setupFingerPrint() {
        if (!keyguardManager.isKeyguardSecure()) {
            // Show a message that the user hasn't set up a fingerprint or lock screen.
            Toast.makeText(this,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Fingerprint' to set up a fingerprint",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!fingerprintManager.hasEnrolledFingerprints()) {
            // This happens when no fingerprints are registered.
            Toast.makeText(this,
                    "Go to 'Settings -> Security -> Fingerprint' and register at least one fingerprint",
                    Toast.LENGTH_LONG).show();
            return;
        }

        createKey();
        if (initCipher()) {
            cryptoObject = new FingerprintManager.CryptoObject(cipher);
            fingerprintHelper = new FingerprintHelper(this);
        }
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    public void createKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
            // for your flow. Use of keys is necessary if you need to know if the set of
            // enrolled fingerprints has changed.

            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore");
            } catch (NoSuchAlgorithmException |
                    NoSuchProviderException e) {
                throw new RuntimeException(
                        "Failed to get KeyGenerator instance", e);
            }

            try {
                keyStore.load(null);
                // Set the alias of the entry in Android KeyStore where the key will appear
                // and the constrains (purposes) in the constructor of the Builder
                keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        // Require the user to authenticate with a fingerprint to authorize every use
                        // of the key
                        .setUserAuthenticationRequired(true)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());
                keyGenerator.generateKey();
                Log.d("main", "createKey: keyGenerate");
            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                    | CertificateException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the {@link #createKey()}
     * method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private boolean initCipher() {
        try {
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    private void checkPassword(TabLayout.Tab tab) {
        if (tab.getPosition() == 1) {
            String password = editTextPassword.getText().toString().trim();
            // example password = 1234
            if (password.equals("1234")) {
                new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(false)
                        .setMessage("Password Authentication succeeded")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                textInputLayout.setError(null);
                            }
                        }).show();
            } else {
                textInputLayout.setError("Password incorrect");
            }
        }
    }

}
