package com.lksnext.sqlite.impl.util;

import org.jooq.DataType;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

public class GenericTable extends TableImpl<Record> {

    private static final long serialVersionUID = -4571092839737556261L;

    public GenericTable(Name name) {
        super(name);
    }

    public void addField(String fieldName, DataType<?> type) {
        createField(DSL.name(fieldName), type);
    }

}
