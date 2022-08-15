/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.elasticsearch.test.IntegTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConcurrencyIntegrationTest extends IntegTestCase {

    private ExecutorService executor;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        executor = Executors.newFixedThreadPool(20);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(500, TimeUnit.MILLISECONDS);
        super.tearDown();
    }

    @Test
    public void testInsertStatementsDoNotShareState() throws Throwable {
        execute("create table t1 (id int primary key, x long) with (number_of_replicas = 0)");
        execute("create table t2 (id int primary key, x string) with (number_of_replicas = 0)");
        execute("create table t3 (x timestamp with time zone) with (number_of_replicas = 0)");
        execute("create table t4 (y string) with (number_of_replicas = 0)");

        final CountDownLatch latch = new CountDownLatch(1000);
        final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();

        String[] statements = new String[]{
            "insert into t1 (id, x) values (1, 10) on conflict (id) do update set x = x + 10 ",
            "insert into t2 (id, x) values (1, 'bar') on conflict (id) do update set x = 'foo' ",
            "insert into t3 (x) values (current_timestamp) ",
            "insert into t4 (y) values ('foo') ",
        };

        // run every statement 5 times, so all will fit into the executors pool
        for (final String statement : statements) {
            for (int i = 0; i < 5; i++) {
                executor.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                while (latch.getCount() > 0) {
                                    execute(statement);
                                    latch.countDown();
                                }
                            } catch (Throwable t) {
                                // ignore VersionConflict.. too many concurrent inserts
                                // retry might not succeed
                                if (!t.getMessage().contains("version conflict")) {
                                    lastThrowable.set(t);
                                }
                            }
                        }
                    }
                );
            }
        }

        latch.await();
        Throwable throwable = lastThrowable.get();
        if (throwable != null) {
            throw throwable;
        }
    }

    @Test
    public void test_column_positions_after_concurrent_mapping_updates_on_non_partitioned_table() throws InterruptedException {
        // update, insert, alter take slightly different paths to update mappings
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
            for (int i = 0; i < 5; i++) {
                execute("update t set b['newcol" + i + "'] = 1 where b['x'] = 1");
            }
        });
        Thread concurrentUpdates2 = new Thread(() -> {
            for (int i = 10; i < 15; i++) {
                execute("update t set b['newcol" + i + "'] = 1 where b['x'] = 1");
            }
        });
        Thread concurrentUpdates3 = new Thread(() -> {
            for (int i = 20; i < 25; i++) {
                execute("update t set b['newcol" + i + "'] = 1 where b['x'] = 1");
            }
        });
        Thread concurrentUpdates4 = new Thread(() -> {
            for (int i = 30; i < 35; i++) {
                execute("alter table t add column b['newcol" + i + "'] int");
            }
        });
        Thread concurrentUpdates5 = new Thread(() -> {
            for (int i = 40; i < 45; i++) {
                execute("alter table t add column b['newcol" + i + "'] int");
            }
        });
        Thread concurrentUpdates6 = new Thread(() -> {
            for (int i = 50; i < 55; i++) {
                execute("alter table t add column b['newcol" + i + "'] int");
            }
        });
        Thread concurrentUpdates7 = new Thread(() -> {
            for (int i = 60; i < 65; i++) {
                execute("insert into t(b) values({newcol" + i + "=1})");
            }
        });
        Thread concurrentUpdates8 = new Thread(() -> {
            for (int i = 70; i < 75; i++) {
                execute("insert into t(b) values({newcol" + i + "=1})");
            }
        });
        Thread concurrentUpdates9 = new Thread(() -> {
            for (int i = 80; i < 85; i++) {
                execute("insert into t(b) values({newcol" + i + "=1})");
            }
        });

        concurrentUpdates1.start();
        concurrentUpdates2.start();
        concurrentUpdates3.start();
        concurrentUpdates4.start();
        concurrentUpdates5.start();
        concurrentUpdates6.start();
        concurrentUpdates7.start();
        concurrentUpdates8.start();
        concurrentUpdates9.start();

        concurrentUpdates1.join();
        concurrentUpdates2.join();
        concurrentUpdates3.join();
        concurrentUpdates4.join();
        concurrentUpdates5.join();
        concurrentUpdates6.join();
        concurrentUpdates7.join();
        concurrentUpdates8.join();
        concurrentUpdates9.join();

        execute("select count(distinct ordinal_position), max(ordinal_position) from information_schema.columns where table_name = 't'");
        assertThat(response.rows()[0][0]).isEqualTo(48L);
        assertThat(response.rows()[0][1]).isEqualTo(48);

        execute("select column_name, ordinal_position from information_schema.columns where table_name = 't' order by ordinal_position limit 3");
        assertThat(printedTable(response.rows())).isEqualTo(
            """
            a| 1
            b| 2
            b['x']| 3
            """);
    }
}
