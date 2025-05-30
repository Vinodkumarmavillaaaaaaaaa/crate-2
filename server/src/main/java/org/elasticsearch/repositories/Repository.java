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

package org.elasticsearch.repositories;


import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.lucene.index.IndexCommit;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.RepositoryMetadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.snapshots.IndexShardSnapshotStatus;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;

import io.crate.action.FutureActionListener;
import io.crate.analyze.repositories.TypeSettings;

/**
 * An interface for interacting with a repository in snapshot and restore.
 * <p>
 * Implementations are responsible for reading and writing both metadata and shard data to and from
 * a repository backend.
 * <p>
 * To perform a snapshot:
 * <ul>
 * <li>Data nodes call {@link Repository#snapshotShard}
 * for each shard</li>
 * <li>When all shard calls return master calls {@link #finalizeSnapshot} with possible list of failures</li>
 * </ul>
 */
public interface Repository extends LifecycleComponent {

    /**
     * An factory interface for constructing repositories.
     * See {@link org.elasticsearch.plugins.RepositoryPlugin}.
     */
    interface Factory {
        /**
         * Constructs a repository.
         * @param metadata    metadata for the repository including name and settings
         */
        Repository create(RepositoryMetadata metadata) throws Exception;

        default Repository create(RepositoryMetadata metadata, Function<String, Repository.Factory> typeLookup) throws Exception {
            return create(metadata);
        }

        TypeSettings settings();
    }

    /**
     * Returns metadata about this repository.
     */
    RepositoryMetadata getMetadata();

    /**
     * Reads snapshot description from repository.
     *
     * @param snapshotId  snapshot id
     * @param listener   listener invoked on completion
     * @deprecated Use {@link #getSnapshotInfo(SnapshotId) instead
     */
    void getSnapshotInfo(SnapshotId snapshotId, ActionListener<SnapshotInfo> listener);

    default CompletableFuture<SnapshotInfo> getSnapshotInfo(SnapshotId snapshotId) {
        FutureActionListener<SnapshotInfo, SnapshotInfo> future = FutureActionListener.newInstance();
        getSnapshotInfo(snapshotId, future);
        return future;
    }

    /**
     * Returns global metadata associated with the snapshot.
     *
     * @param snapshotId the snapshot id to load the global metadata from
     * @param listener   listener invoked on completion
     */
    void getSnapshotGlobalMetadata(SnapshotId snapshotId, ActionListener<Metadata> listener);

    /**
     * Returns the index metadata associated with the snapshot.
     *
     * @param snapshotId the snapshot id to load the index metadata from
     * @param indexId    the {@link IndexId} to load the metadata from
     * @param listener   listener invoked on completion
     */
    void getSnapshotIndexMetadata(SnapshotId snapshotId, IndexId indexId, ActionListener<IndexMetadata> listener) throws IOException;

    /**
     * Returns index metadata associated with the snapshot.
     *
     * @param snapshotId the snapshot id to load the index metadata from
     * @param indexIds   the Collection of {@link IndexId} to load the metadata from
     * @param listener   listener invoked on completion
     */
    void getSnapshotIndexMetadata(SnapshotId snapshotId, Collection<IndexId> indexIds, ActionListener<Collection<IndexMetadata>> listener);

    /**
     * Returns a {@link RepositoryData} to describe the data in the repository, including the snapshots
     * and the indices across all snapshots found in the repository.  Throws a {@link RepositoryException}
     * if there was an error in reading the data.
     *
     * @deprecated Use {@link #getRepositoryData()} instead
     */
    void getRepositoryData(ActionListener<RepositoryData> listener);

    /**
     * Returns a {@link RepositoryData} to describe the data in the repository, including the snapshots
     * and the indices across all snapshots found in the repository.  Throws a {@link RepositoryException}
     * if there was an error in reading the data.
     */
    default CompletableFuture<RepositoryData> getRepositoryData() {
        FutureActionListener<RepositoryData, RepositoryData> future = FutureActionListener.newInstance();
        try {
            getRepositoryData(future);
        } catch (Exception e) {
            future.onFailure(e);
        }
        return future;
    }

