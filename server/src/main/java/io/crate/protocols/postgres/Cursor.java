/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.protocols.postgres;

import java.util.List;

import javax.annotation.Nullable;

import io.crate.action.sql.PreparedStmt;
import io.crate.action.sql.RowConsumerToResultReceiver;
import io.crate.analyze.AnalyzedStatement;

public class Cursor extends Portal {

    private PreparedStmt pStmtOfFetch;
    private List<Object> paramsOfFetch;
    private FormatCodes.FormatCode[] resultFormatCodesOfFetch;

    public static Cursor declare(String portalName,
                                 PreparedStmt preparedStmt,
                                 List<Object> params,
                                 AnalyzedStatement analyzedStatement,
                                 @Nullable FormatCodes.FormatCode[] resultFormatCodes) {
        var c = new Cursor(portalName, preparedStmt, params, analyzedStatement, resultFormatCodes);
        c.bindFetch(preparedStmt, params, resultFormatCodes);
        c.setActiveConsumer(RowConsumerToResultReceiver.SUSPENDED);
        return c;
    }

    private Cursor(String portalName,
                   PreparedStmt preparedStmt,
                   List<Object> params,
                   AnalyzedStatement analyzedStatement,
                   @Nullable FormatCodes.FormatCode[] resultFormatCodes) {
        super(portalName, preparedStmt, params, analyzedStatement, resultFormatCodes);
    }

    public void bindFetch(PreparedStmt preparedStmt,
                          List<Object> params,
                          FormatCodes.FormatCode[] resultFormatCodes) {
        this.pStmtOfFetch = preparedStmt;
        this.paramsOfFetch = params;
        this.resultFormatCodesOfFetch = resultFormatCodes;

        var activeConsumer = this.activeConsumer();
        // equivalent to 'if this is the first time binding a fetch stmt to this cursor'
        if (RowConsumerToResultReceiver.SUSPENDED.equals(activeConsumer)) {
            this.setActiveConsumer(null);
        } else if (activeConsumer != null) {
            // equivalent to 'if this fetch is not the first fetch to be bounded to this cursor'
            // ex) fetch x from cursor; fetch y from cursor;
            // if rowCount is not reset, y is affected by x because the consumer is reused
            activeConsumer.resetRowCount(0);
        }
    }

    @Override
    public PreparedStmt preparedStmt() {
        return pStmtOfFetch;
    }

    @Override
    public AnalyzedStatement analyzedStatement() {
        return pStmtOfFetch.analyzedStatement();
    }

    public void close() {

    }
}
