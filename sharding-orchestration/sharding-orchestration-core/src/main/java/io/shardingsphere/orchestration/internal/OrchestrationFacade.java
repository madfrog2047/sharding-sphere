/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.orchestration.internal;

import io.shardingsphere.api.config.MasterSlaveRuleConfiguration;
import io.shardingsphere.api.config.ShardingRuleConfiguration;
import io.shardingsphere.core.config.DataSourceConfiguration;
import io.shardingsphere.core.rule.Authentication;
import io.shardingsphere.core.yaml.YamlRuleConfiguration;
import io.shardingsphere.orchestration.config.OrchestrationConfiguration;
import io.shardingsphere.orchestration.internal.config.ConfigurationService;
import io.shardingsphere.orchestration.internal.listener.ListenerFactory;
import io.shardingsphere.orchestration.internal.state.datasource.DataSourceService;
import io.shardingsphere.orchestration.internal.state.instance.InstanceStateService;
import io.shardingsphere.orchestration.reg.api.RegistryCenter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Orchestration service facade.
 *
 * @author zhangliang
 * @author caohao
 * @author panjuan
 */
@Slf4j
public final class OrchestrationFacade implements AutoCloseable {
    
    private final RegistryCenter regCenter;
    
    private final boolean isOverwrite;
    
    @Getter
    private final ConfigurationService configService;
    
    private final InstanceStateService instanceStateService;
    
    private final DataSourceService dataSourceService;
    
    @Getter
    private final ListenerFactory listenerManager;
    
    public OrchestrationFacade(final OrchestrationConfiguration orchestrationConfig, final Collection<String> shardingSchemaNames) {
        regCenter = RegistryCenterLoader.load(orchestrationConfig.getRegCenterConfig());
        isOverwrite = orchestrationConfig.isOverwrite();
        configService = new ConfigurationService(orchestrationConfig.getName(), regCenter);
        instanceStateService = new InstanceStateService(orchestrationConfig.getName(), regCenter);
        dataSourceService = new DataSourceService(orchestrationConfig.getName(), regCenter);
        if (shardingSchemaNames.isEmpty()) {
            listenerManager = new ListenerFactory(orchestrationConfig.getName(), regCenter, configService.getAllShardingSchemaNames());
        } else {
            listenerManager = new ListenerFactory(orchestrationConfig.getName(), regCenter, shardingSchemaNames);
        }
    }
    
    /**
     * Initialize for sharding orchestration.
     *
     * @param shardingSchemaName sharding schema name
     * @param dataSourceConfigurations data source configuration map
     * @param shardingRuleConfig sharding rule configuration
     * @param configMap config map
     * @param props sharding properties
     */
    public void init(final String shardingSchemaName, final Map<String, DataSourceConfiguration> dataSourceConfigurations, 
                     final ShardingRuleConfiguration shardingRuleConfig, final Map<String, Object> configMap, final Properties props) {
        configService.persistConfiguration(shardingSchemaName, dataSourceConfigurations, shardingRuleConfig, null, configMap, props, isOverwrite);
        instanceStateService.persistShardingInstanceOnline();
        dataSourceService.persistDataSourcesNode();
        listenerManager.initShardingListeners();
    }
    
    /**
     * Initialize for master-slave orchestration.
     * 
     * @param shardingSchemaName sharding schema name
     * @param dataSourceConfigurations data source configuration map
     * @param masterSlaveRuleConfig master-slave rule configuration
     * @param configMap config map
     * @param props properties
     */
    public void init(final String shardingSchemaName, final Map<String, DataSourceConfiguration> dataSourceConfigurations, 
                     final MasterSlaveRuleConfiguration masterSlaveRuleConfig, final Map<String, Object> configMap, final Properties props) {
        configService.persistConfiguration(shardingSchemaName, dataSourceConfigurations, masterSlaveRuleConfig, null, configMap, props, isOverwrite);
        instanceStateService.persistMasterSlaveInstanceOnline();
        dataSourceService.persistDataSourcesNode();
        listenerManager.initMasterSlaveListeners();
    }
    
    /**
     * Initialize for proxy orchestration.
     *
     * @param dataSourceConfigurationMap schema data source configuration map
     * @param schemaRuleMap schema rule map
     * @param authentication authentication
     * @param prop properties
     */
    public void init(final Map<String, Map<String, DataSourceConfiguration>> dataSourceConfigurationMap,
                     final Map<String, YamlRuleConfiguration> schemaRuleMap, final Authentication authentication, final Properties prop) {
        for (Entry<String, Map<String, DataSourceConfiguration>> entry : dataSourceConfigurationMap.entrySet()) {
            YamlRuleConfiguration yamlRuleConfig = schemaRuleMap.get(entry.getKey());
            if (null == yamlRuleConfig.getShardingRule()) {
                configService.persistConfiguration(
                        entry.getKey(), dataSourceConfigurationMap.get(entry.getKey()), yamlRuleConfig.getMasterSlaveRule(), authentication, Collections.<String, Object>emptyMap(), prop, isOverwrite);
            } else {
                configService.persistConfiguration(
                        entry.getKey(), dataSourceConfigurationMap.get(entry.getKey()), yamlRuleConfig.getShardingRule(), authentication, Collections.<String, Object>emptyMap(), prop, isOverwrite);
            }
        }
        instanceStateService.persistProxyInstanceOnline();
        dataSourceService.persistDataSourcesNode();
        listenerManager.initProxyListeners();
    }
    
    @Override
    public void close() {
        try {
            regCenter.close();
            // CHECKSTYLE:OFF
        } catch (final Exception ex) {
            // CHECKSTYLE:ON
            log.warn("RegCenter exception for: {}", ex.getMessage());
        }
    }
}
