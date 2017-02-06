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
package org.apache.solr.ltr.search;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.ltr.LTRRescorer;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.AbstractReRankQuery;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.RankQuery;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltr.feature.FeatureStore;
import ltr.feature.LocalFeatureStores;
import ltr.model.impl.LinearModel;

import ltr.model.impl.Model;
import ltr.util.CommonLtrParams;
import ltr.util.FeatureException;
import ltr.util.LtrException;

/**
 * Plug into solr a rerank model.
 *
 * Learning to Rank Query Parser Syntax: rq={!ltr efi.w=1.4,0.312,0.1,13 reRankDocs=300
 * efi.myCompanyQueryIntent=0.98}
 *
 */
public class LTRQParserPlugin extends QParserPlugin  {
  public static final String NAME = "ltr";
  private static Query defaultQuery = new MatchAllDocsQuery();

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // params for setting custom external info that features can use, like query
  // intent
  static final String EXTERNAL_FEATURE_INFO = "efi.";

  /** query parser plugin: the name of the attribute for setting the model **/
  public static final String MODEL = "model";

  /** query parser plugin: the name of the attribute for setting the model **/
  public static final String FEATURE_STORE_PARAM = "fs";

  /** query parser plugin: default number of documents to rerank **/
  public static final int DEFAULT_RERANK_DOCS = 200;

  /**
   * query parser plugin:the param that will select how the number of document
   * to rerank
   **/
  public static final String RERANK_DOCS = "reRankDocs";

  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
    super.init(args);
    //threadManager = LTRThreadModule.getInstance(args);
    SolrPluginUtils.invokeSetters(this, args);
  }

  @Override
  public QParser createParser(String qstr, SolrParams localParams,
      SolrParams params, SolrQueryRequest req) {
    return new LTRQParser(qstr, localParams, params, req);
  }

  /**
   * Given a set of local SolrParams, extract all of the efi.key=value params into a map
   * @param rqParams map containing the params contained in the rq field
   * @return Map of efi params, where the key is the name of the efi param, and the
   *  value is the value of the efi param
   */
  private static Map<String,String> extractEFIParams(SolrParams rqParams) {
    final Map<String,String> externalFeatureInfo = new HashMap<>();
    final Iterator<String> iterator = rqParams.getParameterNamesIterator();
    while (iterator.hasNext()){
      final String name =  iterator.next();
      if (name.startsWith(EXTERNAL_FEATURE_INFO)) {
        externalFeatureInfo.put(
            name.substring(EXTERNAL_FEATURE_INFO.length()),
            rqParams.get(name));
      }
    }

    return externalFeatureInfo;
  }






  public class LTRQParser extends QParser {
    private final LocalFeatureStores stores = new LocalFeatureStores();
    private final SolrResourceLoader solrResourceLoader;

    public LTRQParser(String qstr, SolrParams localParams, SolrParams params,
        SolrQueryRequest req) {
      super(qstr, localParams, params, req);
      solrResourceLoader = req.getCore()
          .getResourceLoader();
    }

    @Override
    public Query parse() throws SyntaxError {
      // ReRanking Model
      // linear features
      final String featureStoreName;
      if (rqParams != null){
        featureStoreName = rqParams.get(LTRQParserPlugin.FEATURE_STORE_PARAM, CommonLtrParams.DEFAULT_FEATURE_STORE);
      } else {
        featureStoreName = CommonLtrParams.DEFAULT_FEATURE_STORE;
      }

      final FeatureStore featureStore = this.stores
          .getFeatureStoreFromSolrConfigOrResources(featureStoreName,
              solrResourceLoader);
      final LinearModel model;
      try {
        model = new LinearModel(featureStore);
      } catch (FeatureException e) {
        throw new LtrException("Cannot create linear model "+e.toString());
      }
      model.setExternalFeatureInfo(extractEFIParams(rqParams));
      model.setRequest(req);

      final int reRankDocs;

      if (rqParams != null) {
        reRankDocs = rqParams.getInt(RERANK_DOCS, DEFAULT_RERANK_DOCS);
      } else {
        reRankDocs = DEFAULT_RERANK_DOCS;
      }

      if (reRankDocs <= 0) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
          "Must rerank at least 1 document");
      }

      return new LTRQuery(model, reRankDocs);
    }
  }

  /**
   * A learning to rank Query, will incapsulate a learning to rank model, and delegate to it the rescoring
   * of the documents.
   **/
  public class LTRQuery extends AbstractReRankQuery {

    private final Model scoringQuery;

    public LTRQuery(Model scoringQuery, int reRankDocs) {
      super(defaultQuery, reRankDocs, new LTRRescorer(scoringQuery));
      this.scoringQuery = scoringQuery;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + getOuterType().hashCode();
      result = prime * result
          + ((scoringQuery == null) ? 0 : scoringQuery.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!super.equals(obj)) return false;
      if (getClass() != obj.getClass()) return false;
      LTRQuery other = (LTRQuery) obj;
      if (!getOuterType().equals(other.getOuterType())) return false;
      if (scoringQuery == null) {
        if (other.scoringQuery != null) return false;
      } else if (!scoringQuery.equals(other.scoringQuery)) return false;
      return true;
    }

    @Override
    public RankQuery wrap(Query _mainQuery) {
      super.wrap(_mainQuery);
      scoringQuery.setOriginalQuery(_mainQuery);
      return this;
    }

    @Override
    public String toString(String field) {
      return "{!ltr mainQuery='" + mainQuery.toString() + "' scoringQuery='"
          + scoringQuery.toString()
          + "' reRankDocs=" + reRankDocs + "}";
    }

    @Override
    protected Query rewrite(Query rewrittenMainQuery) throws IOException {
      return new LTRQuery(scoringQuery, reRankDocs).wrap(rewrittenMainQuery);
    }

    private LTRQParserPlugin getOuterType() {
      return LTRQParserPlugin.this;
    }
  }

}
