package org.apache.solr.search.xml;

import org.apache.lucene.queryparser.xml.DOMUtils;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.queryparser.xml.QueryBuilder;
import org.apache.lucene.search.Query;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;

import org.w3c.dom.Element;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Deprecated // in favour of com.bloomberg.news.*.RangeQueryBuilder
public class RangeQueryBuilder implements QueryBuilder {

    protected final IndexSchema schema;

    public RangeQueryBuilder(IndexSchema schema) {
        this.schema = schema;
    }

    @Override
    public Query getQuery(Element e) throws ParserException {
        String field = DOMUtils.getAttributeWithInheritanceOrFail(e,
                "fieldName");
        String lowerTerm = DOMUtils.getAttribute(e, "lowerTerm",null);
        String upperTerm = DOMUtils.getAttribute(e, "upperTerm",null);
        boolean lowerInclusive = DOMUtils.getAttribute(e, "includeLower", true);
        boolean upperInclusive = DOMUtils.getAttribute(e, "includeUpper", true);
        SchemaField sf = schema.getField(field);

        return sf.getType().getRangeQuery(null, sf, lowerTerm, upperTerm,
                lowerInclusive, upperInclusive);
        //the QParser instance is not really used inside getRangeQuery. So passing null.
    }

}