    /**
     * Finalizes snapshotting process
     * <p>
     * This method is called on master after all shards are snapshotted.
     *
     * @param snapshotId            snapshot id
     * @param shardGenerations      updated shard generations
     * @param startTime             start time of the snapshot
     * @param failure               global failure reason or null
     * @param totalShards           total number of shards
     * @param shardFailures         list of shard failures
     * @param repositoryStateId     the unique id identifying the state of the repository when the snapshot began
     * @param includeGlobalState    include cluster global state
     * @param clusterMetaData       cluster metadata
     * @param repositoryMetaVersion version of the updated repository metadata to write
     * @param listener              listener to be called on completion of the snapshot
     */
    void finalizeSnapshot(SnapshotId snapshotId, ShardGenerations shardGenerations, long startTime, String failure,
                          int totalShards, List<SnapshotShardFailure> shardFailures, long repositoryStateId,
                          boolean includeGlobalState, Metadata clusterMetaData,
                          Version repositoryMetaVersion, ActionListener<SnapshotInfo> listener);

    /**
     * Deletes snapshot
     *
     * @param snapshotId            snapshot id
     * @param repositoryStateId     the unique id identifying the state of the repository when the snapshot deletion began
     * @param repositoryMetaVersion version of the updated repository metadata to write
     * @param listener              completion listener
     */
    void deleteSnapshot(SnapshotId snapshotId, long repositoryStateId, Version repositoryMetaVersion, ActionListener<Void> listener);

    /**
     * Verifies repository on the master node and returns the verification token.
     * <p>
     * If the verification token is not null, it's passed to all data nodes for verification. If it's null - no
     * additional verification is required
     *
     * @return verification token that should be passed to all Index Shard Repositories for additional verification or null
     */
    String startVerification();

    /**
     * Called at the end of repository verification process.
     * <p>
     * This method should perform all necessary cleanup of the temporary files created in the repository
     *
     * @param verificationToken verification request generated by {@link #startVerification} command
     */
    void endVerification(String verificationToken);

    /**
     * Verifies repository settings on data node.
     * @param verificationToken value returned by {@link org.elasticsearch.repositories.Repository#startVerification()}
     * @param localNode         the local node information, for inclusion in verification errors
     */
    void verify(String verificationToken, DiscoveryNode localNode);

    /**
     * Returns true if the repository supports only read operations
     * @return true if the repository is read/only
     */
    boolean isReadOnly();

    /**
     * Creates a snapshot of the shard based on the index commit point.
     * <p>
     * The index commit point can be obtained by using {@link org.elasticsearch.index.engine.Engine#acquireLastIndexCommit} method.
     * Repository implementations shouldn't release the snapshot index commit point. It is done by the method caller.
     * <p>
     * As snapshot process progresses, implementation of this method should update {@link IndexShardSnapshotStatus} object and check
     * {@link IndexShardSnapshotStatus#isAborted()} to see if the snapshot process should be aborted.
     * @param store                 store to be snapshotted
     * @param mapperService         the shards mapper service
     * @param snapshotId            snapshot id
     * @param indexId               id for the index being snapshotted
     * @param snapshotIndexCommit   commit point
     * @param snapshotStatus        snapshot status
     * @param repositoryMetaVersion version of the updated repository metadata to write
     * @param listener              listener invoked on completion
     */
    void snapshotShard(Store store, MapperService mapperService, SnapshotId snapshotId, IndexId indexId, IndexCommit snapshotIndexCommit,
                       IndexShardSnapshotStatus snapshotStatus, Version repositoryMetaVersion, ActionListener<String> listener);

    /**
     * Restores snapshot of the shard.
     * <p>
     * The index can be renamed on restore, hence different {@code shardId} and {@code snapshotShardId} are supplied.
     * @param store           the store to restore the index into
     * @param snapshotId      snapshot id
     * @param indexId         id of the index in the repository from which the restore is occurring
     * @param snapshotShardId shard id (in the snapshot)
     * @param recoveryState   recovery state
     * @param listener        listener to invoke once done
     */
    void restoreShard(Store store, SnapshotId snapshotId, IndexId indexId, ShardId snapshotShardId, RecoveryState recoveryState,
                      ActionListener<Void> listener);

    /**
     * Update the repository with the incoming cluster state. This method is invoked from {@link RepositoriesService#applyClusterState} and
     * thus the same semantics as with {@link org.elasticsearch.cluster.ClusterStateApplier#applyClusterState} apply for the
     * {@link ClusterState} that is passed here.
     *
     * @param state new cluster state
     */
    void updateState(ClusterState state);
}
