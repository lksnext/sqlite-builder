package com.lksnext.sqlite.impl.util;

import static org.jooq.impl.DSL.constraint;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.CreateTableColumnStep;
import org.jooq.DDLQuery;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.InsertSetMoreStep;
import org.jooq.InsertSetStep;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public class ResultSetImporter {
	
    private static final String SQLITE_PK_COLUMN = "id";
    private static final String COLUMN_NAME_REGEX = "^(\\w+)(?:\\#(\\w+)\\#)?$";
    private static final String WITHOUT_ROWID_SQL = "WITHOUT ROWID";
    
    @SuppressWarnings("resource")
    public static GenericTable convertResultSetSQLite(DSLContext create, ResultSet resultSet, String tableName)
            throws DataAccessException, SQLException {
        boolean first = true;
        boolean pkDefined = false;

        DDLQuery createTableQuery = (DDLQuery) create.createTable(tableName);

        GenericTable tableDefinition = new GenericTable(DSL.name(tableName));

        while (resultSet.next()) {

            InsertSetStep<Record> insertOperation = create.insertInto(table(name(tableName)));
            InsertSetMoreStep<Record> lastStep = null;

            ResultSetMetaData rsmd = resultSet.getMetaData();
            int total_columns = resultSet.getMetaData().getColumnCount();
            String allColumns[] = new String[total_columns];
            for (int i = 0; i < total_columns; i++) {
                Object value = null;
                
                String rawColumnName = rsmd.getColumnLabel(i + 1).toLowerCase();
                String columnName = rawColumnName;
                String forcedTypeName = null;
                Integer forcedType = null;

                if (columnName.equals("_id") || columnName.equals(SQLITE_PK_COLUMN)) {
                    columnName = SQLITE_PK_COLUMN;
                    pkDefined = true;
                    if (first) {
                        createTableQuery =
                                ((CreateTableColumnStep) createTableQuery).column(SQLITE_PK_COLUMN, SQLDataType.CLOB);
                    }
                } else {
                    Pattern pattern = Pattern.compile(COLUMN_NAME_REGEX);
                    Matcher matcher = pattern.matcher(rawColumnName);
                    if (matcher.matches()) {
                    	columnName = matcher.group(1);
                        forcedTypeName = matcher.group(2);
                        if (forcedTypeName != null) {
                            forcedType = translateForcedType(forcedTypeName);
                        }
                    }
                }

                @SuppressWarnings("rawtypes")
                DataType columDataType;
                int scale = 0;


                // SQLite TEXT type
                columDataType = SQLDataType.CLOB;

                int sqlType = resultSet.getMetaData().getColumnType(i + 1);
                if (forcedType != null) {
                    sqlType = forcedType.intValue();
                }
                switch (sqlType) {
                    case (Types.BIT):
                        columDataType = SQLDataType.BOOLEAN;
                        value = resultSet.getBoolean(i + 1);
                    case (Types.CHAR):
                    case (Types.VARCHAR):
                    case (Types.NVARCHAR):
                        columDataType = SQLDataType.CLOB;
                        value = resultSet.getString(i + 1);

                        break;
                    case (Types.DECIMAL):
                    case (Types.NUMERIC):
                    case (Types.FLOAT):
                        scale = resultSet.getMetaData().getScale(i + 1);
                        if (scale == 0) {
                            columDataType = SQLDataType.INTEGER;
                            value = resultSet.getLong(i + 1);
                        } else {
                            columDataType = SQLDataType.REAL;
                            value = resultSet.getBigDecimal(i + 1);

                        }
                        break;
                    case (Types.DOUBLE):
                        scale = resultSet.getMetaData().getScale(i + 1);
                        columDataType = SQLDataType.REAL;
                        value = resultSet.getDouble(i + 1);
                        break;
                    case (Types.INTEGER):
                        columDataType = SQLDataType.INTEGER;
                        value = resultSet.getLong(i + 1);
                        break;
                    case (Types.TIMESTAMP):
                    case (Types.DATE):
                        value = resultSet.getDate(i + 1);
                        if (value != null) {
                        	if(forcedType != null && forcedType.intValue() == sqlType) {
								columDataType = forcedType.equals(Types.TIMESTAMP) ? SQLDataType.TIMESTAMP
										: SQLDataType.DATE;
                        	} else  if (new SimpleDateFormat("HH:mm:ss").format((Date) value).equals("00:00:00")) {
                                columDataType = SQLDataType.DATE;
                            } else {
                                columDataType = SQLDataType.TIMESTAMP;
                            }
                        } else {
                            columDataType = SQLDataType.TIMESTAMP;
                        }
                        
                        if (SQLDataType.TIMESTAMP.equals(columDataType)) {
                        	value = resultSet.getTimestamp(i + 1);
                        }

                        break;

                }

                if (first) {
                    if (!SQLITE_PK_COLUMN.equals(columnName)) {
                        createTableQuery = ((CreateTableColumnStep) createTableQuery).column(columnName, columDataType);
                        tableDefinition.addField(columnName, columDataType);
                    }
                    allColumns[i] = columnName;
                }

                if (lastStep == null) {
                    lastStep = insertOperation.set(field(columnName), value);
                } else {
                    lastStep = lastStep.set(field(columnName), value);
                }

            }

            if (first) {
                if (pkDefined) {
                    createTableQuery = ((CreateTableColumnStep) createTableQuery)
                            .constraints(constraint("PK_" + tableName).primaryKey(SQLITE_PK_COLUMN));
                    String createTableSQL = createTableQuery.getSQL() + " " + WITHOUT_ROWID_SQL;
                    //createTableQuery.execute();
                    create.execute(createTableSQL);
                } else {
                    ((CreateTableColumnStep) createTableQuery).constraints(constraint("PK_" + tableName).primaryKey(allColumns));
                    String createTableSQL = createTableQuery.getSQL() + " " + WITHOUT_ROWID_SQL;
                    create.execute(createTableSQL);
                }

            }

            lastStep.execute();
            first = false;
        }

        return tableDefinition;
    }

    private static Integer translateForcedType(String typeName) {
        Class<Types> typesClass = Types.class;
        try {
            Field field = typesClass.getField(typeName.toUpperCase());
            return field.getInt(null);
        } catch (Exception e) {
            return null;
        }

    }
}
