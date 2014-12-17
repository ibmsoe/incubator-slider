/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.slider.agent.standalone

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.hadoop.yarn.api.records.ApplicationReport
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.slider.agent.AgentMiniClusterTestBase

import static org.apache.slider.api.ResourceKeys.*
import static org.apache.slider.api.StatusKeys.*
import org.apache.slider.client.SliderClient
import static org.apache.slider.common.SliderKeys.*;
import org.apache.slider.core.conf.ConfTreeOperations
import org.apache.slider.core.main.ServiceLauncher
import org.apache.slider.core.persist.ConfTreeSerDeser

import static org.apache.slider.server.appmaster.web.rest.RestPaths.*;
import org.junit.Test

import static org.apache.slider.server.appmaster.management.MetricsKeys.*

@CompileStatic
@Slf4j
class TestStandaloneAgentWeb extends AgentMiniClusterTestBase {
 
  @Test
  public void testStandaloneAgentWeb() throws Throwable {

    describe "create a standalone AM then perform actions on it"
    //launch fake master
    def conf = configuration
    conf.setBoolean(METRICS_LOGGING_ENABLED, true)
    conf.setInt(METRICS_LOGGING_LOG_INTERVAL, 1)
    String clustername = createMiniCluster("", conf, 1, true)


    ServiceLauncher<SliderClient> launcher =
        createStandaloneAM(clustername, true, false)
    SliderClient client = launcher.service
    addToTeardown(client);

    ApplicationReport report = waitForClusterLive(client)
    def realappmaster = report.originalTrackingUrl

    // set up url config to match
    initConnectionFactory(launcher.configuration)


    execHttpRequest(30000) {
      GET(realappmaster)
    }
    execHttpRequest(30000) {
      def metrics = GET(realappmaster, SYSTEM_METRICS)
      log.info metrics
    }
    
    sleep(5000)
    def appmaster = report.trackingUrl

    GET(appmaster)

    log.info GET(appmaster, SYSTEM_PING)
    log.info GET(appmaster, SYSTEM_THREADS)
    log.info GET(appmaster, SYSTEM_HEALTHCHECK)
    log.info GET(appmaster, SYSTEM_METRICS_JSON)
    
    describe "Codahale operations"
    // now switch to the Hadoop URL connection, with SPNEGO escalation
    getWebPage(appmaster)
    getWebPage(appmaster, SYSTEM_THREADS)
    getWebPage(appmaster, SYSTEM_HEALTHCHECK)
    getWebPage(appmaster, SYSTEM_METRICS_JSON)
    
    log.info getWebPage(realappmaster, SYSTEM_METRICS_JSON)

    // get the root page, including some checks for connectivity
    getWebPage(appmaster, {
      HttpURLConnection conn ->
        assertConnectionNotCaching(conn)
    })

    // now some REST gets
    describe "Application REST GETs"

    ConfTreeOperations tree = fetchConfigTree(conf, appmaster, LIVE_RESOURCES)

    log.info tree.toString()
    def liveAM = tree.getComponent(COMPONENT_AM)
    def desiredInstances = liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES);
    assert desiredInstances == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_ACTUAL)

    assert 1 == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_STARTED)
    assert 0 == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_REQUESTING)
    assert 0 == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_FAILED)
    assert 0 == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_COMPLETED)
    assert 0 == liveAM.getMandatoryOptionInt(COMPONENT_INSTANCES_RELEASING)

  }

  public ConfTreeOperations fetchConfigTree(
      YarnConfiguration conf,
      String appmaster,
      String subpath) {
    ConfTreeSerDeser serDeser = new ConfTreeSerDeser()

    def json = getWebPage(
        appmaster,
        SLIDER_PATH_APPLICATION + subpath)
    def ctree = serDeser.fromJson(json)
    ConfTreeOperations tree = new ConfTreeOperations(ctree)
    return tree
  }


}