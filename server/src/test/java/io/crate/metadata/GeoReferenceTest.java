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

package io.crate.metadata;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.test.ESTestCase;
import org.junit.Test;

public class GeoReferenceTest extends ESTestCase {

    @Test
    public void testStreaming() throws Exception {
        RelationName relationName = new RelationName("doc", "test");
        ReferenceIdent referenceIdent = new ReferenceIdent(relationName, "geo_column");
        GeoReference geoReferenceInfo = new GeoReference(1, referenceIdent, "some_tree", "1m", 3, 0.5d);

        BytesStreamOutput out = new BytesStreamOutput();
        Reference.toStream(geoReferenceInfo, out);
        StreamInput in = out.bytes().streamInput();
        GeoReference geoReferenceInfo2 = Reference.fromStream(in);

        assertThat(geoReferenceInfo2, is(geoReferenceInfo));

        GeoReference geoReferenceInfo3 = new GeoReference(2, referenceIdent, "some_tree", null, null, null);
        out = new BytesStreamOutput();
        Reference.toStream(geoReferenceInfo3, out);
        in = out.bytes().streamInput();
        GeoReference geoReferenceInfo4 = Reference.fromStream(in);

        assertThat(geoReferenceInfo4, is(geoReferenceInfo3));

    }
}
