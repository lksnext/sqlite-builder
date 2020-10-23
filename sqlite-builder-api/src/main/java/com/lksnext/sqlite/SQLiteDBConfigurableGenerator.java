package com.lksnext.sqlite;

import java.util.List;
import java.util.Map;

public interface SQLiteDBConfigurableGenerator {

	void createDatabases(String definition, List<Map<String, String>> contexts);
	void createDatabase(String definition, Map<String, String> context);

}
