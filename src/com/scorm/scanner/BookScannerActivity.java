package com.scorm.scanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 
 * @author Jaffer Ibrahim The BookScannerActivity is the main launch activity
 *         for the application. It will open the barcode scanner (handled by
 *         ZXing) and get the result Currently this assumed the scanned barcode
 *         is for a book (not always the case) ZXing API has a way to determine
 *         the kind of object scanned, will be implemented after the base app is
 *         completed.
 */
// File structure:
// boolean (indicates whether or not the phone came with an email)
// email|user list of pairings
// TODO: Facebook's Android integration is fairly simple.  Wouldn't be hard to post to the users wall
// TODO: Switch to GoogleProducts API -- will require setting up a scorm account on google
// TODO: ISBNDB does provide a price listing (so does googleproducts).  That could be a possible function
public class BookScannerActivity extends Activity implements
		View.OnClickListener {
	Button buttonScan;
	Button buttonHistory;
	MetadataGetter metadataGetter;
	PostToCloud postToCloud;
	NotificationManager notificationManager;
	Dialog userDialog;

	String endpoint;
	String appId;
	String secretKey;
	
	String userEmail;
	String userName;
	boolean foundUser;
	boolean isCustomUser;
	ArrayList<Book> books = new ArrayList<Book>();
	
	ProgressDialog pd;
	Dialog splashDialog;
	Dialog configureDialog;
	SaveState state = null;
	
	public static final int SCANNER_INTENT = 100;
	public static final int VIEWER_INTENT = 101;
	
	private static final long FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1000;
	
	private final String DEFAULT_ENDPOINT = "https://cloud.scorm.com/ScormEngineInterface/TCAPI/public";
	private final String DEFAULT_APPID = "7YY8O781S0";
	private final String DEFAULT_SECRETKEY = "VGVzdFVzZXI6cGFzc3dvcmQ=";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.scannermenu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuConfig:
			configureDialog = dialogConfigure();
			configureDialog.show();
			break;
		case R.id.menuClear:
			getApplicationContext().deleteFile("users.dat");
			//File file = new File("users.dat");
			//boolean deleted = file.delete();
			Intent intent = getIntent();
			finish();
			startActivity(intent);
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * Retains state data for a configuration change (such as rotating the phone)
	 * Used for keeping the AsyncTask object references
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		if (metadataGetter == null && postToCloud == null && ((userEmail == null || userEmail.equals("")) || (userName == null || userName.equals(""))))
			return null;
		state = new SaveState();
		state.metadataInstance = metadataGetter;
		state.postInstance = postToCloud;
		state.userEmail = this.userEmail;
		state.userName = this.userName;
		state.isConfiguring = (configureDialog != null);
		return state;
	}
	
	/**
	 * Activity Construction logic, restores the activity state
	 */
	@Override
	protected void onResume() {
		super.onResume();

		foundUser = false;
		buttonScan = (Button) findViewById(R.id.buttonScan);
		buttonScan.setOnClickListener(this);

		buttonHistory = (Button) findViewById(R.id.buttonHistory);
		buttonHistory.setOnClickListener(this);
		
		if ((SaveState)getLastNonConfigurationInstance() == null) {
			String expectedUserEmail = UserEmailGetter.getEmail(getApplicationContext());
			userName = "";

			{
				FileInputStream fin = null;
				Scanner scanner = null;
				String in;
				String[] emailNamePairs;

				try {
					fin = openFileInput("users.dat");
					scanner = new Scanner(fin);
					in = scanner.nextLine();
					isCustomUser = Boolean.parseBoolean(in);
					userEmail = scanner.nextLine();
					userName = scanner.nextLine();
					if (expectedUserEmail == null || expectedUserEmail.equals("") || expectedUserEmail.equals(userEmail))
						foundUser = true;
					scanner.close();
				} catch (IOException e) {
					userEmail = expectedUserEmail;
				}
			}

			if (!isNetworkAvailable()) {
				Toast.makeText(this, R.string.noNetwork, Toast.LENGTH_LONG)
						.show();
			}
			
			if (!foundUser) {
				showSplash(false);
				dialogUsernameFull().show();
			}
		}
		else
		{
			state = (SaveState)getLastNonConfigurationInstance();
			this.metadataGetter = state.metadataInstance;
			this.postToCloud = state.postInstance;
			this.userEmail = state.userEmail;
			this.userName = state.userName;
			if (metadataGetter != null) {
				metadataGetter.activity = this;
			}
			if (postToCloud != null) {
				postToCloud.activity = this;
			}
		}

		// Attempt to read in the data file
		{
			FileInputStream fin = null;
			Scanner scanner = null;
			try {
				fin = openFileInput("books.dat");
				scanner = new Scanner(fin);
				books = readFile(scanner);
				scanner.close();
			} catch (IOException e) {

			}
		}
		
		{
			FileInputStream fin = null;
			Scanner scanner = null;
			try {
				fin = openFileInput("config.dat");
				scanner = new Scanner(fin);
				endpoint = scanner.nextLine();
				appId = scanner.nextLine();
				secretKey = scanner.nextLine();
				scanner.close();
			} catch (IOException e) {
				endpoint = DEFAULT_ENDPOINT;
				appId = DEFAULT_APPID;
				secretKey = DEFAULT_SECRETKEY;;
			}
		}
		// Don't modify the UI in onCreate, there's no guarantee we have a context or that
		// The display will be entirely accurate
		if (state != null) { 
			if (metadataGetter != null || postToCloud != null) {
				displayLoading();
			} else if (state.isConfiguring) {
				configureDialog = dialogConfigure();
				configureDialog.show();
			}
		}
		buttonHistory.setEnabled(books.size() != 0);
	}
	
	/**
	 * Displays the background splash screen
	 * @param cancelable if the splash should be cancelable (by pressing Back)
	 */
	private void showSplash(boolean cancelable) {
		

		splashDialog = new Dialog(this, R.style.splashScreen);
		splashDialog.setContentView(R.layout.splash);
		splashDialog.setCancelable(cancelable);
		
		if (cancelable) {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (splashDialog != null) {
						splashDialog.dismiss();
						splashDialog = null;
					}
				}
			}, 3000);
		}
		
		splashDialog.show();
	}
	
	/**
	 * Dismisses the splash screen
	 */
	private void dismissSplash() {
		if (splashDialog != null) {
			splashDialog.dismiss();
			splashDialog = null;
		}
	}
	
	/**
	 * Pauses the activity, occurs whenever the activity goes into the background or closes.
	 * Releases the window handles to prevent leaking
	 */
	@Override
	protected void onPause() {
		super.onStop();
		if (splashDialog != null) {
			splashDialog.dismiss();
			splashDialog = null;
		}
		
		if (pd != null) {
			pd.dismiss();
			pd = null;
		}
		
		if (userDialog != null) {
			userDialog.dismiss();
			userDialog = null;
		}
	}
	
	/**
	 * Constructs a dialog for configuring the endpoint
	 * @return An unopened dialog
	 */
	private Dialog dialogConfigure() {
		showSplash(false);
		final View layout = View.inflate(this, R.layout.config, null);
		final EditText editEndpoint = (EditText)layout.findViewById(R.id.editEnpoint);
		final EditText editAppId = (EditText)layout.findViewById(R.id.editAppId);
		final EditText editSecretKey = (EditText)layout.findViewById(R.id.editSecretKey);
		
		editEndpoint.setText(endpoint);
		editAppId.setText(appId);
		editSecretKey.setText(secretKey);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		builder.setTitle(R.string.dialogConfigure);
		builder.setIcon(0);
		
		builder.setPositiveButton(R.string.ok, new Dialog.OnClickListener() {

			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				BookScannerActivity.this.endpoint = editEndpoint.getText().toString();
				BookScannerActivity.this.appId = editAppId.getText().toString();
				BookScannerActivity.this.secretKey = editSecretKey.getText().toString();
				dismissSplash();
				
				FileOutputStream fout = null;
				PrintWriter out = null;
				try {
					fout = openFileOutput("config.dat", Context.MODE_PRIVATE);
					out = new PrintWriter(fout);
					out.println(endpoint);
					out.println(appId);
					out.println(secretKey);
					out.flush();
					out.close();
				} catch (IOException e) {
					
				}
				
			}
			
		});
		builder.setNegativeButton(R.string.cancel, new Dialog.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dismissSplash();
			}
			
		});
		builder.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
				dismissSplash();
			}
			
		});
		
		builder.setView(layout);
		return builder.create();
	}
	
	/**
	 * Deprecated - For privacy concerns
	 * Constructs a dialog for username input
	 * @return An unopened dialog
	 */
	private Dialog dialogUsername() {
	    userDialog = new Dialog(this);
	    final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
	    params.copyFrom(userDialog.getWindow().getAttributes());
	    params.width = WindowManager.LayoutParams.MATCH_PARENT;
	    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
	    userDialog.setCancelable(false);
	    userDialog.setTitle(getString(R.string.dialogUsername) + " " + userEmail);
	    userDialog.setContentView(R.layout.login);
	    
	    final EditText savedText = (EditText)userDialog.findViewById(R.id.editTextLogin);
	    final Button saveButton = (Button)userDialog.findViewById(R.id.buttonLoginDialogSave);
	    saveButton.setEnabled(false);

	    userDialog.setOnShowListener(new OnShowListener() {

			@Override
			public void onShow(DialogInterface arg0) {
				userDialog.getWindow().setAttributes(params);
			}
	    	
	    });

	    
	    saveButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				userName = savedText.getText().toString().trim();
				saveUserName();
				dismissSplash();
				configureDialog = null;
				userDialog.dismiss();
				userDialog = null;
			}
		});
	    
	    savedText.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable edit) {
				saveButton.setEnabled(edit.length() > 3);
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
				
			}

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) {
				
			}
	    	
	    });
	    
	    return userDialog;
	 }
	
	/**
	 * Constructs a dialog for inputting an email and username without accessing
	 * the phones email
	 * @return
	 */
	private Dialog dialogUsernameFull() {
	    userDialog = new Dialog(this);
	    final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
	    params.copyFrom(userDialog.getWindow().getAttributes());
	    params.width = WindowManager.LayoutParams.MATCH_PARENT;
	    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
	    userDialog.setCancelable(false);
	    userDialog.setTitle(getString(R.string.dialogUsername));
	    userDialog.setContentView(R.layout.extendedlogin);
	    userDialog.setOnShowListener(new OnShowListener() {

			@Override
			public void onShow(DialogInterface arg0) {
				userDialog.getWindow().setAttributes(params);
			}
	    	
	    });
	    
	    final EditText emailText = (EditText)userDialog.findViewById(R.id.editEmailFull);

		if (!(userEmail == null || userEmail.equals(""))) {
			emailText.setText(userEmail);
		}
		final EditText nameText = (EditText)userDialog.findViewById(R.id.editNameFull);
	    final Button saveButton = (Button)userDialog.findViewById(R.id.buttonExtendedLoginSave);
	    saveButton.setEnabled(false);

	    
		saveButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				userEmail = emailText.getText().toString().trim();
				userName = nameText.getText().toString().trim();
				saveUserName();
				dismissSplash();
				configureDialog = null;
				userDialog.dismiss();
				userDialog = null;
			}
		});
	    
	    FullDialogWatcher watcher = new FullDialogWatcher();
	    watcher.setEditEmail(emailText);
	    watcher.setEditName(nameText);
	    watcher.setButtonSave(saveButton);
	    nameText.addTextChangedListener(watcher);
	    
	    return userDialog;
	 }
	
	/**
	 * 
	 * @author Jaffer
	 * Provides immediate validation of the text fields as they are being filled in (for login)
	 */
	private class FullDialogWatcher implements TextWatcher {
		private EditText editEmail;
		private EditText editName;
		private Button buttonSave;
		private final Pattern pattern = Pattern.compile("^[\\w\\.-]+@([\\w\\-]+\\.)+[A-Z]{2,4}$", Pattern.CASE_INSENSITIVE);

		public void setEditEmail(EditText editEmail) {
			this.editEmail = editEmail;
		}


		public void setEditName(EditText editName) {
			this.editName = editName;
		}

		public void setButtonSave(Button buttonSave) {
			this.buttonSave = buttonSave;
		}

		@Override
		public void afterTextChanged(Editable arg0) {
			Matcher matcher = pattern.matcher(editEmail.getText().toString().trim());
			buttonSave.setEnabled(editName.length() > 3 && matcher.matches());
		}

		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
			
		}

		@Override
		public void onTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
			
		}
		
	}
	
	/**
	 * Saves the username
	 */
	private void saveUserName() {
		FileOutputStream fout = null;
		PrintWriter out = null;
		try {
			fout = openFileOutput("users.dat", Context.MODE_PRIVATE);
			out = new PrintWriter(fout);
			out.println(isCustomUser);
			out.println(userEmail);
			out.println(userName);
			out.flush();
			out.close();
		} catch (IOException e) {
			
		} finally {
		}
	}
	
	/**
	 * Displays the loading dialog, blocking the UI
	 */
	private void displayLoading() {
		if (splashDialog == null) {
			showSplash(false);
		}
		if (pd == null)
			pd = ProgressDialog.show(this, getString(R.string.progressTitle), getString(R.string.progressText), true, false);
	}
	/**
	 * Dismisses the loading screen
	 */
	private void dismissLoading() {
		if (pd != null) {
			pd.dismiss();
			pd = null;
		}
		dismissSplash();
	}

	/**
	 * Called when the another Activity finishes.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SCANNER_INTENT) {
			if (resultCode == RESULT_OK) {
				String contents = data.getStringExtra("SCAN_RESULT");
				String format = data.getStringExtra("SCAN_RESULT_FORMAT");
 				collectData(contents);
			} else if (resultCode == RESULT_CANCELED) {
				// handle a cancel, if necessary
			}
		} else if (requestCode == VIEWER_INTENT){
			if (resultCode == BookDetailsActivity.RESULT_UPLOAD) {
				int index = data.getExtras().getInt(BookDetailsActivity.UPLOAD_INDEX_KEY);
				Book book = books.get(index);
				if (book.isMetadataCollected()) {
					metadataGetterFinished(book);
				} else {
					String isbn = books.get(index).getIsbn();
					collectData(isbn);
				}
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	/**
	 * 
	 * @param isbn
	 *            The ISBN to collect the metadata for
	 */
	private void collectData(String isbn) {
		if (isbn.length() != 13) {
			Toast.makeText(getApplicationContext(), R.string.errorISBNNotScanned, Toast.LENGTH_SHORT).show();
			return;
		}
		Book data = new Book(isbn);
		displayLoading();
		if (metadataGetter == null && isNetworkAvailable()) { 
			// Only allow one connection at a time and wait for any other connections to close
			metadataGetter = new MetadataGetter();
			metadataGetter.setActivity(this);
			metadataGetter.execute(data);
		} else if (!isNetworkAvailable()) { // Save the object
			createNotification(data, this);
			dismissLoading();
			Toast.makeText(
					this,
					R.string.noNetworkISBN,
					Toast.LENGTH_LONG).show();
			addBook(data);
			buttonHistory.setClickable(books.size() != 0);
		}
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.buttonScan) {
			if (metadataGetter == null) {
				Intent intent = new Intent("com.google.zxing.client.android.SCAN");
				// Intent intent = new Intent("SCAN");
				intent.putExtra("SCAN_MODE", "PRODUCT_MODE");
				startActivityForResult(intent, SCANNER_INTENT);
			} else {
				Toast.makeText(getApplicationContext(),
						R.string.requestInProgress,
						Toast.LENGTH_SHORT).show();
			}
		} else if (v.getId() == R.id.buttonHistory) {
			Intent intent = new Intent(getApplicationContext(), BookViewerActivity.class);
			startActivityForResult(intent, VIEWER_INTENT);
		}

	}

	/**
	 * Determines if the network is available
	 * 
	 * @return True if the network is connected, false otherwise
	 */
	public boolean isNetworkAvailable() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		// If the network info is null, the phone is not connected
		if (networkInfo != null && networkInfo.isConnected())
			return true;
		return false;
	}

	/**
	 * Called when the MetadataGetter finishes. Launches the TinCanAPI post
	 * @param data 
	 */
	public void metadataGetterFinished(Book data) {
		metadataGetter = null;
		// Called when the MetadataGetter finishes. Should begin sending to the
		// Tin Can API
		if (data.getTitle() == null || data.getTitle().equals("")) { // If the title is null then the scanned item is not a book.
			Toast.makeText(getApplicationContext(), R.string.bookFailed, Toast.LENGTH_LONG).show();
			data = null;
			dismissLoading();
		} else {
			if (isNetworkAvailable()) {
				postToCloud = new PostToCloud();
				postToCloud.setActivity(this);
				postToCloud.execute(data);
			} else {
				createNotification(data, this);
				Toast.makeText(getApplicationContext(), R.string.toastPostFailed, Toast.LENGTH_LONG).show();
				addBook(data);
				dismissLoading();
			}
		}
	}
	
	/**
	 * Called when postToCloud finishes
	 */
	public void postToCloudFinished(Book data) {
		postToCloud = null;
		addBook(data);
		buttonHistory.setClickable(books.size() != 0);
		dismissLoading();
	}
	/**
	 * Adds a book to the array and writes the file
	 * @param data 
	 */
	public void addBook(Book data) {
		if (data == null) return;
		
		// first, find if any book matches this book
		for (int i = 0; i < books.size(); i++) {
			if (books.get(i).equals(data)) {
				if ((data.isMetadataCollected() && !books.get(i).isMetadataCollected())
						|| (data.isSuccessfullyUploaded() && !books.get(i).isSuccessfullyUploaded())) {
					books.remove(i);
				} else { // if neither condition is met, the saved book has more information in it.
					return;
				}
				break;
			}
		}
		
		books.add(data);
		
		data = null;
		
		// Now save the file
		FileOutputStream fout = null;
		PrintWriter out = null;
		try {
			fout = openFileOutput("books.dat", Context.MODE_PRIVATE);
			out = new PrintWriter(fout);
			writeFile(out, books);
			out.flush();
			out.close();
		} catch (IOException e) {
			
		} finally {
		}
		
		buttonHistory.setEnabled(books.size() != 0);
	}
	
	public static void createNotification(Book book, Context context) {
		StringBuilder sb = new StringBuilder();
		if (book != null) {
			sb.append(context.getString(R.string.toastUploadBook));
			sb.append(" ");
			sb.append(book.toString());
			sb.append(".");
		} else {
			sb.append(context.getString(R.string.toastUploadReminder));
		}
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		Notification notification = new Notification(R.drawable.notification,
				"Don't forget to upload your books.",
				System.currentTimeMillis() + FIFTEEN_MINUTES_MILLIS);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		Intent intent = new Intent(context, NotificationActivity.class);
		PendingIntent activity = PendingIntent.getActivity(context, 10, intent, 0);
		notification.setLatestEventInfo(context, "Book Scanner",
				sb.toString(), activity);
		notification.number += 1;
		notificationManager.notify(0, notification);
	}
	
	/**
	 * Reads data from a file and returns it as an arraylist
	 * @param scanner
	 * @return
	 */
	public static ArrayList<Book> readFile(Scanner scanner) {
		ArrayList<Book> books = new ArrayList<Book>();
		String read = null;
		while (scanner.hasNextLine()) {
			Book book = new Book();
			book.Load(scanner);
			books.add(book);
		}
		return books;
	}
	
	public static void writeFile(PrintWriter out, ArrayList<Book> books) throws IOException {
		for (Book b : books)
			b.Save(out);
	}

	/**
	 * @author Jaffer Ibrahim
	 * 		   Class used to get the metadata for the provided
	 *         ISBN number Launches an asynchronous task (required by android)
	 *         to connect and retrieve the XML data from isbndb.com
	 */
	static class MetadataGetter extends AsyncTask<Book, String, String> {

		private static final String ACCESS_KEY = "XD83HPR4";
		private static final String INDEX_KEY = "isbn";
		private static final String RESULT_KEY = "texts";

		// Query parameters
		private static final String QUERY_ACCESS_KEY = "access_key";
		private static final String QUERY_RESULT_KEY = "results";
		private static final String QUERY_INDEX = "index1";
		private static final String QUERY_VALUE = "value1";

		// XML Tags
		private static final String XML_TITLE = "Title";
		private static final String XML_AUTHOR = "AuthorsText";
		private static final String XML_PUBLISHER = "PublisherText";
		private static final String XML_SUMMARY = "Summary";
		private static final String XML_NOTES = "Notes";
		
		BookScannerActivity activity;
		String isbn;

		public BookScannerActivity getActivity() {
			return activity;
		}

		public void setActivity(BookScannerActivity activity) {
			this.activity = activity;
		}



		@Override
		protected String doInBackground(Book... params) {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			isbn = params[0].getIsbn(); // Only one ISBN should be passed at any
										// time

			try {
				Uri.Builder builder = new Uri.Builder();
				builder.scheme("http");
				builder.authority("isbndb.com");
				builder.path("api/books.xml");
				// First the access key must be passed
				builder.appendQueryParameter(QUERY_ACCESS_KEY, ACCESS_KEY);
				// Second pass the kind of result we want
				builder.appendQueryParameter(QUERY_RESULT_KEY, RESULT_KEY);
				// third pass the type of access. In this case, ISBN
				builder.appendQueryParameter(QUERY_INDEX, INDEX_KEY);
				// Last, pass the ISBN number
				builder.appendQueryParameter(QUERY_VALUE, isbn);

				String uriString = builder.toString();
				URI uri = new URI(uriString);
				request.setURI(uri);

				HttpResponse response = client.execute(request);

				if (response.getStatusLine().getStatusCode() == 200) { // no
																		// errors
					InputStream contentStream = response.getEntity()
							.getContent();
					InputStreamReader streamReader = new InputStreamReader(
							contentStream);
					BufferedReader in = new BufferedReader(streamReader);

					StringBuffer sb = new StringBuffer();
					String line;
					// Construct the XML file from the stream
					while ((line = in.readLine()) != null) {
						sb.append(line);
						sb.append("\n");
					}
					in.close();
					return sb.toString();

				} else {
					// Most likely some kind of error has occurred
				}
			} catch (IOException e) {
			} catch (URISyntaxException e) {
			}
			return null;
		}
		
	

		/**
		 * Parses the XML provided by isbndb.com
		 * 
		 * @param xml
		 *            The XML document as a string
		 * @return A book object representing the XML Document
		 * @throws XmlPullParserException
		 * @throws IOException
		 */
		private Book parseXML(String xml) throws XmlPullParserException,
				IOException {
			Book book = new Book();
			// Use a pull parser for simplicity
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xpp = factory.newPullParser();

			xpp.setInput(new StringReader(xml));

			// Tag flags. When one is set to true and the TEXT area is hit, we
			// know which variable to assign
			boolean isTitle = false, isAuthor = false, isPublisher = false, isSummary = false, isNotes = false;

			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {

				switch (eventType) {
				case XmlPullParser.START_TAG: // set the flag
					if (xpp.getName().equals(XML_TITLE)) {
						isTitle = true;
					}
					if (xpp.getName().equals(XML_AUTHOR)) {
						isAuthor = true;
					}
					if (xpp.getName().equals(XML_PUBLISHER)) {
						isPublisher = true;
					}
					if (xpp.getName().equals(XML_SUMMARY)) {
						isSummary = true;
					}
					if (xpp.getName().equals(XML_NOTES)) {
						isNotes = true;
					}
					break;
				case XmlPullParser.TEXT: // Place the text into the appropriate
											// member
					if (isTitle)
						book.setTitle(xpp.getText());
					if (isAuthor)
						book.setAuthors(xpp.getText());
					if (isPublisher)
						book.setPublisher(xpp.getText());
					if (isSummary)
						book.setSummary(xpp.getText());
					if (isNotes)
						book.setNotes(xpp.getText());
					break;
				case XmlPullParser.END_TAG: // Just reset all flags
					isTitle = isAuthor = isPublisher = isSummary = isNotes = false;
					break;
				}

				eventType = xpp.next();
			}
			return book;
		}

		@Override
		protected void onPostExecute(String xml) {
			super.onPostExecute(xml);
			Book data = null;
			try {
				data = parseXML(xml); // null the getter so another barcode can be scanned
				data.setIsbn(isbn);
				data.setMetadataCollected(true);
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			activity.metadataGetterFinished(data);
		}

	}

	/**
	 * 
	 * @author Jaffer Ibrahim 
	 * 		   Posts to the cloud that the user has read a book
	 *         TODO: Expand on the posting capabilities (started, skimmed, etc)
	 */
	static class PostToCloud extends AsyncTask<Book, String, Book> {

		BookScannerActivity activity;
		String endpoint;
		String appId;
		String secretKey;
		String userName;
		String userEmail;
		
		public BookScannerActivity getActivity() {
			return activity;
		}
		/**
		 * This class requires a valid activity reference so that it may run in the UI Thread after a detach
		 * @param activity
		 */
		public void setActivity(BookScannerActivity activity) {
			this.activity = activity;
		}
		/**
		 * Launched when .execute() is called.  Sets up a reference to the values it needs to retain
		 * It requires a copy to avoid crashes during a configuration change
		 */
		@Override
		protected void onPreExecute()
		{
			this.endpoint = activity.endpoint;
			this.appId = activity.appId;
			this.secretKey = activity.secretKey;
			this.userName = activity.userName;
			this.userEmail = activity.userEmail;
		}
		/**
		 * The actual work this class will do.  No references to the activity are allowed as this thread will persist
		 * even if the UI Thread is destroyed.
		 */
		@Override
		protected Book doInBackground(Book... params) {
			URL url = null;
			Book data = params[0];
			try {
				StringBuilder urlString = new StringBuilder();
				urlString.append(endpoint);
				urlString.append("/statements");
				url = new URL(urlString.toString());
			} catch (MalformedURLException e1) {
				data.setSuccessfullyUploaded(false);
				return data;
			}

			ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
			InputStream responseStream = null;
			URLConnection connection = null;

			try {

				connection = url.openConnection();
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(30000);
				connection.setDoOutput(true);
				connection.setDoInput(true);
				connection.setUseCaches(false);

				String basicAuthHeader = "Basic "
						+ Base64.encodeBytes((appId + ":" + secretKey)
								.getBytes("UTF-8"));

				((HttpURLConnection) connection).setRequestProperty(
						"Authorization", basicAuthHeader);
				String postData = createJSON(data);
				Log.i("DATA LENGTH", postData.getBytes("UTF-8").length + "");

				if (postData != null) {
					((HttpURLConnection) connection).setRequestMethod("POST");
					connection.setRequestProperty("Content-Type",
							"application/json");
					connection.setRequestProperty("Content-Length", ""
							+ Integer.toString(postData.getBytes("UTF-8").length));
					DataOutputStream wr = new DataOutputStream(
							connection.getOutputStream());
					try {
						wr.writeBytes(postData);
						wr.flush();
					} catch (Exception e) {

					} finally {
						wr.close();
					}
				}
				responseStream = connection.getInputStream();

				bufferedCopyStream(responseStream, responseBytes);
				responseBytes.flush();
				responseBytes.close();
				publishProgress(new String(responseBytes.toByteArray(), "UTF-8"));
				data.setSuccessfullyUploaded(true);
				return data;
			} catch (IOException ioe) {
				ioe.printStackTrace();
				try {
					// publishProgress(ioe.getMessage() + " -> " + readStreamAsString(((HttpURLConnection) connection).getErrorStream()));
				} catch (Exception e) {
				}
			} catch (Exception e) {
			} finally {
				if (responseStream != null) {
					try {
						responseStream.close();
					} catch (IOException e) {
					}
				}
			}
			data.setSuccessfullyUploaded(false);
			return data;
		}
	    
	    public String readStreamAsString(InputStream is) throws Exception {
	    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    	bufferedCopyStream(is, baos);
	    	return new String(baos.toByteArray(), "UTF-8");
	    }
	    
		public boolean bufferedCopyStream(InputStream inStream,
				OutputStream outStream) throws Exception {
			BufferedInputStream bis = new BufferedInputStream(inStream);
			BufferedOutputStream bos = new BufferedOutputStream(outStream);
			while (true) {
				int data = bis.read();
				if (data == -1) {
					break;
				}
				bos.write(data);
			}
			bos.flush();
			return true;
		}
		/**
		 * Runs in the UI thread assuming a valid activity reference exists.
		 */
		@Override
		protected void onPostExecute(Book result) {
			super.onPostExecute(result);
			if (result.isSuccessfullyUploaded()) {
				Toast.makeText(activity.getApplicationContext(), 
						R.string.successfulUpload,
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(activity.getApplicationContext(), 
						R.string.unsuccessfulUpload,
						Toast.LENGTH_SHORT).show();
			}
			activity.postToCloudFinished(result);
		}
		/**
		 * Structures a JSON object based on a book param.  Runs in the async thread.
		 * @param book
		 * @return
		 */
		private String createJSON(Book book) {
			StringBuilder json = new StringBuilder();
			String auth = book.getAuthors().trim();
			// ISBNDB usually returns a comma-delimited list.  And it ends in a comma.  Sometimes.
			auth = auth.charAt(auth.length() - 1) == ',' ? auth.substring(0, auth.length() - 1) : auth;
			String[] authors = book.getAuthors().split(",");
			
			// Do not use String Concat in Android.  A lot of the weaker phones will suffer heavily from it
			json.append("[{");
				json.append("\"actor\":{");
					json.append("\"name\":[\"");
					json.append(userName);
					json.append("\"]");
					json.append(",\"mbox\":[\"mailto:");
					json.append(userEmail);
					json.append("\"]");
				json.append("},");
				json.append("\"verb\":\"");
				json.append("completed");
				json.append("\",");
			
				json.append("\"object\":{");
					json.append("\"objectType\":\"Activity\",");
					json.append("\"id\":\"");
					json.append(book.getTitle());
					json.append("\",");
					json.append("\"definition\":{");
						json.append("\"name\":{\"en-US\":\"");
						json.append(book.getTitle());
						json.append("\"},");
						json.append("\"description\":{\"en-US\":\"");
						json.append(authors[0]);
						for (int i = 1; i < authors.length - 1; i++) { // All ISBNDB books end with an empty author
							json.append(",");
							json.append(authors[i]);
						}
						json.append("\"}");
						// json.append("\"description\":{\"en-US\":\"" + book.getSummary() + "\"}");
					json.append("}");
				json.append("},");
				json.append("\"context\" : { ");
					json.append("\"extensions\" : {");
						json.append("\"verb\" : \"read\"");
					json.append("}");
				json.append("}");
			json.append("}]");
			// Log.i("JSON", json.toString());
			// TODO: Add unicode support to the post
			return json.toString().replaceAll("[^\\x20-\\x7e]", ""); // Remove all non-ASCII characters to avoid errors
		}
	}
	
	static class SaveState {
		PostToCloud postInstance;
		MetadataGetter metadataInstance;
		boolean isConfiguring;
		String userEmail;
		String userName;
	}
}