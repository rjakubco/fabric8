/**
 *  Copyright 2005-2016 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.insight.elasticsearch;

import io.fabric8.insight.metrics.model.MetricsStorageService;
import io.fabric8.insight.metrics.model.QueryResult;
import io.fabric8.insight.metrics.mvel.MetricsStorageServiceImpl;
import io.fabric8.insight.storage.StorageService;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractElasticsearchStorage implements StorageService, MetricsStorageService, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractElasticsearchStorage.class);

    private static final SimpleDateFormat indexFormat = new SimpleDateFormat("yyyy.MM.dd");

    private int max = 1000;

    protected Thread thread;

    protected volatile boolean running;

    private BlockingQueue<ActionRequest> queue = new LinkedBlockingQueue<ActionRequest>();

    private MetricsStorageService metricsStorage = new MetricsStorageServiceImpl(this);

    protected void putInsightTemplate() {
        IndicesAdminClient indicesAdminClient = getNode().client().admin().indices();

        String templateText = new Scanner(AbstractElasticsearchStorage.class.getResourceAsStream("/elasticsearch-index-template.json"), "UTF-8").useDelimiter("\\A").next();

        PutIndexTemplateRequest putInsightTemplateRequest = new PutIndexTemplateRequestBuilder(indicesAdminClient, "insight")
                .setSource(templateText)
                .request();

        indicesAdminClient.putTemplate(putInsightTemplateRequest).actionGet();
    }

    @Override
    public void store(String type, long timestamp, QueryResult queryResult) {
        metricsStorage.store(type, timestamp, queryResult);
    }

    @Override
    public void store(String type, long timestamp, String jsonData) {
        Date ts = new Date(timestamp);
        Date utc = new Date(ts.getTime() + ts.getTimezoneOffset() * 60000);
        IndexRequest ir = new IndexRequest()
                .index("insight-"+ indexFormat.format(utc))
                .type(type)
                .source(jsonData)
                .create(true);
        queue.add(ir);
    }

    public void run() {
        while (running) {
            try {
                ActionRequest req = queue.take();
                // Send data
                BulkRequest bulk = new BulkRequest();
                int nb = 0;
                while (req != null && (nb == 0 || nb < max)) {
                    bulk.add(req);
                    nb++;
                    req = queue.poll();
                }
                if (bulk.numberOfActions() > 0) {
                    BulkResponse rep = getNode().client().bulk(bulk).actionGet();
                    for (BulkItemResponse bir : rep.getItems()) {
                        if (bir.isFailed()) {
                            LOGGER.warn("Error executing request: {}", bir.getFailureMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    LOGGER.warn("Error while sending requests", e);
                }
            }
        }
    }

    public abstract Node getNode();
}
