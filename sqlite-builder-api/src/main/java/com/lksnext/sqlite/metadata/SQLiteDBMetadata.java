package com.lksnext.sqlite.metadata;

import java.util.ArrayList;
import java.util.List;

public class SQLiteDBMetadata {

	public SQLiteDBFileInfo current;
	public List<SQLiteDBFileInfo> previous;
	
	public SQLiteDBMetadata() {
		current= null;
		previous = new ArrayList<SQLiteDBFileInfo>();
	}

	public SQLiteDBFileInfo getCurrent() {
		return current;
	}

	public void setCurrent(SQLiteDBFileInfo current) {
		this.current = current;
	}

	public List<SQLiteDBFileInfo> getPrevious() {
		return previous;
	}

	public void setPrevious(List<SQLiteDBFileInfo> previous) {
		this.previous = previous;
	}

}
