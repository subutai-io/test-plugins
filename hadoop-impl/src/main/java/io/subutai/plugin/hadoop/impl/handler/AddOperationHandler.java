package io.subutai.plugin.hadoop.impl.handler;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.environment.ContainerHostNotFoundException;
import io.subutai.common.environment.Environment;
import io.subutai.common.environment.EnvironmentModificationException;
import io.subutai.common.environment.EnvironmentNotFoundException;
import io.subutai.common.environment.NodeGroup;
import io.subutai.common.environment.Topology;
import io.subutai.common.peer.ContainerHost;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.protocol.PlacementStrategy;
import io.subutai.core.environment.api.EnvironmentManager;
import io.subutai.core.network.api.NetworkManagerException;
import io.subutai.plugin.common.api.AbstractOperationHandler;
import io.subutai.plugin.common.api.ClusterException;
import io.subutai.plugin.hadoop.api.HadoopClusterConfig;
import io.subutai.plugin.hadoop.impl.Commands;
import io.subutai.plugin.hadoop.impl.HadoopImpl;


public class AddOperationHandler extends AbstractOperationHandler<HadoopImpl, HadoopClusterConfig>
{

    private int nodeCount;
    private static final Logger LOGGER = LoggerFactory.getLogger( AddOperationHandler.class );


    public AddOperationHandler( HadoopImpl manager, String clusterName, int nodeCount )
    {
        super( manager, manager.getCluster( clusterName ) );
        this.nodeCount = nodeCount;
        trackerOperation = manager.getTracker().createTrackerOperation( HadoopClusterConfig.PRODUCT_KEY,
                String.format( "Adding node to cluster %s", clusterName ) );
    }


    @Override
    public void run()
    {
        addNode();
    }


    /**
     * Steps: 1) Creates a new container from hadoop template 2) Include node
     */
    public void addNode()
    {
        try
        {
            EnvironmentManager environmentManager = manager.getEnvironmentManager();
            Set<EnvironmentContainerHost> newlyCreatedContainers = new HashSet<>();
            /**
             * first check if there are containers in environment that is not being used in hadoop cluster,
             * if yes, then do not create new containers.
             */
            Environment environment = environmentManager.loadEnvironment( config.getEnvironmentId() );
            int numberOfContainersNotBeingUsed = 0;
            boolean allContainersNotBeingUsed = false;
            for ( ContainerHost containerHost : environment.getContainerHosts() )
            {
                if ( !config.getAllNodes().contains( containerHost.getId() ) )
                {
                    if ( containerHost.getTemplateName().equals( HadoopClusterConfig.TEMPLATE_NAME ) )
                    {
                        if ( !isThisNodeUsedInOtherClusters( containerHost.getId() ) )
                        {
                            allContainersNotBeingUsed = true;
                            numberOfContainersNotBeingUsed++;
                        }
                    }
                }
            }

            if ( ( !allContainersNotBeingUsed ) | ( numberOfContainersNotBeingUsed < nodeCount ) )
            {

                String nodeGroupName = HadoopClusterConfig.PRODUCT_NAME + "_" + System.currentTimeMillis();
                NodeGroup nodeGroup = new NodeGroup( nodeGroupName, HadoopClusterConfig.TEMPLATE_NAME,
                        nodeCount - numberOfContainersNotBeingUsed, 1, 1, new PlacementStrategy( "ROUND_ROBIN" ) );
                Topology topology = new Topology();
                topology.addNodeGroupPlacement( manager.getPeerManager().getLocalPeer(), nodeGroup );


                if ( numberOfContainersNotBeingUsed > 0 )
                {
                    trackerOperation.addLog(
                            "Using " + numberOfContainersNotBeingUsed + " existing containers and creating" + (
                                    nodeCount - numberOfContainersNotBeingUsed ) + " containers." );
                }
                else
                {
                    trackerOperation.addLog( "Creating new containers..." );
                }
                newlyCreatedContainers =
                        environmentManager.growEnvironment( config.getEnvironmentId(), topology, false );
                for ( ContainerHost host : newlyCreatedContainers )
                {
                    config.getDataNodes().add( host.getId() );
                    config.getTaskTrackers().add( host.getId() );
                    trackerOperation.addLog( host.getHostname() + " is added as slave node." );
                }
            }
            else
            {
                trackerOperation.addLog( "Using existing containers that are not taking role in cluster" );
                // update cluster configuration on DB
                int count = 0;
                environment = environmentManager.loadEnvironment( config.getEnvironmentId() );
                for ( EnvironmentContainerHost containerHost : environment.getContainerHosts() )
                {
                    if ( !config.getAllNodes().contains( containerHost.getId() ) )
                    {
                        if ( count <= nodeCount )
                        {
                            config.getDataNodes().add( containerHost.getId() );
                            config.getTaskTrackers().add( containerHost.getId() );
                            trackerOperation.addLog( containerHost.getHostname() + " is added as slave node." );
                            count++;
                            newlyCreatedContainers.add( containerHost );
                        }
                    }
                }
            }

            manager.saveConfig( config );

            // configure ssh keys
            Set<EnvironmentContainerHost> allNodes = new HashSet<>();
            allNodes.addAll( newlyCreatedContainers );
            allNodes.addAll( environment.getContainerHosts() );
            try
            {
                manager.getNetworkManager().exchangeSshKeys( allNodes );
            }
            catch ( NetworkManagerException e )
            {
                logExceptionWithMessage( "Error exchanging with keys", e );
                return;
            }

            // include newly created containers to existing hadoop cluster
            for ( ContainerHost containerHost : newlyCreatedContainers )
            {
                trackerOperation.addLog( "Configuring " + containerHost.getHostname() );
                configureSlaveNode( config, containerHost, environment );
                manager.includeNode( config, containerHost.getHostname() );
            }
            trackerOperation.addLogDone( "Finished." );
        }
        catch ( EnvironmentNotFoundException | EnvironmentModificationException e )
        {
            logExceptionWithMessage( "Error executing operations with environment", e );
        }
        catch ( ClusterException e )
        {
            LOGGER.error( "Could not save cluster configuration", e );
            e.printStackTrace();
        }
    }


