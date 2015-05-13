package org.apache.solr.handler.component;
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

import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.common.SolrException;

public final class ShardResponse {
  private ShardRequest req;
  private String shard;
  private String nodeName;
  private String shardAddress;  // the specific shard that this response was received from
  private int rspCode;
  private Throwable exception;
  private SolrResponse rsp;
  private Long submitWaitingTime_start = null;
  private Long submitWaitingTime_end = null;
  private Long takeWaitingTime_start = null;
  private Long takeWaitingTime_end = null;
  
  @Override
  public String toString() {
    return "ShardResponse:{shard="+shard+",shardAddress="+shardAddress
            +"\n\trequest=" + req
            +"\n\tresponse=" + rsp
            + (exception==null ? "" : "\n\texception="+ SolrException.toStr(exception))
            + (getSubmitWaitingTime()==null ? "" : "\n\tsubmit waiting time=" + getSubmitWaitingTime())
            + (getTakeWaitingTime()==null ? "" : "\n\ttake waiting time=" + getSubmitWaitingTime())
            + "\n\telapsed time=" + rsp.getElapsedTime()
            +"\n}";
  }

  public Throwable getException()
  {
    return exception;
  }

  public ShardRequest getShardRequest()
  {
    return req;
  }

  public SolrResponse getSolrResponse()
  {
    return rsp;
  }

  public String getShard()
  {
    return shard;
  }

  public String getNodeName()
  {
    return nodeName;
  }
  
  public void setShardRequest(ShardRequest rsp)
  {
    this.req = rsp;
  }

  public void setSolrResponse(SolrResponse rsp)
  {
    this.rsp = rsp;
  }

  void setShard(String shard)
  {
    this.shard = shard;
  }

  void setException(Throwable exception)
  {
    this.exception = exception;
  }

  void setResponseCode(int rspCode)
  {
    this.rspCode = rspCode;
  }
  
  void setNodeName(String nodeName) 
  {
    this.nodeName = nodeName;
  }

  /** What was the shard address that returned this response.  Example:  "http://localhost:8983/solr" */
  public String getShardAddress() { return this.shardAddress; }

  void setShardAddress(String addr) { this.shardAddress = addr; }

  public void setSubmitWaitingTime_start(Long val) { submitWaitingTime_start = val; }
  public void setSubmitWaitingTime_end(Long val) { submitWaitingTime_end = val; }
  public Long getSubmitWaitingTime() {
    if (submitWaitingTime_end != null && submitWaitingTime_start != null) {
      return (submitWaitingTime_end - submitWaitingTime_start);
    } else {
      return null;
    }
  }

  public void setTakeWaitingTime_start(Long val) { takeWaitingTime_start = val; }
  public void setTakeWaitingTime_end(Long val) { takeWaitingTime_end = val; }
  public Long getTakeWaitingTime() {
    if (takeWaitingTime_end != null && takeWaitingTime_start != null) {
      return (takeWaitingTime_end - takeWaitingTime_start);
    } else {
      return null;
    }
  }
  
}
