package com.lksnext.sqlite.impl.definition;

public class SchemaElement {
	private String table;
	private String source;
	private String cleanup;

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getCleanup() {
		return cleanup;
	}

	public void setCleanup(String cleanup) {
		this.cleanup = cleanup;
	}

}
