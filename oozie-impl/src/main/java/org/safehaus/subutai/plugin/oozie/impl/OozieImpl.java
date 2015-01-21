package org.safehaus.subutai.plugin.oozie.impl;


import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.safehaus.subutai.common.protocol.EnvironmentBlueprint;
import org.safehaus.subutai.common.protocol.NodeGroup;
import org.safehaus.subutai.common.protocol.PlacementStrategy;
import org.safehaus.subutai.common.settings.Common;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.common.util.UUIDUtil;
import org.safehaus.subutai.core.environment.api.EnvironmentManager;
import org.safehaus.subutai.core.environment.api.helper.Environment;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.PluginDAO;
import org.safehaus.subutai.plugin.common.api.AbstractOperationHandler;
import org.safehaus.subutai.plugin.common.api.ClusterOperationType;
import org.safehaus.subutai.plugin.common.api.ClusterSetupStrategy;
import org.safehaus.subutai.plugin.common.api.NodeOperationType;
import org.safehaus.subutai.plugin.hadoop.api.Hadoop;
import org.safehaus.subutai.plugin.oozie.api.Oozie;
import org.safehaus.subutai.plugin.oozie.api.OozieClusterConfig;
import org.safehaus.subutai.plugin.oozie.api.SetupType;
import org.safehaus.subutai.plugin.oozie.impl.handler.ClusterOperationHandler;
import org.safehaus.subutai.plugin.oozie.impl.handler.NodeOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class OozieImpl implements Oozie
{

    private static final Logger LOG = LoggerFactory.getLogger(OozieImpl.class.getName());
    private Tracker tracker;
    private PluginDAO pluginDao;
    private EnvironmentManager environmentManager;
    private Hadoop hadoopManager;
    private ExecutorService executor;


    public OozieImpl(final Tracker tracker, final EnvironmentManager environmentManager,
                     final Hadoop hadoopManager)
    {
        this.tracker = tracker;
        this.environmentManager = environmentManager;
        this.hadoopManager = hadoopManager;
    }


    public Tracker getTracker()
    {
        return tracker;
    }


    public void setTracker(Tracker tracker)
    {
        this.tracker = tracker;
    }


    public PluginDAO getPluginDao()
    {
        return pluginDao;
    }

    public void setPluginDao(PluginDAO pluginDao)
    {
        this.pluginDao = pluginDao;
    }

    public void setExecutor(ExecutorService executor)
    {
        this.executor = executor;
    }

    public EnvironmentManager getEnvironmentManager()
    {
        return environmentManager;
    }


    public void setEnvironmentManager(EnvironmentManager environmentManager)
    {
        this.environmentManager = environmentManager;
    }

    public Hadoop getHadoopManager()
    {
        return hadoopManager;
    }


    public void setHadoopManager(Hadoop hadoopManager)
    {
        this.hadoopManager = hadoopManager;
    }


    public ExecutorService getExecutor()
    {
        return executor;
    }


    public void init()
    {
        try
        {
            this.pluginDao = new PluginDAO(null);
        } catch (SQLException e)
        {
            LOG.error(e.getMessage(), e);
        }

        executor = Executors.newCachedThreadPool();
    }


    public void destroy()
    {
        tracker = null;
        hadoopManager = null;
        executor.shutdown();
    }


    public UUID installCluster(final OozieClusterConfig config)
    {
        Preconditions.checkNotNull(config, "Configuration is null");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(config.getClusterName()),
                "Cluster name is null or empty");
        AbstractOperationHandler operationHandler =
                new ClusterOperationHandler(this, config, ClusterOperationType.INSTALL);
        executor.execute(operationHandler);
        return operationHandler.getTrackerId();
    }


    public UUID uninstallCluster(final String clusterName)
    {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(clusterName), "Cluster name is null or empty");
        OozieClusterConfig oozieClusterConfig = getCluster(clusterName);
        AbstractOperationHandler operationHandler =
                new ClusterOperationHandler(this, oozieClusterConfig, ClusterOperationType.UNINSTALL);
        executor.execute(operationHandler);
        return operationHandler.getTrackerId();
    }


    public List<OozieClusterConfig> getClusters()
    {
        return pluginDao.getInfo(OozieClusterConfig.PRODUCT_KEY, OozieClusterConfig.class);
    }


    public OozieClusterConfig getCluster(String clusterName)
    {
        return pluginDao.getInfo(OozieClusterConfig.PRODUCT_KEY, clusterName, OozieClusterConfig.class);
    }


    @Override
    public UUID addNode(final String clusterName, final String lxcHostname)
    {
        AbstractOperationHandler h = new NodeOperationHandler(this, clusterName, lxcHostname, NodeOperationType
                .INSTALL);
        executor.execute(h);
        return h.getTrackerId();
    }

    @Override
    public UUID startNode(String clusterName, String lxcHostname)
    {
        AbstractOperationHandler h = new NodeOperationHandler(this, clusterName, lxcHostname, NodeOperationType.START);
        executor.execute(h);
        return h.getTrackerId();
    }

    @Override
    public UUID stopNode(String clusterName, String lxcHostname)
    {
        AbstractOperationHandler h = new NodeOperationHandler(this, clusterName, lxcHostname, NodeOperationType.STOP);
        executor.execute(h);
        return h.getTrackerId();
    }

    @Override
    public UUID checkNode(String clusterName, String lxcHostname)
    {
        AbstractOperationHandler h = new NodeOperationHandler(this, clusterName, lxcHostname, NodeOperationType.STATUS);
        executor.execute(h);
        return h.getTrackerId();
    }

    public ClusterSetupStrategy getClusterSetupStrategy(final Environment environment,
                                                        final OozieClusterConfig config,
                                                        final TrackerOperation po )
    {
        Preconditions.checkNotNull( config, "Oozie cluster config is null" );
        Preconditions.checkNotNull( po, "Product operation is null" );
        if ( config.getSetupType() != SetupType.OVER_HADOOP /*&& config.getSetupType() != SetupType.OVER_ENVIRONMENT */)
        {
            Preconditions.checkNotNull( environment, "Environment is null" );
        }
        else if ( config.getSetupType() == SetupType.OVER_HADOOP)
        {
            return new OverHadoopSetupStrategy( environment, config, po, this );
        }

        return new OozieSetupStrategy(this, po, config);
    }


    public EnvironmentBlueprint getDefaultEnvironmentBlueprint(OozieClusterConfig config)
    {
        EnvironmentBlueprint blueprint = new EnvironmentBlueprint();

        blueprint.setName(String.format("%s-%s", config.getProductKey(), UUIDUtil.generateTimeBasedUUID()));
        blueprint.setExchangeSshKeys(true);
        blueprint.setLinkHosts(true);
        blueprint.setDomainName(Common.DEFAULT_DOMAIN_NAME);

        NodeGroup ng = new NodeGroup();
        ng.setName("Default");
        ng.setNumberOfNodes(config.getNodes().size()); // master +slaves
        ng.setTemplateName(OozieClusterConfig.TEMPLATE_NAME);
        ng.setPlacementStrategy(new PlacementStrategy("MORE_RAM"));
        blueprint.setNodeGroups(Sets.newHashSet(ng));

        return blueprint;

    }

    @Override
    public UUID destroyNode(final String clusterName, final String lxcHostname)
    {
        AbstractOperationHandler h = new NodeOperationHandler(this, clusterName, lxcHostname, NodeOperationType
                .UNINSTALL);
        executor.execute(h);
        return h.getTrackerId();
    }
}
