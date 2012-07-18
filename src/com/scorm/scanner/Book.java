package com.scorm.scanner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class Book {
	private String isbn = "";
	private String title = "";
	private String authors = "";
	private String publisher = "";
	private String summary = "";
	private String notes = "";
	private boolean metadataCollected = false;
	private boolean successfullyUploaded = false;
	
	public String getIsbn() {
		return isbn;
	}
	public void setIsbn(String isbn) {
		this.isbn = isbn;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getAuthors() {
		return authors;
	}
	public void setAuthors(String authors) {
		this.authors = authors;
	}
	public String getPublisher() {
		return publisher;
	}
	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
	public String getNotes() {
		return notes;
	}
	public void setNotes(String notes) {
		this.notes = notes;
	}
	
	public boolean isMetadataCollected() {
		return metadataCollected;
	}
	public void setMetadataCollected(boolean metadataCollected) {
		this.metadataCollected = metadataCollected;
	}
	public boolean isSuccessfullyUploaded() {
		return successfullyUploaded;
	}
	public void setSuccessfullyUploaded(boolean successfullyUploaded) {
		this.successfullyUploaded = successfullyUploaded;
	}
	public Book() {
		
	}
	
	public Book(String isbn) {
		this.isbn = isbn;
		this.metadataCollected = false;
	}
	
	public Book(String isbn, String title, String authors, String publisher, String summary, String notes) {
		this.isbn = isbn;
		this.title = title;
		this.authors = authors;
		this.publisher = publisher;
		this.summary = summary;
		this.notes = notes;
	}
	
	public void Load(Scanner in) {
		this.isbn = in.nextLine();
		this.title = in.nextLine();
		this.authors = in.nextLine();
		this.metadataCollected = Boolean.parseBoolean(in.nextLine());
		this.successfullyUploaded = Boolean.parseBoolean(in.nextLine());
	}
	
	public void Save(BufferedWriter out) throws IOException {
		out.write(isbn);
		out.newLine();
		out.write(title);
		out.newLine();
		out.write(authors);
		out.newLine();
		out.write(Boolean.toString(metadataCollected));
		out.newLine();
		out.write(Boolean.toString(successfullyUploaded));
		out.newLine();
	}

	public void Save(PrintWriter out){
		out.println(isbn);
		out.println(title);
		out.println(authors);
		out.println(Boolean.toString(metadataCollected));
		out.println(Boolean.toString(successfullyUploaded));
	}
	
	@Override
	public boolean equals(Object o) {
		if (o.getClass() != this.getClass()) return false;
		Book other = (Book)o;
		return this.isbn.equals(other.getIsbn());
	}
	
	@Override
	public String toString() {
		if (!metadataCollected) {
			return isbn; 
		} else if (!successfullyUploaded) {
			return title;
		}
		return "Uploaded: " + title;
	}
	
	public void copy(Book book) {
		this.authors = book.getAuthors();
		this.metadataCollected = book.isMetadataCollected();
		this.notes = book.getNotes();
		this.publisher = book.getPublisher();
		this.summary = book.getSummary();
		this.title = book.getTitle();
		this.successfullyUploaded = book.isSuccessfullyUploaded();
	}
}
