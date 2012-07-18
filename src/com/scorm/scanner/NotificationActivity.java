package com.scorm.scanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class NotificationActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notification);
		
		Button upload = (Button)findViewById(R.id.buttonPost);
		Button remind = (Button)findViewById(R.id.buttonRemind);
		
		upload.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(NotificationActivity.this, BookScannerActivity.class);
				finish();
				startActivity(intent);
			}
		});
		
		remind.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				BookScannerActivity.createNotification(null, NotificationActivity.this);
				finish();
			}
		});
	}
}
