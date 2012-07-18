package com.scorm.scanner;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class BookDetailsActivity extends Activity implements View.OnClickListener {
	ArrayList<Book> books;
	Button buttonUpload;
	Button buttonDelete;
	Button buttonRemind;
	int index;
	Book b;
	
	public static final int RESULT_DELETED = 1234;
	public static final int RESULT_UPLOAD = 1235;
	public static final int REQUEST_CODE = 3;
	public static final String UPLOAD_INDEX_KEY = "delindex";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.bookexamine);
		if (getIntent() == null) {
			this.setResult(RESULT_CANCELED);
			finish();
		}
		
		buttonRemind = (Button)findViewById(R.id.buttonRemindExamine);
		buttonUpload = (Button)findViewById(R.id.buttonUpload);
		buttonDelete = (Button)findViewById(R.id.buttonDelete);
		buttonRemind.setOnClickListener(this);
		buttonUpload.setOnClickListener(this);
		buttonDelete.setOnClickListener(this);
		
		// Attempt to read in the data file
		FileInputStream fin = null;
		Scanner scanner = null;
		try {
			fin = openFileInput("books.dat");
			scanner = new Scanner(fin);
			books = BookScannerActivity.readFile(scanner);
			scanner.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Bundle extras = getIntent().getExtras();
		
		index = extras.getInt(BookViewerActivity.INDEX_KEY);
		b = books.get(index);
		TextView isbn = (TextView)findViewById(R.id.textIsbn);
		TextView title = (TextView)findViewById(R.id.textTitle);
		TextView authors = (TextView)findViewById(R.id.textAuthors);
		TextView uploaded = (TextView)findViewById(R.id.textUploaded);
		
		isbn.setText(b.getIsbn());
		title.setText(b.getTitle());
		authors.setText(b.getAuthors());
		uploaded.setText(b.isSuccessfullyUploaded() ? R.string.textYes : R.string.textNo);

		buttonUpload.setEnabled(!b.isSuccessfullyUploaded());
		buttonRemind.setEnabled(!b.isSuccessfullyUploaded());
	}
	@Override
	public void onClick(View view) {
		if (view.getId() == buttonUpload.getId()) {
			Intent intent = new Intent();
			intent.putExtra(UPLOAD_INDEX_KEY, index);
			setResult(RESULT_UPLOAD, intent);
			finish();
		}
		else if (view.getId() == buttonDelete.getId()) {
			books.remove(index);
			FileOutputStream fout = null;
			PrintWriter out = null;
			try {
				fout = openFileOutput("books.dat", Context.MODE_PRIVATE);
				out = new PrintWriter(fout);
				BookScannerActivity.writeFile(out, books);
				out.flush();
				out.close();
			} catch (IOException e) {
				
			} finally {
			}
			setResult(RESULT_DELETED);
			finish();
		}
		else if (view.getId() == buttonRemind.getId()) {
			BookScannerActivity.createNotification(b, this);
			finish();
		}
		
	}
}
