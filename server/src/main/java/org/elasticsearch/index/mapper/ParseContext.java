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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.IndexSettings;

public abstract class ParseContext implements Iterable<ParseContext.Document> {

    /** Fork of {@link org.apache.lucene.document.Document} with additional functionality. */
    public static class Document implements Iterable<IndexableField> {

        private final Document parent;
        private final String prefix;
        private final List<IndexableField> fields;

        private Document(String path, Document parent) {
            fields = new ArrayList<>();
            this.prefix = path.isEmpty() ? "" : path + ".";
            this.parent = parent;
        }

        public Document() {
            this("", null);
        }

        /**
         * Return a prefix that all fields in this document should have.
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Return the parent document, or null if this is the root document.
         */
        public Document getParent() {
            return parent;
        }

        @Override
        public Iterator<IndexableField> iterator() {
            return fields.iterator();
        }

        public List<IndexableField> getFields() {
            return fields;
        }

        public void add(IndexableField field) {
            // either a meta fields or starts with the prefix
            assert field.name().startsWith("_") || field.name().startsWith(prefix) : field.name() + " " + prefix;
            fields.add(field);
        }

        public IndexableField[] getFields(String name) {
            List<IndexableField> f = new ArrayList<>();
            for (IndexableField field : fields) {
                if (field.name().equals(name)) {
                    f.add(field);
                }
            }
            return f.toArray(new IndexableField[f.size()]);
        }

        /**
         * Returns an array of values of the field specified as the method parameter.
         * This method returns an empty array when there are no
         * matching fields.  It never returns null.
         * If you want the actual numeric field instances back, use {@link #getFields}.
         * @param name the name of the field
         * @return a <code>String[]</code> of field values
         */
        public final String[] getValues(String name) {
            List<String> result = new ArrayList<>();
            for (IndexableField field : fields) {
                if (field.name().equals(name) && field.stringValue() != null) {
                    result.add(field.stringValue());
                }
            }
            return result.toArray(new String[result.size()]);
        }

        public IndexableField getField(String name) {
            for (IndexableField field : fields) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }

        public String get(String name) {
            for (IndexableField f : fields) {
                if (f.name().equals(name) && f.stringValue() != null) {
                    return f.stringValue();
                }
            }
            return null;
        }
    }

    private static class FilterParseContext extends ParseContext {

        private final ParseContext in;

        private FilterParseContext(ParseContext in) {
            this.in = in;
        }

        @Override
        public DocumentMapperParser docMapperParser() {
            return in.docMapperParser();
        }

        @Override
        public boolean isWithinCopyTo() {
            return in.isWithinCopyTo();
        }

        @Override
        public IndexSettings indexSettings() {
            return in.indexSettings();
        }

        @Override
        public SourceToParse sourceToParse() {
            return in.sourceToParse();
        }

        @Override
        public ContentPath path() {
            return in.path();
        }

        @Override
        public XContentParser parser() {
            return in.parser();
        }

        @Override
        public Document rootDoc() {
            return in.rootDoc();
        }

        @Override
        public Document doc() {
            return in.doc();
        }

        @Override
        protected void addDoc(Document doc) {
            in.addDoc(doc);
        }

        @Override
        public RootObjectMapper root() {
            return in.root();
        }

        @Override
        public DocumentMapper docMapper() {
            return in.docMapper();
        }

        @Override
        public MapperService mapperService() {
            return in.mapperService();
        }

        @Override
        public Field version() {
            return in.version();
        }

        @Override
        public void version(Field version) {
            in.version(version);
        }

        @Override
        public SeqNoFieldMapper.SequenceIDFields seqID() {
            return in.seqID();
        }

        @Override
        public void seqID(SeqNoFieldMapper.SequenceIDFields seqID) {
            in.seqID(seqID);
        }

        @Override
        public void addDynamicMapper(Mapper update) {
            in.addDynamicMapper(update);
        }

        @Override
        public List<Mapper> getDynamicMappers() {
            return in.getDynamicMappers();
        }

        @Override
        public Iterator<Document> iterator() {
            return in.iterator();
        }
    }

    public static class InternalParseContext extends ParseContext {

        private final DocumentMapper docMapper;

        private final DocumentMapperParser docMapperParser;

