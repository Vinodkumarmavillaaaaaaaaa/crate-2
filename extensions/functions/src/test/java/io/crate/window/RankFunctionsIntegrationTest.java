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

package io.crate.window;

import static io.crate.testing.TestingHelpers.printedTable;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;

import org.elasticsearch.plugins.Plugin;
import org.junit.Test;

import org.elasticsearch.test.IntegTestCase;

public class RankFunctionsIntegrationTest extends IntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        ArrayList<Class<? extends Plugin>> plugins = new ArrayList<>(super.nodePlugins());
        plugins.add(EnterpriseFunctionsProxyTestPlugin.class);
        return plugins;
    }

    @Test
    public void testGeneralPurposeWindowFunctionsWithStandaloneValues() {
        execute("select col1, col2, " +
                "rank() OVER (partition by col2 order by col1), " +
                "dense_rank() OVER (partition by col2 order by col1)" +
                "from unnest(['A', 'B', 'C', 'A', 'B', 'C', 'A'], [True, True, False, True, False, True, False]) " +
                "order by col2, col1");
        assertThat(printedTable(response.rows()))
            .isEqualTo("""
                A| false| 1| 1
                B| false| 2| 2
                C| false| 3| 3
                A| true| 1| 1
                A| true| 1| 1
                B| true| 3| 2
                C| true| 4| 3
                """);
    }

}
