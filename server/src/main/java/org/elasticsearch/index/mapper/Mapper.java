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

package org.elasticsearch.index.mapper;

import org.elasticsearch.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.query.QueryShardContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

public abstract class Mapper implements ToXContentFragment, Iterable<Mapper> {

    public static class BuilderContext {
        private final Settings indexSettings;
        private final ContentPath contentPath;
        private final Mapper.ColumnPositionResolver columnPositionResolver;

        public BuilderContext(Settings indexSettings, ContentPath contentPath) {
            Objects.requireNonNull(indexSettings, "indexSettings is required");
            this.contentPath = contentPath;
            this.indexSettings = indexSettings;
            this.columnPositionResolver = new Mapper.ColumnPositionResolver();
        }

        public ContentPath path() {
            return this.contentPath;
        }

        public Settings indexSettings() {
            return this.indexSettings;
        }

        public Version indexCreatedVersion() {
            return Version.indexCreated(indexSettings);
        }

        public void putPositionInfo(Mapper mapper, Integer position) {
            if (position == null) {
                this.columnPositionResolver.addUnpositionedMapper(mapper, contentPath.currentDepth());
            } else {
                this.columnPositionResolver.updateMaxColumnPosition(position);
            }
        }

        public Mapper.ColumnPositionResolver getColumnPositionResolver() {
            return columnPositionResolver;
        }
    }

    public abstract static class Builder<T extends Builder> {

        public String name;

        protected T builder;

        protected Builder(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        /** Returns a newly built mapper. */
        public abstract Mapper build(BuilderContext context);
    }

    public interface TypeParser {

        class ParserContext {

            private final MapperService mapperService;

            private final Function<String, TypeParser> typeParsers;

            private final Version indexVersionCreated;

            private final Supplier<QueryShardContext> queryShardContextSupplier;

            public ParserContext(MapperService mapperService,
                                 Function<String, TypeParser> typeParsers,
                                 Version indexVersionCreated,
                                 Supplier<QueryShardContext> queryShardContextSupplier) {
                this.mapperService = mapperService;
                this.typeParsers = typeParsers;
                this.indexVersionCreated = indexVersionCreated;
                this.queryShardContextSupplier = queryShardContextSupplier;
            }

            public IndexAnalyzers getIndexAnalyzers() {
                return mapperService.getIndexAnalyzers();
            }

            public MapperService mapperService() {
                return mapperService;
            }

            public TypeParser typeParser(String type) {
                return typeParsers.apply(type);
            }

            public Version indexVersionCreated() {
                return indexVersionCreated;
            }

            public Supplier<QueryShardContext> queryShardContextSupplier() {
                return queryShardContextSupplier;
            }

            protected Function<String, TypeParser> typeParsers() {
                return typeParsers;
            }
        }

        Mapper.Builder<?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException;
    }

    private final String simpleName;

    protected Integer position;

    public Mapper(String simpleName) {
        Objects.requireNonNull(simpleName);
        this.simpleName = simpleName;
    }

    /** Returns the simple name, which identifies this mapper against other mappers at the same level in the mappers hierarchy
     * TODO: make this protected once Mapper and FieldMapper are merged together */
    public final String simpleName() {
        return simpleName;
    }

    /** Returns the canonical name which uniquely identifies the mapper against other mappers in a type. */
    public abstract String name();

    /**
     * Returns a name representing the the type of this mapper.
     */
    public abstract String typeName();

    /** Return the merge of {@code mergeWith} into this.
     *  Both {@code this} and {@code mergeWith} will be left unmodified. */
    public abstract Mapper merge(Mapper mergeWith);

    static class ColumnPositionResolver {
        private final Map<Integer, List<Mapper>> unpositionedMappers = new HashMap<>();
        private int maxColumnPosition = 0;

        ColumnPositionResolver resolve(@Nonnull ColumnPositionResolver toResolve) {
            // only toResolve needs to hold a list of mappers to resolve the col positions
            assert this.unpositionedMappers.size() == 0;
            int maxColumnPosition = Math.max(this.maxColumnPosition, toResolve.maxColumnPosition);
            for (var e : toResolve.unpositionedMappers.values()) {
                for (Mapper unpositionedMapper : e) {
                    unpositionedMapper.position = ++maxColumnPosition;
                }
            }

            var merged = new ColumnPositionResolver();
            merged.maxColumnPosition = maxColumnPosition;
            return merged;
        }

        private void addUnpositionedMapper(Mapper mapper, int depth) {
            // mappers are input in depth first order but want to convert it to breadth first order.
            // That way, parent's position < children's positions.
            List<Mapper> mappersPerDepths = unpositionedMappers.get(depth);
            if (mappersPerDepths == null) {
                List<Mapper> mapperList = new ArrayList<>();
                mapperList.add(mapper);
                unpositionedMappers.put(depth, mapperList);
            } else {
                mappersPerDepths.add(mapper);
            }
        }

        private void updateMaxColumnPosition(@Nonnull Integer columnPosition) {
            this.maxColumnPosition = Math.max(maxColumnPosition, columnPosition);
        }
    }
}
