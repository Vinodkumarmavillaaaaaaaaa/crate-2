/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test;

import com.carrotsearch.hppc.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryRequest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexTemplateMetadata;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.util.concurrent.FutureUtils;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.indices.IndexTemplateMissingException;
import org.elasticsearch.repositories.RepositoryMissingException;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

/**
 * Base test cluster that exposes the basis to run tests against any elasticsearch cluster, whose layout
 * (e.g. number of nodes) is predefined and cannot be changed during the tests execution
 */
public abstract class TestCluster implements Closeable {

    protected final Logger logger = LogManager.getLogger(getClass());
    private final long seed;

    protected Random random;

    public TestCluster(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    /**
     * This method should be executed before each test to reset the cluster to its initial state.
     */
    public void beforeTest(Random random) throws IOException, InterruptedException {
        this.random = new Random(random.nextLong());
    }

    /**
     * Wipes any data that a test can leave behind: indices, templates (except exclude templates) and repositories
     */
    public void wipe(Set<String> excludeTemplates) {
        wipeIndices("_all");
        wipeAllTemplates(excludeTemplates);
        wipeRepositories();
    }

    /**
     * Assertions that should run before the cluster is wiped should be called in this method
     */
    public void beforeIndexDeletion() throws Exception {
    }

    /**
     * This method checks all the things that need to be checked after each test
     */
    public void assertAfterTest() throws IOException {
        ensureEstimatedStats();
    }

    /**
     * This method should be executed during tear down, after each test (but after assertAfterTest)
     */
    public abstract void afterTest() throws IOException;

    /**
     * Returns a client connected to any node in the cluster
     */
    public abstract Client client();

    /**
     * Returns the number of nodes in the cluster.
     */
    public abstract int size();

    /**
     * Returns the number of data nodes in the cluster.
     */
    public abstract int numDataNodes();

    /**
     * Returns the number of data and master eligible nodes in the cluster.
     */
    public abstract int numDataAndMasterNodes();

    /**
     * Returns the http addresses of the nodes within the cluster.
     * Can be used to run REST tests against the test cluster.
     */
    public abstract InetSocketAddress[] httpAddresses();

    /**
     * Closes the current cluster
     */
    @Override
    public abstract void close() throws IOException;

    /**
     * Deletes the given indices from the tests cluster. If no index name is passed to this method
     * all indices are removed.
     */
    public void wipeIndices(String... indices) {
        assert indices != null && indices.length > 0;
        if (size() > 0) {
            try {
                var acknowledgedResponse = FutureUtils.get(client().admin().indices().delete(new DeleteIndexRequest(indices)));
                assertAcked(acknowledgedResponse);
            } catch (IndexNotFoundException e) {
                // ignore
            } catch (IllegalArgumentException e) {
                // Happens if `action.destructive_requires_name` is set to true
                // which is the case in the CloseIndexDisableCloseAllTests
                if ("_all".equals(indices[0])) {
                    var clusterStateResponse = FutureUtils
                        .get(client().admin().cluster().state(new ClusterStateRequest()));
                    ObjectArrayList<String> concreteIndices = new ObjectArrayList<>();
                    for (IndexMetadata indexMetadata : clusterStateResponse.getState().metadata()) {
                        concreteIndices.add(indexMetadata.getIndex().getName());
                    }
                    if (!concreteIndices.isEmpty()) {
                        var acknowledgedResponse = FutureUtils.get(client().admin().indices().delete(new DeleteIndexRequest(concreteIndices.toArray(String.class))));
                        assertAcked(acknowledgedResponse);
                    }
                }
            }
        }
    }

    /**
     * Removes all templates, except the templates defined in the exclude
     */
    public void wipeAllTemplates(Set<String> exclude) {
        if (size() > 0) {
            GetIndexTemplatesResponse response = FutureUtils.get(client().admin().indices().getTemplates(new GetIndexTemplatesRequest()));
            for (IndexTemplateMetadata indexTemplate : response.getIndexTemplates()) {
                if (exclude.contains(indexTemplate.getName())) {
                    continue;
                }
                try {
                    FutureUtils.get(client().admin().indices().deleteTemplate(new DeleteIndexTemplateRequest(indexTemplate.getName())));
                } catch (IndexTemplateMissingException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Deletes repositories, supports wildcard notation.
     */
    public void wipeRepositories(String... repositories) {
        if (size() > 0) {
            // if nothing is provided, delete all
            if (repositories.length == 0) {
                repositories = new String[]{"*"};
            }
            for (String repository : repositories) {
                try {
                    client().admin().cluster().execute(DeleteRepositoryAction.INSTANCE, new DeleteRepositoryRequest().name(repository)).get();
                } catch (InterruptedException | ExecutionException | RepositoryMissingException ex) {
                    // ignore
                }
            }
        }
    }

    /**
     * Ensures that any breaker statistics are reset to 0.
     *
     * The implementation is specific to the test cluster, because the act of
     * checking some breaker stats can increase them.
     */
    public abstract void ensureEstimatedStats();

    /**
     * Returns the cluster name
     */
    public abstract String getClusterName();

    /**
     * Returns an {@link Iterable} over all clients in this test cluster
     */
    public abstract Iterable<Client> getClients();

    /**
     * Returns this clusters {@link NamedWriteableRegistry} this is needed to
     * deserialize binary content from this cluster that might include custom named writeables
     */
    public abstract NamedWriteableRegistry getNamedWriteableRegistry();
}
