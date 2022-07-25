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

import static io.crate.testing.TestingHelpers.printedTable;
import static org.assertj.core.api.Assertions.assertThat;

import io.crate.common.unit.TimeValue;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TemporaryIntegrationTest extends SQLIntegrationTestCase {

    @Test
    public void test_named_index_assigned_column_position() {
        execute("""
                    CREATE TABLE tbl (
                     author TEXT NOT NULL,
                     INDEX author_ft USING FULLTEXT (author) WITH (analyzer = 'standard')
                   );
                    """);
        execute("ALTER TABLE tbl ADD COLUMN dummy text NOT NULL");
        execute("alter table tbl add column dummy2 text index using fulltext with (analyzer = 'standard')");
        execute("alter table tbl add column dummy3 int generated always as (char_length(author))");
        execute("select column_name, ordinal_position from information_schema.columns where table_name = 'tbl'");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                                author| 1
                                                                dummy| 3
                                                                dummy2| 4
                                                                dummy3| 5
                                                                """);
    }
    @Test
    public void test_issue_12630() {
        execute("""
                    CREATE TABLE IF NOT EXISTS "doc"."testing" (
                       "p1" text,
                       "nested" OBJECT(DYNAMIC) AS (
                          "sub1" OBJECT(DYNAMIC) AS (
                             "sub2" BIGINT
                            )
                       ),
                       "obj" OBJECT(DYNAMIC)
                    ) PARTITIONED BY ("p1");
                    """);

        execute("INSERT INTO \"doc\".\"testing\" (\"p1\", \"obj\") VALUES ('a',  '{\"new\": 1}')");
        execute("ALTER TABLE doc.testing ADD COLUMN \"obj\"['added'] TEXT");
        execute("INSERT INTO \"doc\".\"testing\" (\"p1\", \"obj\") VALUES ('a',  '{\"newer\": 1}')");

        execute("select column_name, ordinal_position from information_schema.columns where table_name = 'testing'");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                          p1| 1
                                                          nested| 2
                                                          nested['sub1']| 3
                                                          nested['sub1']['sub2']| 4
                                                          obj| 5
                                                          obj['new']| 6
                                                          obj['added']| 7
                                                          obj['newer']| 8
                                                          """);
        // failed to be fixed: io.crate.exceptions.SQLParseException: Can't overwrite default.properties.obj.position=5 with 4
    }

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
    public void test_column_positions_after_multiple_dynamic_inserts() throws Exception {

        execute(
            """
                create table t (
                    tb array(object(dynamic)),
                    ta object(dynamic)
                ) with (column_policy = 'dynamic');
                """
        );
        execute("""
                    select column_name, ordinal_position
                    from information_schema.columns
                    where table_name = 't'
                    order by 2""");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                         tb| 1
                                                         ta| 2
                                                         """);

        execute("alter table t add column ta['q']['r']['s'] int;");
        execute("""
                    select column_name, ordinal_position
                    from information_schema.columns
                    where table_name = 't'
                    order by 2""");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                               tb| 1
                                                               ta| 2
                                                               ta['q']| 3
                                                               ta['q']['r']| 4
                                                               ta['q']['r']['s']| 5
                                                                """);

        // dynamic insert 1
        execute("insert into t (tc, ta) values ([1,2,3], {td = 1, te = {tf = false}})");
        execute("""
                    select column_name, ordinal_position
                    from information_schema.columns
                    where table_name = 't'
                    order by 2""");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                             tb| 1
                                                             ta| 2
                                                             ta['q']| 3
                                                             ta['q']['r']| 4
                                                             ta['q']['r']['s']| 5
                                                             tc| 6
                                                             ta['td']| 7
                                                             ta['te']| 8
                                                             ta['te']['tf']| 9
                                                                """);

        // dynamic insert 2
        execute("insert into t (td, tb, ta, tz) values (2, [{t1 = 1, t2 = 2}, {t3 = 3}], {te = {ti = 5}}, 'z')");
        waitForMappingUpdateOnAll("t");
        execute("""
                    select column_name, ordinal_position
                    from information_schema.columns
                    where table_name = 't'
                    order by 2""");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                              tb| 1
                                                              ta| 2
                                                              ta['q']| 3
                                                              ta['q']['r']| 4
                                                              ta['q']['r']['s']| 5
                                                              tc| 6
                                                              ta['td']| 7
                                                              ta['te']| 8
                                                              ta['te']['tf']| 9
                                                              td| 10
                                                              tz| 11
                                                              tb['t1']| 12
                                                              tb['t2']| 13
                                                              tb['t3']| 14
                                                              ta['te']['ti']| 15
                                                                """);
    }

    @Test
    public void testConcurrentInsertsDoesNotBreakColumnPositions() throws Exception {

        //copied from testInsertIntoDynamicObjectColumnAddsAllColumnsToTemplate()

        // regression test for issue that caused columns not being added to metadata/tableinfo of partitioned table
        // when inserting a lot of new dynamic columns to various partitions of a table
        execute("create table dyn_parted (id int, bucket string, data object(dynamic), primary key (id, bucket)) " +
                "with (number_of_replicas = 0)");
        ensureYellow();

        int bulkSize = 10;
        int numCols = 5;
        String[] buckets = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"};
        final CountDownLatch countDownLatch = new CountDownLatch(buckets.length);
        AtomicInteger numSuccessfulInserts = new AtomicInteger(0);
        for (String bucket : buckets) {
            Object[][] bulkArgs = new Object[bulkSize][];
            for (int i = 0; i < bulkSize; i++) {
                bulkArgs[i] = new Object[]{i, bucket, createColumnMap(numCols, bucket)};
            }
            new Thread(() -> {
                try {
                    execute("insert into dyn_parted (id, bucket, data) values (?, ?, ?)", bulkArgs, TimeValue.timeValueSeconds(10));
                    numSuccessfulInserts.incrementAndGet();
                } finally {
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();
        // on a reasonable fast machine all inserts always work.
        assertThat(numSuccessfulInserts.get()).isGreaterThanOrEqualTo(1);

        // table info is maybe not up-to-date immediately as doc table info's are cached
        // and invalidated/rebuild on cluster state changes
        assertBusy(() -> {
            execute("select count(distinct ordinal_position) from information_schema.columns where table_name = 'dyn_parted'");
            assertThat(response.rows()[0][0]).isEqualTo(3L + numCols * numSuccessfulInserts.get());
        }, 10L, TimeUnit.SECONDS);
    }

    private static Map createColumnMap(int numCols, String prefix) {
        Map<String, Object> map = new HashMap<>(numCols);
        for (int i = 0; i < numCols; i++) {
            map.put(String.format("%s_col_%d", prefix, i), i);
        }
        return map;
    }

    @Test
    public void testConcurrentUpdatesOnPartitionedTableDoesNotBreakColumnPositions() throws Exception {

        execute("""
                    create table t (a int, b object as (x int)) partitioned by (a) with (number_of_replicas='0')
                    """);
        ensureYellow();

        execute("""
                    insert into t values (1, {x=1})
                    """);
        execute("refresh table t");

        execute("select * from t");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                         1| {x=1}
                                                         """);

        Thread concurrentUpdates1 = new Thread(() -> {
            execute("update t set b['newcol1'] = 1 where b['x'] = 1");
            execute("update t set b['newcol2'] = 2 where b['x'] = 1");
            execute("update t set b['newcol3'] = 3 where b['x'] = 1");
            execute("update t set b['newcol4'] = 4 where b['x'] = 1");
            execute("update t set b['newcol5'] = 5 where b['x'] = 1");
            execute("update t set b['newcol6'] = 6 where b['x'] = 1");
            execute("update t set b['newcol7'] = 7 where b['x'] = 1");
            execute("update t set b['newcol8'] = 8 where b['x'] = 1");
            execute("update t set b['newcol9'] = 9 where b['x'] = 1");
            execute("update t set b['newcol10'] = 10 where b['x'] = 1");
        });
        Thread concurrentUpdates2 = new Thread(() -> {
            execute("update t set b['newcol11'] = 11 where b['x'] = 1");
            execute("update t set b['newcol12'] = 12 where b['x'] = 1");
            execute("update t set b['newcol13'] = 13 where b['x'] = 1");
            execute("update t set b['newcol14'] = 14 where b['x'] = 1");
            execute("update t set b['newcol15'] = 15 where b['x'] = 1");
            execute("update t set b['newcol16'] = 16 where b['x'] = 1");
            execute("update t set b['newcol17'] = 17 where b['x'] = 1");
            execute("update t set b['newcol18'] = 18 where b['x'] = 1");
            execute("update t set b['newcol19'] = 19 where b['x'] = 1");
            execute("update t set b['newcol20'] = 20 where b['x'] = 1");
        });
        Thread concurrentUpdates3 = new Thread(() -> {
            execute("update t set b['newcol21'] = 21 where b['x'] = 1");
            execute("update t set b['newcol22'] = 22 where b['x'] = 1");
            execute("update t set b['newcol23'] = 23 where b['x'] = 1");
            execute("update t set b['newcol24'] = 24 where b['x'] = 1");
            execute("update t set b['newcol25'] = 25 where b['x'] = 1");
            execute("update t set b['newcol26'] = 26 where b['x'] = 1");
            execute("update t set b['newcol27'] = 27 where b['x'] = 1");
            execute("update t set b['newcol28'] = 28 where b['x'] = 1");
            execute("update t set b['newcol29'] = 29 where b['x'] = 1");
            execute("update t set b['newcol30'] = 30 where b['x'] = 1");
        });

        concurrentUpdates1.start();
        concurrentUpdates2.start();
        concurrentUpdates3.start();

        concurrentUpdates1.join();
        concurrentUpdates2.join();
        concurrentUpdates3.join();

        execute("refresh table t");

        execute("select count(distinct ordinal_position) from information_schema.columns where table_name = 't'");
        assertThat(response.rows()[0][0]).isEqualTo(33L);

        execute("select column_name, ordinal_position from information_schema.columns where table_name = 't' order by ordinal_position limit 3");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                            a| 1
                                                            b| 2
                                                            b['x']| 3
                                                            """);
    }

    @Test
    public void testConcurrentUpdatesDoesNotBreakColumnPositions() throws Exception {

        execute("""
                    create table t (a int, b object as (x int)) with (number_of_replicas='0')
                    """);
        ensureYellow();

        execute("""
                    insert into t values (1, {x=1})
                    """);
        execute("refresh table t");

        execute("select * from t");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                         1| {x=1}
                                                         """);

        Thread concurrentUpdates1 = new Thread(() -> {
            execute("update t set b['newcol1'] = 1 where b['x'] = 1");
            execute("update t set b['newcol2'] = 2 where b['x'] = 1");
            execute("update t set b['newcol3'] = 3 where b['x'] = 1");
            execute("update t set b['newcol4'] = 4 where b['x'] = 1");
            execute("update t set b['newcol5'] = 5 where b['x'] = 1");
            execute("update t set b['newcol6'] = 6 where b['x'] = 1");
            execute("update t set b['newcol7'] = 7 where b['x'] = 1");
            execute("update t set b['newcol8'] = 8 where b['x'] = 1");
            execute("update t set b['newcol9'] = 9 where b['x'] = 1");
            execute("update t set b['newcol10'] = 10 where b['x'] = 1");
        });
        Thread concurrentUpdates2 = new Thread(() -> {
            execute("update t set b['newcol11'] = 11 where b['x'] = 1");
            execute("update t set b['newcol12'] = 12 where b['x'] = 1");
            execute("update t set b['newcol13'] = 13 where b['x'] = 1");
            execute("update t set b['newcol14'] = 14 where b['x'] = 1");
            execute("update t set b['newcol15'] = 15 where b['x'] = 1");
            execute("update t set b['newcol16'] = 16 where b['x'] = 1");
            execute("update t set b['newcol17'] = 17 where b['x'] = 1");
            execute("update t set b['newcol18'] = 18 where b['x'] = 1");
            execute("update t set b['newcol19'] = 19 where b['x'] = 1");
            execute("update t set b['newcol20'] = 20 where b['x'] = 1");
        });
        Thread concurrentUpdates3 = new Thread(() -> {
            execute("update t set b['newcol21'] = 21 where b['x'] = 1");
            execute("update t set b['newcol22'] = 22 where b['x'] = 1");
            execute("update t set b['newcol23'] = 23 where b['x'] = 1");
            execute("update t set b['newcol24'] = 24 where b['x'] = 1");
            execute("update t set b['newcol25'] = 25 where b['x'] = 1");
            execute("update t set b['newcol26'] = 26 where b['x'] = 1");
            execute("update t set b['newcol27'] = 27 where b['x'] = 1");
            execute("update t set b['newcol28'] = 28 where b['x'] = 1");
            execute("update t set b['newcol29'] = 29 where b['x'] = 1");
            execute("update t set b['newcol30'] = 30 where b['x'] = 1");
        });

        concurrentUpdates1.start();
        concurrentUpdates2.start();
        concurrentUpdates3.start();

        concurrentUpdates1.join();
        concurrentUpdates2.join();
        concurrentUpdates3.join();

        execute("refresh table t");

        execute("select count(distinct ordinal_position) from information_schema.columns where table_name = 't'");
        assertThat(response.rows()[0][0]).isEqualTo(33L);

        execute("select column_name, ordinal_position from information_schema.columns where table_name = 't' order by ordinal_position limit 3");
        assertThat(printedTable(response.rows())).isEqualTo("""
                                                            a| 1
                                                            b| 2
                                                            b['x']| 3
                                                            """);
    }
}
