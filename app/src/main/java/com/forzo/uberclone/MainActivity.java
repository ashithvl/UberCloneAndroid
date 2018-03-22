package com.forzo.uberclone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.forzo.uberclone.model.User;
import com.forzo.uberclone.model.WelcomeActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rengwuxian.materialedittext.MaterialEditText;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dmax.dialog.SpotsDialog;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.root_relative_layout)
    RelativeLayout root_relative_layout;
    @BindView(R.id.btn_sign_up)
    Button btnSignUp;
    @BindView(R.id.btn_register)
    Button btnRegister;
    private FirebaseAuth auth;
    private FirebaseDatabase db;
    private DatabaseReference users;

    /*
    custom font overriding
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //butter knife initialization
        ButterKnife.bind(this);
        //custom font initialization
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/Arkhip_font.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

        //firebase auth initialize
        auth = FirebaseAuth.getInstance();
        //firebase db initialize
        db = FirebaseDatabase.getInstance();
        //db reference
        users = db.getReference("users");
    }

    @OnClick({R.id.txt_rider_app, R.id.btn_sign_up, R.id.btn_register})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.txt_rider_app:
                break;
            case R.id.btn_sign_up:
                showSignUpDialog();
                break;
            case R.id.btn_register:
                showRegisterDialog();
                break;
        }
    }

    private void showSignUpDialog() {
        AlertDialog waitingDialog = new SpotsDialog(MainActivity.this);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getString(R.string.register));
        dialog.setMessage(R.string.please_register);

        LayoutInflater inflater = LayoutInflater.from(this);
        View login_layout = inflater.inflate(R.layout.layout_login, null);

        MaterialEditText edtEmail = login_layout.findViewById(R.id.edt_email);
        MaterialEditText edtPassword = login_layout.findViewById(R.id.edt_password);

        dialog.setView(login_layout);

        //set positive button
        dialog.setPositiveButton(getString(R.string.sign_in), (DialogInterface dialogInterface, int which) -> {
            dialogInterface.dismiss();

            //disable sign in when processing
            btnSignUp.setEnabled(false);

            waitingDialog.show();

            if (TextUtils.isEmpty(edtEmail.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_email_address), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (TextUtils.isEmpty(edtPassword.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_password), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (edtPassword.getText().toString().length() < 6) {
                Snackbar.make(root_relative_layout, getString(R.string.password_too_short), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            auth.signInWithEmailAndPassword(edtEmail.getText().toString(), edtPassword.getText().toString())
                    .addOnSuccessListener(authResult -> {
                        waitingDialog.hide();
                        startActivity(new Intent(MainActivity.this, WelcomeActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Snackbar.make(root_relative_layout,
                                R.string.failed_to_login + e.getMessage(), Snackbar.LENGTH_LONG)
                                .show();
                        waitingDialog.hide();

                        //enable sign in on clicking cancel
                        btnSignUp.setEnabled(true);

                    });

        });

        //set negative button
        dialog.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            dialogInterface.dismiss();

        });

        dialog.show();

    }

    private void showRegisterDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getString(R.string.register));
        dialog.setMessage(R.string.please_register);

        LayoutInflater inflater = LayoutInflater.from(this);
        View register_layout = inflater.inflate(R.layout.layout_register, null);

        MaterialEditText edtEmail = register_layout.findViewById(R.id.edt_email);
        MaterialEditText edtPassword = register_layout.findViewById(R.id.edt_password);
        MaterialEditText edt_name = register_layout.findViewById(R.id.edt_name);
        MaterialEditText edt_phone = register_layout.findViewById(R.id.edt_phone);

        dialog.setView(register_layout);

        //set positive button
        dialog.setPositiveButton(getString(R.string.register), (DialogInterface dialogInterface, int which) -> {
            dialogInterface.dismiss();

            if (TextUtils.isEmpty(edtEmail.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_email_address), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (TextUtils.isEmpty(edtPassword.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_password), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (edtPassword.getText().toString().length() < 6) {
                Snackbar.make(root_relative_layout, getString(R.string.password_too_short), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (TextUtils.isEmpty(edt_name.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_name), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            if (TextUtils.isEmpty(edt_phone.getText().toString())) {
                Snackbar.make(root_relative_layout, getString(R.string.please_enter_phone), Snackbar.LENGTH_LONG)
                        .show();
                return;
            }

            auth.createUserWithEmailAndPassword(edtEmail.getText().toString(), edtPassword.getText().toString())
                    .addOnSuccessListener(authResult -> {
                        User newUser = new User();
                        newUser.setEmail(edtEmail.getText().toString());
                        newUser.setPassword(edtPassword.getText().toString());
                        newUser.setName(edt_name.getText().toString());
                        newUser.setPhone(edt_phone.getText().toString());

                        //use email as key
                        users.child(auth.getCurrentUser().getUid())
                                .setValue(newUser)
                                .addOnSuccessListener(aVoid -> Snackbar.make(root_relative_layout,
                                        R.string.registration_sucessful, Snackbar.LENGTH_LONG)
                                        .show())
                                .addOnFailureListener(e -> Snackbar.make(root_relative_layout,
                                        R.string.failed_to_register + e.getMessage(), Snackbar.LENGTH_LONG)
                                        .show());
                    }).addOnFailureListener(e -> Snackbar.make(root_relative_layout,
                    R.string.failed_to_register + e.getMessage(), Snackbar.LENGTH_LONG)
                    .show());

        });

        //set negative button
        dialog.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });

        dialog.show();

    }
}
