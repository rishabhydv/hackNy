package com.example.forest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class WelcomePageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_page);
    }

    public void sendToLeafView(View view){
        Intent intent = new Intent(this,LeafInfoActivity.class);
        intent.putExtra(EXTRA_MESSAGE,"Welcome John");
        startActivity(intent);
    }
}
