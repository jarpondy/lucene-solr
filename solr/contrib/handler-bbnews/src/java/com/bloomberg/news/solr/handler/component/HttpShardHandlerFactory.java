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

package com.bloomberg.news.solr.handler.component;

import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;

import org.apache.solr.handler.component.ReplicaListTransformer;

import java.util.List;
import java.util.Random;

public class HttpShardHandlerFactory extends org.apache.solr.handler.component.HttpShardHandlerFactory {

  protected class SolrHostReplicaListTransformer implements org.apache.solr.handler.component.ReplicaListTransformer {
    private final HostSet hostSet;
    private static final int DEFAULT_MOD = 100;
    private static final String DEFAULT_STRATEGY = "";
    public SolrHostReplicaListTransformer(String replicaStrategy, String replicaPermutationSeed, String replicaPermutationMod, Random r)
    {
      log.debug("Raw: replication strategy = {} ; seed = {} ; mod = {}",
                replicaStrategy, replicaPermutationSeed, replicaPermutationMod);
      if (replicaStrategy == null) {
        log.warn("Missing replication strategy. Defaulting to uniform host weights.");
        replicaStrategy = DEFAULT_STRATEGY;
      }

      int permutationMod;
      if (replicaPermutationMod == null) {
        log.warn("Missing replication mod. Defaulting to {}.", DEFAULT_MOD);
        permutationMod = DEFAULT_MOD;
      } else {
        permutationMod = Integer.parseInt(replicaPermutationMod);
        if (permutationMod <= 0) {
          log.warn("Invalid mod ({}). Defaulting to {}.", permutationMod, DEFAULT_MOD);
          permutationMod = DEFAULT_MOD;
        }
      }

      int permutationSeed;
      if (replicaPermutationSeed == null) {
        permutationSeed = -1;
      } else {
        permutationSeed = Integer.parseInt(replicaPermutationSeed);
      }
      if (permutationSeed < 0 || permutationSeed >= permutationMod) {
        permutationSeed = r.nextInt(permutationMod);
        log.warn("Missing or invalid seed ({}), defaulting to random seed ({}).", replicaPermutationSeed, permutationSeed);
      }

      log.debug("Using: replication strategy = {} ; seed = {} ; mod = {}",
                replicaStrategy, permutationSeed, permutationMod);
      hostSet = new HostSet(replicaStrategy, permutationSeed, permutationMod, r);
    }

    @Override
    public void transform(List<?> choices)
    {
      // Because replicaAffinity=... cannot be combined with preferLocalShards=true or
      // with shards=... we are confident here that casting to List<Replica> is safe.
      hostSet.doTransform((List<Replica>)choices);
    }
  };

  @Override
  protected ReplicaListTransformer getReplicaListTransformer(final SolrQueryRequest req)
  {
    SolrParams params = req.getParams();
    log.debug("getReplicaListTransformer ; params = {}", params);

    String[] replicaAffinities = params.getParams("replicaAffinity");
    if (replicaAffinities != null) {

      // replicaAffinity=... cannot be combined with preferLocalShards=true or with shards=...

      if (params.getBool(CommonParams.PREFER_LOCAL_SHARDS, false) || null != params.get(ShardParams.SHARDS)) {
          log.debug("preferring '{}' or '{}' over replicaAffinity={}", CommonParams.PREFER_LOCAL_SHARDS, ShardParams.SHARDS, replicaAffinities);
        return super.getReplicaListTransformer(req);
      }
      for (String replicaAffinity : replicaAffinities) {
        log.debug("replicaAffinity=={} ; params = {}", replicaAffinity, params);
        if ("solrhost".equals(replicaAffinity)) {
          String replicaStrategy = params.get("replicaAffinity.solrhost.hostWeights");
          String replicaPermutationMod = params.get("replicaAffinity.solrhost.mod");
          String replicaPermutationSeed = params.get("replicaAffinity.solrhost.seed");
          return new SolrHostReplicaListTransformer(replicaStrategy, replicaPermutationSeed, replicaPermutationMod, r);
        }
        log.warn("ignoring unsupported replicaAffinity={}", replicaAffinity);
      }
    }

    return super.getReplicaListTransformer(req);
  }
}
