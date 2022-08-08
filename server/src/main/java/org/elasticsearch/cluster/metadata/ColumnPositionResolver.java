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

package org.elasticsearch.cluster.metadata;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public class ColumnPositionResolver<T> {
    // Depths of the columns are used as keys such that deeper columns take higher column positions. (parent's position < children's positions)
    private final Map<Integer, List<Column<T>>> columnsToReposition = new TreeMap<>(Comparator.naturalOrder());
    private int maxColumnPosition = 0;

    public static <T> ColumnPositionResolver<T> resolve(@Nonnull ColumnPositionResolver<T> toResolve) {
        int maxColumnPosition = toResolve.maxColumnPosition;
        for (var o : toResolve.columnsToReposition.values()) {
            // column position calculation : by depth (ascending) first, columnOrdering (descending) second then by name third
            o.sort((o1, o2) -> {
                if (o1.columnOrdering == null && o2.columnOrdering == null) {
                    return o1.name.compareTo(o2.name);
                } else if (o1.columnOrdering == null) {
                    return 1;
                } else if (o2.columnOrdering == null) {
                    return -1;
                } else return o2.columnOrdering.compareTo(o1.columnOrdering);
            });
            for (Column<T> column : o) {
                column.updatePosition(++maxColumnPosition);
            }
        }

        var merged = new ColumnPositionResolver<T>();
        merged.maxColumnPosition = maxColumnPosition;
        return merged;
    }

    public void addColumnToReposition(String name, Integer columnOrdering, T column, BiConsumer<T, Integer> positionUpdater, int depth) {
        // columnOrdering specifies column order whereas column position specifies exact positions.
        Column<T> c = new Column<>(name, columnOrdering, positionUpdater, column);
        List<Column<T>> columnsPerDepths = columnsToReposition.get(depth);
        if (columnsPerDepths == null) {
            List<Column<T>> columns = new ArrayList<>();
            columns.add(c);
            columnsToReposition.put(depth, columns);
        } else {
            columnsPerDepths.add(c);
        }
    }

    public void updateMaxColumnPosition(@Nonnull Integer columnPosition) {
        this.maxColumnPosition = Math.max(maxColumnPosition, columnPosition);
    }

    public int numberOfColumnsToReposition() {
        return this.columnsToReposition.size();
    }

    public int getMaxColumnPosition() {
        return this.maxColumnPosition;
    }

    private record Column<T>(String name, Integer columnOrdering, BiConsumer<T, Integer> positionUpdater, T column) {

        public void updatePosition(Integer position) {
            this.positionUpdater.accept(column, position);
        }
    }
}