    /**
     * Configures newly added slave node.
     *
     * @param config hadoop configuration
     * @param containerHost node to be configured
     * @param environment environment in which given container reside
     */
    private void configureSlaveNode( HadoopClusterConfig config, ContainerHost containerHost, Environment environment )
    {
        try
        {
            // Clear configuration files
            executeCommandOnContainer( containerHost, Commands.getClearMastersCommand() );
            executeCommandOnContainer( containerHost, Commands.getClearSlavesCommand() );
            // Configure NameNode
            ContainerHost namenode = environment.getContainerHostById( config.getNameNode() );
            ContainerHost jobtracker = environment.getContainerHostById( config.getJobTracker() );
            executeCommandOnContainer( containerHost,
                    Commands.getSetMastersCommand( namenode.getHostname(), jobtracker.getHostname(),
                            config.getReplicationFactor() ) );
        }
        catch ( ContainerHostNotFoundException e )
        {
            logExceptionWithMessage( "Error while configuring slave node and getting container host by id", e );
        }
    }


    private void executeCommandOnContainer( ContainerHost containerHost, String command )
    {
        try
        {
            containerHost.execute( new RequestBuilder( command ) );
        }
        catch ( CommandException e )
        {
            logExceptionWithMessage( "Error executing command: " + command, e );
        }
    }


    private void logExceptionWithMessage( String message, Exception e )
    {
        LOGGER.error( message, e );
        trackerOperation.addLogFailed( message );
    }


    private boolean isThisNodeUsedInOtherClusters( String id )
    {
        List<HadoopClusterConfig> configs = manager.getClusters();
        for ( HadoopClusterConfig config1 : configs )
        {
            if ( config1.getAllNodes().contains( id ) )
            {
                return true;
            }
        }
        return false;
    }
}
