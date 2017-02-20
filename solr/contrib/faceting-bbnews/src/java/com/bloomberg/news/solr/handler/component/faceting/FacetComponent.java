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

package com.bloomberg.news.solr.handler.component.faceting;

import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.search.DocSet;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.util.Predicate;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class FacetComponent extends org.apache.solr.handler.component.FacetComponent implements SolrCoreAware {
    private final Map<String, String> fieldToExclusionFilenameMap;
    private final Map<String, Set<BytesRef>> fieldToExcludesMap;

    final static String CONFIG_FIELD_EXCLUSIONS = "fieldExclusions";
    final static String CONFIG_FIELD_EXCLUSIONS_FIELD = "field";
    final static String CONFIG_FIELD_EXCLUSIONS_FILE = "file";

    public static Logger log = LoggerFactory.getLogger(FacetComponent.class);

    public FacetComponent() {
        this.fieldToExcludesMap = new HashMap<>();
        this.fieldToExclusionFilenameMap = new HashMap<>();
    }

    @Override
    public void init(NamedList args) {
        super.init(args);

        if (args == null) {
            return;
        }

        final List<NamedList> fieldExclusions = args.getAll(CONFIG_FIELD_EXCLUSIONS);
        for (NamedList fieldExclusion : fieldExclusions) {
            final String field = (String) fieldExclusion.get(CONFIG_FIELD_EXCLUSIONS_FIELD);
            final String file = (String) fieldExclusion.get(CONFIG_FIELD_EXCLUSIONS_FILE);

            if (field == null || file == null) {
                throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,"Missing field/file name, args=" + args);
            }

            fieldToExclusionFilenameMap.put(field, file);
        }

        if (fieldToExclusionFilenameMap.isEmpty()) {
            log.info("No exclusion files");
        } else {
            log.info("Exclusions={}", Arrays.toString(fieldToExclusionFilenameMap.entrySet().toArray()));
        }
    }

    @Override
    public void inform(SolrCore core) {
        final SolrResourceLoader loader = core.getResourceLoader();

        for (Map.Entry<String, String> fieldExclusion : fieldToExclusionFilenameMap.entrySet()) {
            final String field = fieldExclusion.getKey();
            final String file = fieldExclusion.getValue();

            try {
                final List<String> excludedTerms = loader.getLines(file);
                final HashSet<BytesRef> bytesRefs = new HashSet<>(excludedTerms.size());
                for (String term : excludedTerms) {
                    bytesRefs.add(new BytesRef(term));
                }
                fieldToExcludesMap.put(field, bytesRefs);
            } catch (IOException e) {
                throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unable to read file", e);
            }
        }
    }

    @Override
    protected SimpleFacets newSimpleFacets(SolrQueryRequest req, DocSet docSet, SolrParams params, ResponseBuilder rb) {
        return new SimpleFacets(req, docSet, params, rb) {
            @Override
            protected Predicate<BytesRef> newExcludeBytesRefFilter(String field, SolrParams params) {
                final Predicate<BytesRef> parentFilter = super.newExcludeBytesRefFilter(field, params);

                final Set<BytesRef> excludeTerms = fieldToExcludesMap.get(field);
                if (excludeTerms == null) {
                    return parentFilter;
                }

                Predicate<BytesRef> filter = new Predicate<BytesRef>() {
                    @Override
                    public boolean test(BytesRef bytesRef) {
                        return !excludeTerms.contains(bytesRef);
                    }
                };

                if (parentFilter != null) {
                    filter = filter.and(parentFilter);
                }

                return filter;
            }
        };
    }
}
