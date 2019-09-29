package com.example.forest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity {
    public static final String WELCOME_MESSAGE = "com.example.forest.WELCOME.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendToLandingPage(View view){
        Intent intent = new Intent(this,WelcomePageActivity.class);
        intent.putExtra(EXTRA_MESSAGE,"Welcome John");
        startActivity(intent);
    }
}