        private final ContentPath path;

        private final XContentParser parser;

        private Document document;

        private final List<Document> documents;

        private final IndexSettings indexSettings;

        private final SourceToParse sourceToParse;

        private Field version;

        private SeqNoFieldMapper.SequenceIDFields seqID;

        private final List<Mapper> dynamicMappers;

        public InternalParseContext(IndexSettings indexSettings, DocumentMapperParser docMapperParser, DocumentMapper docMapper,
                                    SourceToParse source, XContentParser parser) {
            this.indexSettings = indexSettings;
            this.docMapper = docMapper;
            this.docMapperParser = docMapperParser;
            this.path = new ContentPath(0);
            this.parser = parser;
            this.document = new Document();
            this.documents = new ArrayList<>();
            this.documents.add(document);
            this.version = null;
            this.sourceToParse = source;
            this.dynamicMappers = new ArrayList<>();
        }

        @Override
        public DocumentMapperParser docMapperParser() {
            return this.docMapperParser;
        }

        @Override
        public IndexSettings indexSettings() {
            return this.indexSettings;
        }

        @Override
        public SourceToParse sourceToParse() {
            return this.sourceToParse;
        }

        @Override
        public ContentPath path() {
            return this.path;
        }

        @Override
        public XContentParser parser() {
            return this.parser;
        }

        @Override
        public Document rootDoc() {
            return documents.get(0);
        }

        List<Document> docs() {
            return this.documents;
        }

        @Override
        public Document doc() {
            return this.document;
        }

        @Override
        protected void addDoc(Document doc) {
            this.documents.add(doc);
        }

        @Override
        public RootObjectMapper root() {
            return docMapper.root();
        }

        @Override
        public DocumentMapper docMapper() {
            return this.docMapper;
        }

        @Override
        public MapperService mapperService() {
            return docMapperParser.mapperService;
        }

        @Override
        public Field version() {
            return this.version;
        }

        @Override
        public void version(Field version) {
            this.version = version;
        }

        @Override
        public SeqNoFieldMapper.SequenceIDFields seqID() {
            return this.seqID;
        }

        @Override
        public void seqID(SeqNoFieldMapper.SequenceIDFields seqID) {
            this.seqID = seqID;
        }

        @Override
        public void addDynamicMapper(Mapper mapper) {
            dynamicMappers.add(mapper);
        }

        @Override
        public List<Mapper> getDynamicMappers() {
            return dynamicMappers;
        }

        @Override
        public Iterator<Document> iterator() {
            return documents.iterator();
        }
    }

    public abstract DocumentMapperParser docMapperParser();

    /**
     * Return a new context that will be within a copy-to operation.
     */
    public final ParseContext createCopyToContext() {
        return new FilterParseContext(this) {
            @Override
            public boolean isWithinCopyTo() {
                return true;
            }
        };
    }

    public boolean isWithinCopyTo() {
        return false;
    }

    /**
     * Return a new context that has the provided document as the current document.
     */
    public final ParseContext switchDoc(final Document document) {
        return new FilterParseContext(this) {
            @Override
            public Document doc() {
                return document;
            }
        };
    }

    /**
     * Return a new context that will have the provided path.
     */
    public final ParseContext overridePath(final ContentPath path) {
        return new FilterParseContext(this) {
            @Override
            public ContentPath path() {
                return path;
            }
        };
    }

    public abstract IndexSettings indexSettings();

    public abstract SourceToParse sourceToParse();

    public abstract ContentPath path();

    public abstract XContentParser parser();

    public abstract Document rootDoc();

    public abstract Document doc();

    protected abstract void addDoc(Document doc);

    public abstract RootObjectMapper root();

    public abstract DocumentMapper docMapper();

    public abstract MapperService mapperService();

    public abstract Field version();

    public abstract void version(Field version);

    public abstract SeqNoFieldMapper.SequenceIDFields seqID();

    public abstract void seqID(SeqNoFieldMapper.SequenceIDFields seqID);

    /**
     * Add a new mapper dynamically created while parsing.
     */
    public abstract void addDynamicMapper(Mapper update);

    /**
     * Get dynamic mappers created while parsing.
     */
    public abstract List<Mapper> getDynamicMappers();
}
