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

package org.elasticsearch.action.admin.indices.mapping.put;

import com.carrotsearch.hppc.ObjectHashSet;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.Version;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.MapperService;

import io.crate.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;


/**
 * Puts mapping definition into one or more indices.
 * <p>
 * If the mappings already exists, the new mappings will be merged with the new one. If there are elements
 * that can't be merged are detected, the request will be rejected.
 *
 * @see org.elasticsearch.client.IndicesAdminClient#putMapping(PutMappingRequest)
 * @see AcknowledgedResponse
 */
public class PutMappingRequest extends AcknowledgedRequest<PutMappingRequest> implements IndicesRequest.Replaceable, ToXContentObject {

    private static ObjectHashSet<String> RESERVED_FIELDS = ObjectHashSet.from(
        "_uid",
        "_id",
        "_type",
        "_source",
        "_all",
        "_analyzer",
        "_parent",
        "_routing",
        "_index",
        "_size",
        "_timestamp",
        "_ttl",
        "_field_names"
    );

    private String[] indices;

    private IndicesOptions indicesOptions = IndicesOptions.fromOptions(false, false, true, true);

    private String source;

    private Index concreteIndex;

    public PutMappingRequest() {
    }

    /**
     * Constructs a new put mapping request against one or more indices. If nothing is set then
     * it will be executed against all indices.
     */
    public PutMappingRequest(String... indices) {
        this.indices = indices;
    }

    /**
     * Sets the indices this put mapping operation will execute on.
     */
    @Override
    public PutMappingRequest indices(String... indices) {
        this.indices = indices;
        return this;
    }

    /**
     * Sets a concrete index for this put mapping request.
     */
    public PutMappingRequest setConcreteIndex(Index index) {
        Objects.requireNonNull(indices, "index must not be null");
        this.concreteIndex = index;
        return this;
    }

    /**
     * Returns a concrete index for this mapping or <code>null</code> if no concrete index is defined
     */
    public Index getConcreteIndex() {
        return concreteIndex;
    }

    /**
     * The indices the mappings will be put.
     */
    @Override
    public String[] indices() {
        return indices;
    }

    @Override
    public IndicesOptions indicesOptions() {
        return indicesOptions;
    }

    public PutMappingRequest indicesOptions(IndicesOptions indicesOptions) {
        this.indicesOptions = indicesOptions;
        return this;
    }

    /**
     * The mapping source definition.
     */
    public String source() {
        return source;
    }

    /**
     * A specialized simplified mapping source method, takes the form of simple properties definition:
     * ("field1", "type=string,store=true").
     *
     * Also supports metadata mapping fields such as `_all` and `_parent` as property definition, these metadata
     * mapping fields will automatically be put on the top level mapping object.
     */
    public PutMappingRequest source(Object... source) {
        return source(buildFromSimplifiedDef(MapperService.SINGLE_MAPPING_NAME, source));
    }

    /**
     * @param type
     *            the mapping type
     * @param source
     *            consisting of field/properties pairs (e.g. "field1",
     *            "type=string,store=true")
     * @throws IllegalArgumentException
     *             if the number of the source arguments is not divisible by two
     * @return the mappings definition
     */
    public static XContentBuilder buildFromSimplifiedDef(String type, Object... source) {
        if (source.length % 2 != 0) {
            throw new IllegalArgumentException("mapping source must be pairs of fieldnames and properties definition.");
        }
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.startObject();
            if (type != null) {
                builder.startObject(type);
            }

            for (int i = 0; i < source.length; i++) {
                String fieldName = source[i++].toString();
                if (RESERVED_FIELDS.contains(fieldName)) {
                    builder.startObject(fieldName);
                    String[] s1 = Strings.splitStringByCommaToArray(source[i].toString());
                    for (String s : s1) {
                        String[] s2 = Strings.split(s, "=");
                        if (s2.length != 2) {
                            throw new IllegalArgumentException("malformed " + s);
                        }
                        builder.field(s2[0], s2[1]);
                    }
                    builder.endObject();
                }
            }

            builder.startObject("properties");
            for (int i = 0; i < source.length; i++) {
                String fieldName = source[i++].toString();
                if (RESERVED_FIELDS.contains(fieldName)) {
                    continue;
                }

                builder.startObject(fieldName);
                String[] s1 = Strings.splitStringByCommaToArray(source[i].toString());
                for (String s : s1) {
                    String[] s2 = Strings.split(s, "=");
                    if (s2.length != 2) {
                        throw new IllegalArgumentException("malformed " + s);
                    }
                    builder.field(s2[0], s2[1]);
                }
                builder.endObject();
            }
            builder.endObject();
            if (type != null) {
                builder.endObject();
            }
            builder.endObject();
            return builder;
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to generate simplified mapping definition", e);
        }
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequest source(XContentBuilder mappingBuilder) {
        return source(Strings.toString(mappingBuilder), mappingBuilder.contentType());
    }

    /**
     * The mapping source definition.
     */
    @SuppressWarnings("unchecked")
    public PutMappingRequest source(Map mappingSource) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(mappingSource);
            return source(Strings.toString(builder), XContentType.JSON);
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + mappingSource + "]", e);
        }
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequest source(String mappingSource, XContentType xContentType) {
        return source(new BytesArray(mappingSource), xContentType);
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequest source(BytesReference mappingSource, XContentType xContentType) {
        Objects.requireNonNull(xContentType);
        try {
            this.source = XContentHelper.convertToJson(mappingSource, xContentType);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to convert source to json", e);
        }
    }

    public PutMappingRequest(StreamInput in) throws IOException {
        super(in);
        indices = in.readStringArray();
        indicesOptions = IndicesOptions.readIndicesOptions(in);
        if (in.getVersion().before(Version.V_4_4_0)) {
            String type = in.readOptionalString();
            if (Constants.DEFAULT_MAPPING_TYPE.equals(type) == false) {
                throw new IllegalArgumentException("Expected type [default] but received [" + type + "]");
            }
        }
        source = in.readString();
        if (in.getVersion().before(Version.V_4_3_0)) {
            in.readBoolean(); // updateAllTypes
        }
        concreteIndex = in.readOptionalWriteable(Index::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArrayNullable(indices);
        indicesOptions.writeIndicesOptions(out);
        if (out.getVersion().before(Version.V_4_4_0)) {
            out.writeOptionalString(Constants.DEFAULT_MAPPING_TYPE);
        }
        out.writeString(source);
        if (out.getVersion().before(Version.V_4_3_0)) {
            out.writeBoolean(true); // updateAllTypes
        }
        out.writeOptionalWriteable(concreteIndex);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (source != null) {
            try (InputStream stream = new BytesArray(source).streamInput()) {
                builder.rawValue(stream, XContentType.JSON);
            }
        } else {
            builder.startObject().endObject();
        }
        return builder;
    }
}
