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
package org.apache.solr.ltr.ranking;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.ltr.log.FeatureLogger;
import org.apache.solr.ltr.model.LTRScoringModel;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.ltr.rest.ManagedFeatureStore;
import org.apache.solr.ltr.rest.ManagedModelStore;
import org.apache.solr.ltr.util.CommonLTRParams;
import org.apache.solr.ltr.util.LTRUtils;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.rest.ManagedResource;
import org.apache.solr.rest.ManagedResourceObserver;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SyntaxError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plug into solr a rerank model.
 *
 * Learning to Rank Query Parser Syntax: rq={!ltr model=6029760550880411648 reRankDocs=300
 * efi.myCompanyQueryIntent=0.98}
 *
 */
public class LTRQParserPlugin extends QParserPlugin implements ResourceLoaderAware, ManagedResourceObserver {
  public static final String NAME = "ltr";

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  // params for setting custom external info that features can use, like query
  // intent
  // TODO: Can we just pass the entire request all the way down to all
  // models/features?
  static final String EXTERNAL_FEATURE_INFO = "efi.";

  private ManagedFeatureStore fr = null;
  private ManagedModelStore mr = null;

  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
    int maxThreads  = LTRUtils.getInt(args.get("LTRMaxThreads"), LTRThreadModule.DEFAULT_MAX_THREADS, "LTRMaxThreads");
    int maxQueryThreads = LTRUtils.getInt(args.get("LTRMaxQueryThreads"), LTRThreadModule.DEFAULT_MAX_QUERYTHREADS, "LTRMaxQueryThreads");
    LTRThreadModule.setThreads(maxThreads, maxQueryThreads);
    LTRThreadModule.initSemaphore();
  }

  @Override
  public QParser createParser(String qstr, SolrParams localParams,
      SolrParams params, SolrQueryRequest req) {
    return new LTRQParser(qstr, localParams, params, req);
  }
  
  /**
   * Given a set of local SolrParams, extract all of the efi.key=value params into a map
   * @param localParams Local request parameters that might conatin efi params
   * @return Map of efi params, where the key is the name of the efi param, and the
   *  value is the value of the efi param
   */
  public static Map<String,String[]> extractEFIParams(SolrParams localParams) {
    final Map<String,String[]> externalFeatureInfo = new HashMap<>();
    for (final Iterator<String> it = localParams.getParameterNamesIterator(); it
        .hasNext();) {
      final String name = it.next();
      if (name.startsWith(EXTERNAL_FEATURE_INFO)) {
        externalFeatureInfo.put(
            name.substring(EXTERNAL_FEATURE_INFO.length()),
            new String[] {localParams.get(name)});
      }
    }
    return externalFeatureInfo;
  }
  

  @Override
  public void inform(ResourceLoader loader) throws IOException {
      SolrResourceLoader solrResourceLoader = (SolrResourceLoader) loader;
      solrResourceLoader.getManagedResourceRegistry().
              registerManagedResource(CommonLTRParams.FEATURE_STORE_END_POINT, ManagedFeatureStore.class, this);

      solrResourceLoader.getManagedResourceRegistry().
              registerManagedResource(CommonLTRParams.MODEL_STORE_END_POINT, ManagedModelStore.class, this);

  }

  @Override
  public void onManagedResourceInitialized(NamedList<?> args, ManagedResource res) throws SolrException {
    if (res instanceof ManagedFeatureStore) {
        fr = (ManagedFeatureStore)res;
    }
    if (res instanceof ManagedModelStore){
        mr = (ManagedModelStore)res;
    }
    if (mr != null && fr != null){
        mr.init(fr);
        // now we can safely load the models
        mr.loadStoredModels();

    }
  }

  public class LTRQParser extends QParser {

    public LTRQParser(String qstr, SolrParams localParams, SolrParams params,
        SolrQueryRequest req) {
      super(qstr, localParams, params, req);

      mr = (ManagedModelStore) req.getCore().getRestManager()
          .getManagedResource(CommonLTRParams.MODEL_STORE_END_POINT);
    }

    @Override
    public Query parse() throws SyntaxError {
      // ReRanking Model
      final String modelName = localParams.get(CommonLTRParams.MODEL);
      if ((modelName == null) || modelName.isEmpty()) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
            "Must provide model in the request");
      }
     
      final LTRScoringModel meta = mr.getModel(modelName);
      if (meta == null) {
        throw new SolrException(ErrorCode.BAD_REQUEST,
            "cannot find " + CommonLTRParams.MODEL + " " + modelName);
      }

      final String modelFeatureStoreName = meta.getFeatureStoreName();
      final Boolean extractFeatures = (Boolean) req.getContext().get(CommonLTRParams.LOG_FEATURES_QUERY_PARAM);
      final String fvStoreName = (String) req.getContext().get(CommonLTRParams.FV_STORE);
      // Check if features are requested and if the model feature store and feature-transform feature store are the same
      final boolean featuresRequestedFromSameStore = (extractFeatures != null && (modelFeatureStoreName.equals(fvStoreName) || fvStoreName == null) ) ? extractFeatures.booleanValue():false;
      
      final ModelQuery reRankModel = new ModelQuery(meta, featuresRequestedFromSameStore);

      // Enable the feature vector caching if we are extracting features, and the features
      // we requested are the same ones we are reranking with 
      if (featuresRequestedFromSameStore) {
        final String fvFeatureFormat = (String) req.getContext().get(CommonLTRParams.FV_FORMAT);
        final FeatureLogger<?> solrLogger = FeatureLogger
            .getFeatureLogger(params.get(CommonLTRParams.FV_RESPONSE_WRITER),
                fvFeatureFormat);
        reRankModel.setFeatureLogger(solrLogger);
        req.getContext().put(CommonLTRParams.LOGGER_NAME, solrLogger);
      }
      req.getContext().put(CommonLTRParams.MODEL, reRankModel);

      int reRankDocs = localParams.getInt(CommonLTRParams.RERANK_DOCS,
          CommonLTRParams.DEFAULT_RERANK_DOCS);
      reRankDocs = Math.max(1, reRankDocs);

      // External features
      reRankModel.setExternalFeatureInfo( extractEFIParams(localParams) );
      reRankModel.setRequest(req);

      return new LTRQuery(reRankModel, reRankDocs);
    }
  }
}