package com.scorm.scanner;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class BookViewerActivity extends ListActivity {
	ArrayList<Book> books;
	public static final String INDEX_KEY = "index";
	public static final String DELETE_INDEX_KEY = "delindex";
	
	@Override
	public void onCreate(Bundle stuff) {
		super.onCreate(stuff);

		setup();
	}
	
	private void setup() {
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
		
		ArrayAdapter<Book> adapter = new ArrayAdapter<Book>(this, android.R.layout.simple_list_item_1, books);
		setListAdapter(adapter);
		if (books.size() == 0)
			finish();
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent intent = new Intent(getApplicationContext(), BookDetailsActivity.class);
		intent.putExtra(INDEX_KEY, position);
		startActivityForResult(intent, BookDetailsActivity.REQUEST_CODE);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == BookDetailsActivity.REQUEST_CODE) {
			switch (resultCode) {
			case BookDetailsActivity.RESULT_DELETED:
				setup();
				break;
			case BookDetailsActivity.RESULT_UPLOAD:
				Intent intent = new Intent();
				intent.putExtra(BookDetailsActivity.UPLOAD_INDEX_KEY, data.getExtras().getInt(BookDetailsActivity.UPLOAD_INDEX_KEY));
				setResult(BookDetailsActivity.RESULT_UPLOAD,intent);
				finish();
				break;
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
}
