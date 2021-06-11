package com.lksnext.sqlite.impl.definition;

import java.util.List;

public class DatabaseDefinition {
	
	private List<String> context;
	private String database;
	private String type;
	private String _extends;
	private String description;
	private Integer version;
	private List<SchemaElement> schema;
	
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	
	public List<String> getContext() {
		return context;
	}
	public void setContext(List<String> context) {
		this.context = context;
	}
	public String getDatabase() {
		return database;
	}
	public void setDatabase(String database) {
		this.database = database;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public List<SchemaElement> getSchema() {
		return schema;
	}
	public void setSchema(List<SchemaElement> schema) {
		this.schema = schema;
	}
	public String getExtends() {
		return _extends;
	}
	public void setExtends(String _extends) {
		this._extends = _extends;
	}
	public Integer getVersion() {
		return version;
	}
	public void setVersion(Integer version) {
		this.version = version;
	}

}
