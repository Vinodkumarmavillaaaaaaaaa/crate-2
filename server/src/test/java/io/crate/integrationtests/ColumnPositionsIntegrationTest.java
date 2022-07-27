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

package io.crate.integrationtests;

import org.junit.Test;

import static io.crate.testing.TestingHelpers.printedTable;
import static org.assertj.core.api.Assertions.assertThat;

public class ColumnPositionsIntegrationTest extends SQLIntegrationTestCase {

    @Test
    public void test_column_positions_from_create_table() {
        execute(
            """
                create table t (
                    ta boolean,
                    tb text,
                    tc object(dynamic) as (
                        td text,
                        te timestamp with time zone,
                        tf object(dynamic) as (
                            tg integer
                        )
                    ),
                    INDEX th using fulltext(tb, tc['td']),
                    ti ARRAY(OBJECT AS (tj INTEGER, tk TEXT)),
                    tl ARRAY(GEO_POINT)
                )
                """
        );
        execute("""
                    select column_name, ordinal_position
                    from information_schema.columns
                    where table_name = 't'
                    order by 2""");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                         ta| 1
                                                         tb| 2
                                                         tc| 3
                                                         tc['td']| 4
                                                         tc['te']| 5
                                                         tc['tf']| 6
                                                         tc['tf']['tg']| 7
                                                         ti| 9
                                                         ti['tj']| 10
                                                         ti['tk']| 11
                                                         tl| 12
                                                         """); // 'th' is a named index and is assigned column position 8
    }

    @Test
    public void test_column_positions_after_multiple_stmts_require_mapping_updates() throws Exception {

        String queryOrdinalPositions = "select column_name, ordinal_position from information_schema.columns where table_name = 't' order by 2";
        String expected = "";
        execute(
            """
                create table t (
                    tb array(object(dynamic)),
                    ta object(dynamic)
                ) with (column_policy = 'dynamic');
                """
        );
        execute(queryOrdinalPositions);
        expected += """
                 tb| 1
                 ta| 2
                 """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // alter1
        execute("alter table t add column ta['q']['r']['s'] int;");
        execute(queryOrdinalPositions);
        expected += """
                   ta['q']| 3
                   ta['q']['r']| 4
                   ta['q']['r']['s']| 5
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // insert1
        execute("insert into t (tc, ta) values ([1,2,3], {td = 1, te = {tf = false}})");
        execute("refresh table t");
        execute(queryOrdinalPositions);
        expected += """
            tc| 6
            ta['td']| 7
            ta['te']| 8
            ta['te']['tf']| 9
            """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // insert2
        execute("insert into t (td, tb, ta, tz) values (2, [{t1 = 1, t2 = 2}, {t3 = 3}], {te = {ti = {tj = {}, tk = [{tl = 1}, {tm = 2}]}}}, 'z')");
        execute("refresh table t");
        execute(queryOrdinalPositions);
        expected += """
                    td| 10
                    tz| 11
                    tb['t1']| 12
                    tb['t2']| 13
                    tb['t3']| 14
                    ta['te']['ti']| 15
                    ta['te']['ti']['tj']| 16
                    ta['te']['ti']['tk']| 17
                    ta['te']['ti']['tk']['tl']| 18
                    ta['te']['ti']['tk']['tm']| 19
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // update
        execute("update t set ta['tu'] = [{tv = {tw = false}},{tx = now()}]");
        execute(queryOrdinalPositions);
        expected += """
                    ta['tu']| 20
                    ta['tu']['tv']| 21
                    ta['tu']['tx']| 22
                    ta['tu']['tv']['tw']| 23
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);
    }

    @Test
    public void test_column_positions_after_multiple_stmts_require_mapping_updates_on_partitioned() throws Exception {

        String queryOrdinalPositions = "select column_name, ordinal_position from information_schema.columns where table_name = 't' order by 2";
        String expected = "";
        execute(
            """
                create table t (
                    tb array(object(dynamic)),
                    ta object(dynamic),
                    p int
                ) partitioned by (p) with (column_policy = 'dynamic');
                """
        );
        execute(queryOrdinalPositions);
        expected += """
                 tb| 1
                 ta| 2
                 p| 3
                 """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // alter1
        execute("alter table t add column ta['q']['r']['s'] int;");
        execute(queryOrdinalPositions);
        expected += """
                   ta['q']| 4
                   ta['q']['r']| 5
                   ta['q']['r']['s']| 6
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // insert1
        execute("insert into t (tc, ta) values ([1,2,3], {td = 1, te = {tf = false}})");
        execute("refresh table t");
        execute(queryOrdinalPositions);
        expected += """
            ta['td']| 7
            ta['te']| 8
            ta['te']['tf']| 9
            tc| 10
            """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // insert2
        execute("insert into t (td, tb, ta, tz) values (2, [{t1 = 1, t2 = 2}, {t3 = 3}], {te = {ti = {tj = {}, tk = [{tl = 1}, {tm = 2}]}}}, 'z')");
        execute("refresh table t");
        execute(queryOrdinalPositions);
        expected += """
            td| 11
            tz| 12
            ta['te']['ti']| 13
            ta['te']['ti']['tj']| 14
            ta['te']['ti']['tk']| 15
            ta['te']['ti']['tk']['tl']| 16
            ta['te']['ti']['tk']['tm']| 17
            tb['t1']| 18
            tb['t2']| 19
            tb['t3']| 20
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);

        // update
        execute("update t set ta['tu'] = [{tv = {tw = false}},{tx = now()}]");
        execute(queryOrdinalPositions);
        expected += """
                    ta['tu']| 21
                    ta['tu']['tv']| 22
                    ta['tu']['tv']['tw']| 23
                    ta['tu']['tx']| 24
                    """;
        assertThat(printedTable(response.rows())).isEqualTo(expected);
    }
}
